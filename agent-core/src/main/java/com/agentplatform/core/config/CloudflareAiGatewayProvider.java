package com.agentplatform.core.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        return new CloudflareCompatibleChatModel(OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl.replace("{accountId}", accountId))
                .modelName(modelName)
                .temperature(0.1)
                .maxTokens(4096)
                .timeout(timeout)
                .build());
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

    /**
     * Cloudflare Workers AI OpenAI-compatible endpoint rejects LangChain4j's
     * multi-{@link Content} message serialization (HTTP 503 "Type mismatch of
     * '/messages/0/content', 'array' not in 'string'") and tool-enabled requests.
     * This wrapper normalizes every incoming message to plain single-string content
     * and drops tool specifications BEFORE they reach the wire — Cloudflare only,
     * every other provider still receives the raw request with tools.
     */
    static final class CloudflareCompatibleChatModel implements ChatModel {

        private final ChatModel delegate;

        CloudflareCompatibleChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            // ponytail: request-level parameters are dropped; AgentChatService sends none,
            // the delegate re-applies its own defaults (fp8 model, temperature, maxTokens).
            return delegate.chat(ChatRequest.builder()
                    .messages(chatRequest.messages().stream()
                            .map(CloudflareCompatibleChatModel::toCloudflareCompatible)
                            .toList())
                    .build());
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return delegate.defaultRequestParameters();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return delegate.listeners();
        }

        @Override
        public ModelProvider provider() {
            return delegate.provider();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return delegate.supportedCapabilities();
        }

        private static ChatMessage toCloudflareCompatible(ChatMessage message) {
            if (message instanceof UserMessage userMessage) {
                if (userMessage.hasSingleText()) {
                    return userMessage;
                }
                return UserMessage.from(textOf(userMessage.contents()));
            }
            if (message instanceof AiMessage aiMessage) {
                if (!aiMessage.hasToolExecutionRequests()) {
                    return aiMessage;
                }
                String text = aiMessage.text() != null ? aiMessage.text() : "";
                return AiMessage.from(text);
            }
            return message;
        }

        private static String textOf(List<Content> contents) {
            return contents.stream()
                    .filter(TextContent.class::isInstance)
                    .map(content -> ((TextContent) content).text())
                    .collect(Collectors.joining("\n"));
        }
    }
}