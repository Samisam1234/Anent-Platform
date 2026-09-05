package com.agentplatform.orchestrator.agent;

import com.agentplatform.core.ai.AiErrorClassifier;
import com.agentplatform.core.config.OllamaChatModelFactory;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.ExperienceGap;
import com.agentplatform.orchestrator.gap.GapSeverity;
import com.agentplatform.orchestrator.gap.ImprovementPriority;
import com.agentplatform.orchestrator.gap.SkillGap;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link AgentReasoningService}: controlled prompts, authoritative-only
 * validation, deterministic fallback, the shared-model strategy, and the per-run
 * AI call budget.
 */
@DisplayName("AgentReasoningService — controlled AI reasoning layer")
class AgentReasoningServiceTest {

    private static CandidateProfile profile() {
        return new CandidateProfile("Alice", null, null, null, List.of(), List.of("Java"),
                List.of(), List.of(), List.of(), List.of(), List.of("Java"), List.of(),
                List.of(), List.of());
    }

    private static Job job(String id) {
        return new Job(id, "Engineer", null, null, null, List.of("Java"), List.of(),
                null, null, null, "mock", null, null, null);
    }

    private static CareerGapAnalysis gap() {
        return new CareerGapAnalysis(1L, "j1",
                List.of(new SkillGap("Java", List.of())),
                List.of(new SkillGap("Kubernetes", List.of())),
                List.of(), List.of(),
                new ExperienceGap(null, null, null, false),
                CareerTrack.SOFTWARE, CareerTrack.SOFTWARE, false, GapSeverity.MEDIUM,
                List.of(new ImprovementPriority(1, "Kubernetes", "REQUIRED_SKILL", "required", "desc")));
    }

    private static TailoredResumeDraft draft() {
        return new TailoredResumeDraft("j1", 1L, "summary", List.of("Java"),
                List.of(), List.of(), List.of(), List.of(), null, List.of());
    }

    private static AgentContext resumeContext() {
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        return ctx;
    }

    private static AgentContext careerContext() {
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setCareerGapAnalysis(gap());
        return ctx;
    }

    private static AgentContext applicationContext() {
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(profile());
        ctx.setTailoredDraft(draft());
        return ctx;
    }

    private static AgentReasoningService service(ChatModel model) {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        when(factory.chatModel(null)).thenReturn(model);
        return new AgentReasoningService(factory, new ObjectMapper());
    }

    private static ChatModel returning(String raw) {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn(raw);
        return model;
    }

    private static String validResponse(String topic, String summary) {
        return "{ \"summary\": \"" + summary + "\", \"explanations\": ["
                + "{ \"topic\": \"" + topic + "\", \"explanation\": \"based on the facts.\" } ] }";
    }

    @Test
    @DisplayName("RESUME reasoning uses canonical skills and returns AI origin")
    void resumeAiReasoning() {
        AgentReasoningService service = service(returning(validResponse("Java", "Alice knows Java.")));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertTrue(result.aiUsed());
        assertEquals(AgentReasoningResult.ORIGIN_AI, result.origin());
        assertEquals("Alice knows Java.", result.summary());
        assertEquals("Java", result.explanations().get(0).topic());
    }

    @Test
    @DisplayName("CAREER_ADVISOR reasoning uses gap skills and priorities")
    void careerAdvisorAiReasoning() {
        AgentReasoningService service = service(returning(validResponse("Kubernetes", "Kubernetes gap.")));
        AgentReasoningResult result = service.reason(AgentType.CAREER_ADVISOR, careerContext(), "explain");
        assertTrue(result.aiUsed());
        assertEquals("Kubernetes", result.explanations().get(0).topic());
    }

    @Test
    @DisplayName("APPLICATION_ADVISOR reasoning uses tailored ordered skills")
    void applicationAdvisorAiReasoning() {
        AgentReasoningService service = service(returning(validResponse("Java", "Tailored for Java.")));
        AgentReasoningResult result = service.reason(AgentType.APPLICATION_ADVISOR, applicationContext(), "explain");
        assertTrue(result.aiUsed());
        assertEquals("Java", result.explanations().get(0).topic());
    }

