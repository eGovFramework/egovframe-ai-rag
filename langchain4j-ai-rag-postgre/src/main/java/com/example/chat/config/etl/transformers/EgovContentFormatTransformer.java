package com.example.chat.config.etl.transformers;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 문서 내용 정규화 변환기
 * - HTML 태그 제거
 * - 공백 및 줄바꿈 정규화
 * - 코드 블록 제거 (선택)
 * - 특수문자 정리
 */
@Slf4j
@Component
public class EgovContentFormatTransformer implements DocumentTransformer {

    // 정규화 설정
    @Value("${document.normalization.enabled}")
    private boolean normalizationEnabled;

    @Value("${document.normalization.remove-html-tags}")
    private boolean removeHtmlTags;

    @Value("${document.normalization.normalize-whitespace}")
    private boolean normalizeWhitespace;

    @Value("${document.normalization.normalize-newlines}")
    private boolean normalizeNewlines;

    @Value("${document.normalization.remove-code-blocks}")
    private boolean removeCodeBlocks;

    @Value("${document.normalization.clean-special-chars}")
    private boolean cleanSpecialChars;

    // 정규식 패턴들
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile(
            "[^\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F\\uA960-\\uA97F\\uD7B0-\\uD7FF" +
                    "a-zA-Z0-9\\s\\n\\t\\-_.,()\\[\\]{}\"':;!?@#$%&*+=|\\\\/<>" +
                    // 한국어 행정문서에 흔한 문자들. 아래가 없으면 본문에서 지워진다.
                    "\\u00B0-\\u00B7" +   // 도, 플러스마이너스, 제곱·세제곱, 가운뎃점
                    "\\u2010-\\u2027" +   // 붙임표류, 따옴표, 말줄임표
                    "\\u20A0-\\u20BF" +   // 통화 기호(원)
                    "\\u2100-\\u214F" +   // 섭씨, 번호
                    "\\u2460-\\u24FF" +   // 원문자 번호
                    "\\u3001-\\u303F" +   // 낫표, 겹낫표, 홑화살괄호
                    "\\u3300-\\u33FF" +   // 제곱미터, 킬로그램 등 단위 기호
                    "\\u4E00-\\u9FFF" +   // 한자
                    "]");

    @Override
    public Document transform(Document document) {
        if (!normalizationEnabled) {
            return document;
        }

        String originalContent = document.text();
        String normalizedContent = originalContent;

        // HTML 태그 제거
        if (removeHtmlTags) {
            normalizedContent = normalizedContent.replaceAll("<[^>]*>", "");
        }

        // 공백 정규화
        if (normalizeWhitespace) {
            normalizedContent = normalizedContent.replaceAll("[^\\S\\r\\n]+", " ");
        }

        // 줄바꿈 정규화 (CRLF -> LF 통일 후 빈 줄 제거)
        if (normalizeNewlines) {
            normalizedContent = normalizedContent.replaceAll("\\r\\n?", "\n");
            normalizedContent = normalizedContent.replaceAll("\n{2,}", "\n");
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
            Metadata newMetadata = document.metadata().copy();
            newMetadata.put("original_length", String.valueOf(originalContent.length()));
            newMetadata.put("normalized_length", String.valueOf(normalizedContent.length()));
            newMetadata.put("normalization_applied", "true");
            newMetadata.put("code_blocks_removed", String.valueOf(removeCodeBlocks));
            newMetadata.put("special_chars_cleaned", String.valueOf(cleanSpecialChars));

            if (log.isDebugEnabled()) {
                log.debug("정규화 적용: {} -> {} (길이: {} -> {})",
                        originalContent.substring(0, Math.min(50, originalContent.length())),
                        normalizedContent.substring(0, Math.min(50, normalizedContent.length())),
                        originalContent.length(), normalizedContent.length());
            }

            return Document.from(normalizedContent, newMetadata);
        }

        return document;
    }

    @Override
    public List<Document> transformAll(List<Document> documents) {
        if (!normalizationEnabled) {
            log.info("문서 정규화가 비활성화되어 있습니다. 원본 문서를 그대로 반환합니다.");
            return documents;
        }

        log.info("문서 형식 변환 시작: {}개 문서 (HTML: {}, 공백: {}, 줄바꿈: {}, 코드블록: {}, 특수문자: {})",
                documents.size(), removeHtmlTags, normalizeWhitespace, normalizeNewlines, removeCodeBlocks,
                cleanSpecialChars);

        List<Document> normalizedDocuments = documents.stream()
                .map(this::transform)
                .collect(Collectors.toList());

        log.info("문서 형식 변환 완료: {}개 문서", normalizedDocuments.size());
        return normalizedDocuments;
    }
}
