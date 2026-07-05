package com.example.chat.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EgovDocumentServiceImpl#uploadMarkdownFiles(MultipartFile[])}의 검증 분기를 검증한다.
 *
 * <p>업로드 메서드는 디스크 저장(transferTo) 이전에 파일 개수/이름/확장자/크기 검증을
 * 수행하고, 위반 시 {@code success=false}와 안내 메시지를 담은 {@code Map}을 반환한다(예외 미사용).
 * 이 검증 분기는 주입 의존성과 {@code documentPath}를 사용하지 않으므로, 의존성을 null로 둔
 * 인스턴스에서 {@link MockMultipartFile}로 호출해 DB/디스크/Spring 컨텍스트 없이 결정적으로 검증한다.
 *
 * <p>검증을 통과한 정상 경로는 파일시스템({@code documentPath}/{@code transferTo})에 의존하므로
 * 단위 테스트 범위에서 제외한다(검증 거부 분기만 대상으로 한다).
 */
class EgovDocumentServiceImplUploadValidationTest {

    // 검증 거부 분기만 대상으로 하므로 모든 협력 객체는 null로 둔다. 인자 수는
    // EgovDocumentServiceImpl 생성자(리더 6 + 변환 2 + writer + repository + executor = 11)와 일치.
    private final EgovDocumentServiceImpl service =
            new EgovDocumentServiceImpl(null, null, null, null, null, null, null, null, null, null, null);

    private MockMultipartFile md(String filename, int size) {
        return new MockMultipartFile("files", filename, "text/markdown", new byte[size]);
    }

    @Test
    @DisplayName("파일 배열이 null이면 실패와 안내 메시지를 반환한다")
    void rejectsNullArray() {
        Map<String, Object> result = service.uploadMarkdownFiles(null);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("업로드할 파일이 없습니다.");
    }

    @Test
    @DisplayName("파일이 0개이면 실패와 안내 메시지를 반환한다")
    void rejectsEmptyArray() {
        Map<String, Object> result = service.uploadMarkdownFiles(new MultipartFile[0]);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("업로드할 파일이 없습니다.");
    }

    @Test
    @DisplayName("파일이 6개(>5)이면 개수 제한으로 거부한다")
    void rejectsMoreThanFiveFiles() {
        MultipartFile[] files = new MultipartFile[6];
        for (int i = 0; i < 6; i++) {
            files[i] = md("doc" + i + ".md", 10);
        }

        Map<String, Object> result = service.uploadMarkdownFiles(files);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("최대 5개 파일만 업로드할 수 있습니다.");
    }

    @Test
    @DisplayName("파일명이 비어 있으면 거부한다")
    void rejectsBlankFilename() {
        MultipartFile[] files = { md("", 10) };

        Map<String, Object> result = service.uploadMarkdownFiles(files);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("파일명이 없습니다.");
    }

    @Test
    @DisplayName(".md가 아닌 확장자는 거부한다")
    void rejectsNonMarkdownExtension() {
        MultipartFile[] files = { md("note.txt", 10) };

        Map<String, Object> result = service.uploadMarkdownFiles(files);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("마크다운(.md) 파일만 업로드 가능합니다.");
    }

    @Test
    @DisplayName("단일 파일이 5MB를 초과하면 거부한다")
    void rejectsSingleFileOver5Mb() {
        MultipartFile[] files = { md("big.md", 5 * 1024 * 1024 + 1) };

        Map<String, Object> result = service.uploadMarkdownFiles(files);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("파일당 최대 5MB까지만 업로드할 수 있습니다.");
    }

    @Test
    @DisplayName("개별 파일은 5MB 이하지만 총합이 20MB를 초과하면 거부한다")
    void rejectsTotalSizeOver20Mb() {
        // 5개 × 5MB = 25MB > 20MB (각 파일은 5MB 한계 이내)
        MultipartFile[] files = new MultipartFile[5];
        for (int i = 0; i < 5; i++) {
            files[i] = md("doc" + i + ".md", 5 * 1024 * 1024);
        }

        Map<String, Object> result = service.uploadMarkdownFiles(files);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("총 20MB를 초과할 수 없습니다.");
    }
}
