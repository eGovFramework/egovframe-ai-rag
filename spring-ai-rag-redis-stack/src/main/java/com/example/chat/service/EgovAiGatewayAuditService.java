package com.example.chat.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.chat.config.EgovAiGatewayProperties;
import com.example.chat.response.EgovAiGatewayAuditEvent;

@Service
public class EgovAiGatewayAuditService extends EgovAbstractServiceImpl {

    private static final int MAX_EVENTS = 100;

    private final EgovAiGatewayProperties properties;
    private final Environment environment;
    private final ConcurrentLinkedDeque<EgovAiGatewayAuditEvent> events = new ConcurrentLinkedDeque<>();
    private final Map<String, EgovAiGatewayAuditEvent> eventsById = new ConcurrentHashMap<>();

    public EgovAiGatewayAuditService(EgovAiGatewayProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public EgovAiGatewayAuditEvent start(
            String route,
            String requestedModel,
            String sessionId,
            boolean ragEnabled,
            String prompt) {
        EgovAiGatewayAuditEvent event = new EgovAiGatewayAuditEvent(
                UUID.randomUUID().toString(),
                route,
                resolveModel(requestedModel),
                resolveProviderProfile(),
                resolveSessionId(sessionId),
                ragEnabled,
                sha256(prompt),
                properties.getAudit().isStorePrompt(),
                properties.getAudit().isStoreResponse(),
                Instant.now());

        events.addFirst(event);
        eventsById.put(event.getRequestId(), event);
        trimEvents();
        return event;
    }

    public void complete(String requestId, String terminalSignal) {
        EgovAiGatewayAuditEvent event = eventsById.get(requestId);
        if (event != null) {
            event.complete(terminalSignal);
        }
    }

    public List<EgovAiGatewayAuditEvent> getRecentEvents() {
        return new ArrayList<>(events);
    }

    public EgovAiGatewayProperties getProperties() {
        return properties;
    }

    private String resolveProviderProfile() {
        return environment.getProperty("spring.ai.model.chat", properties.getProvider());
    }

    private String resolveModel(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel;
        }

        String openAiModel = environment.getProperty("spring.ai.openai.chat.options.model");
        if (StringUtils.hasText(openAiModel)) {
            return openAiModel;
        }

        return environment.getProperty("spring.ai.ollama.chat.model", "default");
    }

    private String resolveSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : "default";
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private void trimEvents() {
        while (events.size() > MAX_EVENTS) {
            EgovAiGatewayAuditEvent removed = events.removeLast();
            eventsById.remove(removed.getRequestId());
        }
    }
}
