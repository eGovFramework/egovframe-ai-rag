package com.example.chat.repository;

import com.example.chat.entity.ChatMemoryEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j의 ChatMemoryStore 인터페이스를 구현하여
 * AiServices와 자동 통합
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryRepository chatMemoryRepository;

    /**
     * 특정 세션의 모든 메시지 조회
     *
     * @param memoryId 세션 ID
     * @return ChatMessage 리스트
     */
    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        log.debug("채팅 메모리 조회 - 세션: {}", sessionId);

        List<ChatMemoryEntity> entities = chatMemoryRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        List<ChatMessage> messages = new ArrayList<>();
        for (ChatMemoryEntity entity : entities) {
            ChatMessage message = convertToLangChain4jMessage(entity);
            if (message != null) {
                messages.add(message);
            }
        }

        log.debug("채팅 메모리 조회 완료 - 세션: {}, 메시지 수: {}", sessionId, messages.size());
        return messages;
    }

    /**
     * 메시지 업데이트
     *
     * @param memoryId 세션 ID
     * @param messages 저장할 메시지 리스트
     */
    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        log.debug("채팅 메모리 업데이트 - 세션: {}, 메시지 수: {}", sessionId, messages.size());

        // 기존 메시지 삭제
        chatMemoryRepository.deleteBySessionId(sessionId);

        // 새 메시지 저장
        for (ChatMessage message : messages) {
            ChatMemoryEntity entity = convertToEntity(sessionId, message);
            if (entity != null) {
                chatMemoryRepository.save(entity);
            }
        }

        log.debug("채팅 메모리 업데이트 완료 - 세션: {}", sessionId);
    }

    /**
     * 특정 세션의 모든 메시지 삭제
     *
     * @param memoryId 세션 ID
     */
    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        log.info("채팅 메모리 삭제 - 세션: {}", sessionId);

        chatMemoryRepository.deleteBySessionId(sessionId);
    }

    /**
     * Entity를 LangChain4j ChatMessage로 변환
     */
    private ChatMessage convertToLangChain4jMessage(ChatMemoryEntity entity) {
        String messageType = entity.getMessageType();
        String content = entity.getContent();

        return switch (messageType) {
            case "USER" -> UserMessage.from(content);
            case "ASSISTANT" -> AiMessage.from(content);
            case "SYSTEM" -> SystemMessage.from(content);
            default -> {
                log.warn("알 수 없는 메시지 타입: {}", messageType);
                yield null;
            }
        };
    }

    /**
     * LangChain4j ChatMessage를 Entity로 변환
     */
    private ChatMemoryEntity convertToEntity(String sessionId, ChatMessage message) {
        String messageType;
        String content;

        if (message instanceof UserMessage userMessage) {
            messageType = "USER";
            content = userMessage.singleText();
        } else if (message instanceof AiMessage aiMessage) {
            messageType = "ASSISTANT";
            content = aiMessage.text();
        } else if (message instanceof SystemMessage systemMessage) {
            messageType = "SYSTEM";
            content = systemMessage.text();
        } else {
            log.warn("지원하지 않는 메시지 타입: {}", message.getClass().getSimpleName());
            return null;
        }

        return new ChatMemoryEntity(sessionId, messageType, content);
    }
}
