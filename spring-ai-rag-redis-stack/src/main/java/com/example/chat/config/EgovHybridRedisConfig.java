package com.example.chat.config;

import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.JedisPooled;

import lombok.extern.slf4j.Slf4j;

/**
 * 하이브리드 검색용 Redis 구성
 *
 * <p>{@code rag.retrieval.hybrid.enabled=true} 일 때만 lexical 채널용
 * {@link JedisPooled} 와 {@link EgovHybridDocumentRetriever} 빈을 등록한다.
 * off(기본) 상태에서는 빈이 만들어지지 않으므로 dense
 * {@link VectorStoreDocumentRetriever} 빈만 존재하여 빈 모호성이 발생하지
 * 않는다.</p>
 *
 * <p>Spring AI {@code RedisVectorStore}는 내부 Jedis 클라이언트를 노출하지
 * 않으므로, {@code spring.data.redis.host/port} 값으로 lexical 채널 전용
 * JedisPooled를 별도 구성한다. 인덱스는 dense와 동일한
 * {@code spring.ai.vectorstore.redis.index-name}을 재사용한다(보조 인덱스
 * 신설 없음 - 차원 불일치 방지).</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rag.retrieval.hybrid", name = "enabled", havingValue = "true")
public class EgovHybridRedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.ai.vectorstore.redis.index-name:spring-ai-index}")
    private String indexName;

    @Value("${rag.top-k:3}")
    private int topK;

    @Value("${rag.retrieval.hybrid.weight.dense:1.0}")
    private double hybridDenseWeight;

    @Value("${rag.retrieval.hybrid.weight.lexical:1.0}")
    private double hybridLexicalWeight;

    @Value("${rag.retrieval.hybrid.top-k:#{null}}")
    private Integer hybridTopK;

    /**
     * lexical 채널용 JedisPooled 빈.
     *
     * <p>RedisVectorStore의 내부 클라이언트와 별개로 {@code spring.data.redis}
     * 접속 정보로 직접 구성한다. FT.SEARCH 호출에만 사용한다.</p>
     */
    @Bean
    public JedisPooled hybridJedisPooled() {
        log.info("하이브리드 lexical 채널 JedisPooled 구성 - {}:{}", redisHost, redisPort);
        return new JedisPooled(redisHost, redisPort);
    }

    /**
     * 하이브리드 DocumentRetriever 빈.
     *
     * <p>dense 검색({@link VectorStoreDocumentRetriever})과 lexical 검색
     * (RediSearch FT.SEARCH)을 RRF로 융합한다. dense 빈은 구체 타입으로 주입되어
     * 모호성이 없다.</p>
     *
     * @param denseRetriever dense 벡터 검색 빈
     * @param jedisPooled    lexical 검색용 JedisPooled
     * @return 하이브리드 DocumentRetriever
     */
    @Bean
    public EgovHybridDocumentRetriever hybridDocumentRetriever(
            VectorStoreDocumentRetriever denseRetriever,
            JedisPooled jedisPooled) {

        int effectiveTopK = (hybridTopK != null) ? hybridTopK : topK;
        log.info("EgovHybridDocumentRetriever 초기화 - index: {}, topK: {}, weight(dense/lexical): {}/{}",
                indexName, effectiveTopK, hybridDenseWeight, hybridLexicalWeight);

        return new EgovHybridDocumentRetriever(
                denseRetriever, jedisPooled, indexName,
                hybridDenseWeight, hybridLexicalWeight, effectiveTopK);
    }
}
