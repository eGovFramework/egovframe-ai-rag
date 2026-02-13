package com.example.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_memory", indexes = {
    @Index(name = "idx_chat_memory_session_id", columnList = "session_id"),
    @Index(name = "idx_chat_memory_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType; // USER, ASSISTANT, SYSTEM

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public ChatMemoryEntity(String sessionId, String messageType, String content) {
        this.sessionId = sessionId;
        this.messageType = messageType;
        this.content = content;
    }
}
