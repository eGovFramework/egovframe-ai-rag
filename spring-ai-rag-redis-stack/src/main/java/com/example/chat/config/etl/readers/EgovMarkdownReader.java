package com.example.chat.config.etl.readers;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;

@Slf4j
@Component
public class EgovMarkdownReader implements DocumentReader {

    @Value("${spring.ai.document.path}")
    private String documentPath;

    @Override
    public List<Document> get() {
        List<Document> documents = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        
        try {
            Resource[] resources = resolver.getResources(documentPath);
            
            if (resources.length == 0) {
                log.warn("마크다운 파일을 찾을 수 없습니다: {}", documentPath);
                return List.of();
            }
            
            log.info("{}개의 마크다운 파일을 찾았습니다.", resources.length);
            
            for (Resource resource : resources) {
                Document doc = processMarkdownResource(resource);
                if (doc != null) {
                    documents.add(doc);
                }
            }
        } catch (IOException e) {
            log.error("마크다운 문서 로드 중 오류 발생", e);
            // 파일이 없는 경우도 있을 수 있으므로 예외를 발생시키지 않고 빈 리스트 반환
            log.warn("마크다운 파일을 찾을 수 없거나 접근할 수 없습니다: {}", documentPath);
            return List.of();
        }
        
        return documents;
    }

    private Document processMarkdownResource(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            log.warn("파일명이 null입니다: {}", resource.getDescription());
            return null;
        }
        
        String content = readResourceContent(resource);
        if (content == null || content.trim().isEmpty()) {
            log.warn("빈 파일 건너뜀: {}", filename);
            return null;
        }
        
        Map<String, Object> metadata = createEnhancedMetadata(filename, content);
        String docId = "doc-" + filename
                .replaceAll("[\\/:*?\"<>|]", "")
                .replaceAll("\\s+", "-");
        
        log.info("마크다운 문서 로드 완료: {}, 크기: {}바이트", filename, content.length());
        return new Document(docId, content, metadata);
    }

    private Map<String, Object> createEnhancedMetadata(String filename, String content) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filename);
        metadata.put("type", "markdown");
        metadata.put("content_length", content.length());
        metadata.put("has_headers", content.matches(".*#{1,6}\\s.*"));
        metadata.put("has_code_blocks", content.contains("```"));
        metadata.put("has_links", content.matches(".*\\[.*\\]\\(.*\\).*"));
        metadata.put("has_images", content.matches(".*!\\[.*\\]\\(.*\\).*"));
        metadata.put("line_count", content.split("\n").length);
        
        return metadata;
    }
    
    private String readResourceContent(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
} 