package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.service.AgentChatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Phase 8.3 RFC 7807 logging contract for {@link GlobalExceptionHandler}:
 * error logs carry useful status/type metadata and never expose raw request
 * body content.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("GlobalExceptionHandler — RFC7807 logging (Phase 8.3)")
class GlobalExceptionHandlerLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ─── F1. AgentChatException logs status and type ────────────────────────

    @Test
    @DisplayName("AgentChatException log includes status=503 and model-unavailable type")
    void agentChatExceptionLogContainsStatusAndType(CapturedOutput output) {
        handler.handleAgentChatException(new AgentChatException("Ollama unavailable"));

        assertTrue(output.getAll().contains("status=503"));
        assertTrue(output.getAll().contains("model-unavailable"));
    }

    // ─── F2. Unreadable body handler does not leak request content ──────────

    @Test
    @DisplayName("unreadable request body log does not contain the secret payload")
    void unreadableBodyDoesNotLeakRequestContent(CapturedOutput output) {
        String secret = "sensitive-body-" + UUID.randomUUID();
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error at line 1: " + secret,
                new MockHttpInputMessage(new byte[0]));

        handler.handleUnreadableBody(ex);

        assertTrue(output.getAll().contains("status=400"));
        assertTrue(output.getAll().contains("bad-request"));
        assertFalse(output.getAll().contains(secret),
                "the raw request content must not appear in the error log");
    }

    // ─── F3. IllegalArgumentException logs status and type ──────────────────

    @Test
    @DisplayName("IllegalArgument log includes status=400 and bad-request type")
    void illegalArgumentLogContainsStatusAndType(CapturedOutput output) {
        handler.handleIllegalArgument(new IllegalArgumentException("blank query"));

        assertTrue(output.getAll().contains("status=400"));
        assertTrue(output.getAll().contains("bad-request"));
    }
}