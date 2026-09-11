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
    @ConditionalOnProperty(prefix = "spring.ai.document.chunking.korean-sentence", name = "enabled", havingValue = "true")
    public EgovKoreanSentenceSplitter koreanSentenceTextSplitter(
            @Value("${spring.ai.document.chunk-size:400}") int chunkSize,
            @Value("${spring.ai.document.min-chunk-size-chars:350}") int minChunkSizeChars,
            @Value("${spring.ai.document.min-chunk-length-to-embed:50}") int minChunkLengthToEmbed,
            @Value("${spring.ai.document.max-num-chunks:500}") int maxNumChunks) {

        log.info("한국어 문장경계 청킹 초기화 - chunkSize: {}, minChunkSizeChars: {}, minChunkLengthToEmbed: {}, maxNumChunks: {}, keepSeparator: {}",
                chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, true);
        return new EgovKoreanSentenceSplitter(
                chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, true);
    }
}
