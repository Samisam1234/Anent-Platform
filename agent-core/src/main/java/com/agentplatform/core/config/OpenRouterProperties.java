package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds OpenRouter (OpenAI-compatible) configuration properties from application.yml.
 *
 * <pre>
 * openrouter:
 *   enabled: false
 *   api-key: ${OPENROUTER_API_KEY}
 *   base-url: https://openrouter.ai/api/v1
 *   chat-model: openrouter/auto
 *   reasoning-timeout: 120s
 * </pre>
 */
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterProperties {

    /**
     * Whether OpenRouter is active. Off by default: the app runs with other providers
     * unless this is explicitly set to {@code true} (and a key is provided).
     */
    private boolean enabled = false;

    /** OpenRouter API key (https://openrouter.ai/keys). */
    private String apiKey;

    /** Base URL for the OpenRouter OpenAI-compatible API. */
    private String baseUrl = "https://openrouter.ai/api/v1";

    /** Name of the OpenRouter chat model to use (e.g. openrouter/auto). */
    private String chatModel = "openrouter/auto";

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