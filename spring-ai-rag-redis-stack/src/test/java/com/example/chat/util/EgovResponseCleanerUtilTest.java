package com.example.chat.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EgovResponseCleanerUtil} 단위테스트.
 * AI 응답에서 think 태그 제거 및 JSON 추출 동작을 검증한다.
 */
class EgovResponseCleanerUtilTest {

    @Test
    @DisplayName("null 입력은 null 그대로 반환한다")
    void nullInput() {
        assertNull(EgovResponseCleanerUtil.cleanResponse(null));
    }

    @Test
    @DisplayName("공백만 있는 입력은 원본 그대로 반환한다")
    void blankInput() {
        assertEquals("   ", EgovResponseCleanerUtil.cleanResponse("   "));
        assertEquals("", EgovResponseCleanerUtil.cleanResponse(""));
    }

    @Test
    @DisplayName("think 태그와 그 내용을 제거하고 JSON만 추출한다")
    void removeThinkTagAndExtractJson() {
        String input = "<think>모델의 추론 과정</think>{\"answer\":\"42\"}";
        assertEquals("{\"answer\":\"42\"}", EgovResponseCleanerUtil.cleanResponse(input));
    }

    @Test
    @DisplayName("think 태그는 대소문자를 구분하지 않고 제거한다")
    void thinkTagCaseInsensitive() {
        String input = "<THINK>reasoning</THINK>{\"k\":2}";
        assertEquals("{\"k\":2}", EgovResponseCleanerUtil.cleanResponse(input));
    }

    @Test
    @DisplayName("think 태그가 여러 줄에 걸쳐 있어도 제거한다(DOTALL)")
    void thinkTagMultiline() {
        String input = "<think>\n여러\n줄\n추론\n</think>\n{\"x\":1}";
        assertEquals("{\"x\":1}", EgovResponseCleanerUtil.cleanResponse(input));
    }

    @Test
    @DisplayName("JSON 앞뒤 텍스트가 있어도 첫 '{'부터 마지막 '}'까지 추출한다")
    void extractJsonBetweenBraces() {
        String input = "여기 결과입니다: {\"a\":1} 끝";
        assertEquals("{\"a\":1}", EgovResponseCleanerUtil.cleanResponse(input));
    }

    @Test
    @DisplayName("중첩 객체는 가장 바깥 범위를 유지한다")
    void nestedObject() {
        String input = "{\"a\":{\"b\":1}}";
        assertEquals("{\"a\":{\"b\":1}}", EgovResponseCleanerUtil.cleanResponse(input));
    }

    @Test
    @DisplayName("중괄호가 없으면 think 제거 후 트림된 텍스트를 반환한다")
    void noBracePassthrough() {
        String input = "<think>x</think>  순수 텍스트  ";
        assertEquals("순수 텍스트", EgovResponseCleanerUtil.cleanResponse(input));
    }
}
