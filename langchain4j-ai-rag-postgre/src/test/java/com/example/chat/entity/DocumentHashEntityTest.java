package com.example.chat.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentHashEntity 단위 테스트")
class DocumentHashEntityTest {

    @Test
    @DisplayName("docId·hash 두 인자 생성자로 필드가 설정된다")
    void twoArgConstructor_setsFields() {
        DocumentHashEntity entity = new DocumentHashEntity("doc-001", "abc123hash");

        assertThat(entity.getDocId()).isEqualTo("doc-001");
        assertThat(entity.getHash()).isEqualTo("abc123hash");
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("전체 인자 생성자로 updatedAt 포함 모든 필드가 설정된다")
    void allArgsConstructor_setsAllFields() {
        LocalDateTime now = LocalDateTime.of(2024, 5, 20, 14, 0, 0);

        DocumentHashEntity entity = new DocumentHashEntity("doc-002", "hashvalue", now);

        assertThat(entity.getDocId()).isEqualTo("doc-002");
        assertThat(entity.getHash()).isEqualTo("hashvalue");
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("기본 생성자로 생성 시 모든 필드가 null이다")
    void noArgsConstructor_allFieldsNull() {
        DocumentHashEntity entity = new DocumentHashEntity();

        assertThat(entity.getDocId()).isNull();
        assertThat(entity.getHash()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("setter로 hash를 변경할 수 있다")
    void setter_updatesHash() {
        DocumentHashEntity entity = new DocumentHashEntity("doc-1", "oldhash");
        entity.setHash("newhash");

        assertThat(entity.getHash()).isEqualTo("newhash");
    }

    @Test
    @DisplayName("동일한 필드 값을 가진 두 객체는 equals가 true이다")
    void equals_withSameFields_returnsTrue() {
        LocalDateTime dt = LocalDateTime.of(2024, 3, 3, 3, 3, 3);

        DocumentHashEntity e1 = new DocumentHashEntity("id", "hash", dt);
        DocumentHashEntity e2 = new DocumentHashEntity("id", "hash", dt);

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    @DisplayName("docId가 다르면 equals가 false이다")
    void equals_differentDocId_returnsFalse() {
        DocumentHashEntity e1 = new DocumentHashEntity("doc-A", "h");
        DocumentHashEntity e2 = new DocumentHashEntity("doc-B", "h");

        assertThat(e1).isNotEqualTo(e2);
    }
}
