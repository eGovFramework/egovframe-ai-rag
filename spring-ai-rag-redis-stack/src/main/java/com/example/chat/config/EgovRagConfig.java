package com.example.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.config.rag.transformers.EgovCompressionQueryTransformer;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Configuration
public class EgovRagConfig {
    
    @Value("${rag.similarity.threshold}")
    private double similarityThreshold;

    @Value("${rag.top-k}")
    private int topK;

    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel) {
        log.info("ChatClient 구성: 기본 어드바이저 없이 생성 (세션별 동적 추가)");
        
        return ChatClient.builder(chatModel)
                .build();
    }

    @Bean
    public VectorStoreDocumentRetriever vectorStoreDocumentRetriever(RedisVectorStore redisVectorStore) {
        log.info("VectorStoreDocumentRetriever 빈 생성 - 유사도 임계값: {}, Top K: {}", similarityThreshold, topK);

        return VectorStoreDocumentRetriever.builder()
                .similarityThreshold(similarityThreshold)
                .topK(topK)
                .vectorStore(redisVectorStore)
                .build();
    }
    
    /**
     * 세션 ID를 직접 전달받아 RAG 어드바이저를 생성하는 정적 메서드
     * QueryTransformer가 ChatMemory에서 히스토리를 조회하여 질문 압축 수행
     *
     * @param sessionId 세션 ID
     * @param compressionTransformer 히스토리 압축 transformer
     * @param documentRetriever Bean으로 생성된 DocumentRetriever (application.yml의 rag.similarity.threshold 적용)
     * @param enableQueryCompression 질문 압축 활성화 여부
     */
    public static Advisor createRagAdvisor(String sessionId,
                                         EgovCompressionQueryTransformer compressionTransformer,
                                         VectorStoreDocumentRetriever documentRetriever,
                                         boolean enableQueryCompression) {
        log.info("RAG 어드바이저 생성 시작 - 세션: {}, 질문 압축: {}", sessionId, enableQueryCompression);

        // 로깅을 위해 DocumentRetriever를 래핑
        LoggingDocumentRetriever loggingRetriever = new LoggingDocumentRetriever(documentRetriever);

        if (enableQueryCompression) {
            // 질문 압축 활성화: QueryTransformer 사용
            // 세션 ID를 전달받는 커스텀 QueryTransformer 생성
            // 이 transformer는 내부에서 ChatMemory를 조회하여 히스토리 기반 질문 압축 수행
            SessionAwareQueryTransformer sessionAwareTransformer = new SessionAwareQueryTransformer(
                compressionTransformer, sessionId);
            log.info("질문 압축 활성화 - SessionAwareQueryTransformer 사용");

            // QueryTransformer와 DocumentRetriever를 함께 사용
            // 흐름: Query → QueryTransformer(히스토리 압축) → DocumentRetriever(벡터 검색)
            RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                    .queryTransformers(sessionAwareTransformer)
                    .documentRetriever(loggingRetriever)
                    .build();

            log.info("RAG 어드바이저 생성 완료 - 세션: {}, 압축 모드", sessionId);
            return advisor;
        } else {
            // 질문 압축 비활성화: DocumentRetriever만 사용
            log.info("질문 압축 비활성화 - 원본 질문으로 검색");

            // QueryTransformer 없이 DocumentRetriever만 사용
            // 흐름: Query → DocumentRetriever(벡터 검색)
            RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(loggingRetriever)
                    .build();

            log.info("RAG 어드바이저 생성 완료 - 세션: {}, 비압축 모드", sessionId);
            return advisor;
        }
    }

    /**
     * QueryTransformer 없이 DocumentRetriever만 사용하는 RAG 어드바이저 생성
     * 히스토리 압축은 이미 완료된 상태이므로 QueryTransformer 불필요
     *
     * @param documentRetriever Bean으로 생성된 DocumentRetriever
     * @return RetrievalAugmentationAdvisor
     */
    public static Advisor createRagAdvisor(VectorStoreDocumentRetriever documentRetriever) {
        
        // QueryTransformer 없이 DocumentRetriever만 사용
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .build();

        return advisor;
    }

    /**
     * 세션 ID를 직접 전달받는 커스텀 QueryTransformer
     * ChatMemory에서 히스토리를 조회하여 CompressionQueryTransformer에 전달
     */
    private static class SessionAwareQueryTransformer implements QueryTransformer {

        private final EgovCompressionQueryTransformer compressionTransformer;
        private final String sessionId;

        public SessionAwareQueryTransformer(EgovCompressionQueryTransformer compressionTransformer, String sessionId) {
            this.compressionTransformer = compressionTransformer;
            this.sessionId = sessionId;
        }

        @Override
        public Query transform(Query query) {
            log.info("SessionAwareQueryTransformer 시작 - 세션: {}, 원본 질문: '{}'", sessionId, query.text());

            // EgovCompressionQueryTransformer에 세션 ID 전달하여 압축 수행
            // 내부에서 ChatMemory 조회 → 히스토리 기반 질문 압축
            Query compressedQuery = compressionTransformer.transformWithSessionId(query, sessionId);

            log.info("SessionAwareQueryTransformer 완료 - 압축된 질문: '{}'", compressedQuery.text());

            return compressedQuery;
        }
    }

    /**
     * DocumentRetriever를 래핑하여 검색된 문서를 로깅하는 클래스
     */
    private static class LoggingDocumentRetriever implements DocumentRetriever {

        private final DocumentRetriever delegate;

        public LoggingDocumentRetriever(DocumentRetriever delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<Document> retrieve(Query query) {
            log.info("RAG 문서 검색 시작 - 질문: '{}'", query.text());

            List<Document> documents = delegate.retrieve(query);

            if (documents.isEmpty()) {
                log.warn("RAG 문서 검색 완료 - 검색된 문서 없음 (유사도 임계값 미달 또는 관련 문서 없음)");
                log.warn("→ LLM이 RAG 문서 없이 일반 지식으로만 답변합니다");
            } else {
                log.info("RAG 문서 검색 완료 - 검색된 문서 수: {} (모두 LLM 컨텍스트에 포함)", documents.size());

                int totalChars = 0;
                for (int i = 0; i < documents.size(); i++) {
                    Document doc = documents.get(i);
                    String content = doc.getText();
                    String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                    totalChars += content.length();

                    // 유사도 점수 로깅 (메타데이터에서 추출)
                    Object similarityScore = doc.getMetadata().get("distance");
                    if (similarityScore == null) {
                        similarityScore = doc.getMetadata().get("score");
                    }

                    log.info("  문서 #{}: 유사도={}, 길이={}자, 내용={}", i + 1, similarityScore, content.length(), preview);
                }

                log.info("→ 총 {}자의 컨텍스트가 LLM에 전달됩니다", totalChars);
            }

            return documents;
        }
    }
}