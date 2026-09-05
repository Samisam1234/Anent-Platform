package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Builds {@link OllamaChatModel} instances on demand so the chat and custom AI
 * endpoints can switch models per request (e.g. {@code llama3.2:3b} for
 * fast chat, {@code qwen2.5:1.5b} for lightweight code,
 * {@code gemma3:4b} for reasoning).
 *
 * <p>Configuration (both optional, with defaults):
 * <pre>
 * ollama:
 *   base-url: http://localhost:11434
 *   default-model: llama3.2:3b
 *   reasoning-timeout: 120s
 * </pre>
 *
 * <p>A {@code null}/blank requested model resolves to the configured default
 * ({@code llama3.2:3b}). Constructing an {@link OllamaChatModel} is cheap and
 * does not touch the network — the call is only made when the model is used.</p>
 */
@Service
public class OllamaChatModelFactory {

    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    public static final String DEFAULT_MODEL = "llama3.2:3b";

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private final String baseUrl;

    private final String defaultModel;

    private final Duration timeout;

    public OllamaChatModelFactory(OllamaProperties props) {
        this.baseUrl = props.getBaseUrl();
        this.defaultModel = props.getChatModel();
        this.timeout = props.getReasoningTimeout();
    }

    /**
     * Resolves the effective model name: the requested model if non-blank,
     * otherwise the configured default ({@code llama3.2:3b}).
     */
    public String resolveModelName(String model) {
        return (model == null || model.isBlank()) ? defaultModel : model;
    }

    /**
     * Returns a ready-to-use {@link ChatModel} backed by Ollama at
     * {@code baseUrl} running {@code resolveModelName(model)}.
     * The model has a configurable request timeout (default 2 minutes) to prevent
     * long UI hangs during cold-start model loads.
     */
    public ChatModel chatModel(String model) {
        String modelName = resolveModelName(model);
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .build();
    }

    /**
     * Returns a safe fallback response when Ollama is unreachable,
     * so the UI completes without hanging.
     */
    public static String fallbackResponse() {
        return "I'm currently offline — the Ollama server is not reachable. "
                + "Please check that Ollama is running (try `ollama pull llama3.2:3b`) and try again.";
    }
}