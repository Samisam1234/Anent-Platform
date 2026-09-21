package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Gemini (Google AI Studio) implementation of {@link LlmProvider}.
 * <p>
 * Wraps the existing Gemini configuration and model construction logic.
 * Only configured when {@code gemini.enabled=true} and API key is present.
 * </p>
 */
@Component("geminiProvider")
public class GeminiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    private final String apiKey;
    private final String defaultModel;
    private final Duration timeout;
    private final boolean configured;

    public GeminiProvider(GeminiProperties props) {
        this.apiKey = props.getApiKey();
        this.defaultModel = props.getChatModel();
        this.timeout = DEFAULT_READ_TIMEOUT;
        this.configured = props.isEnabled() && apiKey != null && !apiKey.isBlank();
        if (configured) {
            log.info("GeminiProvider initialized: model={}, timeout={}", defaultModel, timeout);
        } else {
            log.info("GeminiProvider not configured (enabled={}, apiKeyPresent={})", props.isEnabled(), apiKey != null && !apiKey.isBlank());
        }
    }

    @Override
    public ChatModel chatModel(String model) {
        if (!configured) {
            throw new IllegalStateException("Gemini provider is not configured. Enable gemini.enabled=true and set GEMINI_API_KEY.");
        }
        String modelName = resolveModelName(model);
        log.debug("Building GoogleAiGeminiChatModel for model: {}", modelName);
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.1)
                .timeout(timeout)
                .build();
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String providerLabel() {
        return "Google Gemini";
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
        return timeout;
    }

    /**
     * Resolves the effective model name: the requested model if non-blank,
     * otherwise the configured default.
     */
    public String resolveModelName(String model) {
        return (model == null || model.isBlank()) ? defaultModel : model;
    }
}