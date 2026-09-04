package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchRequest;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.job.JobSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link JobDiscoveryAgent} delegates to {@link JobSearchService}.
 */
@DisplayName("JobDiscoveryAgent — delegates to JobSearchService")
class JobDiscoveryAgentTest {

    private static Job job(String id) {
        return new Job(id, "Engineer", null, null, null, List.of(), List.of(),
                null, null, null, "mock", null, null, null);
    }

    @Test
    @DisplayName("searches jobs through the service")
    void delegatesSearch() {
        JobSearchService svc = mock(JobSearchService.class);
        JobSearchResult searchResult = new JobSearchResult(List.of(job("j1")), 1, "mock", false, "ok");
        when(svc.search(any(JobSearchRequest.class))).thenReturn(searchResult);

        JobDiscoveryAgent agent = new JobDiscoveryAgent(svc);
        AgentContext ctx = new AgentContext();

        AgentResult result = agent.execute(AgentRequest.of(AgentType.JOB_DISCOVERY), ctx);

        assertTrue(result.success());
        verify(svc).search(any(JobSearchRequest.class));
        assertTrue(ctx.jobSearchResult() == searchResult);
    }

    @Test
    @DisplayName("reuses an already-selected job without searching")
    void reusesSelectedJob() {
        JobSearchService svc = mock(JobSearchService.class);
        Job j = job("j9");

        JobDiscoveryAgent agent = new JobDiscoveryAgent(svc);
        AgentContext ctx = new AgentContext();
        ctx.setJob(j);

        AgentResult result = agent.execute(AgentRequest.of(AgentType.JOB_DISCOVERY), ctx);

        assertTrue(result.success());
        verify(svc, never()).search(any(JobSearchRequest.class));
        assertEquals("j9", ctx.job().id());
    }

    @Test
    @DisplayName("search failure yields a non-successful result")
    void searchFailureContained() {
        JobSearchService svc = mock(JobSearchService.class);
        when(svc.search(any(JobSearchRequest.class))).thenThrow(new RuntimeException("boom"));

        JobDiscoveryAgent agent = new JobDiscoveryAgent(svc);
        AgentContext ctx = new AgentContext();

        AgentResult result = agent.execute(AgentRequest.of(AgentType.JOB_DISCOVERY), ctx);

        assertFalse(result.success());
        verify(svc).search(any(JobSearchRequest.class));
    }
}
