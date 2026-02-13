package com.example.chat.controller;

import com.example.chat.util.PromptEngineeringUtil;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 프롬프트 엔지니어링 테스트 API 컨트롤러
 * 다양한 프롬프트 패턴을 테스트할 수 있는 엔드포인트 제공
 */
@Slf4j
@RestController
@RequestMapping("/ai/prompt")
public class PromptTestController {

    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String defaultModelName;

    @Value("${langchain4j.ollama.chat-model.temperature}")
    private Double defaultTemperature;

    /**
     * Zero-shot 패턴 테스트
     */
    @GetMapping(value = "/zero-shot", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testZeroShot(@RequestParam(value = "query", defaultValue = "Spring Boot의 주요 특징을 설명해주세요") String query) {
        log.info("Zero-shot 패턴 테스트 - 쿼리: {}", query);

        String systemPrompt = PromptEngineeringUtil.createZeroShotPrompt();
        String fullPrompt = systemPrompt + "\n\nQuestion: " + query;

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, fullPrompt);

        log.info("Zero-shot 응답 생성 완료");
        return response;
    }

    /**
     * 컨텍스트 기반 답변 패턴 테스트
     */
    @PostMapping(value = "/context-based", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testContextBased(
            @RequestParam(value = "query", defaultValue = "스프링 부트의 자동 구성이란?") String query,
            @RequestBody(required = false) String context) {
        log.info("컨텍스트 기반 답변 테스트 - 쿼리: {}", query);

        if (context == null || context.isBlank()) {
            context = "Spring Boot는 스프링 기반 애플리케이션을 쉽게 만들 수 있도록 도와주는 프레임워크입니다. 자동 구성(Auto-configuration) 기능을 통해 개발자가 직접 설정하지 않아도 대부분의 설정이 자동으로 이루어집니다.";
        }

        String systemPrompt = PromptEngineeringUtil.createContextBasedPrompt(context);
        String fullPrompt = systemPrompt + "\n\nQuestion: " + query;

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, fullPrompt);

        log.info("컨텍스트 기반 응답 생성 완료");
        return response;
    }

    /**
     * Few-shot Learning 패턴 테스트
     */
    @PostMapping(value = "/few-shot", produces = MediaType.APPLICATION_JSON_VALUE)
    public String testFewShot(
            @RequestParam(value = "query", defaultValue = "LangChain4j의 주요 기능은?") String query,
            @RequestBody(required = false) String context) {
        log.info("Few-shot Learning 테스트 - 쿼리: {}", query);

        if (context == null || context.isBlank()) {
            context = "LangChain4j는 Java 애플리케이션에서 LLM을 쉽게 사용할 수 있도록 돕는 라이브러리입니다. RAG(Retrieval-Augmented Generation), 채팅 메모리, 도구 사용 등의 기능을 제공합니다.";
        }

        String systemPrompt = PromptEngineeringUtil.createFewShotLearningPrompt(context);
        String fullPrompt = systemPrompt + "\n\nQuestion: " + query;

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, fullPrompt);

