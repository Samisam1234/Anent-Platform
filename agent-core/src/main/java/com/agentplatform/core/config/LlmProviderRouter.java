package com.agentplatform.core.config;

import com.agentplatform.core.ai.AiErrorClassifier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Router for LLM providers.
 * <p>
 * Maintains an ordered list of configured providers and delegates model
 * construction to the appropriate provider. Providers are ordered by
 * explicit priority (lower number = higher priority).
 * </p>
 * <p>
 * When no explicit provider is requested, a {@link FailoverChatModel} is
 * returned that tries the configured default provider first, then the
 * remaining configured providers by priority, each exactly once, until one
 * succeeds. Failover lives here only — providers are not failover-aware.
 * </p>
 */
@Service
public class LlmProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderRouter.class);

    private final List<LlmProvider> providers;
    private final String configuredDefaultProvider;

    public LlmProviderRouter(List<LlmProvider> providers, LlmProperties llmProperties) {
        // Filter to only configured providers and sort by priority
        this.providers = providers.stream()
                .filter(LlmProvider::isConfigured)
                .sorted(Comparator.comparingInt(this::getProviderPriority))
                .toList();

        this.configuredDefaultProvider = llmProperties != null ? llmProperties.getDefaultProvider() : null;

        log.info("LlmProviderRouter initialized with {} configured providers: {}, defaultProvider={}",
                this.providers.size(),
                this.providers.stream().map(LlmProvider::providerName).toList(),
                configuredDefaultProvider);
    }

    /**
     * Returns a {@link ChatModel} from the specified provider, or the configured
     * default provider, or the highest-priority configured provider if no default is set.
     *
     * @param providerName the name of the provider to use (e.g., "ollama", "gemini"), or null for default
     * @param model the model name to use, or null for the provider's default
     * @return a configured {@link ChatModel}
     * @throws IllegalStateException if no providers are configured or the named provider is not available
     */
    public ChatModel chatModel(String providerName, String model) {
if (providers.isEmpty()) {
            throw new IllegalStateException("No LLM providers are configured. Check ollama, gemini, groq, openrouter, cerebras, and cloudflare-ai-gateway configuration.");
        }

        LlmProvider provider;
        if (providerName != null && !providerName.isBlank()) {
            // Explicit provider requested: single provider, no failover.
            provider = providers.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(providerName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Provider '" + providerName + "' is not configured or not available. Available: " +
                                    providers.stream().map(LlmProvider::providerName).toList()));

            log.debug("Routing to provider: {} for model: {}", provider.providerName(), model);
            return provider.chatModel(model);
        }

        // No explicit provider: failover across all configured providers.
        return new FailoverChatModel(candidateProviders(), model);
    }

    /**
     * Failover candidates: the configured default provider first (if any),
     * then the remaining configured providers by priority. Never contains
     * duplicates or unconfigured providers.
     */
    private List<LlmProvider> candidateProviders() {
        List<LlmProvider> candidates = new ArrayList<>();
        if (configuredDefaultProvider != null && !configuredDefaultProvider.isBlank()) {
            providers.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(configuredDefaultProvider))
                    .findFirst()
                    .ifPresent(candidates::add);
        }
        providers.stream()
                .filter(p -> !candidates.contains(p))
                .forEach(candidates::add);
        return List.copyOf(candidates);
    }

    /**
     * Returns the configured default provider's default model, or the highest-priority
     * configured provider's default model if no default is configured.
     */
    public String defaultModel() {
        if (providers.isEmpty()) {
            return OllamaChatModelFactory.DEFAULT_MODEL;
        }
        if (configuredDefaultProvider != null && !configuredDefaultProvider.isBlank()) {
            return providers.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(configuredDefaultProvider))
                    .findFirst()
                    .map(LlmProvider::defaultModel)
                    .orElse(providers.get(0).defaultModel());
        }
        return providers.get(0).defaultModel();
    }

    /**
     * Returns all configured providers.
     */
    public List<LlmProvider> getConfiguredProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Checks if a provider is configured and available.
     */
    public boolean isProviderAvailable(String providerName) {
        return providers.stream()
                .anyMatch(p -> p.providerName().equalsIgnoreCase(providerName));
    }

    /**
     * Returns the provider by name if configured.
     */
    public Optional<LlmProvider> getProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(providerName))
                .findFirst();
    }

    /**
     * Returns the configured default provider name (may be null).
     */
    public String getConfiguredDefaultProvider() {
        return configuredDefaultProvider;
    }

/**
     * Priority ordering for providers (lower = higher priority).
     * This can be extended via configuration in the future.
     */
    private int getProviderPriority(LlmProvider provider) {
        return switch (provider.providerName().toLowerCase()) {
            case "ollama" -> 10;
            case "gemini" -> 20;
            case "groq" -> 30;
            case "openrouter" -> 40;
            case "cerebras" -> 50;
            case "cloudflare-ai-gateway" -> 60;
            default -> 100;
        };
    }

    /**
     * A {@link ChatModel} that tries each failover candidate once, in order,
     * and returns the first successful response. Every thrown exception from a
     * candidate's chat call is failover-eligible (HTTP 401/403/404/429/5xx,
     * timeouts, connection failures, model-unavailable errors).
     * <p>
     * If every candidate fails, throws an {@link IllegalStateException} whose
     * message aggregates the {@link AiErrorClassifier}-classified, secret-free
     * reasons per provider. Only provider names and classified messages are
     * ever logged or surfaced — never keys, headers, request bodies, or prompts.
     * </p>
     * <p>
     * Do not move provider-specific compatibility (e.g. the Cloudflare wrapper)
     * or tool handling into this class: each provider's own {@code chatModel}
     * contract is preserved as-is.
     * </p>
     */
    static final class FailoverChatModel implements ChatModel {

        private static final Logger log = LoggerFactory.getLogger(FailoverChatModel.class);

        private final List<LlmProvider> candidates;
        private final String model;

        FailoverChatModel(List<LlmProvider> candidates, String model) {
            this.candidates = candidates;
            this.model = model;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<String> failures = new ArrayList<>();
            for (LlmProvider provider : candidates) {
                String name = provider.providerName();
                log.debug("AI provider attempt started: provider={}", name);
                try {
                    ChatResponse response = provider.chatModel(model).chat(request);
                    log.debug("AI provider succeeded: provider={}", name);
                    return response;
                } catch (Exception e) {
                    String reason = AiErrorClassifier.classify(e, name).message();
                    log.warn("AI provider failed, failover to next provider: provider={}, reason={}", name, reason);
                    failures.add(name + ": " + reason);
                }
            }
            throw new IllegalStateException(
                    "All configured AI providers failed: " + String.join(" | ", failures));
        }
    }
}