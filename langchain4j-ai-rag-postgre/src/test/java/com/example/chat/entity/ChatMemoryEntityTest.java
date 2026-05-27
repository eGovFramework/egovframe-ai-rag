package com.example.chat.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatMemoryEntity 단위 테스트")
class ChatMemoryEntityTest {

    @Test
    @DisplayName("sessionId·messageType·content 세 인자 생성자로 필드가 설정된다")
    void threeArgConstructor_setsFields() {
        ChatMemoryEntity entity = new ChatMemoryEntity("s-001", "USER", "질문 내용");

        assertThat(entity.getSessionId()).isEqualTo("s-001");
        assertThat(entity.getMessageType()).isEqualTo("USER");
        assertThat(entity.getContent()).isEqualTo("질문 내용");
        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("전체 인자 생성자로 id와 createdAt을 포함해 설정된다")
    void allArgsConstructor_setsAllFields() {
        LocalDateTime now = LocalDateTime.of(2024, 4, 1, 8, 0, 0);

        ChatMemoryEntity entity = new ChatMemoryEntity(1L, "s-002", "ASSISTANT", "답변", now);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getSessionId()).isEqualTo("s-002");
        assertThat(entity.getMessageType()).isEqualTo("ASSISTANT");
        assertThat(entity.getContent()).isEqualTo("답변");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("기본 생성자로 생성 시 모든 필드가 null이다")
    void noArgsConstructor_allFieldsNull() {
        ChatMemoryEntity entity = new ChatMemoryEntity();

        assertThat(entity.getId()).isNull();
        assertThat(entity.getSessionId()).isNull();
        assertThat(entity.getMessageType()).isNull();
        assertThat(entity.getContent()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("setter로 content를 변경할 수 있다")
    void setter_updatesContent() {
        ChatMemoryEntity entity = new ChatMemoryEntity("s-1", "USER", "원본");
        entity.setContent("수정된 내용");

        assertThat(entity.getContent()).isEqualTo("수정된 내용");
    }

    @Test
    @DisplayName("동일한 필드 값을 가진 두 객체는 equals가 true이다")
    void equals_withSameFields_returnsTrue() {
        LocalDateTime dt = LocalDateTime.of(2024, 1, 1, 0, 0, 0);

        ChatMemoryEntity e1 = new ChatMemoryEntity(1L, "s", "USER", "msg", dt);
        ChatMemoryEntity e2 = new ChatMemoryEntity(1L, "s", "USER", "msg", dt);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }
}
