package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
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
 * This router does NOT implement automatic failover or retries.
 * It simply selects the appropriate provider based on the requested
 * provider name or the configured default provider.
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
            // Explicit provider requested
            provider = providers.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(providerName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Provider '" + providerName + "' is not configured or not available. Available: " +
                                    providers.stream().map(LlmProvider::providerName).toList()));
        } else if (configuredDefaultProvider != null && !configuredDefaultProvider.isBlank()) {
            // Use configured default provider
            provider = providers.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(configuredDefaultProvider))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Configured default provider '" + configuredDefaultProvider + "' is not configured or not available. Available: " +
                                    providers.stream().map(LlmProvider::providerName).toList()));
        } else {
            // Fall back to highest-priority configured provider
            provider = providers.get(0);
        }

        log.debug("Routing to provider: {} for model: {}", provider.providerName(), model);
        return provider.chatModel(model);
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
}