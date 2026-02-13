package com.example.chat.util;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

/**
 * 추론 모델의 <think> 태그를 처리하는 커스텀 StructuredOutputConverter
 */
@Slf4j
public class EgovThinkTagOutputConverter<T> implements StructuredOutputConverter<T> {
    
    private final BeanOutputConverter<T> delegate;

    public EgovThinkTagOutputConverter(Class<T> targetClass) {
        this.delegate = new BeanOutputConverter<>(targetClass);
    }

    @Override
    public T convert(String text) {
        try {
            // 1. <think> 태그 제거 및 JSON 추출
            String cleanedJson = EgovResponseCleanerUtil.cleanResponse(text);
            
            log.debug("원본 응답: {}", text);
            log.debug("정리된 JSON: {}", cleanedJson);
            
            // 2. 기존 BeanOutputConverter를 사용하여 변환
            return delegate.convert(cleanedJson);
            
        } catch (Exception e) {
            log.error("JSON 변환 중 오류 발생. 원본 텍스트: {}", text, e);
            throw new RuntimeException("JSON 변환 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFormat() {
        return delegate.getFormat();
    }

    /**
     * 정적 팩토리 메서드로 ThinkTagAwareOutputConverter 생성
     * 
     * @param targetClass 대상 클래스
     * @return ThinkTagAwareOutputConverter 인스턴스
     */
    public static <T> EgovThinkTagOutputConverter<T> of(Class<T> targetClass) {
        return new EgovThinkTagOutputConverter<>(targetClass);
    }
}
