package com.example.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_hashes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentHashEntity {

    @Id
    @Column(name = "doc_id", length = 500)
    private String docId;

    @Column(name = "hash", nullable = false, length = 32)
    private String hash;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public DocumentHashEntity(String docId, String hash) {
        this.docId = docId;
        this.hash = hash;
    }
}
