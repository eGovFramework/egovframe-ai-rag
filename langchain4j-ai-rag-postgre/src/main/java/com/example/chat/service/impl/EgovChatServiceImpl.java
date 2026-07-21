package com.example.chat.service.impl;

import com.example.chat.context.SessionContext;
import com.example.chat.guard.EgovInjectionGuard;
import com.example.chat.service.EgovAiFallbackHandler;
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

    private static final String GUIDANCE_MESSAGE = "요청을 처리할 수 없습니다. 표준프레임워크 관련 질문을 입력해 주세요.";

    private final ChatbotFactory chatbotFactory;
    private final EgovAiFallbackHandler fallbackHandler;
    private final EgovInjectionGuard injectionGuard;

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

            EgovInjectionGuard.GuardDecision decision = injectionGuard.inspect(query);
            if (decision.matched()) {
                log.warn("프롬프트 인젝션 의심 질의 - 세션: {}, 정책: {}, 패턴: {}", sessionId,
                        decision.policy(), decision.matchedPattern());
            }
            if (!decision.allowed()) {
                return Flux.just(GUIDANCE_MESSAGE);
            }

            // RAG 챗봇 생성 및 스트리밍 응답 (Flux 직접 반환)
            RagChatbot ragChatbot = chatbotFactory.createRagChatbot(model, sessionId);
            return ragChatbot.streamChat(query)
                    .doOnComplete(() -> log.info("RAG 스트리밍 완료 - 세션: {}", sessionId))
                    .doOnError(e -> log.error("RAG 스트리밍 오류 - 세션: {}", sessionId, e))
                    .onErrorResume(e -> {
                        log.warn("RAG 스트리밍 폴백 반환 - 세션: {}", sessionId);
                        return Flux.just(fallbackHandler.getFallbackMessage(e));
                    });

        } catch (Exception e) {
            log.error("RAG 스트리밍 응답 생성 중 오류 - 세션: {}", sessionId, e);
            return Flux.just(fallbackHandler.getFallbackMessage(e));
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

            EgovInjectionGuard.GuardDecision decision = injectionGuard.inspect(query);
            if (decision.matched()) {
                log.warn("프롬프트 인젝션 의심 질의 - 세션: {}, 정책: {}, 패턴: {}", sessionId,
                        decision.policy(), decision.matchedPattern());
            }
            if (!decision.allowed()) {
                return Flux.just(GUIDANCE_MESSAGE);
            }

            // Simple 챗봇 생성 및 스트리밍 응답 (Flux 직접 반환)
            SimpleChatbot simpleChatbot = chatbotFactory.createSimpleChatbot(model, sessionId);
            return simpleChatbot.streamChat(query)
                    .doOnComplete(() -> log.info("Simple 스트리밍 완료 - 세션: {}", sessionId))
                    .doOnError(e -> log.error("Simple 스트리밍 오류 - 세션: {}", sessionId, e))
                    .onErrorResume(e -> {
                        log.warn("Simple 스트리밍 폴백 반환 - 세션: {}", sessionId);
                        return Flux.just(fallbackHandler.getFallbackMessage(e));
                    });

        } catch (Exception e) {
            log.error("Simple 스트리밍 응답 생성 중 오류 - 세션: {}", sessionId, e);
            return Flux.just(fallbackHandler.getFallbackMessage(e));
        }
    }

    /**
     * RAG 응답 생성 (비스트리밍)
     */
    public String generateRagResponse(String query) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("RAG 응답 생성 (비스트리밍) - 세션: {}, 쿼리: {}", sessionId, query);

        try {
            EgovInjectionGuard.GuardDecision decision = injectionGuard.inspect(query);
            if (decision.matched()) {
                log.warn("프롬프트 인젝션 의심 질의 - 세션: {}, 정책: {}, 패턴: {}", sessionId,
                        decision.policy(), decision.matchedPattern());
            }
            if (!decision.allowed()) {
                return GUIDANCE_MESSAGE;
            }

            RagChatbot ragChatbot = chatbotFactory.createRagChatbot(null, sessionId);
            return ragChatbot.chat(query);

        } catch (Exception e) {
            log.error("RAG 응답 생성 중 오류", e);
            return fallbackHandler.getFallbackMessage(e);
        }
    }

    /**
     * 일반 응답 생성 (비스트리밍)
     */
    public String generateSimpleResponse(String query) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("Simple 응답 생성 (비스트리밍) - 세션: {}, 쿼리: {}", sessionId, query);

        try {
            EgovInjectionGuard.GuardDecision decision = injectionGuard.inspect(query);
            if (decision.matched()) {
                log.warn("프롬프트 인젝션 의심 질의 - 세션: {}, 정책: {}, 패턴: {}", sessionId,
                        decision.policy(), decision.matchedPattern());
            }
            if (!decision.allowed()) {
                return GUIDANCE_MESSAGE;
            }

            SimpleChatbot simpleChatbot = chatbotFactory.createSimpleChatbot(null, sessionId);
            return simpleChatbot.chat(query);

        } catch (Exception e) {
            log.error("Simple 응답 생성 중 오류", e);
            return fallbackHandler.getFallbackMessage(e);
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

}
