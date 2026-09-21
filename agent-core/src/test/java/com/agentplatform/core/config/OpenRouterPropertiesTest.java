package com.agentplatform.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenRouterProperties} defaults and binding.
 */
class OpenRouterPropertiesTest {

    @Test
    @DisplayName("defaults match the expected OpenRouter configuration")
    void defaults_areCorrect() {
        OpenRouterProperties properties = new OpenRouterProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiKey()).isNull();
        assertThat(properties.getBaseUrl()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(properties.getChatModel()).isEqualTo("openrouter/auto");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("setters and getters work correctly")
    void settersAndGetters_work() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://custom.openrouter.api/v1");
        properties.setChatModel("anthropic/claude-3.5-sonnet");
        properties.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApiKey()).isEqualTo("test-key");
        assertThat(properties.getBaseUrl()).isEqualTo("https://custom.openrouter.api/v1");
        assertThat(properties.getChatModel()).isEqualTo("anthropic/claude-3.5-sonnet");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }
}