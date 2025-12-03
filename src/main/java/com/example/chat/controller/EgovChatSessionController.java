package com.example.chat.controller;

import com.example.chat.dto.ChatSession;
import com.example.chat.dto.ChatMessageDto;
import com.example.chat.service.EgovChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;
import java.lang.reflect.Method;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EgovChatSessionController {

    private final EgovChatSessionService chatSessionService;

    @PostMapping
    public ResponseEntity<ChatSession> createNewSession() {
        try {
            ChatSession session = chatSessionService.createNewSession();
            log.info("새 채팅 세션 생성됨: {}", session.getSessionId());
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            log.error("세션 생성 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ChatSession>> getAllSessions() {
        try {
            List<ChatSession> sessions = chatSessionService.getAllSessions();
            log.debug("세션 목록 조회: {} 개", sessions.size());
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            log.error("세션 목록 조회 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getSessionMessages(@PathVariable String sessionId) {
        try {
            if (!chatSessionService.sessionExists(sessionId)) {
                log.warn("존재하지 않는 세션 ID: {}", sessionId);
                return ResponseEntity.notFound().build();
            }

            List<Message> messages = chatSessionService.getSessionMessages(sessionId);
            log.debug("세션 {} 메시지 조회 결과: {} 개의 메시지", sessionId, messages.size());
            
            // Spring AI Message를 ChatMessageDto로 변환
            List<ChatMessageDto> messageDtos = messages.stream()
                    .map(message -> {
                        String messageTypeStr = message.getMessageType().name();
                        String content;
                        
                        // Message 타입별로 실제 텍스트 내용 추출
                        if (message instanceof UserMessage) {
                            UserMessage userMsg = (UserMessage) message;
                            content = userMsg.getText();
                        } else if (message instanceof AssistantMessage) {
                            AssistantMessage assistantMsg = (AssistantMessage) message;
                            content = assistantMsg.getText();
                        } else if (message instanceof SystemMessage) {
                            SystemMessage systemMsg = (SystemMessage) message;
                            content = systemMsg.getText();
                        } else {
                            // fallback - 다른 메시지 타입의 경우
                            try {
                                Method getTextMethod = message.getClass().getMethod("getText");
                                content = (String) getTextMethod.invoke(message);
                            } catch (Exception e) {
                                content = "메시지 내용을 가져올 수 없습니다";
                            }
                        }
                        
                        return new ChatMessageDto(messageTypeStr, content);
                    })
                    .collect(Collectors.toList());
            
            log.debug("세션 메시지 조회: {} - {} 개 메시지", sessionId, messageDtos.size());
            return ResponseEntity.ok(messageDtos);
        } catch (Exception e) {
            log.error("세션 메시지 조회 실패: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/{sessionId}/title")
    public ResponseEntity<Void> updateSessionTitle(
            @PathVariable String sessionId, 
            @RequestBody UpdateTitleRequest request) {
        try {
            if (!chatSessionService.sessionExists(sessionId)) {
                log.warn("존재하지 않는 세션 ID: {}", sessionId);
                return ResponseEntity.notFound().build();
            }

            chatSessionService.updateSessionTitle(sessionId, request.getTitle());
            log.info("세션 제목 업데이트: {} -> {}", sessionId, request.getTitle());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("세션 제목 업데이트 실패: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        try {
            if (!chatSessionService.sessionExists(sessionId)) {
                log.warn("존재하지 않는 세션 ID: {}", sessionId);
                return ResponseEntity.notFound().build();
            }

            chatSessionService.deleteSession(sessionId);
            log.info("세션 삭제: {}", sessionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("세션 삭제 실패: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class UpdateTitleRequest {
        private String title;

        public UpdateTitleRequest() {}

        public UpdateTitleRequest(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}