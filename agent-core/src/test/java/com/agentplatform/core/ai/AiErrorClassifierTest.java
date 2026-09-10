package com.agentplatform.core.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiErrorClassifier}. The real messages below mirror what
 * the LangChain4j Google AI client throws (plain RuntimeException whose message
 * is {@code "HTTP error (<status>): {google-json}"}).
 */
class AiErrorClassifierTest {

    @Test
    @DisplayName("HTTP 429 / RESOURCE_EXHAUSTED classifies as quota")
    void quota_classifiedAsQuota() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(new RuntimeException(
                "HTTP error (429): { \"error\": { \"code\": 429, \"message\": "
                        + "\"You exceeded your current quota. Quota exceeded for metric: "
                        + "generativelanguage.googleapis.com/generate_content_free_tier_requests, "
                        + "limit: 20, model: gemini-3.6-flash\", "
                        + "\"status\": \"RESOURCE_EXHAUSTED\" } }"));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.QUOTA);
        assertThat(failure.message()).contains("quota").contains("429")
                .doesNotContain("API key");
    }

    @Test
    @DisplayName("HTTP 401 / UNAUTHENTICATED classifies as authentication")
    void unauthenticated_classifiesAsAuth() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(new RuntimeException(
                "HTTP error (401): { \"error\": { \"code\": 401, \"status\": \"UNAUTHENTICATED\", "
                        + "\"message\": \"Request had invalid authentication credentials.\" } }"),
                "Gemini");

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.AUTHENTICATION);
        assertThat(failure.message()).contains("authentication").contains("GEMINI_API_KEY");
    }

    @Test
    @DisplayName("HTTP 403 / PERMISSION_DENIED classifies as authentication")
    void permissionDenied_classifiesAsAuth() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(new RuntimeException(
                "HTTP error (403): { \"error\": { \"code\": 403, \"status\": \"PERMISSION_DENIED\", "
                        + "\"message\": \"Permission denied.\" } }"));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.AUTHENTICATION);
    }

    @Test
    @DisplayName("HTTP 404 / NOT_FOUND with model name classifies as model unavailable")
    void notFound_modelClassifiesAsModelUnavailable() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(new RuntimeException(
                "HTTP error (404): { \"error\": { \"code\": 404, \"status\": \"NOT_FOUND\", "
                        + "\"message\": \"models/gemini-3.6-flash is not found for API version v1beta\" } }"),
                "Gemini");

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.MODEL_UNAVAILABLE);
        assertThat(failure.message()).contains("model").contains("gemini.chat-model");
    }

    @Test
    @DisplayName("SocketTimeoutException cause classifies as timeout")
    void socketTimeout_classifiesAsTimeout() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("RetryUtils: request failed", new SocketTimeoutException("Read timed out")));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.TIMEOUT);
        assertThat(failure.message()).contains("timed out");
    }

    @Test
    @DisplayName("HttpConnectTimeoutException cause classifies as timeout")
    void httpConnectTimeout_classifiesAsTimeout() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("connect failed", new HttpConnectTimeoutException("Connect timed out")));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.TIMEOUT);
    }

    @Test
    @DisplayName("HttpTimeoutException cause classifies as timeout")
    void httpTimeout_classifiesAsTimeout() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("read failed", new HttpTimeoutException("Read timed out")));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.TIMEOUT);
    }

    @Test
    @DisplayName("message containing 'timed out' classifies as timeout")
    void timedOutMessage_classifiesAsTimeout() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("Request timed out after 120000ms"));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.TIMEOUT);
    }

    @Test
    @DisplayName("message containing 'timeout' classifies as timeout")
    void timeoutMessage_classifiesAsTimeout() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("Request timeout exceeded"));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.TIMEOUT);
    }

    @Test
    @DisplayName("UnknownHostException cause classifies as network")
    void unknownHost_classifiesAsNetwork() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException(new UnknownHostException("generativelanguage.googleapis.com")));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.NETWORK);
    }

    @Test
    @DisplayName("ConnectException cause classifies as network")
    void connectException_classifiesAsNetwork() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("connect failed", new ConnectException("Connection refused")));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.NETWORK);
    }

    @Test
    @DisplayName("generic IOException without HTTP code is treated as generics/network-safe")
    void plainIOException_notCrashed() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException(new IOException("something odd")));

        assertThat(failure.message()).isNotBlank();
    }

    @Test
    @DisplayName("unknown exception falls back to generic with a useful message")
    void unknown_classifiesAsGeneric() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("mystery boom"));

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.GENERIC);
        // The raw exception text must never be echoed: GENERIC messages reach the
        // browser, and an unclassified provider body can carry arbitrary internals.
        assertThat(failure.message()).isNotBlank().doesNotContain("mystery boom");
    }

    @Test
    @DisplayName("null error is safe")
    void nullError_safe() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(null);
        assertThat(failure.message()).isNotBlank();
    }

    @Test
    @DisplayName("non-Gemini provider network failure names the local model server")
    void ollama_connectException_mentionsServer() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("connect failed", new ConnectException("Connection refused")), "Ollama");

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.NETWORK);
        assertThat(failure.message()).contains("Ollama").contains("11434");
    }

    @Test
    @DisplayName("non-Gemini provider timeout failure names the local model server")
    void ollama_timeout_mentionsServer() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("connect failed", new SocketTimeoutException("Read timed out")), "Ollama");

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.TIMEOUT);
        assertThat(failure.message()).contains("Ollama").contains("timed out");
    }

    @Test
    @DisplayName("non-Gemini provider generic failure is labelled with the provider")
    void ollama_genericFailure_labeledWithProvider() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("mystery boom"), "Ollama");

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.GENERIC);
        assertThat(failure.message()).contains("Ollama").doesNotContain("mystery boom");
    }

    @Test
    @DisplayName("default provider label is Ollama (the app's active backend)")
    void defaultProvider_isOllama() {
        AiErrorClassifier.Failure failure = AiErrorClassifier.classify(
                new RuntimeException("mystery boom"));

        assertThat(failure.message()).contains("Ollama").doesNotContain("mystery boom");
    }

    /**
     * Regression: Ollama reports an unpulled model as {@code model 'gemma3:4b' not found}
     * with no HTTP status in the body. That wording used to miss every MODEL_UNAVAILABLE
     * needle, fall through to GENERIC, and surface the raw provider JSON on the Resume
     * page as "Profile created - AI model was not used {"error":"model 'gemma3:4b' not found"}".
     */
    @Test
    @DisplayName("Ollama unpulled model is MODEL_UNAVAILABLE and never leaks the raw JSON body")
    void ollama_modelNotFound_isModelUnavailableWithoutRawBody() {
        String rawBody = "{\"error\":\"model 'gemma3:4b' not found\"}";

        AiErrorClassifier.Failure failure =
                AiErrorClassifier.classify(new RuntimeException(rawBody), "Ollama");

        assertThat(failure.kind()).isEqualTo(AiErrorClassifier.Kind.MODEL_UNAVAILABLE);
        assertThat(failure.message())
                .contains("Ollama")
                .doesNotContain("{")
                .doesNotContain("}")
                .doesNotContain("\"error\"")
                .doesNotContain("gemma3");
    }

    @Test
    @DisplayName("no classifier message ever contains provider JSON syntax")
    void noMessage_containsProviderJson() {
        String[] rawBodies = {
            "{\"error\":\"model 'gemma3:4b' not found\"}",
            "HTTP error (500): {\"error\":\"internal\",\"path\":\"/api/chat\"}",
            "{\"code\":503,\"message\":\"upstream unavailable\"}"
        };
        for (String body : rawBodies) {
            AiErrorClassifier.Failure failure =
                    AiErrorClassifier.classify(new RuntimeException(body), "Ollama");
            assertThat(failure.message())
                    .as("body=%s kind=%s", body, failure.kind())
                    .doesNotContain("{")
                    .doesNotContain("}")
                    .doesNotContain("\"error\"");
        }
    }
}