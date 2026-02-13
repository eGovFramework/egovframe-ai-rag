package com.example.chat.repository;

import com.example.chat.entity.ChatMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, Long> {

    /**
     * 특정 세션의 모든 메시지 조회 (시간 순)
     */
    List<ChatMemoryEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 특정 세션의 메시지 삭제
     */
    void deleteBySessionId(String sessionId);

    /**
     * 특정 세션의 메시지 개수 조회
     */
    long countBySessionId(String sessionId);
}
