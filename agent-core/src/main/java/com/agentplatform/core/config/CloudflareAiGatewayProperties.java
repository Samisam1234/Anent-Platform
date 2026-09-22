package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Cloudflare AI Gateway (OpenAI-compatible) configuration properties from application.yml.
 *
 * <pre>
 * cloudflare-ai-gateway:
 *   enabled: false
 *   api-key: ${CLOUDFLARE_API_TOKEN}
 *   account-id: ${CLOUDFLARE_ACCOUNT_ID}
 *   gateway-id: ${CLOUDFLARE_AI_GATEWAY_ID:}
 *   base-url: https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/v1
 *   chat-model: @cf/meta/llama-3.1-8b-instruct-fp8
 *   reasoning-timeout: 120s
 * </pre>
 */
@ConfigurationProperties(prefix = "cloudflare-ai-gateway")
public class CloudflareAiGatewayProperties {

    /**
     * Whether Cloudflare AI Gateway is active. Off by default: the app runs with other providers
     * unless this is explicitly set to {@code true} (and credentials are provided).
     */
    private boolean enabled = false;

    /** Cloudflare API token (https://dash.cloudflare.com/profile/api-tokens). */
    private String apiKey;

    /** Cloudflare Account ID. */
    private String accountId;

    /** Cloudflare AI Gateway ID (optional, for gateway-specific routing). */
    private String gatewayId;

    /** Base URL for the Cloudflare AI Gateway OpenAI-compatible API. */
    private String baseUrl = "https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/v1";

    /** Name of the Cloudflare AI Gateway chat model to use (e.g., @cf/meta/llama-3.1-8b-instruct-fp8). */
    private String chatModel = "@cf/meta/llama-3.1-8b-instruct-fp8";

    /** Request timeout for AI reasoning calls (default 2 minutes). */
    private java.time.Duration reasoningTimeout = java.time.Duration.ofMinutes(2);

    // ─── Getters & Setters ───────────────────────────────────────────────────

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public java.time.Duration getReasoningTimeout() {
        return reasoningTimeout;
    }

    public void setReasoningTimeout(java.time.Duration reasoningTimeout) {
        this.reasoningTimeout = reasoningTimeout;
    }
}