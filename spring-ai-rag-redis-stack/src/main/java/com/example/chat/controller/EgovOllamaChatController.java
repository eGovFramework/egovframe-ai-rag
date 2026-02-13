package com.example.chat.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.chat.context.SessionContext;
import com.example.chat.response.TechnologyResponse;
import com.example.chat.service.EgovChatSessionService;
import com.example.chat.service.EgovSessionAwareChatService;
import com.example.chat.util.EgovJsonPromptTemplates;
import com.example.chat.util.EgovPromptEngineeringUtil;
import com.example.chat.util.EgovResponseCleanerUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequiredArgsConstructor
public class EgovOllamaChatController {

    private final OllamaChatModel chatModel;
    private final EgovSessionAwareChatService sessionAwareChatService;
    private final EgovChatSessionService chatSessionService;

    /**
     * 일반 응답 생성 (테스트용)
     */
    @GetMapping("/ai/generate")
    public Map<String, String> generate(
            @RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        return Map.of("generation", this.chatModel.call(message));
    }

    /**
     * 스트리밍 응답 생성 (테스트용)
     */
    @GetMapping("/ai/generateStream")
    public Flux<ChatResponse> generateStream(
            @RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        Prompt prompt = new Prompt(new UserMessage(message));
        return this.chatModel.stream(prompt);
    }

    /**
     * RAG 기반 스트리밍 응답 생성
     */
    @GetMapping("/ai/rag/stream")
    public Flux<ChatResponse> streamRagResponse(
            @RequestParam(value = "message", defaultValue = "Tell me about this document") String message,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        log.info("RAG 기반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", message, model, sessionId);
        
        // 세션 컨텍스트 설정
        if (sessionId != null && !sessionId.isEmpty()) {
            log.debug("세션 ID 검증 시작: {}", sessionId);
            if (chatSessionService.sessionExists(sessionId)) {
                log.debug("유효한 세션 ID 확인: {}", sessionId);
                SessionContext.setCurrentSessionId(sessionId);
                
                // 첫 메시지인 경우 세션 제목 업데이트
                List<Message> history = chatSessionService.getSessionMessages(sessionId);
                if (history.isEmpty()) {
                    log.debug("첫 메시지로 판단, 세션 제목 생성: {}", sessionId);
                    String title = chatSessionService.generateSessionTitle(message);
                    chatSessionService.updateSessionTitle(sessionId, title);
                } else {
                    log.debug("기존 세션 메시지 발견: {} - {} 개", sessionId, history.size());
                    // 마지막 메시지 시간 업데이트
                    chatSessionService.updateLastMessageTime(sessionId);
                }
            } else {
                log.warn("존재하지 않는 세션 ID: {}, 기본 세션으로 처리", sessionId);
                // 존재하지 않는 세션 ID인 경우 기본 세션으로 처리
                SessionContext.setCurrentSessionId(ChatMemory.DEFAULT_CONVERSATION_ID);
            }
        } else {
            log.warn("세션 ID가 제공되지 않음, 기본 세션으로 처리");
            // 세션 ID가 없는 경우 기본 세션으로 처리
            SessionContext.setCurrentSessionId(ChatMemory.DEFAULT_CONVERSATION_ID);
        }
        
        String currentSessionId = SessionContext.getCurrentSessionId();
        log.debug("현재 세션 컨텍스트 설정됨: {}", currentSessionId);
        
        return sessionAwareChatService.streamRagResponse(message, model)
                .doFinally(signalType -> {
                    // 스트리밍 완료 후 컨텍스트 정리
                    SessionContext.clear();
                    log.debug("SessionContext 정리 완료 - 세션: {}, 신호: {}", sessionId, signalType);
                });
    }

    /**
     * 일반 스트리밍 응답 생성
     */
    @GetMapping("/ai/simple/stream")
    public Flux<ChatResponse> streamSimpleResponse(
            @RequestParam(value = "message", defaultValue = "Tell me about this document") String message,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        log.info("일반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", message, model, sessionId);
        
        // 세션 컨텍스트 설정
        if (sessionId != null && !sessionId.isEmpty()) {
            if (chatSessionService.sessionExists(sessionId)) {
                SessionContext.setCurrentSessionId(sessionId);
                
                // 첫 메시지인 경우 세션 제목 업데이트
                List<Message> history = chatSessionService.getSessionMessages(sessionId);
                if (history.isEmpty()) {
                    String title = chatSessionService.generateSessionTitle(message);
                    chatSessionService.updateSessionTitle(sessionId, title);
                } else {
                    // 마지막 메시지 시간 업데이트
                    chatSessionService.updateLastMessageTime(sessionId);
                }
            } else {
                log.warn("존재하지 않는 세션 ID: {}, 기본 세션으로 처리", sessionId);
                // 존재하지 않는 세션 ID인 경우 기본 세션으로 처리
                SessionContext.setCurrentSessionId(ChatMemory.DEFAULT_CONVERSATION_ID);
            }
        } else {
            // 세션 ID가 없는 경우 기본 세션으로 처리
            SessionContext.setCurrentSessionId(ChatMemory.DEFAULT_CONVERSATION_ID);
        }
        
        // 일반 스트리밍 응답 생성 (RAG 없이)
        return sessionAwareChatService.streamSimpleResponse(message, model)
                .doFinally(signalType -> {
                    // 스트리밍 완료 후 컨텍스트 정리
                    SessionContext.clear();
                    log.debug("SessionContext 정리 완료 - 세션: {}, 신호: {}", sessionId, signalType);
                });
    }