    @Test
    @DisplayName("job discovery / matching never request AI reasoning")
    void structuralAgentsNeverReason() {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        AgentReasoningService service = new AgentReasoningService(factory, new ObjectMapper());
        AgentContext ctx = resumeContext();
        AgentReasoningResult discovery = service.reason(AgentType.JOB_DISCOVERY, ctx, "explain");
        AgentReasoningResult matching = service.reason(AgentType.MATCHING, ctx, "explain");
        assertFalse(discovery.aiUsed());
        assertFalse(matching.aiUsed());
        verify(factory, never()).chatModel(anyString());
    }

    @Test
    @DisplayName("one AI call at a time; budget is consumed per call")
    void budgetConsumedPerCall() {
        AgentReasoningService service = service(returning(validResponse("Java", "s")));
        AgentContext ctx = resumeContext();
        assertEquals(3, ctx.aiCallsRemaining());
        service.reason(AgentType.RESUME, ctx, "explain");
        assertEquals(2, ctx.aiCallsRemaining());
        service.reason(AgentType.RESUME, ctx, "explain");
        assertEquals(1, ctx.aiCallsRemaining());
    }

    @Test
    @DisplayName("AI budget exhaustion yields deterministic behavior, no LLM call")
    void budgetExhaustedFallsBack() {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        AgentReasoningService service = new AgentReasoningService(factory, new ObjectMapper());
        AgentContext ctx = resumeContext();
        ctx.setAiCallsRemaining(0);
        AgentReasoningResult result = service.reason(AgentType.RESUME, ctx, "explain");
        assertFalse(result.aiUsed());
        assertEquals(AgentReasoningResult.ORIGIN_DETERMINISTIC, result.origin());
        verify(factory, never()).chatModel(anyString());
    }

