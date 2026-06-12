package com.example.chat.response;

import java.time.Duration;
import java.time.Instant;

public class EgovAiGatewayAuditEvent {

    private final String requestId;
    private final String route;
    private final String requestedModel;
    private final String providerProfile;
    private final String sessionId;
    private final boolean ragEnabled;
    private final String promptHash;
    private final boolean promptStored;
    private final boolean responseStored;
    private final Instant startedAt;
    private Instant completedAt;
    private long latencyMs;
    private String terminalSignal;

    public EgovAiGatewayAuditEvent(
            String requestId,
            String route,
            String requestedModel,
            String providerProfile,
            String sessionId,
            boolean ragEnabled,
            String promptHash,
            boolean promptStored,
            boolean responseStored,
            Instant startedAt) {
        this.requestId = requestId;
        this.route = route;
        this.requestedModel = requestedModel;
        this.providerProfile = providerProfile;
        this.sessionId = sessionId;
        this.ragEnabled = ragEnabled;
        this.promptHash = promptHash;
        this.promptStored = promptStored;
        this.responseStored = responseStored;
        this.startedAt = startedAt;
        this.terminalSignal = "STARTED";
    }

    public void complete(String terminalSignal) {
        this.completedAt = Instant.now();
        this.latencyMs = Duration.between(startedAt, completedAt).toMillis();
        this.terminalSignal = terminalSignal;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRoute() {
        return route;
    }

    public String getRequestedModel() {
        return requestedModel;
    }

    public String getProviderProfile() {
        return providerProfile;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public boolean isPromptStored() {
        return promptStored;
    }

    public boolean isResponseStored() {
        return responseStored;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getTerminalSignal() {
        return terminalSignal;
    }
}
