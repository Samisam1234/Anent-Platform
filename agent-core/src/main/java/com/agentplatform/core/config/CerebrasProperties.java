package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Cerebras (OpenAI-compatible) configuration properties from application.yml.
 *
 * <pre>
 * cerebras:
 *   enabled: false
 *   api-key: ${CEREBRAS_API_KEY}
 *   base-url: https://api.cerebras.ai/v1
 *   chat-model: llama3.1-8b
 *   reasoning-timeout: 120s
 * </pre>
 */
@ConfigurationProperties(prefix = "cerebras")
public class CerebrasProperties {

    /**
     * Whether Cerebras is active. Off by default: the app runs with other providers
     * unless this is explicitly set to {@code true} (and a key is provided).
     */
    private boolean enabled = false;

    /** Cerebras API key (https://cloud.cerebras.ai/). */
    private String apiKey;

    /** Base URL for the Cerebras OpenAI-compatible API. */
    private String baseUrl = "https://api.cerebras.ai/v1";

    /** Name of the Cerebras chat model to use (e.g. llama3.1-8b). */
    private String chatModel = "llama3.1-8b";

    /** Request timeout for AI reasoning calls (default 2 minutes). */
    private java.time.Duration reasoningTimeout = java.time.Duration.ofMinutes(2);

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public java.time.Duration getReasoningTimeout() {
        return reasoningTimeout;
    }

    public void setReasoningTimeout(java.time.Duration reasoningTimeout) {
        this.reasoningTimeout = reasoningTimeout;
    }
}