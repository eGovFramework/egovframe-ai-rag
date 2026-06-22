package com.example.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * 애플리케이션 컨텍스트 로드 확인용 smoke 테스트.
 *
 * <p>외부 의존성(Redis, Ollama, ONNX 임베딩 모델) 없이 Spring 컨텍스트가
 * 정상적으로 초기화되는지 검증한다.</p>
 *
 * <ul>
 *   <li>EmbeddingModel — ONNX 모델 파일이 classpath에 포함되지 않으므로 MockitoBean 으로 대체</li>
 *   <li>Redis / Ollama — test/resources/application.yml 의 더미 설정으로 실제 연결 없이 기동</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class SpringAiOllamaApplicationTests {

    @MockitoBean
    EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
        // 컨텍스트가 예외 없이 로드되면 통과
    }
}
