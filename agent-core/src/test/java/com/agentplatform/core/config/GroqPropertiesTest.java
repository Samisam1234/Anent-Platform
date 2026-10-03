package com.agentplatform.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GroqProperties} defaults and binding.
 */
class GroqPropertiesTest {

    @Test
    @DisplayName("defaults match the expected Groq configuration")
    void defaults_areCorrect() {
        GroqProperties properties = new GroqProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiKey()).isNull();
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.groq.com/openai/v1");
        assertThat(properties.getChatModel()).isEqualTo("openai/gpt-oss-20b");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("setters and getters work correctly")
    void settersAndGetters_work() {
        GroqProperties properties = new GroqProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://custom.groq.api/v1");
        properties.setChatModel("llama-3.1-70b-versatile");
        properties.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApiKey()).isEqualTo("test-key");
        assertThat(properties.getBaseUrl()).isEqualTo("https://custom.groq.api/v1");
        assertThat(properties.getChatModel()).isEqualTo("llama-3.1-70b-versatile");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }
}