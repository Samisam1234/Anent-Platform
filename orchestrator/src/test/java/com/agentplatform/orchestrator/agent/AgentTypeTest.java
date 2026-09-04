package com.agentplatform.orchestrator.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the logical agent types in the career pipeline.
 */
@DisplayName("AgentType — logical agent types")
class AgentTypeTest {

    @Test
    @DisplayName("all five agent types are present")
    void allFiveTypesPresent() {
        assertTrue(AgentType.values().length >= 5);
        assertEquals(AgentType.RESUME, AgentType.valueOf("RESUME"));
        assertEquals(AgentType.JOB_DISCOVERY, AgentType.valueOf("JOB_DISCOVERY"));
        assertEquals(AgentType.MATCHING, AgentType.valueOf("MATCHING"));
        assertEquals(AgentType.CAREER_ADVISOR, AgentType.valueOf("CAREER_ADVISOR"));
        assertEquals(AgentType.APPLICATION_ADVISOR, AgentType.valueOf("APPLICATION_ADVISOR"));
    }

    @Test
    @DisplayName("agent types are distinct")
    void distinctTypes() {
        assertEquals(5, java.util.Arrays.stream(AgentType.values()).distinct().count());
    }
}
