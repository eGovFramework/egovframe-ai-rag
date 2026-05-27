package com.example.chat.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChatSession DTO 단위 테스트")
class ChatSessionTest {

    @Test
    @DisplayName("sessionId·title·createdAt 세 인자 생성자는 lastMessageAt을 createdAt과 동일하게 설정한다")
    void threeArgConstructor_setsLastMessageAtEqualToCreatedAt() {
        LocalDateTime now = LocalDateTime.of(2024, 3, 10, 9, 0, 0);

        ChatSession session = new ChatSession("session-001", "첫 번째 세션", now);

        assertThat(session.getSessionId()).isEqualTo("session-001");
        assertThat(session.getTitle()).isEqualTo("첫 번째 세션");
        assertThat(session.getCreatedAt()).isEqualTo(now);
        assertThat(session.getLastMessageAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("전체 인자 생성자로 네 필드를 각각 설정할 수 있다")
    void allArgsConstructor_setsAllFields() {
        LocalDateTime created = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        LocalDateTime last = LocalDateTime.of(2024, 1, 2, 12, 0, 0);

        ChatSession session = new ChatSession("s-abc", "제목", created, last);

        assertThat(session.getSessionId()).isEqualTo("s-abc");
        assertThat(session.getTitle()).isEqualTo("제목");
        assertThat(session.getCreatedAt()).isEqualTo(created);
        assertThat(session.getLastMessageAt()).isEqualTo(last);
    }

    @Test
    @DisplayName("기본 생성자로 생성 시 모든 필드가 null이다")
    void noArgsConstructor_allFieldsNull() {
        ChatSession session = new ChatSession();

        assertThat(session.getSessionId()).isNull();
        assertThat(session.getTitle()).isNull();
        assertThat(session.getCreatedAt()).isNull();
        assertThat(session.getLastMessageAt()).isNull();
    }

    @Test
    @DisplayName("setter로 lastMessageAt을 업데이트할 수 있다")
    void setter_updatesLastMessageAt() {
        LocalDateTime created = LocalDateTime.of(2024, 5, 1, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2024, 5, 1, 11, 30, 0);
        ChatSession session = new ChatSession("s-1", "세션1", created);

        session.setLastMessageAt(updated);

        assertThat(session.getLastMessageAt()).isEqualTo(updated);
        assertThat(session.getCreatedAt()).isEqualTo(created);
    }

    @Test
    @DisplayName("동일한 필드 값을 가진 두 객체는 equals가 true이다")
    void equals_withSameFields_returnsTrue() {
        LocalDateTime dt = LocalDateTime.of(2024, 7, 4, 0, 0, 0);

        ChatSession s1 = new ChatSession("id", "title", dt, dt);
        ChatSession s2 = new ChatSession("id", "title", dt, dt);

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }
}
