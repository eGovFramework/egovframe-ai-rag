package com.example.chat.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatSessionEntity 단위 테스트")
class ChatSessionEntityTest {

    @Test
    @DisplayName("전체 인자 생성자로 모든 필드가 설정된다")
    void allArgsConstructor_setsAllFields() {
        LocalDateTime created = LocalDateTime.of(2024, 2, 1, 9, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2024, 2, 1, 10, 0, 0);

        ChatSessionEntity entity = new ChatSessionEntity("s-abc", "세션 제목", created, updated);

        assertThat(entity.getSessionId()).isEqualTo("s-abc");
        assertThat(entity.getTitle()).isEqualTo("세션 제목");
        assertThat(entity.getCreatedAt()).isEqualTo(created);
        assertThat(entity.getUpdatedAt()).isEqualTo(updated);
    }

    @Test
    @DisplayName("기본 생성자로 생성 시 모든 필드가 null이다")
    void noArgsConstructor_allFieldsNull() {
        ChatSessionEntity entity = new ChatSessionEntity();

        assertThat(entity.getSessionId()).isNull();
        assertThat(entity.getTitle()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("setter로 title을 변경할 수 있다")
    void setter_updatesTitle() {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setSessionId("s-1");
        entity.setTitle("새 제목");

        assertThat(entity.getSessionId()).isEqualTo("s-1");
        assertThat(entity.getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("동일한 필드 값을 가진 두 객체는 equals가 true이다")
    void equals_withSameFields_returnsTrue() {
        LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 12, 0, 0);

        ChatSessionEntity e1 = new ChatSessionEntity("id", "title", dt, dt);
        ChatSessionEntity e2 = new ChatSessionEntity("id", "title", dt, dt);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    @DisplayName("sessionId가 다르면 equals가 false이다")
    void equals_differentSessionId_returnsFalse() {
        LocalDateTime dt = LocalDateTime.now();

        ChatSessionEntity e1 = new ChatSessionEntity("id-1", "title", dt, dt);
        ChatSessionEntity e2 = new ChatSessionEntity("id-2", "title", dt, dt);

        assertThat(e1).isNotEqualTo(e2);
    }
}
