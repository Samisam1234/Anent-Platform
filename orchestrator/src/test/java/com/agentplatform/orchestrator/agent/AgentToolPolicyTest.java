package com.agentplatform.orchestrator.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the deterministic {@link AgentToolPolicy} access-control rules.
 */
@DisplayName("AgentToolPolicy — deterministic allowed-tool rules")
class AgentToolPolicyTest {

    private final AgentToolPolicy policy = new AgentToolPolicy();

    @Test
    @DisplayName("known allowed tool for RESUME is permitted (generateImage)")
    void knownAllowedToolPermitted() {
        assertTrue(policy.isAllowed(AgentType.RESUME, AgentToolPolicy.TOOL_GENERATE_IMAGE));
    }

    @Test
    @DisplayName("denied tool for every agent")
    void emailDeniedForAllAgents() {
        for (AgentType type : AgentType.values()) {
            assertFalse(policy.isAllowed(type, AgentToolPolicy.TOOL_SEND_EMAIL),
                    "sendEmail must be denied for " + type);
        }
    }

    @Test
    @DisplayName("unknown tool denied")
    void unknownToolDenied() {
        assertFalse(policy.isAllowed(AgentType.RESUME, "deleteFile"));
        assertFalse(policy.isAllowed(AgentType.RESUME, ""));
        assertFalse(policy.isAllowed(AgentType.RESUME, null));
    }

    @Test
    @DisplayName("unknown agent denied")
    void unknownAgentDenied() {
        assertFalse(policy.isAllowed(null, AgentToolPolicy.TOOL_GENERATE_IMAGE));
        assertFalse(policy.isAllowed(null, AgentToolPolicy.TOOL_SEND_EMAIL));
    }

    @Test
    @DisplayName("structural + advisor agents have no external tools")
    void structuralAgentsNoTools() {
        assertFalse(policy.isAllowed(AgentType.JOB_DISCOVERY, AgentToolPolicy.TOOL_GENERATE_IMAGE));
        assertFalse(policy.isAllowed(AgentType.MATCHING, AgentToolPolicy.TOOL_GENERATE_IMAGE));
        assertFalse(policy.isAllowed(AgentType.CAREER_ADVISOR, AgentToolPolicy.TOOL_GENERATE_IMAGE));
        assertFalse(policy.isAllowed(AgentType.APPLICATION_ADVISOR, AgentToolPolicy.TOOL_GENERATE_IMAGE));
        assertFalse(policy.isAllowed(AgentType.JOB_DISCOVERY, AgentToolPolicy.TOOL_SEND_WHATSAPP));
    }

    @Test
    @DisplayName("repeated denied requests are deterministic")
    void denialsAreDeterministic() {
        for (int i = 0; i < 5; i++) {
            assertFalse(policy.isAllowed(AgentType.MATCHING, AgentToolPolicy.TOOL_SEND_EMAIL));
            assertFalse(policy.isAllowed(AgentType.CAREER_ADVISOR, AgentToolPolicy.TOOL_SEND_EMAIL));
            assertFalse(policy.isAllowed(AgentType.RESUME, "unknownTool"));
            assertTrue(policy.isAllowed(AgentType.RESUME, AgentToolPolicy.TOOL_GENERATE_IMAGE));
        }
    }
}
