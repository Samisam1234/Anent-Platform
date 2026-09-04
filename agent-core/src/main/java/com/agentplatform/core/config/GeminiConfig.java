package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/**
 * Optional Google Gemini wiring. Everything here is gated behind
 * {@code gemini.enabled=true} (default {@code false}), so the app runs
 * Ollama-only unless explicitly opted in — no user-facing feature depends on
 * Gemini.
 *
 * <p>When Gemini is disabled, the {@code ChatModel} bean is provided by
 * {@link OllamaConfig} (Ollama-backed) instead. This config only activates when
 * {@code gemini.enabled=true}: it then produces the Gemini {@code ChatModel}
 * and {@code EmbeddingModel} beans and fails fast at startup if the
 * {@code GEMINI_API_KEY} is missing.</p>
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiConfig.class);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gemini", name = "enabled", havingValue = "true")
    public ChatModel chatModel(GeminiProperties props) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "gemini.enabled=true but no GEMINI_API_KEY is set. Set the key or set gemini.enabled=false.");
        }
        log.info("Configuring GoogleAiGeminiChatModel: model={}", props.getChatModel());
        return GoogleAiGeminiChatModel.builder()
                .apiKey(props.getApiKey())
                .modelName(props.getChatModel())
                .temperature(0.1)
                .timeout(READ_TIMEOUT)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gemini", name = "enabled", havingValue = "true")
    public EmbeddingModel embeddingModel(GeminiProperties props) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "gemini.enabled=true but no GEMINI_API_KEY is set. Set the key or set gemini.enabled=false.");
        }
        log.info("Configuring GoogleAiEmbeddingModel: model={}", props.getEmbeddingModel());
        return GoogleAiEmbeddingModel.builder()
                .apiKey(props.getApiKey())
                .modelName(props.getEmbeddingModel())
                .timeout(READ_TIMEOUT)
                .build();
    }
}