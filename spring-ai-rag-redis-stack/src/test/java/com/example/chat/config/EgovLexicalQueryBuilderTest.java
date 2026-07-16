package com.example.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EgovLexicalQueryBuilder} 의 한글 어절 분해·폴백 질의 생성 로직을
 * Redis 비의존 순수함수 단위로 검증한다.
 */
class EgovLexicalQueryBuilderTest {

    @Test
    @DisplayName("공백 기준 어절 분해 - 순서 보존·중복 제거")
    void tokenizeSplitsByWhitespace() {
        assertThat(EgovLexicalQueryBuilder.tokenize("주민등록번호 변경 신청"))
                .containsExactly("주민등록번호", "변경", "신청");
        // 중복·다중 공백 정리
        assertThat(EgovLexicalQueryBuilder.tokenize("신청   변경 신청"))
                .containsExactly("신청", "변경");
    }

    @Test
    @DisplayName("빈/공백 질의는 빈 어절 리스트")
    void tokenizeBlankYieldsEmpty() {
        assertThat(EgovLexicalQueryBuilder.tokenize(null)).isEmpty();
        assertThat(EgovLexicalQueryBuilder.tokenize("   ")).isEmpty();
    }

    @Test
    @DisplayName("정확매칭 질의는 어절을 AND(공백)로 묶는다")
    void exactQueryJoinsTermsWithAnd() {
        assertThat(EgovLexicalQueryBuilder.exactQuery(List.of("주민등록번호", "변경")))
                .isEqualTo("@content:(주민등록번호 변경)");
    }

    @Test
    @DisplayName("접두매칭 질의는 어절 뒤에 * 를 붙여 OR 로 묶는다")
    void prefixQueryAppendsWildcard() {
        assertThat(EgovLexicalQueryBuilder.prefixQuery(List.of("주민", "여권")))
                .isEqualTo("@content:(주민* | 여권*)");
    }

    @Test
    @DisplayName("중위매칭 질의는 어절 양쪽에 * 를 붙여 OR 로 묶는다")
    void infixQueryWrapsWildcard() {
        assertThat(EgovLexicalQueryBuilder.infixQuery(List.of("등록", "민원")))
                .isEqualTo("@content:(*등록* | *민원*)");
    }

    @Test
    @DisplayName("폴백 단계는 [정확, 접두, 중위] 순으로 생성된다")
    void stagedQueriesAreOrdered() {
        List<String> staged = EgovLexicalQueryBuilder.buildStagedQueries("주민등록번호 변경");

        assertThat(staged).hasSize(3);
        assertThat(staged.get(0)).isEqualTo("@content:(주민등록번호 변경)");
        assertThat(staged.get(1)).isEqualTo("@content:(주민등록번호* | 변경*)");
        assertThat(staged.get(2)).isEqualTo("@content:(*주민등록번호* | *변경*)");
    }

    @Test
    @DisplayName("어절이 없으면 단계 질의도 비어 있다")
    void stagedQueriesEmptyForBlank() {
        assertThat(EgovLexicalQueryBuilder.buildStagedQueries("")).isEmpty();
    }

    @Test
    @DisplayName("RediSearch 특수문자는 이스케이프된다")
    void escapesSpecialChars() {
        // 콜론·괄호 등은 백슬래시로 이스케이프되어 질의 구문을 깨뜨리지 않는다.
        assertThat(EgovLexicalQueryBuilder.escape("a:b(c)"))
                .isEqualTo("a\\:b\\(c\\)");
        // 일반 한글/영문은 변형 없음
        assertThat(EgovLexicalQueryBuilder.escape("주민등록")).isEqualTo("주민등록");
    }
}
