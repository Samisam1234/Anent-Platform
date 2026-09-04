package com.agentplatform.orchestrator.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link AgentToolRequest} validation and {@link AgentToolResult}
 * structured behavior.
 */
@DisplayName("AgentTool models — typed request/result")
class AgentToolModelsTest {

    @Test
    @DisplayName("null agentType is rejected")
    void nullAgentTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentToolRequest(null, "generateImage", Map.of(), "reason"));
    }

    @Test
    @DisplayName("blank tool name is rejected")
    void blankToolRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentToolRequest(AgentType.RESUME, "  ", Map.of(), "reason"));
    }

    @Test
    @DisplayName("arguments are defensively copied and normalized")
    void argumentsCopied() {
        Map<String, Object> args = new HashMap<>();
        args.put("prompt", "robot");
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage", args, "need an image");
        args.put("prompt", "mutated");
        assertEquals("robot", req.arguments().get("prompt"), "constructed request must not reflect later mutation");
        assertEquals("need an image", req.reason());
        assertEquals("generateImage", req.toolName());
        assertEquals(AgentType.RESUME, req.agentType());
    }

    @Test
    @DisplayName("null arguments normalize to empty map")
    void nullArgumentsNormalized() {
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage", null);
        assertNotNull(req.arguments());
        assertTrue(req.arguments().isEmpty());
        assertNull(req.validationError());
    }

    @Test
    @DisplayName("oversized argument is flagged")
    void oversizedArgumentFlagged() {
        String huge = "x".repeat(AgentToolRequest.MAX_ARGUMENT_LENGTH + 1);
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage",
                Map.of("prompt", huge), "reason");
        assertEquals("ARGUMENT_TOO_LARGE", req.validationError());
    }

    @Test
    @DisplayName("too many arguments are flagged")
    void tooManyArgumentsFlagged() {
        Map<String, Object> args = new HashMap<>();
        for (int i = 0; i <= AgentToolRequest.MAX_ARGUMENT_COUNT; i++) {
            args.put("key" + i, "v");
        }
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage", args, "r");
        assertEquals("TOO_MANY_ARGUMENTS", req.validationError());
    }

    @Test
    @DisplayName("success result factories")
    void successResult() {
        AgentToolResult r = AgentToolResult.success("generateImage", "done", "<img>");
        assertTrue(r.success());
        assertEquals("generateImage", r.toolName());
        assertEquals("<img>", r.output());
        assertNull(r.errorCode());
        assertEquals("done", r.message());
    }

    @Test
    @DisplayName("failure result carries a stable error code and no output leakage")
    void failureResult() {
        AgentToolResult r = AgentToolResult.failure("sendEmail", "denied", AgentToolResult.ERR_TOOL_DENIED);
        assertFalse(r.success());
        assertEquals(AgentToolResult.ERR_TOOL_DENIED, r.errorCode());
        assertEquals("", r.output());
    }
}
