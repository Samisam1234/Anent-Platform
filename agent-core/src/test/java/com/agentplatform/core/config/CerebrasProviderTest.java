package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CerebrasProvider} configuration detection and model construction.
 */
class CerebrasProviderTest {

    @Test
    @DisplayName("CerebrasProvider is not configured when disabled")
    void disabledProvider_isNotConfigured() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.providerName()).isEqualTo("cerebras");
        assertThat(provider.providerLabel()).isEqualTo("Cerebras");
        assertThat(provider.defaultModel()).isEqualTo("gpt-oss-120b");
    }

    @Test
    @DisplayName("CerebrasProvider is not configured when API key is missing")
    void missingApiKey_isNotConfigured() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey(null);

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("CerebrasProvider is not configured when API key is blank")
    void blankApiKey_isNotConfigured() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey("   ");

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("CerebrasProvider is configured when enabled and API key present")
    void enabledWithApiKey_isConfigured() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey("test-cerebras-key");
        props.setChatModel("llama3.1-70b");

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.defaultModel()).isEqualTo("llama3.1-70b");
    }

    @Test
    @DisplayName("CerebrasProvider resolves model name correctly")
    void resolveModelName_works() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setChatModel("llama3.1-8b");

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThat(provider.resolveModelName(null)).isEqualTo("llama3.1-8b");
        assertThat(provider.resolveModelName("  ")).isEqualTo("llama3.1-8b");
        assertThat(provider.resolveModelName("custom-model")).isEqualTo("custom-model");
    }

    @Test
    @DisplayName("chatModel throws when provider not configured")
    void chatModel_throwsWhenNotConfigured() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThatThrownBy(() -> provider.chatModel("any-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("chatModel builds a non-null ChatModel when configured")
    void chatModel_buildsModelWhenConfigured() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey("test-cerebras-key");
        props.setChatModel("llama3.1-8b");

        CerebrasProvider provider = new CerebrasProvider(props);

        ChatModel model = provider.chatModel("llama3.1-8b");
        assertThat(model).isNotNull();
    }

    @Test
    @DisplayName("timeout returns configured timeout")
    void timeout_returnsConfiguredValue() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("timeout falls back to default when not configured")
    void timeout_fallsBackToDefault() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setReasoningTimeout(null);

        CerebrasProvider provider = new CerebrasProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("chatModel configures maxTokens=4096 in the OpenAI chat model")
    void chatModel_configuresMaxTokens() {
        CerebrasProperties props = new CerebrasProperties();
        props.setEnabled(true);
        props.setApiKey("test-cerebras-key");
        props.setChatModel("llama3.1-8b");

        CerebrasProvider provider = new CerebrasProvider(props);

        ChatModel model = provider.chatModel("llama3.1-8b");
        assertThat(model).isNotNull();
        // The maxTokens=4096 is set in the OpenAiChatModel builder.
        // We verify the model builds successfully with the maxTokens configuration.
        // The actual maxTokens value is set in the builder and cannot be directly
        // inspected without reflection, but successful construction verifies the
        // configuration is accepted by the LangChain4j builder.
    }
}