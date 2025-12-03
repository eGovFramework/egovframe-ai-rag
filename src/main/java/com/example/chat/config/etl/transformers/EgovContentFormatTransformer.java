package com.example.chat.config.etl.transformers;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring AI ContentFormatTransformer 표준 기능과 커스텀 정규화를 결합한 문서 변환기
 * - Spring AI: 템플릿 포맷팅 (text, source, type 메타데이터)
 * - 커스텀: 내용 정규화 (HTML 제거, 공백/줄바꿈 정규화, 특수문자 처리)
 */
@Slf4j
@Component
public class EgovContentFormatTransformer implements DocumentTransformer {
    
    private final ContentFormatTransformer contentFormatTransformer;
    
    // 정규화 설정
    @Value("${spring.ai.document.normalization.enabled}")
    private boolean normalizationEnabled;
    
    @Value("${spring.ai.document.normalization.remove-html-tags}")
    private boolean removeHtmlTags;
    
    @Value("${spring.ai.document.normalization.normalize-whitespace}")
    private boolean normalizeWhitespace;
    
    @Value("${spring.ai.document.normalization.normalize-newlines}")
    private boolean normalizeNewlines;
    
    @Value("${spring.ai.document.normalization.remove-code-blocks}")
    private boolean removeCodeBlocks;
    
    @Value("${spring.ai.document.normalization.clean-special-chars}")
    private boolean cleanSpecialChars;
    
    // 정규식 패턴들
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile(
        "[^\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F\\uA960-\\uA97F\\uD7B0-\\uD7FF" +
        "a-zA-Z0-9\\s\\n\\t\\-_.,()\\[\\]{}\"':;!?@#$%&*+=|\\\\/<>]");
    
    public EgovContentFormatTransformer() {
        // Spring AI의 DefaultContentFormatter 사용 - 템플릿 포맷팅만 담당
        ContentFormatter contentFormatter = DefaultContentFormatter.builder()
                .withTextTemplate("{text}")
                .withMetadataTemplate("source: {source}\\ntype: {type}\\nnormalized: true")
                .withMetadataSeparator("\\n")
                .build();
                
        this.contentFormatTransformer = new ContentFormatTransformer(contentFormatter);
    }

    @Override
    public List<Document> apply(List<Document> documents) { 
        if (!normalizationEnabled) {
            log.info("문서 정규화가 비활성화되어 있습니다. 원본 문서를 그대로 반환합니다.");
            return documents;
        }
        
        log.info("문서 형식 변환 시작: {}개 문서 (HTML: {}, 공백: {}, 줄바꿈: {}, 코드블록: {}, 특수문자: {})", 
                 documents.size(), removeHtmlTags, normalizeWhitespace, normalizeNewlines, removeCodeBlocks, cleanSpecialChars);
        
        // 1. Spring AI ContentFormatTransformer로 템플릿 포맷팅 수행
        List<Document> formattedDocuments = contentFormatTransformer.apply(documents);
        
        // 2. 커스텀 정규화 수행
        List<Document> normalizedDocuments = applyCustomNormalization(formattedDocuments);
        
        log.info("문서 형식 변환 완료: {}개 문서 (Spring AI 포맷팅 + 커스텀 정규화)", normalizedDocuments.size());
        return normalizedDocuments;
    }
    
    /**
     * 커스텀 정규화 적용
     * 모든 내용 정규화는 여기서 처리
     */
    private List<Document> applyCustomNormalization(List<Document> documents) {
        return documents.stream()
                .map(this::applyCustomNormalizationToDocument)
                .toList();
    }
    
    /**
     * 개별 문서에 커스텀 정규화 적용
     * 모든 정규화를 커스텀으로 처리
     */
    private Document applyCustomNormalizationToDocument(Document document) {
        String originalContent = document.getText();
        String normalizedContent = originalContent;
        
        // HTML 태그 제거
        if (removeHtmlTags) {
            normalizedContent = normalizedContent.replaceAll("<[^>]*>", "");
        }
        
        // 공백 정규화
        if (normalizeWhitespace) {
            normalizedContent = normalizedContent.replaceAll("\\s+", " ");
        }
        
        // 줄바꿈 정규화
        if (normalizeNewlines) {
            normalizedContent = normalizedContent.replaceAll("\\n{2,}", "\\n");
        }
        
        // 코드 블록 제거
        if (removeCodeBlocks) {
            normalizedContent = CODE_BLOCK_PATTERN.matcher(normalizedContent).replaceAll("");
        }
        
        // 특수문자 정리
        if (cleanSpecialChars) {
            normalizedContent = SPECIAL_CHARS_PATTERN.matcher(normalizedContent).replaceAll("");
        }
        
        // 앞뒤 공백 제거
        normalizedContent = normalizedContent.trim();
        
        // 내용이 변경된 경우에만 새 Document 생성
        if (!originalContent.equals(normalizedContent)) {
            // 메타데이터에 정규화 정보 추가
            document.getMetadata().put("original_length", originalContent.length());
            document.getMetadata().put("normalized_length", normalizedContent.length());
            document.getMetadata().put("custom_normalization_applied", true);
            document.getMetadata().put("code_blocks_removed", removeCodeBlocks);
            document.getMetadata().put("special_chars_cleaned", cleanSpecialChars);
            
            if (log.isDebugEnabled()) {
                log.debug("커스텀 정규화 적용: {} -> {} (길이: {} -> {})", 
                    originalContent.substring(0, Math.min(50, originalContent.length())), 
                    normalizedContent.substring(0, Math.min(50, normalizedContent.length())),
                    originalContent.length(), normalizedContent.length());
            }
            
            return new Document(document.getId(), normalizedContent, document.getMetadata());
        }
        
        return document;
    }
}