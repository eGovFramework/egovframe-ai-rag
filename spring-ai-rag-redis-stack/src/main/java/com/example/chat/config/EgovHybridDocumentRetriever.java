package com.example.chat.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.SearchResult;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 하이브리드 DocumentRetriever
 *
 * <p>dense 벡터 검색({@link DocumentRetriever} 위임)과 lexical 키워드 검색
 * (RediSearch {@code FT.SEARCH})을 각각 수행한 뒤 {@link EgovRrfFusion}으로
 * 융합해 최종 결과를 반환한다. 두 채널은 단순성을 위해 순차로 호출한다.</p>
 *
 * <p>융합 키는 {@link Document#getId()}를 사용한다. lexical 채널은 RediSearch가
 * 색인한 문서 키 {@code embedding:<id>}에서 prefix를 벗긴 {@code <id>}를 키로
 * 사용하므로(= dense 채널의 {@code getId()}와 동일 값), 같은 문서는 양 채널에서
 * 동일한 융합 키를 가진다.</p>
 *
 * <p>lexical 검색이나 융합 과정에서 오류가 발생하면 dense 결과만 반환해 RAG
 * 기능 자체는 보존한다(graceful degrade). 본 빈은
 * {@code rag.retrieval.hybrid.enabled=true} 일 때만 등록되므로, 기본(off)
 * 상태에서는 dense {@code VectorStoreDocumentRetriever} 빈만 존재해 빈 모호성이
 * 발생하지 않는다.</p>
 */
@Slf4j
public class EgovHybridDocumentRetriever implements DocumentRetriever {

    /** RedisVectorStore가 색인하는 문서 키 prefix. lexical 채널 키 정규화에 사용. */
    static final String KEY_PREFIX = "embedding:";

    /** RediSearch 인덱스의 본문(TEXT) 필드명. RedisVectorStore 기본값. */
    static final String CONTENT_FIELD = "content";

    private final DocumentRetriever denseRetriever;
    private final LexicalSearch lexicalSearch;
    private final String indexName;
    private final double denseWeight;
    private final double lexicalWeight;
    private final int topK;

    /**
     * lexical 채널 추상화. 질의 문자열을 받아 매칭 문서의 id 순위(상위가 앞)를
     * 반환한다. Redis 비의존 단위 테스트를 위해 별도 인터페이스로 분리한다.
     */
    @FunctionalInterface
    public interface LexicalSearch {
        /**
         * @param queryText 사용자 질의(원본)
         * @param topK      최대 회수 개수
         * @return 매칭 문서의 id 순위 리스트(상위가 앞). 매칭 없으면 빈 리스트
         */
        List<String> search(String queryText, int topK);
    }

    /**
     * 운영용 생성자. JedisPooled FT.SEARCH 기반 lexical 채널을 구성한다.
     *
     * @param denseRetriever dense 벡터 검색 빈
     * @param jedisPooled    lexical 검색용 JedisPooled
     * @param indexName      RediSearch 인덱스명(dense와 동일 인덱스 재사용)
     * @param denseWeight    dense 채널 가중치
     * @param lexicalWeight  lexical 채널 가중치
     * @param topK           반환할 상위 문서 개수
     */
    public EgovHybridDocumentRetriever(DocumentRetriever denseRetriever,
                                       JedisPooled jedisPooled,
                                       String indexName,
                                       double denseWeight,
                                       double lexicalWeight,
                                       int topK) {
        this(denseRetriever,
                (queryText, k) -> ftSearch(jedisPooled, indexName, queryText, k),
                indexName, denseWeight, lexicalWeight, topK);
    }

    /**
     * 테스트용 생성자. lexical 채널을 임의로 주입한다.
     */
    EgovHybridDocumentRetriever(DocumentRetriever denseRetriever,
                                LexicalSearch lexicalSearch,
                                String indexName,
                                double denseWeight,
                                double lexicalWeight,
                                int topK) {
        this.denseRetriever = denseRetriever;
        this.lexicalSearch = lexicalSearch;
        this.indexName = indexName;
        this.denseWeight = denseWeight;
        this.lexicalWeight = lexicalWeight;
        this.topK = topK;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // 1) dense 채널 검색 (벡터 유사도)
        List<Document> denseDocuments = denseRetriever.retrieve(query);

        // 2) lexical 채널 검색 (FT.SEARCH). 실패해도 dense 결과는 보존한다.
        List<String> lexicalRanking;
        try {
            lexicalRanking = lexicalSearch.search(query.text(), topK);
        } catch (Exception e) {
            log.warn("lexical 검색 실패 - dense 결과만 사용합니다. 원인: {}", e.getMessage());
            return denseDocuments;
        }

        // 3) 융합 키 → Document 매핑 구성 (먼저 등장한 Document를 대표로 유지)
        Map<String, Document> byKey = new LinkedHashMap<>();
        List<String> denseRanking = new ArrayList<>(denseDocuments.size());
        for (Document doc : denseDocuments) {
            String key = doc.getId();
            denseRanking.add(key);
            byKey.putIfAbsent(key, doc);
        }

        // 4) RRF 융합
        List<String> fusedKeys = EgovRrfFusion.fuse(
                denseRanking, lexicalRanking, denseWeight, lexicalWeight, EgovRrfFusion.DEFAULT_K, topK);

        // lexical 단독 문서(dense에 없는 키)는 본문이 dense 결과에 없으므로 제외하고,
        // 융합 점수로 재정렬된 dense 문서만 컨텍스트에 포함한다. lexical 신호는 순위
        // 가중치로만 반영된다(본문 중복 적재·차원 불일치 우회 - 확정 설계).
        List<Document> result = new ArrayList<>(fusedKeys.size());
        for (String key : fusedKeys) {
            // byKey에는 dense 결과만 담겨 있으므로, lexical 단독 키는 여기서 null이 되어 조용히 제외된다(위 주석 참조).
            Document doc = byKey.get(key);
            if (doc != null) {
                result.add(doc);
            }
        }

        log.debug("하이브리드 검색 - dense: {}, lexical: {}, 융합: {}",
                denseDocuments.size(), lexicalRanking.size(), result.size());
        return result;
    }

    /**
     * JedisPooled FT.SEARCH로 lexical 검색을 수행한다. 정확→접두→중위 순으로
     * 폴백하며, 회수가 발생한 첫 단계의 결과를 사용한다.
     *
     * @return 매칭 문서의 id 순위(상위가 앞). prefix를 벗긴 순수 id
     */
    static List<String> ftSearch(JedisPooled jedis, String indexName, String queryText, int topK) {
        for (String q : EgovLexicalQueryBuilder.buildStagedQueries(queryText)) {
            redis.clients.jedis.search.Query searchQuery =
                    new redis.clients.jedis.search.Query(q)
                            .limit(0, Math.max(topK, 0))
                            .setNoContent();
            SearchResult r = jedis.ftSearch(indexName, searchQuery);
            if (r.getTotalResults() > 0) {
                List<String> ids = new ArrayList<>();
                for (redis.clients.jedis.search.Document d : r.getDocuments()) {
                    ids.add(stripPrefix(d.getId()));
                }
                return ids;
            }
        }
        return List.of();
    }

    /** FT.SEARCH 반환 키 {@code embedding:<id>}에서 prefix를 벗겨 순수 id로 만든다. */
    static String stripPrefix(String key) {
        if (key != null && key.startsWith(KEY_PREFIX)) {
            return key.substring(KEY_PREFIX.length());
        }
        return key;
    }
}
