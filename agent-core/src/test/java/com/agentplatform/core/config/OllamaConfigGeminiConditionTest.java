package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the Gemini/Ollama bean collision fixed in
 * {@link OllamaConfig}: exactly one raw {@code ChatModel} and one
 * {@code EmbeddingModel} bean exists at any time — Ollama when Gemini is
 * disabled (or unset), Gemini when enabled. Previously enabling Gemini failed
 * startup with a {@code BeanDefinitionOverrideException} on both beans.
 */
class OllamaConfigGeminiConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OllamaConfig.class, GeminiConfig.class);

    @Test
    @DisplayName("gemini disabled: only the Ollama chat/embedding beans are registered")
    void geminiDisabled_registersOnlyOllamaBeans() {
        runner.withPropertyValues("gemini.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(OllamaChatModel.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OllamaEmbeddingModel.class);
        });
    }

    @Test
    @DisplayName("gemini unset (default): Ollama beans still registered via matchIfMissing")
    void geminiUnset_registersOnlyOllamaBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(OllamaChatModel.class);
        });
    }

    @Test
    @DisplayName("gemini enabled: only the Gemini chat/embedding beans are registered, no bean-definition override error")
    void geminiEnabled_registersOnlyGeminiBeans() {
        runner.withPropertyValues("gemini.enabled=true", "gemini.api-key=test-key").run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(GoogleAiGeminiChatModel.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(GoogleAiEmbeddingModel.class);
        });
    }
}