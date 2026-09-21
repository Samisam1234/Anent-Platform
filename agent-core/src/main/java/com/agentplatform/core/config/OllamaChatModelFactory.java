package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Builds {@link ChatModel} instances on demand for the chat and custom AI
 * endpoints. Delegates to {@link LlmProviderRouter} for provider-aware model
 * construction while preserving the original Ollama-only API for backward
 * compatibility.
 *
 * <p>Existing callers continue to work unchanged — they receive an Ollama-backed
 * {@link ChatModel} by default. New callers can specify a provider name via
 * the overloaded {@link #chatModel(String, String)} method.</p>
 */
@Service
public class OllamaChatModelFactory {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    public static final String DEFAULT_MODEL = "llama3.2:3b";

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(OllamaChatModelFactory.class);

    private final LlmProviderRouter router;
    private final OllamaProvider ollamaProvider;
    private final Duration timeout;

    public OllamaChatModelFactory(OllamaProperties props, LlmProviderRouter router, OllamaProvider ollamaProvider) {
        this.router = router;
        this.ollamaProvider = ollamaProvider;
        this.timeout = props.getReasoningTimeout();
    }

    public LlmProviderRouter getRouter() {
        return router;
    }

    /**
     * Resolves the effective model name: the requested model if non-blank,
     * otherwise the configured default ({@code llama3.2:3b}).
     */
    public String resolveModelName(String model) {
        return ollamaProvider.resolveModelName(model);
    }

    /**
     * Returns a ready-to-use {@link ChatModel} from the configured default provider
     * (or highest-priority configured provider if no default is configured).
     * This replaces the previous hardcoded "ollama" behavior.
     */
    public ChatModel chatModel(String model) {
        return router.chatModel(null, model);
    }

    /**
     * Returns a {@link ChatModel} from the specified provider.
     * New method for provider-aware callers.
     *
     * @param providerName the provider to use ("ollama", "gemini", etc.)
     * @param model the model name, or null for the provider's default
     * @return a configured {@link ChatModel}
     */
    public ChatModel chatModel(String providerName, String model) {
        return router.chatModel(providerName, model);
    }

    /**
     * The configured reasoning timeout ({@code ollama.reasoning-timeout}).
     *
     * <p>Callers that must guarantee a response use this as a <em>wall-clock</em>
     * deadline around {@link ChatModel#chat}. The same value is passed to
     * the underlying model's timeout, but that maps to the HTTP client's
     * socket-idle read timeout, which does not bound the total duration of a
     * long generation. See {@code AiStatusService} for the established pattern.</p>
     */
    public Duration timeout() {
        return timeout != null ? timeout : DEFAULT_TIMEOUT;
    }

    /**
     * Returns a safe fallback response when the default provider is unreachable,
     * so the UI completes without hanging.
     */
    public static String fallbackResponse() {
        return "I'm currently offline — the Ollama server is not reachable. "
                + "Please check that Ollama is running (try `ollama pull llama3.2:3b`) and try again.";
    }
}