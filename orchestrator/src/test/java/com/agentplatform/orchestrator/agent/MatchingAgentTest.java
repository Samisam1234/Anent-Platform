package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchRequest;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingService;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link MatchingAgent} delegates to {@link JobMatchingService}.
 */
@DisplayName("MatchingAgent — delegates to JobMatchingService")
class MatchingAgentTest {

    private static CandidateProfile profile() {
        return new CandidateProfile("Alice", null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of("Java"), List.of(),
                List.of(), List.of());
    }

    private static Job job(String id) {
        return new Job(id, "Engineer", null, null, null, List.of(), List.of(),
                null, null, null, "mock", null, null, null);
    }

    @Test
    @DisplayName("matches supplied jobs through the service")
    void delegatesMatchingForSelectedJob() {
        JobMatchingService svc = mock(JobMatchingService.class);
        Job j = job("j1");
        JobMatch match = new JobMatch(j, 90, RecommendationLevel.STRONG_MATCH,
                List.of(), List.of(), List.of(), List.of(), true, true,
                null, null, "e", List.of(), List.of(), 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        JobMatchResult matchResult = new JobMatchResult(1L, "Alice", 1, List.of(match), "x", false, "ok");
        when(svc.matchJobs(any(JobMatchRequest.class))).thenReturn(matchResult);

        MatchingAgent agent = new MatchingAgent(svc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateId(1L);
        ctx.setCandidateProfile(profile());
        ctx.setJob(j);

        assertTrue(agent.canExecute(ctx));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.MATCHING), ctx);

        assertTrue(result.success());
        verify(svc).matchJobs(any(JobMatchRequest.class));
        assertTrue(ctx.jobMatchResult() == matchResult);
    }

    @Test
    @DisplayName("matches the job catalog when no single job is selected")
    void delegatesForCatalog() {
        JobMatchingService svc = mock(JobMatchingService.class);
        JobSearchResult searchResult = new JobSearchResult(List.of(job("j1"), job("j2")), 2, "mock", false, "ok");
        JobMatchResult matchResult = new JobMatchResult(null, "Alice", 2, List.of(), "mock", false, "ok");
        when(svc.matchJobs(any(JobMatchRequest.class))).thenReturn(matchResult);

        MatchingAgent agent = new MatchingAgent(svc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJobSearchResult(searchResult);

        AgentResult result = agent.execute(AgentRequest.of(AgentType.MATCHING), ctx);

        assertTrue(result.success());
        verify(svc).matchJobs(any(JobMatchRequest.class));
    }

    @Test
    @DisplayName("cannot execute without a candidate profile or any jobs")
    void cannotExecuteWithoutInput() {
        MatchingAgent agent = new MatchingAgent(mock(JobMatchingService.class));
        assertFalse(agent.canExecute(new AgentContext()));

        AgentContext onlyProfile = new AgentContext();
        onlyProfile.setCandidateProfile(profile());
        assertFalse(agent.canExecute(onlyProfile));
    }

    @Test
    @DisplayName("matching failure yields a non-successful result, never a throw")
    void failureContained() {
        JobMatchingService svc = mock(JobMatchingService.class);
        when(svc.matchJobs(any(JobMatchRequest.class))).thenThrow(new RuntimeException("boom"));

        MatchingAgent agent = new MatchingAgent(svc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        AgentResult result = agent.execute(AgentRequest.of(AgentType.MATCHING), ctx);

        assertFalse(result.success());
        assertEquals(AgentStatus.FAILED, result.status());
    }
}
