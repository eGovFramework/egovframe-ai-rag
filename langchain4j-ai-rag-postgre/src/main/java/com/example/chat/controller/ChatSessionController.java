package com.example.chat.controller;

import com.example.chat.dto.ChatMessageDto;
import com.example.chat.dto.ChatSession;
import com.example.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 새 채팅 세션 생성
     */
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

    /**
     * 모든 채팅 세션 목록 조회
     */
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

    /**
     * 특정 세션의 메시지 목록 조회
     */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getSessionMessages(@PathVariable String sessionId) {
        try {
            if (!chatSessionService.sessionExists(sessionId)) {
                log.warn("존재하지 않는 세션 ID: {}", sessionId);
                return ResponseEntity.notFound().build();
            }

            List<ChatMessageDto> messages = chatSessionService.getSessionMessages(sessionId);
            log.debug("세션 {} 메시지 조회 결과: {} 개의 메시지", sessionId, messages.size());

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("세션 메시지 조회 실패: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 세션 제목 업데이트
     */
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

    /**
     * 세션 삭제
     */
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

    /**
     * 세션 제목 업데이트 요청 DTO
     */
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
