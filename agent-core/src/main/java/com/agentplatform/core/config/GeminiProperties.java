package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Gemini (Google AI Studio) configuration properties from application.yml.
 *
 * <pre>
 * gemini:
 *   api-key: ${GEMINI_API_KEY}
 *   chat-model: gemini-2.5-flash
 *   embedding-model: text-embedding-004
 * </pre>
 */
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    /**
     * Whether Gemini is active. Off by default: the app runs Ollama-only unless
     * this is explicitly set to {@code true} (and a key is provided).
     */
    private boolean enabled = false;

    /** Google AI Studio API key (https://ai.google.dev/gemini-api/docs/api-key). */
    private String apiKey;

    /** Name of the Gemini chat model to use (e.g. gemini-2.5-flash). */
    private String chatModel = "gemini-2.5-flash";

    /** Name of the Gemini embedding model to use (e.g. text-embedding-004). */
    private String embeddingModel = "text-embedding-004";

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

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}