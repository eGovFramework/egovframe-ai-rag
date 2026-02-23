package com.example.chat.service.impl;

import com.example.chat.context.SessionContext;
import com.example.chat.service.EgovChatService;
import com.example.chat.service.ChatbotFactory;
import com.example.chat.service.RagChatbot;
import com.example.chat.service.SimpleChatbot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 세션별 채팅 서비스 구현체
 * - AiServices 기반 스트리밍 구현
 * - ChatMemory를 통한 자동 히스토리 관리
 * - langchain4j-reactor를 통한 네이티브 Flux 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EgovChatServiceImpl extends EgovAbstractServiceImpl implements EgovChatService {

    private final ChatbotFactory chatbotFactory;

    /**
     * 세션별 RAG 기반 스트리밍 응답 생성
     * - AiServices + ContentRetriever로 자동 RAG 검색
     * - ChatMemory로 자동 히스토리 관리
     * - langchain4j-reactor가 Flux 변환 자동 처리
     */
    @Override
    public Flux<String> streamRagResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("RAG 스트리밍 질의 - 세션: {}, 모델: {}, 쿼리: {}", sessionId, model, query);

        try {
            validateSessionId(sessionId);

            // RAG 챗봇 생성 및 스트리밍 응답 (Flux 직접 반환)
            RagChatbot ragChatbot = chatbotFactory.createRagChatbot(model, sessionId);
            return ragChatbot.streamChat(query)
                    .doOnComplete(() -> log.info("RAG 스트리밍 완료 - 세션: {}", sessionId))
                    .doOnError(e -> log.error("RAG 스트리밍 오류 - 세션: {}", sessionId, e));

        } catch (Exception e) {
            log.error("RAG 스트리밍 응답 생성 중 오류 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }

    /**
     * 세션별 일반 스트리밍 응답 생성 (RAG 없음)
     * langchain4j-reactor가 Flux 변환 자동 처리
     */
    @Override
    public Flux<String> streamSimpleResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("Simple 스트리밍 질의 - 세션: {}, 모델: {}, 쿼리: {}", sessionId, model, query);

        try {
            validateSessionId(sessionId);

            // Simple 챗봇 생성 및 스트리밍 응답 (Flux 직접 반환)
            SimpleChatbot simpleChatbot = chatbotFactory.createSimpleChatbot(model, sessionId);
            return simpleChatbot.streamChat(query)
                    .doOnComplete(() -> log.info("Simple 스트리밍 완료 - 세션: {}", sessionId))
                    .doOnError(e -> log.error("Simple 스트리밍 오류 - 세션: {}", sessionId, e));

        } catch (Exception e) {
            log.error("Simple 스트리밍 응답 생성 중 오류 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }

    /**
     * RAG 응답 생성 (비스트리밍)
     */
    public String generateRagResponse(String query) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("RAG 응답 생성 (비스트리밍) - 세션: {}, 쿼리: {}", sessionId, query);

        try {
            RagChatbot ragChatbot = chatbotFactory.createRagChatbot(null, sessionId);
            return ragChatbot.chat(query);

        } catch (Exception e) {
            log.error("RAG 응답 생성 중 오류", e);
            return handleException(e);
        }
    }

    /**
     * 일반 응답 생성 (비스트리밍)
     */
    public String generateSimpleResponse(String query) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("Simple 응답 생성 (비스트리밍) - 세션: {}, 쿼리: {}", sessionId, query);

        try {
            SimpleChatbot simpleChatbot = chatbotFactory.createSimpleChatbot(null, sessionId);
            return simpleChatbot.chat(query);

        } catch (Exception e) {
            log.error("Simple 응답 생성 중 오류", e);
            return handleException(e);
        }
    }

    /**
     * 세션 ID 검증
     */
    private void validateSessionId(String sessionId) {
        if ("default".equals(sessionId)) {
            log.warn("세션 ID가 'default'로 설정됨 - 세션 관리에 문제가 있을 수 있습니다");
        }
    }

    /**
     * 예외 처리
     */
    private String handleException(Exception e) {
        String errorMessage = e.getMessage();

        if (errorMessage != null && (errorMessage.contains("timeout")
                || errorMessage.contains("timed out")
                || errorMessage.contains("connection")
                || e instanceof java.net.SocketTimeoutException
                || e instanceof java.util.concurrent.TimeoutException)) {
            return "죄송합니다. 서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
        }

        return "죄송합니다. 응답을 생성하는 중에 오류가 발생했습니다: " + errorMessage;
    }
}
