package com.example.chat.config.etl;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.config.etl.readers.EgovMarkdownReader;
import com.example.chat.config.etl.readers.EgovPdfReader;
import com.example.chat.config.etl.transformers.EgovEnhancedDocumentTransformer;
import com.example.chat.config.etl.transformers.EgovContentFormatTransformer;
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
    public EgovPdfReader pdfReader() {
        log.info("EgovPdfReader 빈 생성");
        return new EgovPdfReader();
    }

    @Bean
    public EgovContentFormatTransformer egovContentFormatTransformer() {
        log.info("EgovContentFormatTransformer 빈 생성");
        return new EgovContentFormatTransformer();
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