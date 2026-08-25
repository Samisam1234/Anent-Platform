package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Ollama configuration properties from application.yml.
 *
 * <pre>
 * ollama:
 *   base-url: http://localhost:11434
 *   chat-model: gemma3:4b
 *   embedding-model: nomic-embed-text
 * </pre>
 */
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    /** Base URL of the locally running Ollama server. */
    private String baseUrl = "http://localhost:11434";

    /** Name of the chat/completion model to use (e.g. gemma3:4b). */
    private String chatModel = "gemma3:4b";

    /** Name of the embedding model to use (e.g. nomic-embed-text). */
    private String embeddingModel = "nomic-embed-text";

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
}
