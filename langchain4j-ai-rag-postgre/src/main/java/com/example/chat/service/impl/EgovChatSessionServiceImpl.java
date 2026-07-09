package com.example.chat.service.impl;

import com.example.chat.dto.ChatMessageDto;
import com.example.chat.dto.ChatSession;
import com.example.chat.entity.ChatMemoryEntity;
import com.example.chat.entity.ChatSessionEntity;
import com.example.chat.repository.ChatMemoryRepository;
import com.example.chat.repository.ChatSessionRepository;
import com.example.chat.repository.PersistentChatMemoryStore;
import com.example.chat.service.EgovChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EgovChatSessionServiceImpl extends EgovAbstractServiceImpl implements EgovChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryRepository chatMemoryRepository;

    @Override
    @Transactional
    public ChatSession createNewSession() {
        String sessionId = UUID.randomUUID().toString();

        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setSessionId(sessionId);
        entity.setTitle("새 채팅");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        chatSessionRepository.save(entity);

        log.debug("새 채팅 세션 생성: {}", sessionId);
        return new ChatSession(sessionId, "새 채팅", LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSession> getAllSessions() {
        List<ChatSessionEntity> entities = chatSessionRepository.findAllByOrderByUpdatedAtDesc();

        return entities.stream()
                .map(entity -> new ChatSession(
                        entity.getSessionId(),
                        entity.getTitle(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getSessionMessages(String sessionId) {
        List<ChatMemoryEntity> entities = chatMemoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);

        // USER와 ASSISTANT 메시지만 반환 (SYSTEM 메시지 제외)
        // USER 메시지는 RAG 증강 구분자 이후 내용(검색 결과)을 제거하고 원본 질의만 반환한다.
        return entities.stream()
                .filter(entity -> "USER".equals(entity.getMessageType()) ||
                        "ASSISTANT".equals(entity.getMessageType()))
                .map(entity -> {
                    String content = entity.getContent();
                    if ("USER".equals(entity.getMessageType())) {
                        int sep = content.indexOf(PersistentChatMemoryStore.RAG_SEP);
                        if (sep >= 0) content = content.substring(0, sep);
                    }
                    return new ChatMessageDto(entity.getMessageType(), content, entity.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateSessionTitle(String sessionId, String title) {
        chatSessionRepository.findById(sessionId).ifPresent(entity -> {
            entity.setTitle(title);
            entity.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(entity);
            log.debug("세션 제목 업데이트: {} -> {}", sessionId, title);
        });
    }

    @Override
    @Transactional
    public void updateLastMessageTime(String sessionId) {
        chatSessionRepository.findById(sessionId).ifPresent(entity -> {
            entity.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(entity);
        });
    }

    @Override
    public String generateSessionTitle(String firstMessage) {
        if (firstMessage == null || firstMessage.trim().isEmpty()) {
            return "새 채팅";
        }

        // 첫 메시지에서 제목 생성 (최대 30자)
        String title = firstMessage.trim();
        if (title.length() > 30) {
            title = title.substring(0, 27) + "...";
        }

        return title;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean sessionExists(String sessionId) {
        return chatSessionRepository.existsById(sessionId);
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        // 채팅 메모리 삭제 (cascade)
        chatMemoryRepository.deleteBySessionId(sessionId);

        // 세션 삭제
        chatSessionRepository.deleteById(sessionId);

        log.debug("세션 삭제: {}", sessionId);
    }
}
