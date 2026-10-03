package com.agentplatform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Global LLM routing configuration.
 * <p>
 * Controls which provider is used by default when no explicit provider is
 * requested, plus the in-memory cooldown/circuit-breaker policy applied by
 * automatic failover (Phase 13.9).
 * </p>
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * Default provider name (e.g., "ollama", "gemini", "groq").
     * When null or blank, the highest-priority configured provider is used.
     * This allows switching the active provider without code changes.
     */
    private String defaultProvider;

    /**
     * Consecutive failover-eligible provider failures before the provider is
     * placed into cooldown (property {@code llm.failure-threshold}, env
     * {@code LLM_FAILURE_THRESHOLD}). Only automatic failover consults this —
     * explicit provider requests are never redirected. Default: 3.
     */
    private int failureThreshold = 3;

    /**
     * How long a provider that reached the failure threshold stays out of the
     * automatic-failover rotation (property {@code llm.cooldown-duration}, env
     * {@code LLM_COOLDOWN_DURATION}). A successful request clears cooldown
     * immediately; otherwise the provider becomes eligible again when the
     * duration elapses. In-memory only, per runtime instance. Default: 30s.
     */
    private Duration cooldownDuration = Duration.ofSeconds(30);

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public Duration getCooldownDuration() {
        return cooldownDuration;
    }

    public void setCooldownDuration(Duration cooldownDuration) {
        this.cooldownDuration = cooldownDuration;
    }
}