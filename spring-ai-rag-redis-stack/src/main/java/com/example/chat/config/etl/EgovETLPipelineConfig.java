package com.example.chat.config.etl;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.config.etl.readers.EgovDocxReader;
import com.example.chat.config.etl.readers.EgovHwpReader;
import com.example.chat.config.etl.readers.EgovHwpxReader;
import com.example.chat.config.etl.readers.EgovMarkdownReader;
import com.example.chat.config.etl.readers.EgovPdfReader;
import com.example.chat.config.etl.transformers.EgovEnhancedDocumentTransformer;
import com.example.chat.config.etl.transformers.EgovContentFormatTransformer;
import com.example.chat.config.etl.transformers.EgovPiiMaskingTransformer;
import com.example.chat.config.etl.writers.EgovVectorStoreWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class EgovETLPipelineConfig {

    @Bean
    public EgovMarkdownReader markdownReader() {
        log.info("EgovMarkdownReader 빈 생성");
        return new EgovMarkdownReader();
    }

    @Bean
    public EgovDocxReader docxReader() {
        log.info("EgovDocxReader 빈 생성");
        return new EgovDocxReader();
    }

    @Bean
    public EgovPdfReader pdfReader() {
        log.info("EgovPdfReader 빈 생성");
        return new EgovPdfReader();
    }

    @Bean
    public EgovHwpReader hwpReader() {
        log.info("EgovHwpReader 빈 생성");
        return new EgovHwpReader();
    }

    @Bean
    public EgovHwpxReader hwpxReader() {
        log.info("EgovHwpxReader 빈 생성");
        return new EgovHwpxReader();
    }

    @Bean
    public EgovContentFormatTransformer egovContentFormatTransformer() {
        log.info("EgovContentFormatTransformer 빈 생성");
        return new EgovContentFormatTransformer();
    }

    @Bean
    public EgovPiiMaskingTransformer egovPiiMaskingTransformer(
            @Value("${spring.ai.document.pii-masking.enabled:false}") boolean enabled,
            @Value("${spring.ai.document.pii-masking.mask-rrn:true}") boolean maskRrn,
            @Value("${spring.ai.document.pii-masking.mask-card:true}") boolean maskCard,
            @Value("${spring.ai.document.pii-masking.mask-secret:true}") boolean maskSecret,
            @Value("${spring.ai.document.pii-masking.rrn-token:[RRN]}") String rrnToken,
            @Value("${spring.ai.document.pii-masking.card-token:[CARD]}") String cardToken,
            @Value("${spring.ai.document.pii-masking.secret-token:[SECRET]}") String secretToken,
            @Value("${spring.ai.document.pii-masking.account-token:[ACCOUNT]}") String accountToken,
            @Value("${spring.ai.document.pii-masking.account-regex:}") String accountRegex) {
        log.info("EgovPiiMaskingTransformer 빈 생성 (enabled={})", enabled);
        return new EgovPiiMaskingTransformer(enabled, maskRrn, maskCard, maskSecret,
                rrnToken, cardToken, secretToken, accountToken, accountRegex);
    }

    @Bean
    public EgovEnhancedDocumentTransformer egovEnhancedDocumentTransformer(OllamaChatModel ollamaChatModel) {
        log.info("EgovEnhancedDocumentTransformer 빈 생성");
        return new EgovEnhancedDocumentTransformer(ollamaChatModel);
    }

    @Bean
    public EgovVectorStoreWriter vectorStoreWriter(RedisVectorStore redisVectorStore) {
        log.info("VectorStore DocumentWriter 빈 생성");
        return new EgovVectorStoreWriter(redisVectorStore);
    }
}
