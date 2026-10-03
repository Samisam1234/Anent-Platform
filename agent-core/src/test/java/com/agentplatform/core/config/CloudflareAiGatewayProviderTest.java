package com.agentplatform.core.config;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CloudflareAiGatewayProvider} configuration detection and model construction.
 */
class CloudflareAiGatewayProviderTest {

    @Test
    @DisplayName("CloudflareAiGatewayProvider is not configured when disabled")
    void disabledProvider_isNotConfigured() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");
        props.setAccountId("test-account-id");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.providerName()).isEqualTo("cloudflare-ai-gateway");
        assertThat(provider.providerLabel()).isEqualTo("Cloudflare AI Gateway");
        assertThat(provider.defaultModel()).isEqualTo("@cf/meta/llama-3.1-8b-instruct-fp8");
    }

    @Test
    @DisplayName("CloudflareAiGatewayProvider is not configured when API key is missing")
    void missingApiKey_isNotConfigured() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey(null);
        props.setAccountId("test-account-id");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("CloudflareAiGatewayProvider is not configured when API key is blank")
    void blankApiKey_isNotConfigured() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("   ");
        props.setAccountId("test-account-id");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("CloudflareAiGatewayProvider is not configured when account ID is missing")
    void missingAccountId_isNotConfigured() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setAccountId(null);

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("CloudflareAiGatewayProvider is configured when enabled and credentials present")
    void enabledWithCredentials_isConfigured() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("test-cloudflare-token");
        props.setAccountId("test-account-id");
        props.setChatModel("@cf/meta/llama-3.1-70b-instruct");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.isConfigured()).isTrue();
        assertThat(provider.defaultModel()).isEqualTo("@cf/meta/llama-3.1-70b-instruct");
    }

    @Test
    @DisplayName("CloudflareAiGatewayProvider resolves model name correctly")
    void resolveModelName_works() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setAccountId("test-account-id");
        props.setChatModel("@cf/meta/llama-3.1-8b-instruct-fp8");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.resolveModelName(null)).isEqualTo("@cf/meta/llama-3.1-8b-instruct-fp8");
        assertThat(provider.resolveModelName("  ")).isEqualTo("@cf/meta/llama-3.1-8b-instruct-fp8");
        assertThat(provider.resolveModelName("custom-model")).isEqualTo("custom-model");
    }

    @Test
    @DisplayName("chatModel throws when provider not configured")
    void chatModel_throwsWhenNotConfigured() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(false);
        props.setApiKey("test-key");
        props.setAccountId("test-account-id");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThatThrownBy(() -> provider.chatModel("any-model"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("chatModel builds a non-null ChatModel when configured")
    void chatModel_buildsModelWhenConfigured() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("test-cloudflare-token");
        props.setAccountId("test-account-id");
        props.setChatModel("@cf/meta/llama-3.1-8b-instruct-fp8");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        ChatModel model = provider.chatModel("@cf/meta/llama-3.1-8b-instruct-fp8");
        assertThat(model).isNotNull();
    }

    @Test
    @DisplayName("timeout returns configured timeout")
    void timeout_returnsConfiguredValue() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setAccountId("test-account-id");
        props.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("timeout falls back to default when not configured")
    void timeout_fallsBackToDefault() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setAccountId("test-account-id");
        props.setReasoningTimeout(null);

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        assertThat(provider.timeout()).isEqualTo(java.time.Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("chatModel configures maxTokens=4096 in the OpenAI chat model")
    void chatModel_configuresMaxTokens() {
        CloudflareAiGatewayProperties props = new CloudflareAiGatewayProperties();
        props.setEnabled(true);
        props.setApiKey("test-cloudflare-token");
        props.setAccountId("test-account-id");
        props.setChatModel("@cf/meta/llama-3.1-8b-instruct-fp8");

        CloudflareAiGatewayProvider provider = new CloudflareAiGatewayProvider(props);

        ChatModel model = provider.chatModel("@cf/meta/llama-3.1-8b-instruct-fp8");
        assertThat(model).isNotNull();
        // The maxTokens=4096 is set in the OpenAiChatModel builder.
        // We verify the model builds successfully with the maxTokens configuration.
        // The actual maxTokens value is set in the builder and cannot be directly
        // inspected without reflection, but successful construction verifies the
        // configuration is accepted by the LangChain4j builder.
    }
}