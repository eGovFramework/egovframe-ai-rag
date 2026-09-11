package com.example.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 업로드 계약(파일당 5MB, 총 20MB)이 톰캣 멀티파트 파서를 통과하는지 검증한다.
 *
 * <p>서비스 단위 테스트는 {@code MockMultipartFile} 로 서비스를 직접 호출하므로 파서를 건너뛴다.
 * 그래서 {@code spring.servlet.multipart} 한도가 기본값(1MB/10MB)이던 동안에도 계약 테스트는
 * 초록인 채였고 실제 HTTP 업로드만 413 으로 끊겼다. 이 테스트가 그 구간을 덮는다.
 *
 * <p>지원하지 않는 확장자로 보내는 이유 — 요청이 파서를 통과했는지만 보면 되고, 서비스가 확장자
 * 검사에서 곧바로 400 을 돌려주므로 디스크에 아무것도 쓰지 않는다. 한도에 걸린 413 은 본문이
 * 비어 있어 구분된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EgovDocumentUploadMultipartLimitTest {

    private static final int FIVE_MB = 5 * 1024 * 1024;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("계약 상한인 5MB 파일 1개가 서비스까지 도달한다")
    void singleFileAtContractLimitReachesService() {
        assertReachesService(upload(FIVE_MB));
    }

    @Test
    @DisplayName("계약 상한인 총 20MB(5MB × 4) 요청이 서비스까지 도달한다")
    void requestAtContractLimitReachesService() {
        assertReachesService(upload(FIVE_MB, FIVE_MB, FIVE_MB, FIVE_MB));
    }

    private void assertReachesService(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().value())
                .as("413 이면 멀티파트 한도가 업로드 계약보다 낮은 것이다")
                .isEqualTo(400);
        assertThat(response.getBody()).contains("지원하지 않는 파일 형식입니다.");
    }

    private ResponseEntity<String> upload(int... sizes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (int i = 0; i < sizes.length; i++) {
            final String filename = "doc" + i + ".txt";
            body.add("files", new ByteArrayResource(new byte[sizes[i]]) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.postForEntity("http://localhost:" + port + "/api/documents/upload",
                new HttpEntity<>(body, headers), String.class);
    }
}
