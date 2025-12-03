package com.example.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private String messageType; // USER, ASSISTANT, SYSTEM
    private String content;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    public ChatMessageDto(String messageType, String content) {
        this.messageType = messageType;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }
}