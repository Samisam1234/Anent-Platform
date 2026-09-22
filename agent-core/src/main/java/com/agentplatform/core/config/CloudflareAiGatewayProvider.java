package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cloudflare AI Gateway (OpenAI-compatible) implementation of {@link LlmProvider}.
 * <p>
 * Wraps the Cloudflare AI Gateway configuration and model construction logic.
 * Only configured when {@code cloudflare-ai-gateway.enabled=true} and credentials are present.
 * </p>
 */
@Component("cloudflareAiGatewayProvider")
public class CloudflareAiGatewayProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(CloudflareAiGatewayProvider.class);

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(120);

    private final String apiKey;
    private final String accountId;
    private final String gatewayId;
    private final String baseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final boolean configured;

    public CloudflareAiGatewayProvider(CloudflareAiGatewayProperties props) {
        this.apiKey = props.getApiKey();
        this.accountId = props.getAccountId();
        this.gatewayId = props.getGatewayId();
        this.baseUrl = props.getBaseUrl();
        this.defaultModel = props.getChatModel();
        this.timeout = props.getReasoningTimeout() != null ? props.getReasoningTimeout() : DEFAULT_READ_TIMEOUT;
        this.configured = props.isEnabled()
                && apiKey != null && !apiKey.isBlank()
                && accountId != null && !accountId.isBlank();
        if (configured) {
            log.info("CloudflareAiGatewayProvider initialized: accountId={}, model={}, timeout={}", accountId, defaultModel, timeout);
        } else {
            log.info("CloudflareAiGatewayProvider not configured (enabled={}, apiKeyPresent={}, accountIdPresent={})",
                    props.isEnabled(), apiKey != null && !apiKey.isBlank(), accountId != null && !accountId.isBlank());
        }
    }

    @Override
    public ChatModel chatModel(String model) {
        if (!configured) {
            throw new IllegalStateException("Cloudflare AI Gateway provider is not configured. Enable cloudflare-ai-gateway.enabled=true and set CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID.");
        }
        String modelName = resolveModelName(model);
        String effectiveBaseUrl = baseUrl.replace("{accountId}", accountId);
        log.debug("Building OpenAiChatModel (Cloudflare AI Gateway) for model: {}", modelName);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl.replace("{accountId}", accountId))
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(4096)
                .timeout(timeout)
                .build();
    }

    @Override
    public String providerName() {
        return "cloudflare-ai-gateway";
    }

    @Override
    public String providerLabel() {
        return "Cloudflare AI Gateway";
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