    @Test
    @DisplayName("Ollama exception falls back deterministically, never throws")
    void ollamaDownFallsBack() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("HTTP error (404): model not found"));
        AgentReasoningService service = service(model);
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
        assertNotNull(result.summary());
    }

    @Test
    @DisplayName("empty model response falls back deterministically")
    void emptyResponseFallsBack() {
        AgentReasoningService service = service(returning("   "));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
        assertFalse(result.summary().isBlank());
    }

    @Test
    @DisplayName("malformed non-JSON response falls back deterministically")
    void malformedResponseFallsBack() {
        AgentReasoningService service = service(returning("this is not json"));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
    }

    @Test
    @DisplayName("hallucinated topic outside authoritative set is rejected")
    void hallucinatedTopicRejected() {
        AgentReasoningService service = service(returning(
                validResponse("Neuro-engineering ninja", "invented skill")));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
    }

    @Test
    @DisplayName("empty explanation topic is rejected")
    void emptyTopicRejected() {
        AgentReasoningService service = service(returning(validResponse("", "no topic")));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
    }

    @Test
    @DisplayName("empty explanations array is rejected as invalid")
    void emptyExplanationsRejected() {
        AgentReasoningService service = service(returning("{ \"summary\": \"s\", \"explanations\": [] }"));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
    }

    @Test
    @DisplayName("no authoritative topics means no LLM call (empty profile)")
    void noTopicsFallsBackWithoutCall() {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        AgentReasoningService service = new AgentReasoningService(factory, new ObjectMapper());
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(new CandidateProfile("Alice", null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        AgentReasoningResult result = service.reason(AgentType.RESUME, ctx, "explain");
        assertFalse(result.aiUsed());
        verify(factory, never()).chatModel(anyString());
    }

    @Test
    @DisplayName("deterministic fallback still yields explanations derived from facts")
    void deterministicFallbackHasExplanations() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("network"));
        AgentReasoningService service = service(model);
        AgentReasoningResult result = service.reason(AgentType.CAREER_ADVISOR, careerContext(), "explain");
        assertFalse(result.aiUsed());
        assertFalse(result.explanations().isEmpty());
    }

    @Test
    @DisplayName("summary is bounded when AI returns an overlong summary")
    void summaryBounded() {
        String longSummary = "x".repeat(1000);
        AgentReasoningService service = service(returning(validResponse("Java", longSummary)));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertTrue(result.summary().length() <= 400);
    }

    @Test
    @DisplayName("null agent type or context never throws")
    void nullArgumentsNeverThrow() {
        AgentReasoningService service = service(returning(validResponse("Java", "s")));
        AgentReasoningResult r1 = service.reason(null, resumeContext(), "task");
        AgentReasoningResult r2 = service.reason(AgentType.RESUME, null, "task");
        assertNotNull(r1);
        assertNotNull(r2);
        assertFalse(r2.aiUsed());
    }

    @Test
    @DisplayName("reasoning never mutates the deterministic domain objects")
    void reasoningDoesNotMutateInputs() {
        AgentReasoningService service = service(returning(validResponse("Java", "s")));
        CandidateProfile before = profile();
        AgentContext ctx = resumeContext();
        service.reason(AgentType.RESUME, ctx, "explain");
        assertEquals(before, ctx.candidateProfile());
    }

    @Test
    @DisplayName("SocketTimeoutException falls back with TIMEOUT error code")
    void socketTimeoutFallsBackWithErrorCode() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("Read timed out", new SocketTimeoutException("Read timed out")));
        AgentReasoningService service = service(model);
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
        assertEquals(AiErrorClassifier.Kind.TIMEOUT.name(), result.errorCode());
        assertNotNull(result.summary());
    }

    @Test
    @DisplayName("HttpConnectTimeoutException falls back with TIMEOUT error code")
    void httpConnectTimeoutFallsBackWithErrorCode() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("Connect timed out", new java.net.http.HttpConnectTimeoutException("Connect timed out")));
        AgentReasoningService service = service(model);
        AgentReasoningResult result = service.reason(AgentType.CAREER_ADVISOR, careerContext(), "explain");
        assertFalse(result.aiUsed());
        assertEquals(AiErrorClassifier.Kind.TIMEOUT.name(), result.errorCode());
    }

    @Test
    @DisplayName("HTTP 404 model not found falls back with MODEL_UNAVAILABLE error code")
    void modelNotFoundFallsBackWithErrorCode() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("HTTP error (404): model not found"));
        AgentReasoningService service = service(model);
        AgentReasoningResult result = service.reason(AgentType.APPLICATION_ADVISOR, applicationContext(), "explain");
        assertFalse(result.aiUsed());
        assertEquals(AiErrorClassifier.Kind.MODEL_UNAVAILABLE.name(), result.errorCode());
    }

    @Test
    @DisplayName("AI budget exhausted yields deterministic behavior with AI_BUDGET_EXHAUSTED error code")
    void budgetExhaustedFallsBackWithErrorCode() {
        OllamaChatModelFactory factory = mock(OllamaChatModelFactory.class);
        AgentReasoningService service = new AgentReasoningService(factory, new ObjectMapper());
        AgentContext ctx = resumeContext();
        ctx.setAiCallsRemaining(0);
        AgentReasoningResult result = service.reason(AgentType.RESUME, ctx, "explain");
        assertFalse(result.aiUsed());
        assertEquals(AgentReasoningResult.ORIGIN_DETERMINISTIC, result.origin());
        assertEquals("AI_BUDGET_EXHAUSTED", result.errorCode());
        verify(factory, never()).chatModel(anyString());
    }

    @Test
    @DisplayName("empty model response falls back with EMPTY_RESPONSE error code")
    void emptyResponseFallsBackWithErrorCode() {
        AgentReasoningService service = service(returning("   "));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
        assertEquals("EMPTY_RESPONSE", result.errorCode());
    }

    @Test
    @DisplayName("malformed non-JSON response falls back with INVALID_OUTPUT error code")
    void malformedResponseFallsBackWithErrorCode() {
        AgentReasoningService service = service(returning("this is not json"));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
        assertEquals("INVALID_OUTPUT", result.errorCode());
    }

    @Test
    @DisplayName("hallucinated topic outside authoritative set falls back with INVALID_OUTPUT error code")
    void hallucinatedTopicRejectedWithErrorCode() {
        AgentReasoningService service = service(returning(
                validResponse("Neuro-engineering ninja", "invented skill")));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertFalse(result.aiUsed());
        assertEquals("INVALID_OUTPUT", result.errorCode());
    }

    @Test
    @DisplayName("successful AI response has NONE error code")
    void successfulResponseHasNoneErrorCode() {
        AgentReasoningService service = service(returning(validResponse("Java", "Alice knows Java.")));
        AgentReasoningResult result = service.reason(AgentType.RESUME, resumeContext(), "explain");
        assertTrue(result.aiUsed());
        assertEquals("NONE", result.errorCode());
    }
}
