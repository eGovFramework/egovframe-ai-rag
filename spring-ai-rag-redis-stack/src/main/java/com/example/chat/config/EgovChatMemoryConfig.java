package com.example.chat.config;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.repository.EgovRedisChatMemoryRepository;

@Configuration
public class EgovChatMemoryConfig {

    @Value("${chat.memory.max-messages:20}")
    private int maxMessages;
    
    @Bean
    public ChatMemory chatMemory(EgovRedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(maxMessages) // 설정을 통해 유지 메시지 수 조정
                .build();
    }
    
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .build();
    }
}
