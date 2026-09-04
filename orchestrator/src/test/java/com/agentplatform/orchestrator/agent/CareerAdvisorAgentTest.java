package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.gap.CareerImprovementPlan;
import com.agentplatform.orchestrator.gap.CareerImprovementPlanService;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.gap.GapSeverity;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link CareerAdvisorAgent} delegates to {@link CareerGapAnalysisService}
 * and {@link CareerImprovementPlanService}.
 */
@DisplayName("CareerAdvisorAgent — delegates to gap + improvement plan services")
class CareerAdvisorAgentTest {

    private static CandidateProfile profile() {
        return new CandidateProfile("Alice", null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of("Java"), List.of(),
                List.of(), List.of());
    }

    private static Job job(String id) {
        return new Job(id, "Engineer", null, null, null, List.of("Java"), List.of(),
                null, null, null, "mock", null, null, null);
    }

    private static CareerGapAnalysis gap(String jobId) {
        return new CareerGapAnalysis(1L, jobId, List.of(), List.of(), List.of(), List.of(),
                new ExperienceGap(null, null, null, false),
                CareerTrack.SOFTWARE, CareerTrack.SOFTWARE, false,
                GapSeverity.NO_GAP, List.of());
    }

    @Test
    @DisplayName("delegates to gap analysis and improvement plan services")
    void delegatesToServices() {
        CareerGapAnalysisService gapSvc = mock(CareerGapAnalysisService.class);
        CareerImprovementPlanService planSvc = mock(CareerImprovementPlanService.class);
        CareerGapAnalysis analysis = gap("j1");
        CareerImprovementPlan plan = new CareerImprovementPlan("j1", 1L, "summary",
                CareerImprovementPlan.ORIGIN_DETERMINISTIC, List.of());
        when(gapSvc.analyze(profile(), job("j1"))).thenReturn(analysis);
        when(planSvc.generatePlan(analysis)).thenReturn(plan);

        CareerAdvisorAgent agent = new CareerAdvisorAgent(gapSvc, planSvc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        assertTrue(agent.canExecute(ctx));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.CAREER_ADVISOR), ctx);

        assertTrue(result.success());
        verify(gapSvc).analyze(profile(), job("j1"));
        verify(planSvc).generatePlan(analysis);
        assertTrue(ctx.careerGapAnalysis() == analysis);
        assertTrue(ctx.improvementPlan() == plan);
    }

    @Test
    @DisplayName("improvement plan failure still completes with a deterministic gap result")
    void planFailureDoesNotFailAdvisor() {
        CareerGapAnalysisService gapSvc = mock(CareerGapAnalysisService.class);
        CareerImprovementPlanService planSvc = mock(CareerImprovementPlanService.class);
        CareerGapAnalysis analysis = gap("j1");
        when(gapSvc.analyze(profile(), job("j1"))).thenReturn(analysis);
        when(planSvc.generatePlan(analysis)).thenThrow(new RuntimeException("ollama down"));

        CareerAdvisorAgent agent = new CareerAdvisorAgent(gapSvc, planSvc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        AgentResult result = agent.execute(AgentRequest.of(AgentType.CAREER_ADVISOR), ctx);

        assertTrue(result.success(), "advisor should still succeed with a deterministic gap result");
        assertNull(ctx.improvementPlan());
        assertTrue(ctx.careerGapAnalysis() == analysis);
    }

    @Test
    @DisplayName("cannot execute without a candidate and a job")
    void cannotExecuteWithoutInput() {
        CareerAdvisorAgent agent = new CareerAdvisorAgent(
                mock(CareerGapAnalysisService.class), mock(CareerImprovementPlanService.class));
        assertFalse(agent.canExecute(new AgentContext()));

        AgentContext onlyProfile = new AgentContext();
        onlyProfile.setCandidateProfile(profile());
        assertFalse(agent.canExecute(onlyProfile));
    }

    @Test
    @DisplayName("gap analysis failure yields a FAILED result, never a throw")
    void gapFailureContained() {
        CareerGapAnalysisService gapSvc = mock(CareerGapAnalysisService.class);
        CareerImprovementPlanService planSvc = mock(CareerImprovementPlanService.class);
        when(gapSvc.analyze(profile(), job("j1"))).thenThrow(new RuntimeException("boom"));

        CareerAdvisorAgent agent = new CareerAdvisorAgent(gapSvc, planSvc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        AgentResult result = agent.execute(AgentRequest.of(AgentType.CAREER_ADVISOR), ctx);

        assertFalse(result.success());
        assertEquals(AgentStatus.FAILED, result.status());
        verify(planSvc, never()).generatePlan(org.mockito.ArgumentMatchers.any());
    }
}
