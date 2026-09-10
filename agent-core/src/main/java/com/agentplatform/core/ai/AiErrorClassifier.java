package com.agentplatform.core.ai;

/**
 * Classifies AI request failures into safe, actionable diagnostics so the UI
 * can tell the difference between a quota hit, an authentication failure, an
 * unsupported model, a timeout, and a network problem — instead of the generic
 * "check your API key" message.
 *
 * <p>The LangChain4j Google AI integration surfaces HTTP failures as a plain
 * {@link RuntimeException} whose message looks like
 * {@code "HTTP error (429): {google-json}"}, so classification is based on the
 * HTTP status code embedded in the message plus the Google RPC status names in
 * the JSON body. API keys are never leaked into any diagnostic.</p>
 *
 * <p>A provider label can be supplied (e.g. {@code "Ollama"} for local models,
 * {@code "Gemini"} for Google AI Studio) so messages name the backend that
 * actually failed. When omitted the label defaults to {@code "Ollama"} (the
 * default active backend of the app).</p>
 */
public final class AiErrorClassifier {

    /**
     * Matches the wording local model servers actually use when a tag has not been
     * pulled, e.g. Ollama's {@code model 'gemma3:4b' not found}. The literal needles in
     * {@link #classify} do not match this because the model name sits between
     * "model" and "not found".
     */
    private static final java.util.regex.Pattern MODEL_NOT_FOUND = java.util.regex.Pattern
            .compile("model\\s*['\"][^'\"]*['\"]?\\s*(?:not found|does not exist)",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    public enum Kind {
        AUTHENTICATION,
        QUOTA,
        MODEL_UNAVAILABLE,
        NETWORK,
        TIMEOUT,
        GENERIC
    }

    /** A classified failure: a {@link Kind} plus a safe, user-facing message. */
    public record Failure(Kind kind, String message) {
    }

    /**
     * Classifies {@code error} using the default provider label ("Ollama").
     * Never returns {@code null}.
     */
    public static Failure classify(Throwable error) {
        return classify(error, null);
    }

    /**
     * Classifies {@code error} naming {@code provider} in the message.
     * Never returns {@code null}.
     */
    public static Failure classify(Throwable error, String provider) {
        Throwable root = rootCause(error);
        String text = combinedMessage(error, root);

        int status = extractHttpStatus(text);
        boolean quota = containsAny(text, "RESOURCE_EXHAUSTED", "quota", "rate limit", "rate_limit", "429");
        boolean auth = containsAny(text, "PERMISSION_DENIED", "UNAUTHENTICATED", "API key not valid",
                "api key invalid", "API_KEY_INVALID", "401", "403");
        boolean modelGone = containsAny(text, "NOT_FOUND", "model not found", "model does not exist",
                "does not exist for model", "models/")
                // Ollama words it differently and has no HTTP status in the body, so the
                // literal needles above miss it and it used to fall through to GENERIC —
                // which leaked the raw {"error":"model '...' not found"} body to the user.
                || MODEL_NOT_FOUND.matcher(text == null ? "" : text).find();
        boolean timedOut = containsAny(text, "timed out", "timeout", "SocketTimeoutException",
                "HttpConnectTimeoutException", "ReadTimeout", "read timeout");

        // Check for timeout first (before network) so timeouts get their own kind
        if (timedOut || root instanceof java.net.SocketTimeoutException
                || root instanceof java.net.http.HttpConnectTimeoutException
                || root instanceof java.net.http.HttpTimeoutException) {
            return new Failure(Kind.TIMEOUT, timeoutMessage(provider, text));
        }
        if (root instanceof java.net.UnknownHostException
                || root instanceof java.net.ConnectException
                || (root instanceof java.io.IOException && status == 0)
                || containsAny(text, "UnknownHost", "connection refused", "ConnectException")) {
            return new Failure(Kind.NETWORK, networkMessage(provider, text));
        }
        if (auth && !quota) {
            return new Failure(Kind.AUTHENTICATION, authenticationMessage(provider, status, text));
        }
        if (quota) {
            return new Failure(Kind.QUOTA, quotaMessage(provider, text));
        }
        if (modelGone || status == 404) {
            return new Failure(Kind.MODEL_UNAVAILABLE, modelMessage(provider, status, text));
        }
        if (status == 400 && containsAny(text, "INVALID_ARGUMENT")
                && containsAny(text, "model")) {
            return new Failure(Kind.MODEL_UNAVAILABLE, modelMessage(provider, status, text));
        }
        return new Failure(Kind.GENERIC, genericMessage(provider, text));
    }

    // ─── Message builders ─────────────────────────────────────────────────────

    private static boolean isGemini(String provider) {
        return provider != null && "Gemini".equalsIgnoreCase(provider);
    }

    private static String providerLabel(String provider) {
        return (provider == null || provider.isBlank()) ? "Ollama" : provider;
    }

    private static String authenticationMessage(String provider, int status, String text) {
        if (isGemini(provider)) {
            return "Gemini authentication failed"
                    + (status > 0 ? " (HTTP " + status + ")" : "")
                    + ". The API key was rejected — check that GEMINI_API_KEY is set to a valid "
                    + "Google AI Studio key for this project.";
        }
        return providerLabel(provider) + " authentication failed"
                + (status > 0 ? " (HTTP " + status + ")" : "")
                + ". The AI provider rejected the request credentials.";
    }

    private static String quotaMessage(String provider, String text) {
        if (isGemini(provider)) {
            String detail = googleMessage(text);
            return "Gemini quota exceeded (HTTP 429): the free-tier daily request limit was reached."
                    + (detail != null ? " " + detail : "")
                    + " Retry later or upgrade the Google AI plan.";
        }
        return providerLabel(provider) + " quota exceeded (HTTP 429): a request limit was reached. "
                + "Retry later or increase the limit.";
    }

    private static String modelMessage(String provider, int status, String text) {
        if (isGemini(provider)) {
            return "Gemini model unavailable"
                    + (status > 0 ? " (HTTP " + status + ")" : "")
                    + ". The configured chat model is not supported by the current Gemini API. "
                    + "Verify the gemini.chat-model setting.";
        }
        return providerLabel(provider) + " model unavailable"
                + (status > 0 ? " (HTTP " + status + ")" : "")
                + ". The requested model is not installed or not supported by the model server. "
                + "Verify the model selection (e.g. `ollama pull <model>`).";
    }

    private static String networkMessage(String provider, String text) {
        if (isGemini(provider)) {
            String detail = googleMessage(text);
            return "Network/API failure reaching Google Gemini."
                    + (detail != null ? " " + detail : "")
                    + " Check your internet connection and try again.";
        }
        return "Could not reach " + providerLabel(provider) + " at http://localhost:11434. "
                + "Check that the Ollama server is running and that the selected model is pulled, "
                + "then try again.";
    }

    private static String timeoutMessage(String provider, String text) {
        if (isGemini(provider)) {
            return "Gemini request timed out. The model took too long to respond. "
                    + "Check your internet connection and try again.";
        }
        return providerLabel(provider) + " request timed out. The model took too long to respond "
                + "(cold-start model loads on CPU can be slow). "
                + "Check that Ollama is running and the model is pulled, then try again. "
                + "If this persists, consider increasing the ollama.reasoning-timeout setting.";
    }

    /**
     * Safe catch-all message. Deliberately does NOT embed the provider's raw response
     * body: an unclassified provider failure can carry arbitrary JSON (model names,
     * internal endpoints, quota payloads) that must never reach a browser. The technical
     * detail stays in the server log, where the calling code logs the throwable itself.
     */
    private static String genericMessage(String provider, String text) {
        return providerLabel(provider) + " AI request failed. The request could not be completed, "
                + "so the built-in fallback processing was used instead. "
                + "Check that the AI provider is running and reachable, then try again.";
    }

    // ─── Extraction helpers ───────────────────────────────────────────────────

    private static int extractHttpStatus(String text) {
        if (text == null) {
            return 0;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("HTTP\\s*error\\s*\\(?\\s*(\\d{3})\\s*\\)?")
                .matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        m = java.util.regex.Pattern.compile("\"code\"\\s*:\\s*(\\d{3})").matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Pulls the human-readable Google error message out of the JSON body. */
    private static String googleMessage(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"message\"\\s*:\\s*\"([^\"]{1,260})")
                .matcher(text);
        if (m.find()) {
            String msg = m.group(1);
            return msg.length() > 240 ? msg.substring(0, 240) + "…" : msg;
        }
        if (!text.startsWith("HTTP error") || text.isBlank()) {
            return null;
        }
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }

    private static String combinedMessage(Throwable top, Throwable root) {
        StringBuilder sb = new StringBuilder(400);
        if (top != null && top.getMessage() != null) {
            sb.append(top.getMessage()).append(' ');
        }
        if (root != null && root != top && root.getMessage() != null) {
            sb.append(root.getMessage()).append(' ');
        }
        return sb.toString();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        Throwable root = t;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        return root;
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null) {
            return false;
        }
        for (String needle : needles) {
            if (text.toLowerCase().contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private AiErrorClassifier() {
        throw new AssertionError("Utility class");
    }
}