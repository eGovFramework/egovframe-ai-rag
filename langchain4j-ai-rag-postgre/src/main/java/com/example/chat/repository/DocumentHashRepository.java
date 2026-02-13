package com.example.chat.repository;

import com.example.chat.entity.DocumentHashEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentHashRepository extends JpaRepository<DocumentHashEntity, String> {
    // 기본 CRUD 메서드만 사용
}
