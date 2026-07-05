package com.example.chat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * RAG 설정 클래스
 * ContentRetriever를 통해 벡터 저장소에서 관련 문서를 검색
 */
@Slf4j
@Configuration
public class EgovRagConfig {

    @Value("${rag.top-k:3}")
    private int topK;

    @Value("${rag.similarity.threshold:0.20}")
    private double similarityThreshold;

    @Value("${pgvector.table-name:document_embeddings}")
    private String tableName;

    @Value("${rag.retrieval.hybrid.weight.dense:1.0}")
    private double hybridDenseWeight;

    @Value("${rag.retrieval.hybrid.weight.lexical:1.0}")
    private double hybridLexicalWeight;

    @Value("${rag.retrieval.hybrid.top-k:#{null}}")
    private Integer hybridTopK;

    // lexical(pg_trgm word_similarity) 임계값. `%>` 연산자가 참조하는 GUC
    // pg_trgm.word_similarity_threshold(기본 0.6)는 한국어 긴 청크에서 너무 엄격하다.
    // 실제 문서(runtime README, 4000자 청크) 측정상 0.30 부근이 recall@3~0.8로 최적이라 기본값을
    // 0.30으로 둔다. 코퍼스 언어·문서 특성에 따라 프로퍼티로 조정한다.
    @Value("${rag.retrieval.hybrid.lexical.word-similarity-threshold:0.30}")
    private double hybridLexicalWordSimilarityThreshold;

    /**
     * ContentRetriever 빈 생성
     * EmbeddingStoreContentRetriever를 사용하여 벡터 검색 수행
     *
     * @param embeddingStore 벡터 저장소
     * @param embeddingModel 임베딩 모델
     * @return ContentRetriever
     */
    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {

        log.info("ContentRetriever 초기화 - topK: {}, minScore: {}", topK, similarityThreshold);

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(topK)
                .minScore(similarityThreshold)
                .build();
    }

    /**
     * 하이브리드 ContentRetriever 빈 생성
     *
     * <p>{@code rag.retrieval.hybrid.enabled=true} 일 때만 등록한다. off(기본) 상태에서는
     * 빈이 만들어지지 않으므로 dense {@code contentRetriever} 빈만 존재하여 빈 모호성이
     * 발생하지 않는다. dense 검색({@link #contentRetriever})과 lexical 검색(pg_trgm)을
     * RRF로 융합한다.</p>
     *
     * @param denseContentRetriever dense 벡터 검색 빈
     * @param jdbcTemplate          lexical 검색용 JdbcTemplate(자동 구성)
     * @param transactionManager    lexical 임계값을 트랜잭션 스코프로 적용하기 위한 트랜잭션 매니저
     * @return 하이브리드 ContentRetriever
     */
    @Bean
    @ConditionalOnProperty(prefix = "rag.retrieval.hybrid", name = "enabled", havingValue = "true")
    public ContentRetriever hybridContentRetriever(
            @Qualifier("contentRetriever") ContentRetriever denseContentRetriever,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {

        int effectiveTopK = (hybridTopK != null) ? hybridTopK : topK;
        log.info("HybridContentRetriever 초기화 - topK: {}, weight(dense/lexical): {}/{}, lexical word_similarity 임계값: {}",
                effectiveTopK, hybridDenseWeight, hybridLexicalWeight, hybridLexicalWordSimilarityThreshold);

        return new EgovHybridContentRetriever(
                denseContentRetriever, jdbcTemplate, transactionManager, tableName,
                hybridDenseWeight, hybridLexicalWeight, hybridLexicalWordSimilarityThreshold, effectiveTopK);
    }
}
