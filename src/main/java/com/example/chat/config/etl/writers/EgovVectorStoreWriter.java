package com.example.chat.config.etl.writers;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EgovVectorStoreWriter implements DocumentWriter {

    private final RedisVectorStore redisVectorStore;

    @Override
    public void accept(List<Document> documents) {
        log.info("벡터 저장소에 {}개 문서 저장 시작", documents.size());
        
        if (documents.isEmpty()) {
            log.warn("저장할 문서가 없습니다.");
            return;
        }
        
        // 문서 정보 로깅
        for (int i = 0; i < Math.min(documents.size(), 3); i++) {
            Document doc = documents.get(i);
            log.debug("문서 {}: ID={}, 크기={}바이트, 메타데이터={}", 
                    i, doc.getId(), doc.getText().length(), doc.getMetadata());
        }
        
        try {
            redisVectorStore.add(documents);
            log.info("벡터 저장소에 {}개 문서 저장 완료", documents.size());
        } catch (Exception e) {
            log.error("벡터 저장소 저장 중 오류 발생", e);
            throw new RuntimeException("벡터 저장소 저장 중 오류 발생", e);
        }
    }
} 