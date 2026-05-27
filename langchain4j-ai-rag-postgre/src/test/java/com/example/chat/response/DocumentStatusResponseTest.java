package com.example.chat.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentStatusResponse 단위 테스트")
class DocumentStatusResponseTest {

    @Test
    @DisplayName("전체 인자 생성자로 모든 필드가 올바르게 설정된다")
    void allArgsConstructor_setsAllFields() {
        DocumentStatusResponse response = new DocumentStatusResponse(true, 5, 10, 3, true);

        assertThat(response.processing()).isTrue();
        assertThat(response.processedCount()).isEqualTo(5);
        assertThat(response.totalCount()).isEqualTo(10);
        assertThat(response.changedCount()).isEqualTo(3);
        assertThat(response.hasDocuments()).isTrue();
    }

    @Test
    @DisplayName("3인자 생성자는 changedCount=0, hasDocuments=totalCount>0으로 설정한다")
    void threeArgConstructor_setsDefaults() {
        DocumentStatusResponse response = new DocumentStatusResponse(false, 7, 7);

        assertThat(response.processing()).isFalse();
        assertThat(response.processedCount()).isEqualTo(7);
        assertThat(response.totalCount()).isEqualTo(7);
        assertThat(response.changedCount()).isEqualTo(0);
        assertThat(response.hasDocuments()).isTrue();
    }

    @Test
    @DisplayName("3인자 생성자에서 totalCount=0이면 hasDocuments=false이다")
    void threeArgConstructor_zeroTotal_hasDocumentsFalse() {
        DocumentStatusResponse response = new DocumentStatusResponse(false, 0, 0);

        assertThat(response.hasDocuments()).isFalse();
    }

    @Test
    @DisplayName("4인자 생성자는 changedCount를 설정하고 hasDocuments=totalCount>0으로 설정한다")
    void fourArgConstructor_setsChangedCount() {
        DocumentStatusResponse response = new DocumentStatusResponse(true, 3, 5, 2);

        assertThat(response.changedCount()).isEqualTo(2);
        assertThat(response.hasDocuments()).isTrue();
        assertThat(response.totalCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("처리 완료 상태(processing=false, processedCount==totalCount)를 올바르게 표현한다")
    void processingComplete_correctlyRepresented() {
        DocumentStatusResponse response = new DocumentStatusResponse(false, 10, 10);

        assertThat(response.processing()).isFalse();
        assertThat(response.processedCount()).isEqualTo(response.totalCount());
    }

    @Test
    @DisplayName("동일한 값을 가진 두 레코드는 equals가 true이다")
    void equals_withSameValues_returnsTrue() {
        DocumentStatusResponse r1 = new DocumentStatusResponse(true, 1, 2, 1, true);
        DocumentStatusResponse r2 = new DocumentStatusResponse(true, 1, 2, 1, true);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
