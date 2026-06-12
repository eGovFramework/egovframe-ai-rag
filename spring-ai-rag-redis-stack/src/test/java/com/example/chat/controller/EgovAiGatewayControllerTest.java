package com.example.chat.controller;

import java.util.Map;

import com.example.chat.config.EgovAiGatewayProperties;
import com.example.chat.service.EgovAiGatewayAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class EgovAiGatewayControllerTest {

    @Test
    void statusDoesNotExposeApiKey() {
        EgovAiGatewayProperties properties = new EgovAiGatewayProperties();
        properties.setProvider("opengatellm");
        properties.setRoute("kr-gov-local-general");
        properties.setDefaultPolicy("LOCAL_LLM_FIRST");

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.openai.base-url", "http://localhost:8000")
                .withProperty("spring.ai.openai.api-key", "secret-value")
                .withProperty("spring.ai.openai.chat.options.model", "kr-gov-local-general");

        EgovAiGatewayAuditService auditService = new EgovAiGatewayAuditService(properties, environment);
        EgovAiGatewayController controller = new EgovAiGatewayController(
                properties,
                auditService,
                environment,
                RestClient.builder());

        ResponseEntity<Map<String, Object>> response = controller.status();

        assertThat(response.getBody()).containsEntry("chatProvider", "openai");
        assertThat(response.getBody()).containsEntry("openGateLlmBaseUrl", "http://localhost:8000");
        assertThat(response.getBody()).containsEntry("openGateLlmApiKeyConfigured", true);
        assertThat(response.getBody().toString()).doesNotContain("secret-value");
        assertThat(response.getBody()).containsKey("openGateLlmMapping");
    }
}
