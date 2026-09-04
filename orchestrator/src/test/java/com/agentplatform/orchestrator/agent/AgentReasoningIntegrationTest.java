package com.agentplatform.orchestrator.agent;

import com.agentplatform.core.config.OllamaChatModelFactory;
import com.agentplatform.orchestrator.application.ApplicationDraftStatus;
import com.agentplatform.orchestrator.application.ApplicationEmailDraft;
import com.agentplatform.orchestrator.application.ApplicationPreparationService;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.gap.CareerImprovementPlan;
import com.agentplatform.orchestrator.gap.CareerImprovementPlanService;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.gap.GapSeverity;
import com.agentplatform.orchestrator.gap.SkillGap;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeProfileService;
import com.agentplatform.orchestrator.tailoring.AtsReadinessAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies end-to-end controlled reasoning across the AI-eligible agents:
 * message enrichment, the deterministic output preserved, the shared per-run AI
 * budget, safety (no mutation / no email), and that structural agents stay AI-free.
 */
@DisplayName("AgentReasoningIntegration — controlled reasoning across agents")
class AgentReasoningIntegrationTest {

    private static CandidateProfile profile() {
        return new CandidateProfile("Alice", null, null, null, List.of(), List.of("Java"),
                List.of(), List.of(), List.of(), List.of(), List.of("Java"), List.of(),
                List.of(), List.of());
    }

    private static Job job(String id) {
        return new Job(id, "Engineer", "Acme", null, null, List.of("Java"), List.of(),
                null, null, null, "mock", null, null, null);
    }

    private static CareerGapAnalysis gap() {
        return new CareerGapAnalysis(1L, "j1",
                List.of(new SkillGap("Java", List.of())),
                List.of(new SkillGap("Kubernetes", List.of())),
                List.of(), List.of(),
                new ExperienceGap(null, null, null, false),
                CareerTrack.SOFTWARE, CareerTrack.SOFTWARE, false, GapSeverity.MEDIUM,
                List.of());
    }

    private static ResumeTailoringAnalysis analysis() {
        return new ResumeTailoringAnalysis(1L, "j1", List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new AtsReadinessAnalysis(88, "label", 1, 0, 0, 0, true, true, "explanation"));
    }

    private static AgentReasoningService reasoningService(ChatModel model, AtomicInteger calls) {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        when(factory.chatModel(null)).thenReturn(model);
        return new AgentReasoningService(factory, new ObjectMapper());
    }

    private static ChatModel returningTopic(String topic) {
        ChatModel model = mock(ChatModel.class);
        String body = "{ \"summary\": \"auto reasoning.\", \"explanations\": ["
                + "{ \"topic\": \"" + topic + "\", \"explanation\": \"based on facts.\" } ] }";
        when(model.chat(anyString())).thenReturn(body);
        return model;
    }

