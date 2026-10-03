package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration that wires LangChain4j Ollama model beans.
 *
 * <p>All connection parameters (base URL, model names, timeouts) are read from
 * {@link OllamaProperties} which is bound to the {@code ollama.*} namespace in
 * {@code application.yml}. Nothing is hard-coded here.</p>
 *
 * <p>Beans produced:
 * <ul>
 *   <li>{@link ChatModel}     — used by the orchestrator for chat/completion</li>
 *   <li>{@link EmbeddingModel} — reserved for RAG / memory (nomic-embed-text)</li>
 * </ul>
 * </p>
 *
 * <p>NOTE: LangChain4j 1.0.0 renamed {@code ChatLanguageModel} to {@code ChatModel}
 * (in {@code dev.langchain4j.model.chat}). Imports updated accordingly.</p>
 */
@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
public class OllamaConfig {

    private static final Logger log = LoggerFactory.getLogger(OllamaConfig.class);

    /** Connection timeout for Ollama requests. Generous for local hardware. */


    /** Read timeout — local models on modest hardware can be slow to respond. */
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(3);

    // ─── Chat Model ──────────────────────────────────────────────────────────

    /**
     * Creates the {@link ChatModel} bean backed by an Ollama chat endpoint.
     *
     * <p>Only registered when Gemini is disabled (or unset, the default), so
     * exactly one raw {@code chatModel} bean exists: Ollama here, or Gemini
     * from {@code GeminiConfig} when {@code gemini.enabled=true}. When Gemini
     * is enabled, {@code OllamaProvider} remains available through the
     * {@code LlmProviderRouter}.</p>
     *
     * @param props Ollama connection properties
     * @return configured {@link OllamaChatModel} instance
     */
    @Bean
    @ConditionalOnProperty(prefix = "gemini", name = "enabled", havingValue = "false", matchIfMissing = true)
    public ChatModel chatModel(OllamaProperties props) {
        log.info("Configuring OllamaChatModel: baseUrl={}, model={}",
                props.getBaseUrl(), props.getChatModel());

        return OllamaChatModel.builder()
                .baseUrl(props.getBaseUrl())
                .modelName(props.getChatModel())
                .temperature(0.7)
                .timeout(READ_TIMEOUT)
                .build();
    }

    // ─── Embedding Model ─────────────────────────────────────────────────────

    /**
     * Creates the {@link EmbeddingModel} bean backed by an Ollama embedding endpoint.
     *
     * <p>Uses {@code nomic-embed-text} which produces 768-dimensional vectors —
     * matching the {@code vector(768)} column defined in {@code init.sql}.</p>
     *
     * <p>Like {@link #chatModel(OllamaProperties)}, only registered when Gemini
     * is disabled so {@code GeminiConfig} owns the bean when Gemini is on.</p>
     *
     * @param props Ollama connection properties
     * @return configured {@link OllamaEmbeddingModel} instance
     */
    @Bean
    @ConditionalOnProperty(prefix = "gemini", name = "enabled", havingValue = "false", matchIfMissing = true)
    public EmbeddingModel embeddingModel(OllamaProperties props) {
        log.info("Configuring OllamaEmbeddingModel: baseUrl={}, model={}",
                props.getBaseUrl(), props.getEmbeddingModel());

        return OllamaEmbeddingModel.builder()
                .baseUrl(props.getBaseUrl())
                .modelName(props.getEmbeddingModel())
                .timeout(READ_TIMEOUT)
                .build();
    }
}
