package com.agentplatform.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GeminiProperties} defaults and binding.
 */
class GeminiPropertiesTest {

    @Test
    @DisplayName("defaults match application.yml (enabled=false, chat + embedding models)")
    void defaults_matchApplicationYml() {
        GeminiProperties properties = new GeminiProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiKey()).isNull();
        assertThat(properties.getChatModel()).isEqualTo("gemini-2.5-flash");
        assertThat(properties.getEmbeddingModel()).isEqualTo("text-embedding-004");
    }

    @Test
    @DisplayName("explicit values are honoured")
    void setters_areHonoured() {
        GeminiProperties properties = new GeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("secret");
        properties.setChatModel("gemini-2.5-pro");
        properties.setEmbeddingModel("text-embedding-001");

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApiKey()).isEqualTo("secret");
        assertThat(properties.getChatModel()).isEqualTo("gemini-2.5-pro");
        assertThat(properties.getEmbeddingModel()).isEqualTo("text-embedding-001");
    }
}