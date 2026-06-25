package com.example.chat.config;

import com.example.chat.util.DocumentHashUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하이브리드 ContentRetriever
 *
 * <p>dense 벡터 검색({@link ContentRetriever} 위임)과 lexical 키워드 검색
 * (PostgreSQL {@code pg_trgm})을 각각 수행한 뒤 {@link EgovRrfFusion}으로
 * 융합해 최종 결과를 반환한다. 두 채널은 단순성을 위해 순차로 호출한다.</p>
 *
 * <p>융합 키는 메타데이터 {@code id}를 우선 사용하고, 없으면 세그먼트 텍스트의
 * 해시({@link DocumentHashUtil})를 사용한다. lexical 검색이나 융합 과정에서
 * 오류가 발생하면 dense 결과만 반환해 RAG 기능 자체는 보존한다.</p>
 *
 * <p>본 빈은 {@code rag.retrieval.hybrid.enabled=true} 일 때만 등록되므로,
 * 기본(off) 상태에서는 dense 빈만 존재해 빈 모호성이 발생하지 않는다.</p>
 */
@Slf4j
public class EgovHybridContentRetriever implements ContentRetriever {

    /** lexical 검색 대상 테이블의 텍스트 컬럼명. PgVectorEmbeddingStore 기본값. */
    private static final String TEXT_COLUMN = "text";

    /** SQL에서 metadata JSON으로부터 추출한 id 별칭 컬럼명. */
    private static final String DOC_ID_COLUMN = "doc_id";

    private final ContentRetriever denseRetriever;
    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final double denseWeight;
    private final double lexicalWeight;
    private final int topK;

    public EgovHybridContentRetriever(ContentRetriever denseRetriever,
                                      JdbcTemplate jdbcTemplate,
                                      String tableName,
                                      double denseWeight,
                                      double lexicalWeight,
                                      int topK) {
        this.denseRetriever = denseRetriever;
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.denseWeight = denseWeight;
        this.lexicalWeight = lexicalWeight;
        this.topK = topK;
    }

    @Override
    public List<Content> retrieve(Query query) {
        // 1) dense 채널 검색 (벡터 유사도)
        List<Content> denseContents = denseRetriever.retrieve(query);

        // 2) lexical 채널 검색 (pg_trgm). 실패해도 dense 결과는 보존한다.
        List<Content> lexicalContents;
        try {
            lexicalContents = lexicalSearch(query.text());
        } catch (Exception e) {
            log.warn("lexical 검색 실패 - dense 결과만 사용합니다. 원인: {}", e.getMessage());
            return denseContents;
        }

        // 3) 융합 키 → Content 매핑 구성 (먼저 등장한 Content를 대표로 유지)
        Map<String, Content> byKey = new LinkedHashMap<>();
        List<String> denseRanking = toRanking(denseContents, byKey);
        List<String> lexicalRanking = toRanking(lexicalContents, byKey);

        // 4) RRF 융합
        List<String> fusedKeys = EgovRrfFusion.fuse(
                denseRanking, lexicalRanking, denseWeight, lexicalWeight, EgovRrfFusion.DEFAULT_K, topK);

        List<Content> result = new ArrayList<>(fusedKeys.size());
        for (String key : fusedKeys) {
            Content content = byKey.get(key);
            if (content != null) {
                result.add(content);
            }
        }

        log.debug("하이브리드 검색 - dense: {}, lexical: {}, 융합: {}",
                denseContents.size(), lexicalContents.size(), result.size());
        return result;
    }

    /**
     * pg_trgm 유사도 기반 lexical 검색.
     * {@code text % ?} 연산자로 후보를 거른 뒤 similarity 내림차순으로 정렬한다.
     */
    private List<Content> lexicalSearch(String queryText) {
        // id는 SQL단에서 metadata::jsonb ->> 'id'로 추출한다. 이렇게 하면 dense 채널의
        // Metadata.getString("id")와 동일한 문자열 값이 보장되어(숫자형·중첩·이스케이프
        // 무관) 융합 키가 채널 간 일치한다. ::jsonb 캐스팅으로 metadata가 text든 jsonb든
        // 안전하게 동작한다.
        String sql = "SELECT " + TEXT_COLUMN + ", metadata::jsonb ->> 'id' AS " + DOC_ID_COLUMN
                + " FROM " + tableName
                + " WHERE " + TEXT_COLUMN + " % ? ORDER BY similarity(" + TEXT_COLUMN + ", ?) DESC LIMIT ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String text = rs.getString(TEXT_COLUMN);
            String docId = rs.getString(DOC_ID_COLUMN);
            return toContent(text, docId);
        }, queryText, queryText, topK);
    }

    /** lexical 결과 행을 Content로 변환한다. SQL로 추출한 id만 보존한다. */
    private Content toContent(String text, String docId) {
        Metadata metadata = new Metadata();
        if (docId != null && !docId.isBlank()) {
            metadata.put("id", docId);
        }
        return Content.from(TextSegment.from(text, metadata));
    }

    /**
     * Content 리스트를 융합 키 순위로 변환하면서 키→Content 매핑에 등록한다.
     */
    private List<String> toRanking(List<Content> contents, Map<String, Content> byKey) {
        List<String> ranking = new ArrayList<>(contents.size());
        for (Content content : contents) {
            String key = fusionKey(content.textSegment());
            ranking.add(key);
            byKey.putIfAbsent(key, content);
        }
        return ranking;
    }

    /**
     * 융합 키 산출: 메타데이터 {@code id} 우선, 없으면 텍스트 해시.
     *
     * <p>dense 채널은 langchain4j {@code Metadata.getString("id")}로, lexical 채널은
     * SQL {@code metadata::jsonb ->> 'id'}로 동일한 {@code id} 값을 보존하므로 같은
     * 문서는 양 채널에서 동일한 융합 키를 가진다.</p>
     */
    private String fusionKey(TextSegment segment) {
        String id = segment.metadata() != null ? segment.metadata().getString("id") : null;
        if (id != null && !id.isBlank()) {
            return "id:" + id;
        }
        // id가 없을 때만 텍스트 해시로 폴백한다. 빈/null 텍스트는 dedup 과도 병합을
        // 막기 위해 별도 키로 분리한다.
        String text = segment.text() == null ? "" : segment.text().trim();
        if (text.isEmpty()) {
            return "empty:" + System.identityHashCode(segment);
        }
        return "hash:" + DocumentHashUtil.calculateHash(text);
    }
}
