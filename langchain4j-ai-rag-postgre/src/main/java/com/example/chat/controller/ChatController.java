package com.example.chat.controller;

import com.example.chat.context.SessionContext;
import com.example.chat.dto.ChatMessageDto;
import com.example.chat.dto.StreamTokenDto;
import com.example.chat.service.ChatService;
import com.example.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatSessionService chatSessionService;

    /**
     * RAG 기반 스트리밍 응답 생성
     */
    @GetMapping(value = "/ai/rag/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<StreamTokenDto> streamRagResponse(
            @RequestParam(value = "message", defaultValue = "Tell me about this document") String message,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        log.info("RAG 기반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", message, model, sessionId);

        // 세션 컨텍스트 설정
        if (sessionId != null && !sessionId.isEmpty()) {
            log.debug("세션 ID 검증 시작: {}", sessionId);
            if (chatSessionService.sessionExists(sessionId)) {
                log.debug("유효한 세션 ID 확인: {}", sessionId);
                SessionContext.setCurrentSessionId(sessionId);

                // 첫 메시지인 경우 세션 제목 업데이트
                List<ChatMessageDto> history = chatSessionService.getSessionMessages(sessionId);
                if (history.isEmpty()) {
                    log.debug("첫 메시지로 판단, 세션 제목 생성: {}", sessionId);
                    String title = chatSessionService.generateSessionTitle(message);
                    chatSessionService.updateSessionTitle(sessionId, title);
                } else {
                    log.debug("기존 세션 메시지 발견: {} - {} 개", sessionId, history.size());
                    // 마지막 메시지 시간 업데이트
                    chatSessionService.updateLastMessageTime(sessionId);
                }
            } else {
                log.warn("존재하지 않는 세션 ID: {}, 기본 세션으로 처리", sessionId);
                // 존재하지 않는 세션 ID인 경우 기본 세션으로 처리
                SessionContext.setCurrentSessionId("default");
            }
        } else {
            log.warn("세션 ID가 제공되지 않음, 기본 세션으로 처리");
            // 세션 ID가 없는 경우 기본 세션으로 처리
            SessionContext.setCurrentSessionId("default");
        }

        String currentSessionId = SessionContext.getCurrentSessionId();
        log.debug("현재 세션 컨텍스트 설정됨: {}", currentSessionId);

        return chatService.streamRagResponse(message, model)
                .map(StreamTokenDto::new)
                .doFinally(signalType -> {
                    // 스트리밍 완료 후 컨텍스트 정리
                    SessionContext.clear();
                    log.debug("SessionContext 정리 완료 - 세션: {}, 신호: {}", sessionId, signalType);
                });
    }

    /**
     * 일반 스트리밍 응답 생성
     */
    @GetMapping(value = "/ai/simple/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<StreamTokenDto> streamSimpleResponse(
            @RequestParam(value = "message", defaultValue = "Tell me about this document") String message,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        log.info("일반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", message, model, sessionId);

        // 세션 컨텍스트 설정
        if (sessionId != null && !sessionId.isEmpty()) {
            if (chatSessionService.sessionExists(sessionId)) {
                SessionContext.setCurrentSessionId(sessionId);

                // 첫 메시지인 경우 세션 제목 업데이트
                List<ChatMessageDto> history = chatSessionService.getSessionMessages(sessionId);
                if (history.isEmpty()) {
                    String title = chatSessionService.generateSessionTitle(message);
                    chatSessionService.updateSessionTitle(sessionId, title);
                } else {
                    // 마지막 메시지 시간 업데이트
                    chatSessionService.updateLastMessageTime(sessionId);
                }
            } else {
                log.warn("존재하지 않는 세션 ID: {}, 기본 세션으로 처리", sessionId);
                // 존재하지 않는 세션 ID인 경우 기본 세션으로 처리
                SessionContext.setCurrentSessionId("default");
            }
        } else {
            // 세션 ID가 없는 경우 기본 세션으로 처리
            SessionContext.setCurrentSessionId("default");
        }

        // 일반 스트리밍 응답 생성 (RAG 없이)
        return chatService.streamSimpleResponse(message, model)
                .map(StreamTokenDto::new)
                .doFinally(signalType -> {
                    // 스트리밍 완료 후 컨텍스트 정리
                    SessionContext.clear();
                    log.debug("SessionContext 정리 완료 - 세션: {}, 신호: {}", sessionId, signalType);
                });
    }
}
