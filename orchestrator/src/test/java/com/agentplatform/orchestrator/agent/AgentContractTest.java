package com.agentplatform.orchestrator.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the core agent contract: {@link AgentRequest}, {@link AgentResult},
 * and {@link AgentContext} behaviors.
 */
@DisplayName("AgentContract — request, result and context validation")
class AgentContractTest {

    @Test
    @DisplayName("AgentRequest rejects a null agent type")
    void requestRejectsNullType() {
        assertThrows(IllegalArgumentException.class, () -> new AgentRequest(null));
    }

    @Test
    @DisplayName("AgentResult requires a type and status")
    void resultRequiresTypeAndStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResult(null, AgentStatus.COMPLETED, true, "m", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentResult(AgentType.RESUME, null, true, "m", null, null));
    }

    @Test
    @DisplayName("AgentResult exposes typed output reference")
    void resultExposesStructuredOutput() {
        String output = "data";
        AgentResult r = AgentResult.completed(AgentType.RESUME, "ok", output);
        assertTrue(r.success());
        assertEquals(AgentStatus.COMPLETED, r.status());
        assertEquals("data", r.outputAs(String.class));
        assertNull(r.errorCode());
    }

    @Test
    @DisplayName("AgentResult failed result is non-successful with error code")
    void failedResultNonSuccessful() {
        AgentResult r = AgentResult.failed(AgentType.MATCHING, "boom", "MATCHING_FAILED");
        assertFalse(r.success());
        assertEquals(AgentStatus.FAILED, r.status());
        assertEquals("MATCHING_FAILED", r.errorCode());
        assertNull(r.output());
    }

    @Test
    @DisplayName("AgentContext records and retrieves agent results")
    void contextRecordsResults() {
        AgentContext ctx = new AgentContext();
        ctx.record(AgentResult.completed(AgentType.RESUME, "ok", "p"));
        ctx.record(AgentResult.failed(AgentType.MATCHING, "boom", "MATCHING_FAILED"));
        assertEquals(AgentStatus.COMPLETED, ctx.resultOf(AgentType.RESUME).status());
        assertEquals(AgentStatus.FAILED, ctx.resultOf(AgentType.MATCHING).status());
        assertFalse(ctx.allCompleted());
    }

    @Test
    @DisplayName("AgentContext carries candidate and job references without duplication")
    void contextReusesDomainModels() {
        com.agentplatform.orchestrator.resume.CandidateProfile profile =
                new com.agentplatform.orchestrator.resume.CandidateProfile(
                        "Alice", null, null, null, java.util.List.of(), java.util.List.of(),
                        java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
                        java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        com.agentplatform.orchestrator.job.Job job =
                new com.agentplatform.orchestrator.job.Job("j1", "Engineer", null, null, null,
                        java.util.List.of(), java.util.List.of(), null, null, null, null, null, null, null);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile);
        ctx.setJob(job);
        assertTrue(ctx.candidateProfile() == profile, "profile reference reused, not duplicated");
        assertTrue(ctx.job() == job, "job reference reused, not duplicated");
    }
}
