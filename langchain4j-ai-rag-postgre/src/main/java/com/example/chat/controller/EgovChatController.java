package com.example.chat.controller;

import com.example.chat.context.SessionContext;
import com.example.chat.dto.ChatMessageDto;
import com.example.chat.dto.StreamTokenDto;
import com.example.chat.service.EgovChatService;
import com.example.chat.service.EgovChatSessionService;
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
public class EgovChatController {

    private final EgovChatService egovChatService;
    private final EgovChatSessionService egovChatSessionService;

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
            if (egovChatSessionService.sessionExists(sessionId)) {
                log.debug("유효한 세션 ID 확인: {}", sessionId);
                SessionContext.setCurrentSessionId(sessionId);

                // 첫 메시지인 경우 세션 제목 업데이트
                List<ChatMessageDto> history = egovChatSessionService.getSessionMessages(sessionId);
                if (history.isEmpty()) {
                    log.debug("첫 메시지로 판단, 세션 제목 생성: {}", sessionId);
                    String title = egovChatSessionService.generateSessionTitle(message);
                    egovChatSessionService.updateSessionTitle(sessionId, title);
                } else {
                    log.debug("기존 세션 메시지 발견: {} - {} 개", sessionId, history.size());
                    // 마지막 메시지 시간 업데이트
                    egovChatSessionService.updateLastMessageTime(sessionId);
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

        // 서비스가 sessionId를 동기적으로 읽어 처리하므로, 스트림을 반환하기 전
        // 요청 스레드에서 ThreadLocal 세션 컨텍스트를 정리한다. 이전에는 doFinally
        // 안에서 정리했으나, doFinally는 reactor 스레드에서 실행되어 요청(서블릿)
        // 스레드의 ThreadLocal이 정리되지 않고 워커 스레드에 남는 문제가 있었다.
        Flux<StreamTokenDto> response = egovChatService.streamRagResponse(message, model)
                .map(StreamTokenDto::new);
        SessionContext.clear();
        return response;
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
            if (egovChatSessionService.sessionExists(sessionId)) {
                SessionContext.setCurrentSessionId(sessionId);

                // 첫 메시지인 경우 세션 제목 업데이트
                List<ChatMessageDto> history = egovChatSessionService.getSessionMessages(sessionId);
                if (history.isEmpty()) {
                    String title = egovChatSessionService.generateSessionTitle(message);
                    egovChatSessionService.updateSessionTitle(sessionId, title);
                } else {
                    // 마지막 메시지 시간 업데이트
                    egovChatSessionService.updateLastMessageTime(sessionId);
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
        // 서비스가 sessionId를 동기적으로 읽어 처리하므로, 스트림을 반환하기 전
        // 요청 스레드에서 ThreadLocal 세션 컨텍스트를 정리한다.(doFinally는 reactor
        // 스레드에서 실행되어 요청 스레드의 ThreadLocal을 정리하지 못했다.)
        Flux<StreamTokenDto> response = egovChatService.streamSimpleResponse(message, model)
                .map(StreamTokenDto::new);
        SessionContext.clear();
        return response;
    }
}