    // ===== PromptEngineeringUtil 활용 테스트 엔드포인트들 =====

    /**
     * Zero-shot 패턴 테스트
     */
    @GetMapping("/ai/prompt/zero-shot")
    public Map<String, String> zeroShot(
            @RequestParam(value = "message", defaultValue = "Spring Boot란 무엇인가요?") String message) {
        String prompt = EgovPromptEngineeringUtil.createZeroShotPrompt();
        String fullPrompt = prompt + "\n\nQuestion: " + message;
        return Map.of("generation", this.chatModel.call(fullPrompt));
    }

    /**
     * 컨텍스트 기반 답변 패턴 테스트
     */
    @GetMapping("/ai/prompt/context-based")
    public Map<String, String> contextBased(
            @RequestParam(value = "message", defaultValue = "Spring Boot의 특징은?") String message,
            @RequestParam(value = "context", defaultValue = "Spring Boot is a Java-based framework for developing web applications. It provides auto-configuration and embedded servers.") String context) {
        String prompt = EgovPromptEngineeringUtil.createContextBasedPrompt(context);
        String fullPrompt = prompt + "\n\nQuestion: " + message;
        return Map.of("generation", this.chatModel.call(fullPrompt));
    }

    /**
     * Few-shot Learning 패턴 테스트
     */
    @GetMapping("/ai/prompt/few-shot")
    public Map<String, String> fewShot(
            @RequestParam(value = "message", defaultValue = "Python의 특징은?") String message,
            @RequestParam(value = "context", defaultValue = "Spring Boot is a Java-based framework for developing web applications. It provides auto-configuration and embedded servers.") String context) {
        String prompt = EgovPromptEngineeringUtil.createFewShotLearningPrompt(context);
        String fullPrompt = prompt + "\n\nQuestion: " + message;
        return Map.of("generation", this.chatModel.call(fullPrompt));
    }

    /**
     * Chain-of-Thought 패턴 테스트
     */
    @GetMapping("/ai/prompt/chain-of-thought")
    public Map<String, String> chainOfThought(
            @RequestParam(value = "message", defaultValue = "마이크로서비스 아키텍처의 장단점은?") String message) {
        String prompt = EgovPromptEngineeringUtil.createChainOfThoughtPrompt();
        String fullPrompt = prompt + "\n\nQuestion: " + message;
        return Map.of("generation", this.chatModel.call(fullPrompt));
    }

    /**
     * Code Generation 패턴 테스트
     */
    @GetMapping("/ai/prompt/code-generation")
    public Map<String, String> codeGeneration(
            @RequestParam(value = "requirement", defaultValue = "사용자 정보를 저장하는 REST API 엔드포인트 생성") String requirement,
            @RequestParam(value = "language", defaultValue = "Java") String language) {
        String prompt = EgovPromptEngineeringUtil.createCodeGenerationPrompt(language, requirement);
        return Map.of("generation", this.chatModel.call(prompt));
    }

    /**
     * Zero-shot Code Generation 패턴 테스트
     */
    @GetMapping("/ai/prompt/zero-shot-code")
    public Map<String, String> zeroShotCodeGeneration(
            @RequestParam(value = "requirement", defaultValue = "사용자 인증 시스템 구현") String requirement,
            @RequestParam(value = "language", defaultValue = "Java") String language) {
        String prompt = EgovPromptEngineeringUtil.createZeroShotCodeGenerationPrompt(language, requirement);
        return Map.of("generation", this.chatModel.call(prompt));
    }

    /**
     * Structured Output 패턴 테스트
     */
    @GetMapping("/ai/prompt/structured")
    public Map<String, String> structured(
            @RequestParam(value = "message", defaultValue = "Spring Boot의 특징을 설명해주세요") String message) {
        String structure = EgovPromptEngineeringUtil.getDefaultStructuredFormat();
        String prompt = EgovPromptEngineeringUtil.createStructuredOutputPrompt(structure);
        String fullPrompt = prompt + "\n\nQuestion: " + message;
        return Map.of("generation", this.chatModel.call(fullPrompt));
    }

    /**
     * Role-based 패턴 테스트
     */
    @GetMapping("/ai/prompt/role-based")
    public Map<String, String> roleBased(
            @RequestParam(value = "task", defaultValue = "웹 보안 강화 방법 제시") String task,
            @RequestParam(value = "role", defaultValue = "보안 전문가") String role) {
        String prompt = EgovPromptEngineeringUtil.createRoleBasedPrompt(role, task);
        return Map.of("generation", this.chatModel.call(prompt));
    }

