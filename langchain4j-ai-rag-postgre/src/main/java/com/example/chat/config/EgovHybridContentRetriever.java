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
        String sql = "SELECT " + TEXT_COLUMN + ", metadata FROM " + tableName
                + " WHERE " + TEXT_COLUMN + " % ? ORDER BY similarity(" + TEXT_COLUMN + ", ?) DESC LIMIT ?";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String text = rs.getString(TEXT_COLUMN);
            String metadataJson = rs.getString("metadata");
            return toContent(text, metadataJson);
        }, queryText, queryText, topK);
    }

    /** lexical 결과 행을 Content로 변환한다. metadata JSON의 id만 보존한다. */
    private Content toContent(String text, String metadataJson) {
        Metadata metadata = new Metadata();
        String id = extractId(metadataJson);
        if (id != null) {
            metadata.put("id", id);
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
     */
    private String fusionKey(TextSegment segment) {
        String id = segment.metadata() != null ? segment.metadata().getString("id") : null;
        if (id != null && !id.isBlank()) {
            return "id:" + id;
        }
        String text = segment.text() == null ? "" : segment.text().trim();
        return "hash:" + DocumentHashUtil.calculateHash(text);
    }

    /**
     * metadata JSON 문자열에서 {@code "id"} 값을 추출한다.
     * 정식 JSON 파서 의존을 피하기 위해 단순 키 탐색만 수행하며, 실패 시 null.
     */
    private String extractId(String metadataJson) {
        if (metadataJson == null) {
            return null;
        }
        int keyIdx = metadataJson.indexOf("\"id\"");
        if (keyIdx < 0) {
            return null;
        }
        int colon = metadataJson.indexOf(':', keyIdx + 4);
        if (colon < 0) {
            return null;
        }
        int firstQuote = metadataJson.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = metadataJson.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return metadataJson.substring(firstQuote + 1, secondQuote);
    }
}
