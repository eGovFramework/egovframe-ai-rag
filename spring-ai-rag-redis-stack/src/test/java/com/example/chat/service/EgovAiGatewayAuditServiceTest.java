package com.example.chat.service;

import com.example.chat.config.EgovAiGatewayProperties;
import com.example.chat.response.EgovAiGatewayAuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class EgovAiGatewayAuditServiceTest {

    @Test
    void storesMetadataOnlyAuditEventWithPromptHash() {
        EgovAiGatewayProperties properties = new EgovAiGatewayProperties();
        properties.setProvider("opengatellm");
        properties.getAudit().setMetadataOnly(true);
        properties.getAudit().setStorePrompt(false);
        properties.getAudit().setStoreResponse(false);

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.openai.chat.options.model", "kr-gov-local-general");

        EgovAiGatewayAuditService service = new EgovAiGatewayAuditService(properties, environment);

        EgovAiGatewayAuditEvent event = service.start(
                "/ai/rag/stream",
                null,
                "session-1",
                true,
                "resident registration number must never be stored as raw prompt");
        service.complete(event.getRequestId(), "ON_COMPLETE");

        assertThat(event.getProviderProfile()).isEqualTo("openai");
        assertThat(event.getRequestedModel()).isEqualTo("kr-gov-local-general");
        assertThat(event.getPromptHash()).hasSize(64);
        assertThat(event.getPromptHash()).doesNotContain("resident");
        assertThat(event.isPromptStored()).isFalse();
        assertThat(event.isResponseStored()).isFalse();
        assertThat(event.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(event.getTerminalSignal()).isEqualTo("ON_COMPLETE");
        assertThat(service.getRecentEvents()).containsExactly(event);
    }
}
