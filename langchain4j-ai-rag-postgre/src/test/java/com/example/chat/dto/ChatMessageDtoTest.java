package com.example.chat.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.temporal.ChronoUnit;

@DisplayName("ChatMessageDto 단위 테스트")
class ChatMessageDtoTest {

    @Test
    @DisplayName("전체 인자 생성자로 필드가 올바르게 설정된다")
    void allArgsConstructor_setsFieldsCorrectly() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        ChatMessageDto dto = new ChatMessageDto("USER", "안녕하세요", timestamp);

        assertThat(dto.getMessageType()).isEqualTo("USER");
        assertThat(dto.getContent()).isEqualTo("안녕하세요");
        assertThat(dto.getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("messageType, content 두 인자 생성자는 timestamp를 현재 시각으로 설정한다")
    void twoArgConstructor_setsTimestampToNow() {
        LocalDateTime before = LocalDateTime.now();

        ChatMessageDto dto = new ChatMessageDto("ASSISTANT", "응답입니다");

        LocalDateTime after = LocalDateTime.now();

        assertThat(dto.getMessageType()).isEqualTo("ASSISTANT");
        assertThat(dto.getContent()).isEqualTo("응답입니다");
        assertThat(dto.getTimestamp()).isBetween(before, after);
    }

    @Test
    @DisplayName("기본 생성자로 생성 시 모든 필드가 null이다")
    void noArgsConstructor_allFieldsNull() {
        ChatMessageDto dto = new ChatMessageDto();

        assertThat(dto.getMessageType()).isNull();
        assertThat(dto.getContent()).isNull();
        assertThat(dto.getTimestamp()).isNull();
    }

    @Test
    @DisplayName("setter로 필드를 변경할 수 있다")
    void setters_updateFields() {
        ChatMessageDto dto = new ChatMessageDto();
        LocalDateTime now = LocalDateTime.now();

        dto.setMessageType("SYSTEM");
        dto.setContent("시스템 메시지");
        dto.setTimestamp(now);

        assertThat(dto.getMessageType()).isEqualTo("SYSTEM");
        assertThat(dto.getContent()).isEqualTo("시스템 메시지");
        assertThat(dto.getTimestamp()).isEqualTo(now);
    }

    @Test
    @DisplayName("동일한 필드 값을 가진 두 객체는 equals가 true이다")
    void equals_withSameFields_returnsTrue() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 6, 1, 12, 0, 0);

        ChatMessageDto dto1 = new ChatMessageDto("USER", "hello", timestamp);
        ChatMessageDto dto2 = new ChatMessageDto("USER", "hello", timestamp);

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }

    @Test
    @DisplayName("toString에 주요 필드 정보가 포함된다")
    void toString_containsFieldInfo() {
        ChatMessageDto dto = new ChatMessageDto("USER", "내용", null);

        String str = dto.toString();

        assertThat(str).contains("USER");
        assertThat(str).contains("내용");
    }
}
