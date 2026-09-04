package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.application.ApplicationEmailDraft;
import com.agentplatform.orchestrator.application.ApplicationDraftStatus;
import com.agentplatform.orchestrator.application.ApplicationPreparationService;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraftService;
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
 * Verifies that {@link ApplicationAdvisorAgent} delegates to the tailoring,
 * draft and preparation services and never sends email.
 */
@DisplayName("ApplicationAdvisorAgent — delegates to tailoring + preparation services")
class ApplicationAdvisorAgentTest {

    private static CandidateProfile profile() {
        return new CandidateProfile("Alice", null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of("Java"), List.of(),
                List.of(), List.of());
    }

    private static Job job(String id) {
        return new Job(id, "Engineer", "Acme", null, null, List.of("Java"), List.of(),
                null, null, null, "mock", null, null, null);
    }

    private static ResumeTailoringAnalysis analysis() {
        return new ResumeTailoringAnalysis(1L, "j1", List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new AtsReadinessAnalysis(88, "label", 1, 0, 0, 0, true, true, "explanation"));
    }

    private static TailoredResumeDraft draft() {
        return new TailoredResumeDraft("j1", 1L, "summary", List.of(), List.of(), List.of(),
                List.of(), List.of(), null, List.of());
    }

    private static ApplicationEmailDraft emailDraft() {
        return new ApplicationEmailDraft("j1", 1L, "Acme", "Engineer", "Hiring Manager",
                null, "Application for Engineer", "body", "DRAFT_ONLY",
                ApplicationDraftStatus.REVIEW_REQUIRED, List.of());
    }

    @Test
    @DisplayName("delegates to tailoring, draft and preparation services")
    void delegatesToServices() {
        ResumeTailoringAnalysisService tailoringSvc = mock(ResumeTailoringAnalysisService.class);
        TailoredResumeDraftService draftSvc = mock(TailoredResumeDraftService.class);
        ApplicationPreparationService prepSvc = mock(ApplicationPreparationService.class);
        ResumeTailoringAnalysis analysis = analysis();
        TailoredResumeDraft draft = draft();
        ApplicationEmailDraft email = emailDraft();
        when(tailoringSvc.analyze(profile(), job("j1"), null)).thenReturn(analysis);
        when(draftSvc.generate(profile(), job("j1"), analysis)).thenReturn(draft);
        when(prepSvc.prepare(profile(), job("j1"), draft)).thenReturn(email);

        ApplicationAdvisorAgent agent = new ApplicationAdvisorAgent(tailoringSvc, draftSvc, prepSvc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        assertTrue(agent.canExecute(ctx));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.APPLICATION_ADVISOR), ctx);

        assertTrue(result.success());
        verify(tailoringSvc).analyze(profile(), job("j1"), null);
        verify(draftSvc).generate(profile(), job("j1"), analysis);
        verify(prepSvc).prepare(profile(), job("j1"), draft);
        assertTrue(ctx.applicationDraft() == email);
        assertTrue(ctx.tailoredDraft() == draft);
        assertTrue(ctx.tailoringAnalysis() == analysis);
    }

    @Test
    @DisplayName("cannot execute without a candidate and a job")
    void cannotExecuteWithoutInput() {
        ApplicationAdvisorAgent agent = new ApplicationAdvisorAgent(
                mock(ResumeTailoringAnalysisService.class),
                mock(TailoredResumeDraftService.class),
                mock(ApplicationPreparationService.class));
        assertFalse(agent.canExecute(new AgentContext()));

        AgentContext onlyJob = new AgentContext();
        onlyJob.setJob(job("j1"));
        assertFalse(agent.canExecute(onlyJob));
    }

    @Test
    @DisplayName("preparation failure yields a FAILED result, never a throw")
    void failureContained() {
        ResumeTailoringAnalysisService tailoringSvc = mock(ResumeTailoringAnalysisService.class);
        TailoredResumeDraftService draftSvc = mock(TailoredResumeDraftService.class);
        ApplicationPreparationService prepSvc = mock(ApplicationPreparationService.class);
        when(tailoringSvc.analyze(any(), any(), any())).thenThrow(new RuntimeException("boom"));

        ApplicationAdvisorAgent agent = new ApplicationAdvisorAgent(tailoringSvc, draftSvc, prepSvc);
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));

        AgentResult result = agent.execute(AgentRequest.of(AgentType.APPLICATION_ADVISOR), ctx);

        assertFalse(result.success());
        assertEquals(AgentStatus.FAILED, result.status());
        verify(draftSvc, never()).generate(any(), any(), any());
    }
}
