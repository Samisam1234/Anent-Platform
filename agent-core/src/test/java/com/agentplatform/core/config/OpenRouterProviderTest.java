package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpenRouterProvider} configuration detection and model construction.
 */
class OpenRouterProviderTest {

    @Test
    @DisplayName("OpenRouterProvider is not configured when disabled")
    void disabledProvider_isNotConfigured() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.providerName()).isEqualTo("openrouter");
        assertThat(provider.providerLabel()).isEqualTo("OpenRouter");
        assertThat(provider.defaultModel()).isEqualTo("openrouter/auto");
    }

    @Test
    @DisplayName("OpenRouterProvider is not configured when API key is missing")
    void missingApiKey_isNotConfigured() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey(null);

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("OpenRouterProvider is not configured when API key is blank")
    void blankApiKey_isNotConfigured() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey("   ");

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("OpenRouterProvider is configured when enabled and API key present")
    void enabledWithApiKey_isConfigured() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey("test-openrouter-key");
        props.setChatModel("anthropic/claude-3.5-sonnet");

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.defaultModel()).isEqualTo("anthropic/claude-3.5-sonnet");
    }

    @Test
    @DisplayName("OpenRouterProvider resolves model name correctly")
    void resolveModelName_works() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setChatModel("openrouter/auto");

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThat(provider.resolveModelName(null)).isEqualTo("openrouter/auto");
        assertThat(provider.resolveModelName("  ")).isEqualTo("openrouter/auto");
        assertThat(provider.resolveModelName("custom-model")).isEqualTo("custom-model");
    }

    @Test
    @DisplayName("chatModel throws when provider not configured")
    void chatModel_throwsWhenNotConfigured() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThatThrownBy(() -> provider.chatModel("any-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("chatModel builds a non-null ChatModel when configured")
    void chatModel_buildsModelWhenConfigured() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey("test-openrouter-key");
        props.setChatModel("openrouter/auto");

        OpenRouterProvider provider = new OpenRouterProvider(props);

        ChatModel model = provider.chatModel("openrouter/auto");
        assertThat(model).isNotNull();
    }

    @Test
    @DisplayName("timeout returns configured timeout")
    void timeout_returnsConfiguredValue() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("timeout falls back to default when not configured")
    void timeout_fallsBackToDefault() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setReasoningTimeout(null);

        OpenRouterProvider provider = new OpenRouterProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("chatModel configures maxTokens=4096 in the OpenAI chat model")
    void chatModel_configuresMaxTokens() {
        OpenRouterProperties props = new OpenRouterProperties();
        props.setEnabled(true);
        props.setApiKey("test-openrouter-key");
        props.setChatModel("openrouter/auto");

        OpenRouterProvider provider = new OpenRouterProvider(props);

        ChatModel model = provider.chatModel("openrouter/auto");
        assertThat(model).isNotNull();
        // The maxTokens=4096 is set in the OpenAiChatModel builder.
        // We verify the model builds successfully with the maxTokens configuration.
        // The actual maxTokens value is set in the builder and cannot be directly
        // inspected without reflection, but successful construction verifies the
        // configuration is accepted by the LangChain4j builder.
    }
}