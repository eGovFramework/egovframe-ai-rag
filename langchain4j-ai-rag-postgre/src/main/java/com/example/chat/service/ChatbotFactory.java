package com.example.chat.service;

import com.example.chat.repository.PersistentChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 챗봇 인스턴스 생성 Factory
 * - 동적 모델 선택 지원 (요청별 다른 LLM 모델 사용 가능)
 * - 세션별 ChatMemory 생성 및 PersistentChatMemoryStore 연동
 * - 기본 모델은 빈으로 주입받아 재사용
 */
@Slf4j
@Component
public class ChatbotFactory {

    private final ContentRetriever contentRetriever;
    private final QueryTransformer queryTransformer;
    private final PersistentChatMemoryStore chatMemoryStore;
    private final OllamaStreamingChatModel defaultStreamingModel;

    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String defaultModelName;

    @Value("${langchain4j.ollama.chat-model.temperature}")
    private Double defaultTemperature;

    @Value("${langchain4j.ollama.chat-model.timeout:60s}")
    private Duration defaultTimeout;

    @Value("${chat.memory.max-messages:20}")
    private int maxMessages;

    /**
     * @param hybridContentRetriever 하이브리드 검색 빈. {@code rag.retrieval.hybrid.enabled=true}
     *                               일 때만 등록되며 off(기본) 상태에서는 null 이다.
     * @param denseContentRetriever  dense 벡터 검색 빈. 항상 존재한다.
     * @param queryTransformer       멀티턴 질의 압축 빈. {@code rag.enable-query-compression=true}
     *                               일 때만 등록되며 off(기본) 상태에서는 null 이다.
     */
    public ChatbotFactory(
            @Qualifier("hybridContentRetriever") @Autowired(required = false) ContentRetriever hybridContentRetriever,
            @Qualifier("contentRetriever") ContentRetriever denseContentRetriever,
            @Autowired(required = false) QueryTransformer queryTransformer,
            PersistentChatMemoryStore chatMemoryStore,
            OllamaStreamingChatModel defaultStreamingModel) {
        // 하이브리드 빈이 등록된 경우 우선 사용하고, 없으면 기존 dense 경로를 유지한다.
        this.contentRetriever = (hybridContentRetriever != null) ? hybridContentRetriever : denseContentRetriever;
        this.queryTransformer = queryTransformer;
        this.chatMemoryStore = chatMemoryStore;
        this.defaultStreamingModel = defaultStreamingModel;

        if (hybridContentRetriever != null) {
            log.info("ChatbotFactory - 하이브리드 ContentRetriever 사용");
        }
        if (queryTransformer != null) {
            log.info("ChatbotFactory - 멀티턴 질의 압축(QueryTransformer) 사용");
        }
    }

    /**
     * RAG 챗봇 인스턴스 생성
     * - 세션별 ChatMemory 생성하여 AiServices에 주입
     * - ContentRetriever를 통한 자동 RAG 검색
     *
     * @param modelName 사용할 모델명 (null이면 기본 모델)
     * @param sessionId 세션 ID (메모리 관리용)
     * @return RagChatbot 인스턴스
     */
    public RagChatbot createRagChatbot(String modelName, String sessionId) {
        StreamingChatModel streamingModel = isDefaultModel(modelName)
                ? defaultStreamingModel
                : createStreamingModel(modelName);

        log.info("RAG 챗봇 생성 - 모델: {}, 세션: {}",
                isDefaultModel(modelName) ? defaultModelName : modelName, sessionId);

        AiServices<RagChatbot> builder = AiServices.builder(RagChatbot.class)
                .streamingChatModel(streamingModel)
                .chatMemory(createChatMemory(sessionId));

        if (queryTransformer != null) {
            // 질의 압축이 켜진 경우: 대화 이력 기반으로 재작성된 질의로 검색하도록
            // RetrievalAugmentor 경로를 사용한다 (기존 contentRetriever 경로와 동일 검색기 사용).
            builder.retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                    .queryTransformer(queryTransformer)
                    .contentRetriever(contentRetriever)
                    .build());
        } else {
            builder.contentRetriever(contentRetriever);
        }

        return builder.build();
    }

    /**
     * Simple 챗봇 인스턴스 생성
     *
     * @param modelName 사용할 모델명 (null이면 기본 모델)
     * @param sessionId 세션 ID (메모리 관리용)
     * @return SimpleChatbot 인스턴스
     */
    public SimpleChatbot createSimpleChatbot(String modelName, String sessionId) {
        StreamingChatModel streamingModel = isDefaultModel(modelName)
                ? defaultStreamingModel
                : createStreamingModel(modelName);

        log.info("Simple 챗봇 생성 - 모델: {}, 세션: {}",
                isDefaultModel(modelName) ? defaultModelName : modelName, sessionId);

        return AiServices.builder(SimpleChatbot.class)
                .streamingChatModel(streamingModel)
                .chatMemory(createChatMemory(sessionId))
                .build();
    }

    /**
     * MessageWindowChatMemory 생성
     * - 최근 N개 메시지만 유지
     * - PersistentChatMemoryStore를 통해 PostgreSQL에 자동 저장
     */
    private MessageWindowChatMemory createChatMemory(String sessionId) {
        return MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(maxMessages)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    /**
     * 스트리밍 모델 생성
     */
    private StreamingChatModel createStreamingModel(String modelName) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(modelName)
                .temperature(defaultTemperature)
                .timeout(defaultTimeout)
                .build();
    }

    /**
     * 기본 모델인지 확인
     */
    private boolean isDefaultModel(String modelName) {
        return modelName == null || modelName.trim().isEmpty() || modelName.equals(defaultModelName);
    }
}
