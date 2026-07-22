package com.example.chat.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 멀티턴 질의 압축(재작성) 설정
 *
 * <p>후속 질문("두 번째 항목을 설명해줘")은 그 자체로는 검색어가 되지 못해 RAG 검색 품질이
 * 떨어진다. 대화 이력을 참고해 후속 질문을 독립형 질의로 재작성한 뒤 검색에 사용한다.
 * spring-ai 모듈의 EgovCompressionQueryTransformer와 동일한 목적/규칙의 기능으로,
 * 두 모듈의 기능 패리티를 맞춘다.</p>
 *
 * <p>{@code rag.enable-query-compression=true} 일 때만 활성화되며(기본 OFF, 기존 동작 보존),
 * 재작성에는 스트리밍이 필요 없어 동기 {@link OllamaChatModel}을 별도로 생성해 사용한다.</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "rag.enable-query-compression", havingValue = "true")
public class EgovQueryCompressionConfig {

    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    /** 재작성용 모델. 미지정 시 채팅 모델과 동일한 모델을 사용한다. */
    @Value("${rag.query-compression.model-name:${langchain4j.ollama.chat-model.model-name}}")
    private String modelName;

    @Value("${rag.query-compression.timeout:30s}")
    private Duration timeout;

    /**
     * 재작성 프롬프트. spring-ai 모듈의 커스텀 프롬프트와 동일한 규칙(출력은 재작성 질의만,
     * 원 질문 언어 유지, 대명사·지시어를 대화 이력의 구체 용어로 치환)을 적용한다.
     * {@code {{chatMemory}}}, {@code {{query}}} 플레이스홀더는 CompressingQueryTransformer 규약이다.
     */
    private static final PromptTemplate COMPRESSION_PROMPT = PromptTemplate.from(
            """
            You are a query rewriting assistant. Your ONLY job is to rewrite the user's \
            follow-up query into a standalone query using the conversation.

            STRICT RULES:
            - Output ONLY the rewritten query, nothing else
            - NO explanations, NO thinking process, DO NOT use <think> tags or any XML tags
            - Keep the SAME language as the original query (Korean -> Korean, English -> English)
            - Replace pronouns and references with specific terms from the conversation

            Conversation:
            {{chatMemory}}

            User query: {{query}}""");

    @Bean
    public QueryTransformer compressingQueryTransformer() {
        ChatModel compressionModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(modelName)
                .temperature(0.0)
                .timeout(timeout)
                .build();
        log.info("질의 압축(CompressingQueryTransformer) 활성화 - model: {}", modelName);
        return new CompressingQueryTransformer(compressionModel, COMPRESSION_PROMPT);
    }
}
