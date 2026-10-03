package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cerebras (OpenAI-compatible) implementation of {@link LlmProvider}.
 * <p>
 * Wraps the Cerebras configuration and model construction logic.
 * Only configured when {@code cerebras.enabled=true} and API key is present.
 * </p>
 */
@Component("cerebrasProvider")
public class CerebrasProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CerebrasProvider.class);

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(120);

    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final boolean configured;

    public CerebrasProvider(CerebrasProperties props) {
        this.apiKey = props.getApiKey();
        this.baseUrl = props.getBaseUrl();
        this.defaultModel = props.getChatModel();
        this.timeout = props.getReasoningTimeout() != null ? props.getReasoningTimeout() : DEFAULT_READ_TIMEOUT;
        this.configured = props.isEnabled() && apiKey != null && !apiKey.isBlank();
        if (configured) {
            log.info("CerebrasProvider initialized: baseUrl={}, model={}, timeout={}", baseUrl, defaultModel, timeout);
        } else {
            log.info("CerebrasProvider not configured (enabled={}, apiKeyPresent={})", props.isEnabled(), apiKey != null && !apiKey.isBlank());
        }
    }

    @Override
    public ChatModel chatModel(String model) {
        if (!configured) {
            throw new IllegalStateException("Cerebras provider is not configured. Enable cerebras.enabled=true and set CEREBRAS_API_KEY.");
        }
        String modelName = resolveModelName(model);
        log.debug("Building OpenAiChatModel (Cerebras) for model: {}", modelName);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(4096)
                .timeout(timeout)
                .build();
    }

    @Override
    public String providerName() {
        return "cerebras";
    }

    @Override
    public String providerLabel() {
        return "Cerebras";
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