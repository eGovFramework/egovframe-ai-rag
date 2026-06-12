package com.example.chat.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.chat.config.EgovAiGatewayProperties;
import com.example.chat.response.EgovAiGatewayAuditEvent;
import com.example.chat.service.EgovAiGatewayAuditService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ai-gateway")
public class EgovAiGatewayController {

    private final EgovAiGatewayProperties properties;
    private final EgovAiGatewayAuditService auditService;
    private final Environment environment;
    private final RestClient restClient;

    public EgovAiGatewayController(
            EgovAiGatewayProperties properties,
            EgovAiGatewayAuditService auditService,
            Environment environment,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.auditService = auditService;
        this.environment = environment;
        this.restClient = restClientBuilder.build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("chatProvider", environment.getProperty("spring.ai.model.chat", properties.getProvider()));
        response.put("route", properties.getRoute());
        response.put("defaultPolicy", properties.getDefaultPolicy());
        response.put("defaultModel", resolveDefaultModel());
        response.put("openGateLlmBaseUrl", resolveOpenGateLlmBaseUrl());
        response.put("openGateLlmApiKeyConfigured", StringUtils.hasText(environment.getProperty("spring.ai.openai.api-key")));
        response.put("audit", Map.of(
                "metadataOnly", properties.getAudit().isMetadataOnly(),
                "promptStored", properties.getAudit().isStorePrompt(),
                "responseStored", properties.getAudit().isStoreResponse()));
        response.put("features", properties.getFeatures());
        response.put("openGateLlmMapping", Map.of(
                "router", "spring.ai.openai.chat.options.model",
                "provider", "OpenGateLLM provider selected behind the router",
                "metrics", List.of("latency", "ttft", "normalized_latency", "inflight"),
                "dataSovereignty", List.of("model_hosting_zone", "metadata-only-audit", "prompt-response-storage-policy")));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models() {
        String baseUrl = resolveOpenGateLlmBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return ResponseEntity.ok(Map.of(
                    "available", false,
                    "message", "OpenGateLLM base URL is not configured. Use the opengatellm Spring profile."));
        }

        String modelsUrl = trimTrailingSlash(baseUrl) + "/v1/models";
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(modelsUrl);
            String apiKey = environment.getProperty("spring.ai.openai.api-key");
            if (StringUtils.hasText(apiKey)) {
                request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }

            Object gatewayResponse = request.retrieve().body(Object.class);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("available", true);
            response.put("modelsUrl", modelsUrl);
            response.put("response", gatewayResponse);
            return ResponseEntity.ok(response);
        } catch (RestClientException e) {
            log.warn("OpenGateLLM model router lookup failed: {}", e.getMessage());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("available", false);
            response.put("modelsUrl", modelsUrl);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/audit/events")
    public ResponseEntity<List<EgovAiGatewayAuditEvent>> auditEvents() {
        return ResponseEntity.ok(auditService.getRecentEvents());
    }

    private String resolveDefaultModel() {
        String openAiModel = environment.getProperty("spring.ai.openai.chat.options.model");
        if (StringUtils.hasText(openAiModel)) {
            return openAiModel;
        }
        return environment.getProperty("spring.ai.ollama.chat.model", "default");
    }

    private String resolveOpenGateLlmBaseUrl() {
        String chatBaseUrl = environment.getProperty("spring.ai.openai.chat.base-url");
        if (StringUtils.hasText(chatBaseUrl)) {
            return chatBaseUrl;
        }
        return environment.getProperty("spring.ai.openai.base-url", "");
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
