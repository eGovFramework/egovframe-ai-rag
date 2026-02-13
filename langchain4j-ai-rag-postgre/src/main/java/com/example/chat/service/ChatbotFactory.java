package com.example.chat.service;

import com.example.chat.repository.PersistentChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
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

    public ChatbotFactory(ContentRetriever contentRetriever,
                          PersistentChatMemoryStore chatMemoryStore,
                          OllamaStreamingChatModel defaultStreamingModel) {
        this.contentRetriever = contentRetriever;
        this.chatMemoryStore = chatMemoryStore;
        this.defaultStreamingModel = defaultStreamingModel;
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

        return AiServices.builder(RagChatbot.class)
                .streamingChatModel(streamingModel)
                .contentRetriever(contentRetriever)
                .chatMemory(createChatMemory(sessionId))
                .build();
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
