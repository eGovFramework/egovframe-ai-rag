package com.example.chat.controller;

import com.example.chat.context.SessionContext;
import com.example.chat.service.EgovChatSessionService;
import com.example.chat.service.EgovSessionAwareChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import reactor.core.publisher.Flux;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SessionContext(ThreadLocal) 정리가 요청 스레드에서 이루어지는지 검증한다.
 *
 * <p>스트리밍 엔드포인트는 {@code Flux}를 반환하며, 세션 ID는 서비스가 동기적으로 읽어
 * 캡처한다. 이전 구현은 {@code Flux.doFinally(...)} 안에서 {@code SessionContext.clear()}를
 * 호출했는데, doFinally 콜백은 스트림을 구독·종료시키는 reactor 스레드에서 실행되므로
 * 요청(서블릿) 스레드에 설정된 ThreadLocal이 정리되지 않고 워커 스레드에 남았다.
 * 본 테스트는 컨트롤러 메서드 반환(=서비스의 동기 캡처 완료) 직후 요청 스레드의 세션
 * 컨텍스트가 비워지는지 확인한다. 따라서 reactor 스레드 구독 없이도 결정적으로 동작한다.
 */
class EgovOllamaChatControllerSessionCleanupTest {

    private final OllamaChatModel chatModel = mock(OllamaChatModel.class);
    private final EgovSessionAwareChatService chatService = mock(EgovSessionAwareChatService.class);
    private final EgovChatSessionService sessionService = mock(EgovChatSessionService.class);
    private final EgovOllamaChatController controller =
            new EgovOllamaChatController(chatModel, chatService, sessionService);

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    @DisplayName("유효 세션으로 RAG 스트림 요청 후 요청 스레드의 세션 컨텍스트가 정리된다")
    void clearsSessionContextOnRequestThreadAfterRagStream() {
        when(sessionService.sessionExists("s-1")).thenReturn(true);
        when(sessionService.getSessionMessages("s-1")).thenReturn(Collections.emptyList());
        when(sessionService.generateSessionTitle(anyString())).thenReturn("title");
        when(chatService.streamRagResponse(anyString(), any())).thenReturn(Flux.<ChatResponse>empty());

        controller.streamRagResponse("hello", null, "s-1");

        // 정리되면 ThreadLocal이 비어 기본 세션 ID가 반환된다.
        // 정리되지 않았다면(버그) "s-1"이 남아 단언이 실패한다.
        assertThat(SessionContext.getCurrentSessionId())
                .isEqualTo(ChatMemory.DEFAULT_CONVERSATION_ID);
        verify(sessionService).sessionExists("s-1");
    }

    @Test
    @DisplayName("유효 세션으로 일반 스트림 요청 후 요청 스레드의 세션 컨텍스트가 정리된다")
    void clearsSessionContextOnRequestThreadAfterSimpleStream() {
        when(sessionService.sessionExists("s-2")).thenReturn(true);
        when(sessionService.getSessionMessages("s-2")).thenReturn(Collections.emptyList());
        when(sessionService.generateSessionTitle(anyString())).thenReturn("title");
        when(chatService.streamSimpleResponse(anyString(), any())).thenReturn(Flux.<ChatResponse>empty());

        controller.streamSimpleResponse("hi", null, "s-2");

        assertThat(SessionContext.getCurrentSessionId())
                .isEqualTo(ChatMemory.DEFAULT_CONVERSATION_ID);
        verify(sessionService).sessionExists("s-2");
    }
}
