package com.example.chat.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "egov.ai.gateway")
public class EgovAiGatewayProperties {

    private String provider = "ollama";
    private String route = "default";
    private String defaultPolicy = "LOCAL_LLM_FIRST";
    private List<String> features = new ArrayList<>(List.of(
            "openai-compatible-api",
            "router-provider-separation",
            "provider-load-balancing",
            "qos-metrics",
            "usage-cost-environmental-metadata",
            "metadata-only-audit"));
    private Audit audit = new Audit();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(String defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }

    public static class Audit {

        private boolean metadataOnly = true;
        private boolean storePrompt = false;
        private boolean storeResponse = false;

        public boolean isMetadataOnly() {
            return metadataOnly;
        }

        public void setMetadataOnly(boolean metadataOnly) {
            this.metadataOnly = metadataOnly;
        }

        public boolean isStorePrompt() {
            return storePrompt;
        }

        public void setStorePrompt(boolean storePrompt) {
            this.storePrompt = storePrompt;
        }

        public boolean isStoreResponse() {
            return storeResponse;
        }

        public void setStoreResponse(boolean storeResponse) {
            this.storeResponse = storeResponse;
        }
    }
}