        log.info("Few-shot 응답 생성 완료");
        return response;
    }

    /**
     * Chain-of-Thought 패턴 테스트
     */
    @GetMapping(value = "/chain-of-thought", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testChainOfThought(@RequestParam(value = "query", defaultValue = "마이크로서비스 아키텍처를 설명해주세요") String query) {
        log.info("Chain-of-Thought 테스트 - 쿼리: {}", query);

        String systemPrompt = PromptEngineeringUtil.createChainOfThoughtPrompt();
        String fullPrompt = systemPrompt + "\n\nQuestion: " + query;

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, fullPrompt);

        log.info("Chain-of-Thought 응답 생성 완료");
        return response;
    }

    /**
     * 코드 생성 패턴 테스트
     */
    @GetMapping(value = "/code-generation", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testCodeGeneration(
            @RequestParam(value = "language", defaultValue = "Java") String language,
            @RequestParam(value = "requirement", defaultValue = "간단한 REST API 컨트롤러 작성") String requirement) {
        log.info("코드 생성 테스트 - 언어: {}, 요구사항: {}", language, requirement);

        String prompt = PromptEngineeringUtil.createCodeGenerationPrompt(language, requirement);

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, prompt);

        log.info("코드 생성 완료");
        return response;
    }

    /**
     * Zero-shot 코드 생성 패턴 테스트
     */
    @GetMapping(value = "/zero-shot-code", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testZeroShotCodeGeneration(
            @RequestParam(value = "language", defaultValue = "Python") String language,
            @RequestParam(value = "requirement", defaultValue = "리스트를 정렬하는 함수") String requirement) {
        log.info("Zero-shot 코드 생성 테스트 - 언어: {}, 요구사항: {}", language, requirement);

        String prompt = PromptEngineeringUtil.createZeroShotCodeGenerationPrompt(language, requirement);

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, prompt);

        log.info("Zero-shot 코드 생성 완료");
        return response;
    }

    /**
     * 구조화된 출력 패턴 테스트
     */
    @GetMapping(value = "/structured", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testStructuredOutput(
            @RequestParam(value = "query", defaultValue = "Docker의 장점을 설명해주세요") String query,
            @RequestParam(value = "structure", required = false) String structure) {
        log.info("구조화된 출력 테스트 - 쿼리: {}", query);

        if (structure == null || structure.isBlank()) {
            structure = PromptEngineeringUtil.getDefaultStructuredFormat();
        }

        String systemPrompt = PromptEngineeringUtil.createStructuredOutputPrompt(structure);
        String fullPrompt = systemPrompt + "\n\nQuestion: " + query;

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, fullPrompt);

        log.info("구조화된 출력 생성 완료");
        return response;
    }

    /**
     * 역할 기반 프롬프트 테스트
     */
    @GetMapping(value = "/role-based", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testRoleBased(
            @RequestParam(value = "role", defaultValue = "시니어 백엔드 개발자") String role,
            @RequestParam(value = "task", defaultValue = "RESTful API 설계 원칙을 설명해주세요") String task) {
        log.info("역할 기반 프롬프트 테스트 - 역할: {}, 작업: {}", role, task);

        String prompt = PromptEngineeringUtil.createRoleBasedPrompt(role, task);

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, prompt);

        log.info("역할 기반 응답 생성 완료");
        return response;
    }

    /**
     * Zero-shot 역할 기반 프롬프트 테스트
     */
    @GetMapping(value = "/zero-shot-role", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testZeroShotRoleBased(
            @RequestParam(value = "role", defaultValue = "데이터베이스 관리자") String role,
            @RequestParam(value = "task", defaultValue = "인덱스 최적화 전략을 제안해주세요") String task) {
        log.info("Zero-shot 역할 기반 테스트 - 역할: {}, 작업: {}", role, task);

        String prompt = PromptEngineeringUtil.createZeroShotRoleBasedPrompt(role, task);

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, prompt);

        log.info("Zero-shot 역할 기반 응답 생성 완료");
        return response;
    }

    /**
     * 단계별 작업 분해 테스트
     */
    @GetMapping(value = "/step-by-step", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testStepByStep(@RequestParam(value = "task", defaultValue = "CI/CD 파이프라인 구축하기") String task) {
        log.info("단계별 작업 분해 테스트 - 작업: {}", task);

        String prompt = PromptEngineeringUtil.createStepByStepPrompt(task);

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, prompt);

        log.info("단계별 분해 응답 생성 완료");
        return response;
    }

    /**
     * 품질 검증 테스트
     */
    @PostMapping(value = "/quality-check", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testQualityCheck(
            @RequestParam(value = "criteria", defaultValue = "코드 가독성, 성능, 보안, 유지보수성") String criteria,
            @RequestBody String content) {
        log.info("품질 검증 테스트 - 기준: {}", criteria);

        String prompt = PromptEngineeringUtil.createQualityCheckPrompt(criteria, content);

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, prompt);

        log.info("품질 검증 응답 생성 완료");
        return response;
    }

    /**
     * 동적 Few-shot 예시 테스트
     */
    @PostMapping(value = "/dynamic-few-shot", produces = MediaType.TEXT_PLAIN_VALUE)
    public String testDynamicFewShot(
            @RequestParam(value = "query", defaultValue = "PostgreSQL의 성능을 최적화하려면?") String query,
            @RequestBody Map<String, Object> requestBody) {
        log.info("동적 Few-shot 테스트 - 쿼리: {}", query);

        String context = (String) requestBody.getOrDefault("context", "PostgreSQL은 강력한 오픈소스 관계형 데이터베이스입니다. 인덱싱, 쿼리 최적화, 연결 풀링 등 다양한 성능 최적화 기법을 제공합니다.");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> examplesList = (List<Map<String, String>>) requestBody.getOrDefault("examples", List.of(
                Map.of("question", "인덱스는 언제 사용하나요?", "answer", "자주 조회되는 컬럼에 인덱스를 생성하면 검색 속도가 향상됩니다."),
                Map.of("question", "연결 풀은 무엇인가요?", "answer", "연결 풀은 데이터베이스 연결을 재사용하여 연결 비용을 줄입니다.")
        ));

        List<Map.Entry<String, String>> examples = examplesList.stream()
                .<Map.Entry<String, String>>map(map -> new AbstractMap.SimpleEntry<>(
                        map.getOrDefault("question", ""),
                        map.getOrDefault("answer", "")
                ))
                .collect(Collectors.toList());

        String systemPrompt = PromptEngineeringUtil.createDynamicFewShotPrompt(context, examples);
        String fullPrompt = systemPrompt + "\n\nQuestion: " + query;

        OllamaChatModel chatModel = createChatModel();
        String response = generateResponse(chatModel, fullPrompt);

        log.info("동적 Few-shot 응답 생성 완료");
        return response;
    }

    /**
     * 프롬프트 비교 테스트 (Zero-shot vs Few-shot)
     */
    @GetMapping(value = "/compare", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> testPromptComparison(@RequestParam(value = "query", defaultValue = "Kubernetes의 주요 개념을 설명해주세요") String query) {
        log.info("프롬프트 비교 테스트 - 쿼리: {}", query);

        OllamaChatModel chatModel = createChatModel();

        // Zero-shot 테스트
        String zeroShotPrompt = PromptEngineeringUtil.createZeroShotPrompt() + "\n\nQuestion: " + query;
        String zeroShotResponse = generateResponse(chatModel, zeroShotPrompt);

        // Few-shot 테스트
        String context = "Kubernetes는 컨테이너 오케스트레이션 플랫폼입니다. Pod, Service, Deployment, Namespace 등의 개념을 통해 컨테이너를 관리합니다.";
        String fewShotPrompt = PromptEngineeringUtil.createFewShotLearningPrompt(context) + "\n\nQuestion: " + query;
        String fewShotResponse = generateResponse(chatModel, fewShotPrompt);

        log.info("프롬프트 비교 응답 생성 완료");

        return Map.of(
                "zero_shot", zeroShotResponse,
                "few_shot", fewShotResponse,
                "query", query
        );
    }

    /**
     * OllamaChatModel 생성 헬퍼 메서드
     */
    private OllamaChatModel createChatModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(defaultModelName)
                .temperature(defaultTemperature)
                .build();
    }

    /**
     * 프롬프트로부터 응답 생성 헬퍼 메서드
     */
    private String generateResponse(OllamaChatModel chatModel, String prompt) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build();
        ChatResponse chatResponse = chatModel.chat(chatRequest);
        return chatResponse.aiMessage().text();
    }
}
