package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Binds Ollama configuration properties from application.yml.
 *
 * <pre>
 * ollama:
 *   base-url: http://localhost:11434
 *   chat-model: llama3.2:3b
 *   embedding-model: nomic-embed-text
 *   reasoning-timeout: 120s
 * </pre>
 */
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    /** Base URL of the locally running Ollama server. */
    private String baseUrl = "http://localhost:11434";

    /** Name of the chat/completion model to use (e.g. llama3.2:3b). */
    private String chatModel = "llama3.2:3b";

    /** Name of the embedding model to use (e.g. nomic-embed-text). */
    private String embeddingModel = "nomic-embed-text";

    /** Request timeout for AI reasoning calls (default 2 minutes). */
    private Duration reasoningTimeout = Duration.ofMinutes(2);

    // ─── Getters & Setters ───────────────────────────────────────────────────

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

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Duration getReasoningTimeout() {
        return reasoningTimeout;
    }

    public void setReasoningTimeout(Duration reasoningTimeout) {
        this.reasoningTimeout = reasoningTimeout;
    }
}
