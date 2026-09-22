package com.agentplatform.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CloudflareAiGatewayProperties} defaults and binding.
 */
class CloudflareAiGatewayPropertiesTest {

    @Test
    @DisplayName("defaults match the expected Cloudflare AI Gateway configuration")
    void defaults_areCorrect() {
        CloudflareAiGatewayProperties properties = new CloudflareAiGatewayProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getApiKey()).isNull();
        assertThat(properties.getAccountId()).isNull();
        assertThat(properties.getGatewayId()).isNull();
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/v1");
        assertThat(properties.getChatModel()).isEqualTo("@cf/meta/llama-3.1-8b-instruct");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("setters and getters work correctly")
    void settersAndGetters_work() {
        CloudflareAiGatewayProperties properties = new CloudflareAiGatewayProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setAccountId("test-account-id");
        properties.setGatewayId("test-gateway-id");
        properties.setBaseUrl("https://custom.cloudflare.api/v1");
        properties.setChatModel("@cf/meta/llama-3.1-70b-instruct");
        properties.setReasoningTimeout(java.time.Duration.ofMinutes(5));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApiKey()).isEqualTo("test-key");
        assertThat(properties.getAccountId()).isEqualTo("test-account-id");
        assertThat(properties.getGatewayId()).isEqualTo("test-gateway-id");
        assertThat(properties.getBaseUrl()).isEqualTo("https://custom.cloudflare.api/v1");
        assertThat(properties.getChatModel()).isEqualTo("@cf/meta/llama-3.1-70b-instruct");
        assertThat(properties.getReasoningTimeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }
}