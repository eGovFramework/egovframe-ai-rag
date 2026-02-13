package com.example.chat.repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EgovRedisChatMemoryRepository implements ChatMemoryRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public EgovRedisChatMemoryRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        // Jackson 설정
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 실패한 타입 정보는 무시하고 기본 Object로 처리
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    }
    
    private static final String CHAT_MEMORY_KEY_PREFIX = "chat:memory:";

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        try {
            String key = CHAT_MEMORY_KEY_PREFIX + conversationId;
            
            // Message 객체들을 간단한 맵으로 변환
            List<Map<String, Object>> simpleMessages = messages.stream()
                .map(this::messageToMap)
                .collect(Collectors.toList());
            
            // 맵 리스트를 JSON 문자열로 직렬화
            String messagesJson = objectMapper.writeValueAsString(simpleMessages);
            redisTemplate.opsForValue().set(key, messagesJson);
            
            log.debug("Redis에 채팅 메모리 저장: {} - {} 개 메시지", conversationId, messages.size());
        } catch (Exception e) {
            log.error("채팅 메모리 저장 실패: {}", conversationId, e);
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            String key = CHAT_MEMORY_KEY_PREFIX + conversationId;
            Object value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                log.debug("Redis에서 채팅 메모리 없음: {}", conversationId);
                return new ArrayList<>();
            }
            
            // JSON 문자열을 Map 리스트로 역직렬화
            String messagesJson = value.toString();
            List<Map<String, Object>> simpleMaps = objectMapper.readValue(messagesJson, new TypeReference<List<Map<String, Object>>>() {});
            
            // 맵 리스트를 Message 객체들로 변환
            List<Message> messages = simpleMaps.stream()
                .map(this::mapToMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            log.debug("Redis에서 채팅 메모리 조회: {} - {} 개 메시지", conversationId, messages.size());
            return messages;
            
        } catch (Exception e) {
            log.error("채팅 메모리 조회 실패: {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            String key = CHAT_MEMORY_KEY_PREFIX + conversationId;
            redisTemplate.delete(key);
            log.debug("Redis에서 채팅 메모리 삭제: {}", conversationId);
        } catch (Exception e) {
            log.error("채팅 메모리 삭제 실패: {}", conversationId, e);
        }
    }

    @Override
    public List<String> findConversationIds() {
        try {
            Set<String> keys = redisTemplate.keys(CHAT_MEMORY_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 키에서 conversationId 부분만 추출
            return keys.stream()
                    .map(key -> key.substring(CHAT_MEMORY_KEY_PREFIX.length()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("대화 ID 목록 조회 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * Message 객체를 간단한 Map으로 변환
     */
    private Map<String, Object> messageToMap(Message message) {
        Map<String, Object> map = new HashMap<>();
        map.put("messageType", message.getMessageType().name());
        
        // 메시지 내용 추출
        String content;
        if (message instanceof UserMessage) {
            content = ((UserMessage) message).getText();
        } else if (message instanceof AssistantMessage) {
            content = ((AssistantMessage) message).getText();
        } else if (message instanceof SystemMessage) {
            content = ((SystemMessage) message).getText();
        } else {
            // fallback - 다른 메시지 타입의 경우
            try {
                Method getTextMethod = message.getClass().getMethod("getText");
                content = (String) getTextMethod.invoke(message);
            } catch (Exception e) {
                log.warn("메시지 내용 추출 실패: {}", message.getClass().getSimpleName());
                content = "";
            }
        }
        
        map.put("content", content);
        return map;
    }

    /**
     * Map을 Message 객체로 변환
     */
    private Message mapToMessage(Map<String, Object> map) {
        try {
            String messageTypeStr = (String) map.get("messageType");
            String content = (String) map.get("content");
            
            if (messageTypeStr == null || content == null) {
                log.warn("메시지 맵에서 필수 정보 누락: {}", map);
                return null;
            }
            
            switch (messageTypeStr) {
                case "USER":
                    return new UserMessage(content);
                case "ASSISTANT":
                    return new AssistantMessage(content);
                case "SYSTEM":
                    return new SystemMessage(content);
                default:
                    log.warn("알 수 없는 메시지 타입: {}", messageTypeStr);
                    return null;
            }
        } catch (Exception e) {
            log.warn("Map을 Message로 변환 실패: {}", map, e);
            return null;
        }
    }

}