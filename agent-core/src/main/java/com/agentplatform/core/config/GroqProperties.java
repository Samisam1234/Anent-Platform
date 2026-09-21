package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Groq (OpenAI-compatible) configuration properties from application.yml.
 *
 * <pre>
 * groq:
 *   enabled: false
 *   api-key: ${GROQ_API_KEY}
 *   base-url: https://api.groq.com/openai/v1
 *   chat-model: llama-3.1-8b-instant
 *   reasoning-timeout: 120s
 * </pre>
 */
@ConfigurationProperties(prefix = "groq")
public class GroqProperties {

    /**
     * Whether Groq is active. Off by default: the app runs Ollama-only unless
     * this is explicitly set to {@code true} (and a key is provided).
     */
    private boolean enabled = false;

    /** Groq API key (https://console.groq.com/keys). */
    private String apiKey;

    /** Base URL for the Groq OpenAI-compatible API. */
    private String baseUrl = "https://api.groq.com/openai/v1";

    /** Name of the Groq chat model to use (e.g. llama-3.1-8b-instant). */
    private String chatModel = "llama-3.1-8b-instant";

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