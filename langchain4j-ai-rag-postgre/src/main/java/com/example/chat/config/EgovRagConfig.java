package com.example.chat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
