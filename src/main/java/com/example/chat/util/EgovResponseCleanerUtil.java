package com.example.chat.util;

import java.util.regex.Pattern;

/**
 * AI 모델 응답에서 마크업 태그를 제거하는 유틸리티 클래스
 */
public class EgovResponseCleanerUtil {

    // <think> 태그와 그 내용을 제거하는 정규식
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile(
        "<think>.*?</think>", 
        Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /**
     * AI 모델 응답에서 <think> 태그를 제거하고 순수한 JSON만 추출
     * 
     * @param response AI 모델의 원본 응답
     * @return 정리된 JSON 문자열
     */
    public static String cleanResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }

        String cleaned = response.trim();
        
        // <think> 태그와 그 내용 제거
        cleaned = THINK_TAG_PATTERN.matcher(cleaned).replaceAll("");
        
        // JSON 객체 시작과 끝 찾기
        int startBrace = cleaned.indexOf('{');
        int endBrace = cleaned.lastIndexOf('}');
        
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            // JSON 부분만 추출
            cleaned = cleaned.substring(startBrace, endBrace + 1);
        }
        
        // 앞뒤 공백 제거
        cleaned = cleaned.trim();
        
        return cleaned;
    }
}
