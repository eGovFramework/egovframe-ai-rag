package com.example.chat.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StreamTokenDto 단위 테스트")
class StreamTokenDtoTest {

    @Test
    @DisplayName("token 값이 올바르게 저장된다")
    void token_storedCorrectly() {
        StreamTokenDto dto = new StreamTokenDto("hello");

        assertThat(dto.token()).isEqualTo("hello");
    }

    @Test
    @DisplayName("공백 토큰도 그대로 저장된다")
    void whitespaceToken_storedAsIs() {
        StreamTokenDto dto = new StreamTokenDto(" ");

        assertThat(dto.token()).isEqualTo(" ");
    }

    @Test
    @DisplayName("null 토큰도 허용된다")
    void nullToken_allowed() {
        StreamTokenDto dto = new StreamTokenDto(null);

        assertThat(dto.token()).isNull();
    }

    @Test
    @DisplayName("동일한 token 값을 가진 두 레코드는 equals가 true이다")
    void equals_withSameToken_returnsTrue() {
        StreamTokenDto dto1 = new StreamTokenDto("word");
        StreamTokenDto dto2 = new StreamTokenDto("word");

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    @DisplayName("서로 다른 token 값을 가진 두 레코드는 equals가 false이다")
    void equals_withDifferentToken_returnsFalse() {
        StreamTokenDto dto1 = new StreamTokenDto("a");
        StreamTokenDto dto2 = new StreamTokenDto("b");

        assertThat(dto1).isNotEqualTo(dto2);
    }
}
