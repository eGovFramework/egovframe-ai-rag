package com.example.chat.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EgovChatSessionServiceImpl#generateSessionTitle(String)}의 경계 동작을 검증한다.
 *
 * <p>해당 메서드는 주입 의존성(RedisTemplate/ChatMemory/Repository)을 사용하지 않는
 * 순수 문자열 가공 로직이므로, 의존성을 null로 둔 인스턴스에서 직접 호출해
 * Redis 등 외부 인프라 없이 결정적으로 검증한다.
 *
 * <p>규칙: null/공백이면 "새 채팅"을 반환하고, 그 외에는 trim 후 길이가 30자를 초과할
 * 때만 앞 27자에 "..."를 덧붙여 길이 30으로 절단한다.
 */
class EgovChatSessionServiceImplTitleTest {

    private final EgovChatSessionServiceImpl service =
            new EgovChatSessionServiceImpl(null, null, null);

    @Test
    @DisplayName("null 입력이면 기본 제목 '새 채팅'을 반환한다")
    void returnsDefaultTitleWhenNull() {
        assertThat(service.generateSessionTitle(null)).isEqualTo("새 채팅");
    }

    @Test
    @DisplayName("빈 문자열이면 기본 제목 '새 채팅'을 반환한다")
    void returnsDefaultTitleWhenEmpty() {
        assertThat(service.generateSessionTitle("")).isEqualTo("새 채팅");
    }

    @Test
    @DisplayName("공백만 있으면 기본 제목 '새 채팅'을 반환한다")
    void returnsDefaultTitleWhenBlank() {
        assertThat(service.generateSessionTitle("   ")).isEqualTo("새 채팅");
    }

    @Test
    @DisplayName("양끝 공백은 제거된다")
    void trimsSurroundingWhitespace() {
        assertThat(service.generateSessionTitle("  안녕  ")).isEqualTo("안녕");
    }

    @Test
    @DisplayName("짧은 일반 메시지는 그대로 제목이 된다")
    void returnsShortMessageAsIs() {
        assertThat(service.generateSessionTitle("eGovFrame RAG")).isEqualTo("eGovFrame RAG");
    }

    @Test
    @DisplayName("정확히 30자이면 절단 없이 그대로 반환한다")
    void keepsExactly30Characters() {
        String input = "a".repeat(30);
        String result = service.generateSessionTitle(input);

        assertThat(result).isEqualTo(input);
        assertThat(result).hasSize(30);
    }

    @Test
    @DisplayName("31자이면 앞 27자에 '...'을 붙여 길이 30으로 절단한다")
    void truncatesWhenLongerThan30Characters() {
        String input = "a".repeat(31);
        String result = service.generateSessionTitle(input);

        assertThat(result).hasSize(30);
        assertThat(result).isEqualTo("a".repeat(27) + "...");
        assertThat(result).endsWith("...");
    }

    @Test
    @DisplayName("한글 30자 경계도 char 단위로 동일하게 처리된다(서로게이트 없음)")
    void handlesKoreanAt30CharacterBoundary() {
        String exactly30 = "가".repeat(30);
        assertThat(service.generateSessionTitle(exactly30)).isEqualTo(exactly30);

        String over30 = "가".repeat(31);
        String result = service.generateSessionTitle(over30);
        assertThat(result).hasSize(30);
        assertThat(result).isEqualTo("가".repeat(27) + "...");
    }
}
