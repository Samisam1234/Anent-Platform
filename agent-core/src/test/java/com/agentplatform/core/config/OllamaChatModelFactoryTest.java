package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OllamaChatModelFactory} — name resolution defaults and
 * on-demand model construction. Building an {@code OllamaChatModel} does not
 * touch the network, so constructing one here is hermetic.
 */
class OllamaChatModelFactoryTest {

    private final OllamaChatModelFactory factory;

    OllamaChatModelFactoryTest() {
        OllamaProperties props = new OllamaProperties();
        props.setBaseUrl("http://localhost:11434");
        props.setChatModel("llama3.2:3b");
        props.setReasoningTimeout(java.time.Duration.ofMinutes(3));

        // Create minimal router and provider for testing
        OllamaProvider ollamaProvider = new OllamaProvider(props);
        LlmProviderRouter router = new LlmProviderRouter(java.util.List.of(ollamaProvider));

        this.factory = new OllamaChatModelFactory(props, router, ollamaProvider);
    }

    @Test
    @DisplayName("null requested model resolves to the configured default")
    void resolveModelName_null_usesDefault() {
        assertThat(factory.resolveModelName(null)).isEqualTo("llama3.2:3b");
    }

    @Test
    @DisplayName("blank requested model resolves to the configured default")
    void resolveModelName_blank_usesDefault() {
        assertThat(factory.resolveModelName("  ")).isEqualTo("llama3.2:3b");
    }

    @Test
    @DisplayName("explicit model name is passed through")
    void resolveModelName_explicit_passesThrough() {
        assertThat(factory.resolveModelName("llama3.2:3b")).isEqualTo("llama3.2:3b");
    }

    @Test
    @DisplayName("defaults match the requirement: localhost:11434 + llama3.2:3b")
    void feedingDefaults_areExposedAsConstants() {
        assertThat(OllamaChatModelFactory.DEFAULT_BASE_URL).isEqualTo("http://localhost:11434");
        assertThat(OllamaChatModelFactory.DEFAULT_MODEL).isEqualTo("llama3.2:3b");
    }

    @Test
    @DisplayName("chatModel builds a non-null ChatModel for the requested model")
    void chatModel_buildsModel() {
        ChatModel model = factory.chatModel("llama3.2:3b");
        assertThat(model).isNotNull();
    }

    @Test
    @DisplayName("chatModel with provider name builds a non-null ChatModel")
    void chatModel_withProvider_buildsModel() {
        ChatModel model = factory.chatModel("ollama", "llama3.2:3b");
        assertThat(model).isNotNull();
    }
}