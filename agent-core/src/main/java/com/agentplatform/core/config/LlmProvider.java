package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Provider abstraction for LLM backends.
 * <p>
 * Each provider encapsulates the construction of a {@link ChatModel} for a given
 * model name and exposes metadata used for routing and status reporting.
 * </p>
 */
public interface LlmProvider {

    /**
     * Returns a {@link ChatModel} configured for the given model name.
     * <p>
     * A {@code null} or blank model name should resolve to the provider's
     * configured default model.
     * </p>
     *
     * @param model the requested model name (may be null or blank)
     * @return a configured {@link ChatModel} instance
     */
    ChatModel chatModel(String model);

    /**
     * Returns the unique provider identifier (e.g., "ollama", "gemini").
     * Used for routing, logging, and status reporting.
     */
    String providerName();

    /**
     * Returns the human-readable provider label (e.g., "Ollama", "Google Gemini").
     */
    String providerLabel();

    /**
     * Returns the configured default model name for this provider.
     */
    String defaultModel();

    /**
     * Returns whether this provider is configured and available for use.
     * A provider may be present in the classpath but not configured
     * (e.g., missing API key, disabled via config).
     */
    boolean isConfigured();

    /**
     * Returns the configured timeout for this provider, if any.
     * Used by callers that enforce wall-clock deadlines.
     */
    java.time.Duration timeout();
}