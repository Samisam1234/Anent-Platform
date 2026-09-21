package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GroqProvider} configuration detection and model construction.
 */
class GroqProviderTest {

    @Test
    @DisplayName("GroqProvider is not configured when disabled")
    void disabledProvider_isNotConfigured() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");

        GroqProvider provider = new GroqProvider(props);

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.providerName()).isEqualTo("groq");
        assertThat(provider.providerLabel()).isEqualTo("Groq");
        assertThat(provider.defaultModel()).isEqualTo("llama-3.1-8b-instant");
    }

    @Test
    @DisplayName("GroqProvider is not configured when API key is missing")
    void missingApiKey_isNotConfigured() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(true);
        props.setApiKey(null);

        GroqProvider provider = new GroqProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("GroqProvider is not configured when API key is blank")
    void blankApiKey_isNotConfigured() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(true);
        props.setApiKey("   ");

        GroqProvider provider = new GroqProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("GroqProvider is configured when enabled and API key present")
    void enabledWithApiKey_isConfigured() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(true);
        props.setApiKey("test-groq-key");
        props.setChatModel("llama-3.1-70b-versatile");

        GroqProvider provider = new GroqProvider(props);

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.defaultModel()).isEqualTo("llama-3.1-70b-versatile");
    }

    @Test
    @DisplayName("GroqProvider resolves model name correctly")
    void resolveModelName_works() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setChatModel("llama-3.1-8b-instant");

        GroqProvider provider = new GroqProvider(props);

        assertThat(provider.resolveModelName(null)).isEqualTo("llama-3.1-8b-instant");
        assertThat(provider.resolveModelName("  ")).isEqualTo("llama-3.1-8b-instant");
        assertThat(provider.resolveModelName("custom-model")).isEqualTo("custom-model");
    }

    @Test
    @DisplayName("chatModel throws when provider not configured")
    void chatModel_throwsWhenNotConfigured() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");

        GroqProvider provider = new GroqProvider(props);

        assertThatThrownBy(() -> provider.chatModel("any-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("chatModel builds a non-null ChatModel when configured")
    void chatModel_buildsModelWhenConfigured() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(true);
        props.setApiKey("test-groq-key");
        props.setChatModel("llama-3.1-8b-instant");

        GroqProvider provider = new GroqProvider(props);

        ChatModel model = provider.chatModel("llama-3.1-8b-instant");
        assertThat(model).isNotNull();
    }

    @Test
    @DisplayName("timeout returns configured timeout")
    void timeout_returnsConfiguredValue() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        GroqProvider provider = new GroqProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("timeout falls back to default when not configured")
    void timeout_fallsBackToDefault() {
        GroqProperties props = new GroqProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setReasoningTimeout(null);

        GroqProvider provider = new GroqProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofSeconds(120));
    }
}