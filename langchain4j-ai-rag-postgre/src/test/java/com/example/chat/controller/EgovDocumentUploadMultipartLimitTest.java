package com.example.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.example.chat.config.EgovLangChain4jConfig;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * 업로드 계약의 파일당 상한(5MB)이 톰캣 멀티파트 파서를 통과하는지 검증한다.
 *
 * <p>서비스 단위 테스트는 {@code MockMultipartFile} 로 서비스를 직접 호출하므로 파서를 건너뛴다.
 * 그래서 {@code spring.servlet.multipart} 한도가 기본값(1MB/10MB)이던 동안에도 계약 테스트는
 * 초록인 채였고 실제 HTTP 업로드만 413 으로 끊겼다. 이 테스트가 그 구간을 덮는다.
 *
 * <p>지원하지 않는 확장자로 보내는 이유 — 요청이 파서를 통과했는지만 보면 되고, 서비스가 확장자
 * 검사에서 곧바로 400 을 돌려주므로 디스크에 아무것도 쓰지 않는다. 한도에 걸린 413 은 본문이
 * 비어 있어 구분된다.
 *
 * <p>총 20MB(요청 상한) 경계는 여기서 다루지 않는다. 이 모듈은 webflux 를 함께 의존해
 * {@link TestRestTemplate} 이 reactor-netty 클라이언트를 쓰는데, 그 클라이언트가 20MB 멀티파트
 * 본문을 서버 한도와 무관하게 끝내 전송하지 못한다. 요청 상한은 spring-ai 모듈 쪽 같은 이름의
 * 테스트가 덮는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EgovDocumentUploadMultipartLimitTest {

    private static final int FIVE_MB = 5 * 1024 * 1024;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private OllamaChatModel chatLanguageModel;

    @MockitoBean
    private OllamaStreamingChatModel streamingChatLanguageModel;

    @MockitoBean
    private EmbeddingStore<TextSegment> embeddingStore;

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    @DisplayName("계약 상한인 5MB 파일 1개가 서비스까지 도달한다")
    void singleFileAtContractLimitReachesService() {
        ResponseEntity<String> response = upload(FIVE_MB);

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

    @TestConfiguration
    static class TestConfig {
        @Bean
        public static BeanFactoryPostProcessor removeRealConfig() {
            return (ConfigurableListableBeanFactory beanFactory) -> {
                if (beanFactory instanceof BeanDefinitionRegistry registry) {
                    for (String name : beanFactory.getBeanNamesForType(EgovLangChain4jConfig.class, false, false)) {
                        registry.removeBeanDefinition(name);
                    }
                }
            };
        }
    }
}
