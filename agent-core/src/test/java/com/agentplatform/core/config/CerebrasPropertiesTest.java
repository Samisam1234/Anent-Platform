package com.agentplatform.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CerebrasProperties} defaults and binding.
 */
class CerebrasPropertiesTest {

    @Test
    @DisplayName("defaults match the expected Cerebras configuration")
    void defaults_areCorrect() {
        CerebrasProperties properties = new CerebrasProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiKey()).isNull();
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.cerebras.ai/v1");
        assertThat(properties.getChatModel()).isEqualTo("gpt-oss-120b");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("setters and getters work correctly")
    void settersAndGetters_work() {
        CerebrasProperties properties = new CerebrasProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://custom.cerebras.ai/v1");
        properties.setChatModel("llama3.1-70b");
        properties.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApiKey()).isEqualTo("test-key");
        assertThat(properties.getBaseUrl()).isEqualTo("https://custom.cerebras.ai/v1");
        assertThat(properties.getChatModel()).isEqualTo("llama3.1-70b");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }
}