    /**
     * Zero-shot Role-based 패턴 테스트
     */
    @GetMapping("/ai/prompt/zero-shot-role")
    public Map<String, String> zeroShotRoleBased(
            @RequestParam(value = "task", defaultValue = "데이터베이스 최적화 방법 제시") String task,
            @RequestParam(value = "role", defaultValue = "데이터베이스 전문가") String role) {
        String prompt = EgovPromptEngineeringUtil.createZeroShotRoleBasedPrompt(role, task);
        return Map.of("generation", this.chatModel.call(prompt));
    }

    /**
     * Step-by-Step 패턴 테스트
     */
    @GetMapping("/ai/prompt/step-by-step")
    public Map<String, String> stepByStep(
            @RequestParam(value = "task", defaultValue = "마이크로서비스 아키텍처로 전환하기") String task) {
        String prompt = EgovPromptEngineeringUtil.createStepByStepPrompt(task);
        return Map.of("generation", this.chatModel.call(prompt));
    }

    /**
     * Quality Check 패턴 테스트
     */
    @GetMapping("/ai/prompt/quality-check")
    public Map<String, String> qualityCheck(
            @RequestParam(value = "content", defaultValue = "Spring Boot는 Java 기반의 웹 애플리케이션 개발을 위한 프레임워크입니다.") String content,
            @RequestParam(value = "criteria", defaultValue = "정확성, 완성도, 가독성, 실용성") String criteria) {
        String prompt = EgovPromptEngineeringUtil.createQualityCheckPrompt(criteria, content);
        return Map.of("generation", this.chatModel.call(prompt));
    }

    /**
     * 동적 Few-shot 패턴 테스트
     */
    @GetMapping("/ai/prompt/dynamic-few-shot")
    public Map<String, String> dynamicFewShot(
            @RequestParam(value = "message", defaultValue = "React의 특징은?") String message,
            @RequestParam(value = "context", defaultValue = "Spring Boot is a Java-based framework for developing web applications.") String context) {
        
        // 동적 예시 생성
        List<Map.Entry<String, String>> examples = Arrays.asList(
            Map.entry("What is Spring Boot?", "Spring Boot is a Java-based framework for developing web applications."),
            Map.entry("How does Spring Boot work?", "Spring Boot uses auto-configuration and embedded servers to simplify development."),
            Map.entry("What are the benefits of Spring Boot?", "Spring Boot reduces boilerplate code and provides rapid development capabilities.")
        );
        
        String prompt = EgovPromptEngineeringUtil.createDynamicFewShotPrompt(context, examples);
        String fullPrompt = prompt + "\n\nQuestion: " + message;
        return Map.of("generation", this.chatModel.call(fullPrompt));
    }

    /**
     * 프롬프트 비교 테스트 (Zero-shot vs Few-shot)
     */
    @GetMapping("/ai/prompt/compare")
    public Map<String, Object> comparePrompts(
            @RequestParam(value = "message", defaultValue = "Docker의 장점은?") String message,
            @RequestParam(value = "context", defaultValue = "Spring Boot is a Java-based framework for developing web applications.") String context) {
        
        String zeroShotPrompt = EgovPromptEngineeringUtil.createContextBasedPrompt(context);
        String fewShotPrompt = EgovPromptEngineeringUtil.createFewShotLearningPrompt(context);
        
        String zeroShotFull = zeroShotPrompt + "\n\nQuestion: " + message;
        String fewShotFull = fewShotPrompt + "\n\nQuestion: " + message;
        
        return Map.of(
            "zeroShot", this.chatModel.call(zeroShotFull),
            "fewShot", this.chatModel.call(fewShotFull)
        );
    }

    // ===== JSON 구조화된 출력 테스트 엔드포인트들 =====

    /**
     * 기술 정보 JSON 응답 테스트
     */
    @GetMapping("/ai/json/technology")
    public TechnologyResponse getTechnologyInfoAsJson(
            @RequestParam(value = "query", defaultValue = "Spring Boot에 대해 설명해주세요") String query) {
        return sessionAwareChatService.getTechnologyInfoAsJson(query);
    }

    /**
     * JSON 응답 디버깅 - 기술 정보 원본 텍스트 응답 확인
     */
    @GetMapping("/ai/json/debug/technology")
    public Map<String, String> debugTechnologyInfo(
            @RequestParam(value = "query", defaultValue = "Spring Boot에 대해 설명해주세요") String query) {
        try {
            String jsonPrompt = EgovJsonPromptTemplates.createTechnologyInfoPrompt(query);
            String rawResponse = chatModel.call(jsonPrompt);
            
            // 응답 정리 테스트
            String cleanedJson = EgovResponseCleanerUtil.cleanResponse(rawResponse);
            
            return Map.of(
                "originalQuery", query,
                "jsonPrompt", jsonPrompt,
                "rawResponse", rawResponse,
                "cleanedJson", cleanedJson,
                "hasThinkTag", String.valueOf(rawResponse.contains("<think>"))
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
