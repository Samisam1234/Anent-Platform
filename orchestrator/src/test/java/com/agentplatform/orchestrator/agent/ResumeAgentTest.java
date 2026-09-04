package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ResumeAgent} delegates to the existing
 * {@link ResumeProfileService} and reuses an already-present profile.
 */
@DisplayName("ResumeAgent — delegates to ResumeProfileService")
class ResumeAgentTest {

    private static CandidateProfile profile(String name) {
        return new CandidateProfile(name, null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    @Test
    @DisplayName("builds profile from resume text via the service")
    void delegatesAndBuildsProfile() {
        ResumeProfileService svc = mock(ResumeProfileService.class);
        CandidateProfile built = profile("Alice");
        when(svc.buildProfile(anyString())).thenReturn(built);

        ResumeAgent agent = new ResumeAgent(svc);
        AgentContext ctx = new AgentContext();
        ctx.setResumeText("Real resume text with enough characters to parse.");

        assertTrue(agent.canExecute(ctx));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.RESUME), ctx);

        assertTrue(result.success());
        verify(svc).buildProfile("Real resume text with enough characters to parse.");
        assertTrue(ctx.candidateProfile() == built);
    }

    @Test
    @DisplayName("reuses an existing profile without re-parsing")
    void reusesExistingProfile() {
        ResumeProfileService svc = mock(ResumeProfileService.class);
        CandidateProfile existing = profile("Bob");

        ResumeAgent agent = new ResumeAgent(svc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(existing);

        AgentResult result = agent.execute(AgentRequest.of(AgentType.RESUME), ctx);

        assertTrue(result.success());
        verify(svc, never()).buildProfile(anyString());
        assertTrue(ctx.candidateProfile() == existing);
    }

    @Test
    @DisplayName("service failure yields a FAILED result, never a throw")
    void failureIsContained() {
        ResumeProfileService svc = mock(ResumeProfileService.class);
        when(svc.buildProfile(anyString())).thenThrow(new RuntimeException("boom"));

        ResumeAgent agent = new ResumeAgent(svc);
        AgentContext ctx = new AgentContext();
        ctx.setResumeText("Some resume text that is long enough to parse.");

        AgentResult result = agent.execute(AgentRequest.of(AgentType.RESUME), ctx);

        assertFalse(result.success());
        assertEquals(AgentStatus.FAILED, result.status());
        verify(svc).buildProfile(anyString());
    }

    @Test
    @DisplayName("cannot execute without a profile or resume text")
    void cannotExecuteWithoutInput() {
        ResumeAgent agent = new ResumeAgent(mock(ResumeProfileService.class));
        assertFalse(agent.canExecute(new AgentContext()));
        assertNull(new AgentContext().candidateProfile());
    }
}