    @Test
    @DisplayName("ResumeAgent enriches message with AI summary, output stays the profile")
    void resumeAgentWithReasoning() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return "{ \"summary\": \"resume reasoning.\", \"explanations\": ["
                    + "{ \"topic\": \"Java\", \"explanation\": \"based on facts.\" } ] }";
        });
        AgentReasoningService rs = reasoningService(model, calls);
        ResumeProfileService svc = mock(ResumeProfileService.class);
        when(svc.buildProfile("resume")).thenReturn(profile());
        ResumeAgent agent = new ResumeAgent(svc, rs);

        AgentContext ctx = new AgentContext();
        ctx.setResumeText("resume");
        AgentResult result = agent.execute(AgentRequest.of(AgentType.RESUME), ctx);

        assertTrue(result.success());
        assertTrue(result.message().contains("Reasoning: resume reasoning."));
        assertEquals(profile(), result.outputAs(CandidateProfile.class));
        assertEquals(profile(), ctx.candidateProfile());
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("ResumeAgent reasoning failure keeps deterministic result and message")
    void resumeAgentReasoningFailureKeepsDeterministic() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("down"));
        AgentReasoningService rs = reasoningService(model, new AtomicInteger());
        ResumeProfileService svc = mock(ResumeProfileService.class);
        when(svc.buildProfile("resume")).thenReturn(profile());
        ResumeAgent agent = new ResumeAgent(svc, rs);

        AgentContext ctx = new AgentContext();
        ctx.setResumeText("resume");
        AgentResult result = agent.execute(AgentRequest.of(AgentType.RESUME), ctx);

        assertTrue(result.success());
        assertFalse(result.message().contains("Reasoning:"));
        assertEquals(profile(), result.outputAs(CandidateProfile.class));
    }

    @Test
    @DisplayName("CareerAdvisorAgent enriches message, output stays the gap analysis")
    void careerAdvisorWithReasoning() {
        ChatModel model = returningTopic("Kubernetes");
        AgentReasoningService rs = reasoningService(model, new AtomicInteger());
        CareerGapAnalysisService gapSvc = mock(CareerGapAnalysisService.class);
        CareerImprovementPlanService planSvc = mock(CareerImprovementPlanService.class);
        when(gapSvc.analyze(profile(), job("j1"))).thenReturn(gap());
        when(planSvc.generatePlan(gap())).thenReturn(new CareerImprovementPlan("j1", 1L, "s",
                CareerImprovementPlan.ORIGIN_DETERMINISTIC, List.of()));
        CareerAdvisorAgent agent = new CareerAdvisorAgent(gapSvc, planSvc, rs);

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.CAREER_ADVISOR), ctx);

        assertTrue(result.success());
        assertTrue(result.message().contains("Reasoning: auto reasoning."));
        assertEquals(gap(), result.outputAs(CareerGapAnalysis.class));
    }

    @Test
    @DisplayName("CareerAdvisorAgent reasoning failure keeps deterministic result")
    void careerAdvisorReasoningFailureKeepsDeterministic() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("down"));
        AgentReasoningService rs = reasoningService(model, new AtomicInteger());
        CareerGapAnalysisService gapSvc = mock(CareerGapAnalysisService.class);
        CareerImprovementPlanService planSvc = mock(CareerImprovementPlanService.class);
        when(gapSvc.analyze(profile(), job("j1"))).thenReturn(gap());
        CareerAdvisorAgent agent = new CareerAdvisorAgent(gapSvc, planSvc, rs);

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.CAREER_ADVISOR), ctx);

        assertTrue(result.success());
        assertEquals(gap(), result.outputAs(CareerGapAnalysis.class));
    }

    @Test
    @DisplayName("ApplicationAdvisorAgent enriches message, output stays the email draft")
    void applicationAdvisorWithReasoning() {
        ChatModel model = returningTopic("Java");
        AgentReasoningService rs = reasoningService(model, new AtomicInteger());
        ResumeTailoringAnalysisService tailoringSvc = mock(ResumeTailoringAnalysisService.class);
        TailoredResumeDraftService draftSvc = mock(TailoredResumeDraftService.class);
        ApplicationPreparationService prepSvc = mock(ApplicationPreparationService.class);
        TailoredResumeDraft draft = new TailoredResumeDraft("j1", 1L, "summary", List.of("Java"),
                List.of(), List.of(), List.of(), List.of(), null, List.of());
        ApplicationEmailDraft email = new ApplicationEmailDraft("j1", 1L, "Acme", "Engineer",
                "Hiring Manager", null, "subject", "body", "DRAFT_ONLY",
                ApplicationDraftStatus.REVIEW_REQUIRED, List.of());
        when(tailoringSvc.analyze(profile(), job("j1"), null)).thenReturn(analysis());
        when(draftSvc.generate(profile(), job("j1"), analysis())).thenReturn(draft);
        when(prepSvc.prepare(profile(), job("j1"), draft)).thenReturn(email);
        ApplicationAdvisorAgent agent = new ApplicationAdvisorAgent(tailoringSvc, draftSvc, prepSvc, rs);

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.APPLICATION_ADVISOR), ctx);

        assertTrue(result.success());
        assertTrue(result.message().contains("Reasoning: auto reasoning."));
        assertEquals(email, result.outputAs(ApplicationEmailDraft.class));
    }

    @Test
    @DisplayName("ApplicationAdvisorAgent reasoning failure keeps deterministic result")
    void applicationAdvisorReasoningFailureKeepsDeterministic() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("down"));
        AgentReasoningService rs = reasoningService(model, new AtomicInteger());
        ResumeTailoringAnalysisService tailoringSvc = mock(ResumeTailoringAnalysisService.class);
        TailoredResumeDraftService draftSvc = mock(TailoredResumeDraftService.class);
        ApplicationPreparationService prepSvc = mock(ApplicationPreparationService.class);
        TailoredResumeDraft draft = new TailoredResumeDraft("j1", 1L, "summary", List.of("Java"),
                List.of(), List.of(), List.of(), List.of(), null, List.of());
        ApplicationEmailDraft email = new ApplicationEmailDraft("j1", 1L, "Acme", "Engineer",
                "Hiring Manager", null, "subject", "body", "DRAFT_ONLY",
                ApplicationDraftStatus.REVIEW_REQUIRED, List.of());
        when(tailoringSvc.analyze(profile(), job("j1"), null)).thenReturn(analysis());
        when(draftSvc.generate(profile(), job("j1"), analysis())).thenReturn(draft);
        when(prepSvc.prepare(profile(), job("j1"), draft)).thenReturn(email);
        ApplicationAdvisorAgent agent = new ApplicationAdvisorAgent(tailoringSvc, draftSvc, prepSvc, rs);

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setJob(job("j1"));
        AgentResult result = agent.execute(AgentRequest.of(AgentType.APPLICATION_ADVISOR), ctx);

        assertTrue(result.success());
        assertEquals(email, result.outputAs(ApplicationEmailDraft.class));
    }

    @Test
    @DisplayName("backward-compatible constructors work without a reasoning service")
    void backwardCompatibleConstructors() {
        ResumeAgent ra = new ResumeAgent(mock(ResumeProfileService.class));
        assertFalse(ra.execute(AgentRequest.of(AgentType.RESUME), resumeTextContext()).message().contains("Reasoning:"));
    }

    private static AgentContext resumeTextContext() {
        AgentContext ctx = new AgentContext();
        ctx.setResumeText("resume");
        return ctx;
    }

    @Test
    @DisplayName("a shared run budget bounds total LLM calls to MAX_AI_CALLS")
    void sharedBudgetBoundsTotalCalls() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenAnswer(inv -> {
            calls.incrementAndGet();
            return "{ \"summary\": \"s\", \"explanations\": ["
                    + "{ \"topic\": \"Java\", \"explanation\": \"x\" } ] }";
        });
        AgentReasoningService rs = reasoningService(model, calls);

        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setCareerGapAnalysis(gap());
        ctx.setTailoredDraft(new TailoredResumeDraft("j1", 1L, "summary", List.of("Java"),
                List.of(), List.of(), List.of(), List.of(), null, List.of()));
        assertEquals(CareerAgentOrchestrator.MAX_AI_CALLS, ctx.aiCallsRemaining());

        rs.reason(AgentType.RESUME, ctx, "t");
        rs.reason(AgentType.CAREER_ADVISOR, ctx, "t");
        rs.reason(AgentType.APPLICATION_ADVISOR, ctx, "t");
        rs.reason(AgentType.RESUME, ctx, "t"); // budget exhausted → fallback

        assertEquals(3, calls.get(), "exactly MAX_AI_CALLS LLM calls are made for one run");
        assertEquals(0, ctx.aiCallsRemaining());
    }

    @Test
    @DisplayName("reasoning across a run never mocks email or mutates deterministic objects")
    void safetyAcrossReasoning() {
        ChatModel model = returningTopic("Java");
        AgentReasoningService rs = reasoningService(model, new AtomicInteger());
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        rs.reason(AgentType.RESUME, ctx, "t");
        assertEquals(profile(), ctx.candidateProfile(), "profile unchanged by reasoning");
        assertTrue(ctx.applicationDraft() == null, "no application/email draft is produced by reasoning");
    }
}
