package com.example.chat.service.impl;

import com.example.chat.dto.ChatSession;
import com.example.chat.repository.EgovRedisChatMemoryRepository;
import com.example.chat.service.EgovChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EgovChatSessionServiceImpl extends EgovAbstractServiceImpl implements EgovChatSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatMemory chatMemory;
    private final EgovRedisChatMemoryRepository redisChatMemoryRepository;

    private static final String SESSIONS_LIST_KEY = "chat:sessions:list";
    private static final String SESSION_INFO_KEY_PREFIX = "chat:session:";
    private static final String SESSION_INFO_KEY_SUFFIX = ":info";

    @Override
    public ChatSession createNewSession() {
        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX;

        // Redis Hash에 세션 정보 저장
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("title", "새 채팅");
        sessionInfo.put("createdAt", LocalDateTime.now().toString());
        sessionInfo.put("lastMessageAt", LocalDateTime.now().toString());

        redisTemplate.opsForHash().putAll(sessionKey, sessionInfo);

        // 세션 목록에 추가
        redisTemplate.opsForSet().add(SESSIONS_LIST_KEY, sessionId);

        log.debug("새 채팅 세션 생성: {}", sessionId);
        return new ChatSession(sessionId, "새 채팅", LocalDateTime.now());
    }

    @Override
    public List<ChatSession> getAllSessions() {
        Set<Object> sessionIds = redisTemplate.opsForSet().members(SESSIONS_LIST_KEY);

        if (sessionIds == null || sessionIds.isEmpty()) {
            return new ArrayList<>();
        }

        return sessionIds.stream()
                .map(id -> {
                    String sessionKey = SESSION_INFO_KEY_PREFIX + id + SESSION_INFO_KEY_SUFFIX;
                    Map<Object, Object> info = redisTemplate.opsForHash().entries(sessionKey);

                    if (info.isEmpty()) {
                        return null;
                    }

                    try {
                        return new ChatSession(
                                (String) id,
                                (String) info.get("title"),
                                LocalDateTime.parse((String) info.get("createdAt")),
                                LocalDateTime.parse((String) info.get("lastMessageAt"))
                        );
                    } catch (Exception e) {
                        log.warn("세션 정보 파싱 실패: {}", id, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.getLastMessageAt().compareTo(a.getLastMessageAt()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> getSessionMessages(String sessionId) {
        List<Message> all = chatMemory.get(sessionId);
        // UI에는 사용자/어시스턴트 메시지만 노출 (System 등 내부 메시지는 제외)
        return all.stream()
                .filter(m -> (m instanceof UserMessage) || (m instanceof AssistantMessage))
                .collect(Collectors.toList());
    }

    @Override
    public void updateSessionTitle(String sessionId, String title) {
        String sessionKey = SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX;
        redisTemplate.opsForHash().put(sessionKey, "title", title);
        redisTemplate.opsForHash().put(sessionKey, "lastMessageAt", LocalDateTime.now().toString());
        
        log.debug("세션 제목 업데이트: {} -> {}", sessionId, title);
    }

    @Override
    public void updateLastMessageTime(String sessionId) {
        String sessionKey = SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX;
        redisTemplate.opsForHash().put(sessionKey, "lastMessageAt", LocalDateTime.now().toString());
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
    public boolean sessionExists(String sessionId) {
        return redisTemplate.opsForSet().isMember(SESSIONS_LIST_KEY, sessionId);
    }

    @Override
    public void deleteSession(String sessionId) {
        // 세션 목록에서 제거
        redisTemplate.opsForSet().remove(SESSIONS_LIST_KEY, sessionId);
        
        // 세션 정보 삭제
        String sessionKey = SESSION_INFO_KEY_PREFIX + sessionId + SESSION_INFO_KEY_SUFFIX;
        redisTemplate.delete(sessionKey);
        
        // 채팅 메모리 삭제
        redisChatMemoryRepository.deleteByConversationId(sessionId);
        
        log.debug("세션 삭제: {}", sessionId);
    }
}
