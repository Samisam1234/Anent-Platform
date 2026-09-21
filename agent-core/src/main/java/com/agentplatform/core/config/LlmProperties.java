package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global LLM routing configuration.
 * <p>
 * Controls which provider is used by default when no explicit provider is requested.
 * </p>
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * Default provider name (e.g., "ollama", "gemini", "groq").
     * When null or blank, the highest-priority configured provider is used.
     * This allows switching the active provider without code changes.
     */
    private String defaultProvider;

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }
}