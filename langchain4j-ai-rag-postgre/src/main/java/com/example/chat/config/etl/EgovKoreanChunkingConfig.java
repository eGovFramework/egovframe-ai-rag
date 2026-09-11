package com.example.chat.config.etl;

import com.example.chat.config.etl.transformers.EgovKoreanSentenceSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 한국어 문장경계 인지 청킹 설정.
 */
@Slf4j
@Configuration
public class EgovKoreanChunkingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "document.chunking.korean-sentence", name = "enabled", havingValue = "true")
    public EgovKoreanSentenceSplitter koreanSentenceDocumentSplitter(
            @Value("${document.chunk-size:800}") int chunkSize,
            @Value("${document.chunking.korean-sentence.overlap-size-chars:#{null}}") Integer overlapSizeChars) {

        // 기본 오버랩은 기존 recursive 분할기와 같은 값을 쓰되, 청크가 작을 때 상한(절반)을 넘지 않게 맞춘다.
        int effectiveOverlapSizeChars = overlapSizeChars != null
                ? overlapSizeChars
                : Math.min(Math.max(chunkSize / 10, 50), chunkSize / 2);
        log.info("한국어 문장경계 청킹 초기화 - chunkSize: {}, overlapSizeChars: {}",
                chunkSize, effectiveOverlapSizeChars);
        return new EgovKoreanSentenceSplitter(chunkSize, effectiveOverlapSizeChars);
    }
}
