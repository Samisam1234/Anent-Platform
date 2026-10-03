package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ollama implementation of {@link LlmProvider}.
 * <p>
 * Wraps the existing Ollama configuration and model construction logic.
 * </p>
 */
@Component("ollamaProvider")
public class OllamaProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);

    private final String baseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final boolean configured;

    public OllamaProvider(OllamaProperties props) {
        this.baseUrl = props.getBaseUrl();
        this.defaultModel = props.getChatModel();
        this.timeout = props.getReasoningTimeout();
        this.configured = true; // Ollama is always configured (local server, no API key required)
        log.info("OllamaProvider initialized: baseUrl={}, defaultModel={}, timeout={}",
                baseUrl, defaultModel, timeout);
    }

    @Override
    public ChatModel chatModel(String model) {
        String modelName = resolveModelName(model);
        log.debug("Building OllamaChatModel for model: {}", modelName);
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .build();
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public String providerLabel() {
        return "Ollama";
    }

    @Override
    public String defaultModel() {
        return defaultModel;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public Duration timeout() {
        return timeout != null ? timeout : Duration.ofMinutes(2);
    }

    /**
     * Resolves the effective model name: the requested model if non-blank,
     * otherwise the configured default.
     */
    public String resolveModelName(String model) {
        return (model == null || model.isBlank()) ? defaultModel : model;
    }
}