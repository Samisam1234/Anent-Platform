package com.agentplatform.orchestrator.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the finite execution status set for agents.
 */
@DisplayName("AgentStatus — finite execution status set")
class AgentStatusTest {

    @Test
    @DisplayName("all expected status values are present")
    void valuesPresent() {
        assertTrue(AgentStatus.values().length >= 5);
        assertEquals(AgentStatus.PENDING, AgentStatus.valueOf("PENDING"));
        assertEquals(AgentStatus.RUNNING, AgentStatus.valueOf("RUNNING"));
        assertEquals(AgentStatus.COMPLETED, AgentStatus.valueOf("COMPLETED"));
        assertEquals(AgentStatus.FAILED, AgentStatus.valueOf("FAILED"));
        assertEquals(AgentStatus.SKIPPED, AgentStatus.valueOf("SKIPPED"));
    }

    @Test
    @DisplayName("there is no retry or looping state")
    void noRetryOrLoopState() {
        for (AgentStatus s : AgentStatus.values()) {
            assertTrue(!s.name().contains("RETRY") && !s.name().contains("LOOP"),
                    "Unexpected lifecycle state: " + s);
        }
    }
}
