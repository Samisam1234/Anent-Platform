package com.agentplatform.core.ai;

/**
 * Payload of {@code GET /api/v1/ai/status}.
 *
 * <ul>
 *   <li>{@code provider} — the active backend ({@code "Ollama"} by default,
 *       {@code "Google Gemini"} only when explicitly enabled).</li>
 *   <li>{@code configured} — whether the provider is configured/active.</li>
 *   <li>{@code available} — result of the last live probe ({@code null} until a
 *       probe runs via {@code ?probe=true}).</li>
 *   <li>{@code checkedAt} — epoch millis of that probe ({@code null} if never).</li>
 * </ul>
 *
 * The API key itself is never returned.
 */
public record AiStatusResponse(
        String provider,
        boolean configured,
        Boolean available,
        String model,
        String message,
        Long checkedAt) {

    public static AiStatusResponse unconfigured(String provider, String model) {
        String verb = provider.toLowerCase().contains("gemini") ? "No Gemini API key configured"
                : "Ollama is the active provider";
        return new AiStatusResponse(provider, false, null, model,
                verb + ". Call with ?probe=true to run a live probe.", null);
    }

    public static AiStatusResponse unverified(String provider, String model) {
        return new AiStatusResponse(provider, true, null, model,
                provider + " is configured; live availability not verified yet "
                        + "(call with ?probe=true to run a live probe).", null);
    }

    public static AiStatusResponse verified(String provider, String model, boolean available,
                                            String message, Long checkedAt) {
        return new AiStatusResponse(provider, true, available, model, message, checkedAt);
    }
}