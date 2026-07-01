package com.example.chat.config;

import com.example.chat.util.DocumentHashUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;
    private final String tableName;
    private final double denseWeight;
    private final double lexicalWeight;
    private final double lexicalWordSimilarityThreshold;
    private final int topK;

    public EgovHybridContentRetriever(ContentRetriever denseRetriever,
                                      JdbcTemplate jdbcTemplate,
                                      PlatformTransactionManager transactionManager,
                                      String tableName,
                                      double denseWeight,
                                      double lexicalWeight,
                                      double lexicalWordSimilarityThreshold,
                                      int topK) {
        this.denseRetriever = denseRetriever;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.tableName = tableName;
        this.denseWeight = denseWeight;
        this.lexicalWeight = lexicalWeight;
        this.lexicalWordSimilarityThreshold = lexicalWordSimilarityThreshold;
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
     * pg_trgm word_similarity 기반 lexical 검색.
     * {@code text %> ?} 연산자로 후보를 거른 뒤 word_similarity 내림차순으로 정렬한다.
     *
     * <p><b>연산자 선택 근거</b> — 대칭 similarity({@code %})는 문서(청크)가 길수록 trigram
     * Jaccard 분모가 커져 값이 붕괴한다. 앱 기본 청크 크기(4000자, {@code DocumentSplitters.recursive})로
     * 색인한 실제 한국어 문서에서 정답 청크의 {@code similarity}는 0.006~0.058(전부 0.06 미만)로,
     * 어떤 실용 임계값에서도 lexical 채널이 무력화된다. 반면 {@code word_similarity}는 질의를
     * 문서의 "가장 잘 맞는 연속 구간"과 비교하므로 청크 길이에 강건하다(동일 코퍼스에서 정답
     * 청크 word_similarity 0.17~1.0). {@code %>}는 {@code text} 컬럼의 GIN trigram 인덱스를
     * 그대로 사용한다(Bitmap Index Scan). {@code text %> ?}는 {@code word_similarity(?, text) >=
     * pg_trgm.word_similarity_threshold}와 동치다.</p>
     */
    private List<Content> lexicalSearch(String queryText) {
        // id는 SQL단에서 metadata::jsonb ->> 'id'로 추출한다. 이렇게 하면 dense 채널의
        // Metadata.getString("id")와 동일한 문자열 값이 보장되어(숫자형·중첩·이스케이프
        // 무관) 융합 키가 채널 간 일치한다. ::jsonb 캐스팅으로 metadata가 text든 jsonb든
        // 안전하게 동작한다.
        String sql = "SELECT " + TEXT_COLUMN + ", metadata::jsonb ->> 'id' AS " + DOC_ID_COLUMN
                + " FROM " + tableName
                + " WHERE " + TEXT_COLUMN + " %> ? ORDER BY word_similarity(?, " + TEXT_COLUMN + ") DESC LIMIT ?";

        // word_similarity 임계값을 트랜잭션 스코프(is_local=true)로만 적용한다. 트랜잭션
        // 커밋/롤백 시 값이 자동 복귀하므로 커넥션 풀에 임계값이 누수되지 않고, %>(GIN trigram
        // 인덱스) 경로도 유지된다. 코퍼스 언어·문서 특성에 맞춰 프로퍼티로 조정한다.
        return transactionTemplate.execute(status -> {
            // set_config는 값을 반환하는 SELECT이므로 queryForObject로 실행한다(update의 executeUpdate는
            // 행을 반환하는 문에서 예외). 반환값은 사용하지 않는다.
            jdbcTemplate.queryForObject("SELECT set_config('pg_trgm.word_similarity_threshold', ?, true)",
                    String.class, Double.toString(lexicalWordSimilarityThreshold));
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String text = rs.getString(TEXT_COLUMN);
                String docId = rs.getString(DOC_ID_COLUMN);
                return toContent(text, docId);
            }, queryText, queryText, topK);
        });
    }

    /**
     * lexical 결과 행을 Content로 변환한다. 융합 키로 쓰는 {@code id}만 보존하고 파일명·소스
     * 경로 등 나머지 메타데이터는 담지 않는다(의도된 설계). 양 채널에 모두 등장한 문서는 dense
     * 채널의 완전한 Content가 융합 대표로 유지되고(먼저 등록되어 우선), lexical 단독 문서는
     * id·text만 컨텍스트에 포함된다 — 본문 자체는 보존되므로 답변 생성에는 지장이 없다.
     */
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
