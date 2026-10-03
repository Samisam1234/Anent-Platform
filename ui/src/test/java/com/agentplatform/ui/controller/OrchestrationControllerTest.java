package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.agent.AgentResult;
import com.agentplatform.orchestrator.agent.AgentType;
import com.agentplatform.orchestrator.agent.OrchestrationRun;
import com.agentplatform.orchestrator.agent.EvaluationResult;
import com.agentplatform.orchestrator.agent.RunStatus;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.service.OrchestrationService;
import com.agentplatform.orchestrator.job.exception.JobNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link OrchestrationController}. Only the web layer is loaded —
 * no Spring context, no DB, no external APIs. {@link OrchestrationService} is
 * mocked, so no real orchestration, LLM, or email work is performed.
 */
@WebMvcTest(controllers = {OrchestrationController.class, GlobalExceptionHandler.class})
class OrchestrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrchestrationService orchestrationService;

    private static final List<AgentResult> FULL_COMPLETED = List.of(
            AgentResult.completed(AgentType.RESUME, "done").withStartTime().withCompletionTime(),
            AgentResult.completed(AgentType.JOB_DISCOVERY, "done").withStartTime().withCompletionTime(),
            AgentResult.completed(AgentType.MATCHING, "done").withStartTime().withCompletionTime(),
            AgentResult.completed(AgentType.CAREER_ADVISOR, "done").withStartTime().withCompletionTime(),
            AgentResult.completed(AgentType.APPLICATION_ADVISOR, "done").withStartTime().withCompletionTime()
    );

    private static OrchestrationService.OrchestrationWithEvaluation withEvaluation(OrchestrationRun run) {
        return new OrchestrationService.OrchestrationWithEvaluation(run, EvaluationResult.from(run, null));
    }

    private String body(Long candidateId, String jobId) throws Exception {
        return objectMapper.writeValueAsString(
                new com.agentplatform.ui.dto.OrchestrationRequestDto(candidateId, jobId));
    }

    // ─── 1. Valid candidate + job → 200 ─────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/agent/orchestrate — valid request returns 200 with runStatus")
    void orchestrate_validRequest_returns200() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                0, 0, true, "Career orchestration completed successfully.", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.runStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── 2. Response contains runStatus ─────────────────────────────────────

    @Test
    @DisplayName("response exposes runStatus")
    void response_exposesRunStatus() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1"))
                .thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                        0, 0, true, "ok", null),
                EvaluationResult.from(
                        new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                                0, 0, true, "ok", null), null)
        ));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(jsonPath("$.runStatus").isString())
                .andExpect(jsonPath("$.runStatus").value("COMPLETED"));
    }

    // ─── 3. Agent execution list present ────────────────────────────────────

    @Test
    @DisplayName("response contains the agent execution list")
    void response_containsAgentExecutions() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1"))
                .thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                        0, 0, true, "ok", null),
                EvaluationResult.from(
                        new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                                0, 0, true, "ok", null), null)
        ));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(jsonPath("$.agentExecutions").isArray())
                .andExpect(jsonPath("$.agentExecutions.length()").value(5));
    }

    // ─── 4. Fixed agent order preserved ─────────────────────────────────────

    @Test
    @DisplayName("agent executions preserve the fixed pipeline order")
    void response_fixedAgentOrder() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1"))
                .thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                        0, 0, true, "ok", null),
                EvaluationResult.from(
                        new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                                0, 0, true, "ok", null), null)
        ));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(jsonPath("$.agentExecutions[0].agentType").value("RESUME"))
                .andExpect(jsonPath("$.agentExecutions[1].agentType").value("JOB_DISCOVERY"))
                .andExpect(jsonPath("$.agentExecutions[2].agentType").value("MATCHING"))
                .andExpect(jsonPath("$.agentExecutions[3].agentType").value("CAREER_ADVISOR"))
                .andExpect(jsonPath("$.agentExecutions[4].agentType").value("APPLICATION_ADVISOR"));
    }

    // ─── 5 & 6. AI / tool counts returned ───────────────────────────────────

    @Test
    @DisplayName("response returns AI and tool call counts")
    void response_returnsUsageCounts() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                2, 3, true, "ok", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(jsonPath("$.aiCallsUsed").value(2))
                .andExpect(jsonPath("$.toolCallsUsed").value(3));
    }

    // ─── 7. Blocking failure reflected safely ───────────────────────────────

    @Test
    @DisplayName("blocking failure → 200 with FAILED status and SKIPPED dependents, no internals")
    void response_blockingFailureSafe() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.FAILED, List.of(
                AgentResult.failed(AgentType.RESUME, "Resume input invalid.", "AGENT_INPUT_MISSING"),
                AgentResult.skipped(AgentType.JOB_DISCOVERY, "Skipped: ..."),
                AgentResult.skipped(AgentType.MATCHING, "Skipped: ..."),
                AgentResult.skipped(AgentType.CAREER_ADVISOR, "Skipped: ..."),
                AgentResult.skipped(AgentType.APPLICATION_ADVISOR, "Skipped: ...")
        ), 0, 0, false, "Blocking agent RESUME failed.", AgentType.RESUME);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runStatus").value("FAILED"))
                .andExpect(jsonPath("$.stoppingAgentType").value("RESUME"))
                .andExpect(jsonPath("$.agentExecutions[0].status").value("FAILED"))
                .andExpect(jsonPath("$.agentExecutions[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.agentExecutions[0].errorCode").value("AGENT_INPUT_MISSING"));
    }

    // ─── 8. Optional failure reflected safely ───────────────────────────────

    @Test
    @DisplayName("optional failure → 200 with PARTIAL status, downstream still runs")
    void response_optionalFailureIsPartial() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.PARTIAL, List.of(
                AgentResult.completed(AgentType.RESUME, "done"),
                AgentResult.completed(AgentType.JOB_DISCOVERY, "done"),
                AgentResult.failed(AgentType.MATCHING, "Matching error.", "AGENT_OPTIONAL_FAILURE"),
                AgentResult.completed(AgentType.CAREER_ADVISOR, "done"),
                AgentResult.completed(AgentType.APPLICATION_ADVISOR, "done")
        ), 0, 0, false, "Some stages skipped/failed.", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.agentExecutions[2].status").value("FAILED"))
                .andExpect(jsonPath("$.agentExecutions[3].status").value("COMPLETED"));
    }

    // ─── 9. Missing candidate → 404 ─────────────────────────────────────────

    @Test
    @DisplayName("unknown candidate → 404 ProblemDetail")
    void orchestrate_unknownCandidate_returns404() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(anyLong(), eq("job-1")))
                .thenThrow(new CandidateProfileNotFoundException(999L));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999L, "job-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Candidate Profile Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ─── 10. Missing job → 404 ──────────────────────────────────────────────

    @Test
    @DisplayName("unknown job → 404 ProblemDetail")
    void orchestrate_unknownJob_returns404() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(1L, "nope"))
                .thenThrow(new JobNotFoundException("nope"));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "nope")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Job Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ─── 11. Invalid request → 400 ──────────────────────────────────────────

    @Test
    @DisplayName("missing candidateId → 400")
    void orchestrate_missingCandidate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "job-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("negative candidateId → 400")
    void orchestrate_negativeCandidate_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(-1L, "job-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("blank jobId → 400")
    void orchestrate_blankJob_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("oversized jobId → 400")
    void orchestrate_oversizedJob_returns400() throws Exception {
        String tooLong = "x".repeat(201);
        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("malformed JSON → 400")
    void orchestrate_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());
    }

    // ─── 12 & 13 & 14. Unexpected error → 500 safe, no stack trace ──────────

    @Test
    @DisplayName("unexpected error → 500 with safe generic message, no stack trace or internals")
    void orchestrate_unexpectedError_returns500Safe() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(eq(1L), eq("job-1")))
                .thenThrow(new IllegalStateException("super secret DB password and stacktrace"));

        String raw = mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(raw.contains("super secret"),
                "must not leak exception message");
        org.junit.jupiter.api.Assertions.assertFalse(raw.contains("IllegalStateException"),
                "must not leak exception class name");
        org.junit.jupiter.api.Assertions.assertFalse(raw.contains(" at "),
                "must not leak a stack trace");
    }

    // ─── 15. No email-send capability via this endpoint ─────────────────────

    @Test
    @DisplayName("request contract cannot trigger email sending (no approved/email fields)")
    void request_hasNoEmailCommand() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1"))
                .thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                        0, 0, true, "ok", null),
                EvaluationResult.from(
                        new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                                0, 0, true, "ok", null), null)
        ));

        // An 'approved' field must be ignored — it is not part of the contract.
        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\": 1, \"jobId\": \"job-1\", \"approved\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // And the service is only ever called with ids — never with an approval signal.
        org.mockito.Mockito.verify(orchestrationService).orchestrateWithEvaluation(1L, "job-1");
    }

    // ─── 16. No arbitrary tool execution ────────────────────────────────────

    @Test
    @DisplayName("no generic tool-execution endpoint exists under /api/v1/agent")
    void noGenericToolEndpoint() throws Exception {
        // A request to an unknown agent sub-path must not invoke tools.
        mockMvc.perform(post("/api/v1/agent/tools/sendEmail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─── 17. Budgets are exposed and within limits ──────────────────────────

    @Test
    @DisplayName("exposed usage counts are within the documented budgets")
    void response_budgetsWithinLimits() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                3, 4, true, "ok", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(jsonPath("$.aiCallsUsed").value(3))
                .andExpect(jsonPath("$.toolCallsUsed").value(4))
                .andExpect(jsonPath("$.aiCallsUsed").value(
                        org.hamcrest.Matchers.lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.toolCallsUsed").value(
                        org.hamcrest.Matchers.lessThanOrEqualTo(4)));
    }

    // ─── 18. Deterministic repeat ───────────────────────────────────────────

    @Test
    @DisplayName("identical requests produce identical responses (deterministic)")
    void response_deterministicSameResult() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                0, 0, true, "Career orchestration completed successfully.", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        String a = mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String b = mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(a, b);
    }

    // ─── 19. Frontend-consumed JSON shape (Phase 6.8 contract guard) ────────

    /**
     * Guards the exact JSON field names/types that careerAgent.js reads so a
     * rename or removal in the DTO cannot silently break the UI.
     */
    @Test
    @DisplayName("response exposes the exact safe JSON shape the frontend consumes")
    void response_frontendJsonShape() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                2, 3, true, "Career orchestration completed successfully.", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                // Top-level fields read by careerAgent.js
                .andExpect(jsonPath("$.runStatus").isString())
                .andExpect(jsonPath("$.success").isBoolean())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.aiCallsUsed").isNumber())
                .andExpect(jsonPath("$.toolCallsUsed").isNumber())
                .andExpect(jsonPath("$.agentExecutions").isArray());
    }

    // ─── 20. Agent execution entry shape consumed by the UI ─────────────────

    @Test
    @DisplayName("each agent execution exposes agentType/status/success/message/errorCode")
    void response_agentExecutionShape() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1"))
                .thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                        0, 0, true, "ok", null),
                EvaluationResult.from(
                        new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                                0, 0, true, "ok", null), null)
        ));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentExecutions[0].agentType").value("RESUME"))
                .andExpect(jsonPath("$.agentExecutions[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.agentExecutions[0].success").value(true))
                .andExpect(jsonPath("$.agentExecutions[0].message").isString())
                // errorCode may legitimately be null for a completed agent
                .andExpect(jsonPath("$.agentExecutions[0].errorCode").value(
                        org.hamcrest.Matchers.anyOf(
                                org.hamcrest.Matchers.nullValue(),
                                org.hamcrest.Matchers.instanceOf(String.class))));
    }

    // ─── 21. Response shape exposes only safe fields (no internals) ─────────

    @Test
    @DisplayName("response exposes only safe status fields — never raw outputs/context")
    void response_neverExposesInternals() throws Exception {
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1"))
                .thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                new OrchestrationRun(RunStatus.PARTIAL, List.of(
                        AgentResult.completed(AgentType.RESUME, "Resume summary ready."),
                        AgentResult.completed(AgentType.JOB_DISCOVERY, "Found 12 candidate roles."),
                        AgentResult.failed(AgentType.MATCHING, "Matching unavailable.", "AGENT_OPTIONAL_FAILURE"),
                        AgentResult.completed(AgentType.CAREER_ADVISOR, "Advice ready."),
                        AgentResult.skipped(AgentType.APPLICATION_ADVISOR, "Skipped.")
                ), 2, 1, false, "Some stages did not complete.", null),
                EvaluationResult.from(
                        new OrchestrationRun(RunStatus.PARTIAL, List.of(
                                AgentResult.completed(AgentType.RESUME, "Resume summary ready."),
                                AgentResult.completed(AgentType.JOB_DISCOVERY, "Found 12 candidate roles."),
                                AgentResult.failed(AgentType.MATCHING, "Matching unavailable.", "AGENT_OPTIONAL_FAILURE"),
                                AgentResult.completed(AgentType.CAREER_ADVISOR, "Advice ready."),
                                AgentResult.skipped(AgentType.APPLICATION_ADVISOR, "Skipped.")
                        ), 2, 1, false, "Some stages did not complete.", null), null)
        ));

        String raw = mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentExecutions.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        // The DTO must never serialize the raw AgentContext, agent output objects,
        // tool arguments, resume text, credentials, or a stack trace.
        for (String forbidden : new String[]{
                "\"output\"", "\"agentContext\"", "\"context\"", "\"toolArguments\"",
                "\"credentials\"", "\"resumeText\"", " at com.", "Exception("}) {
            org.junit.jupiter.api.Assertions.assertFalse(raw.contains(forbidden),
                    "must not expose internal field/key: " + forbidden);
        }
    }

    // ─── 22. Timing and error code fields (Phase 6.8) ───────────────────────────

    @Test
    @DisplayName("agent execution includes startedAt, completedAt, durationMs")
    void response_agentExecutionIncludesTiming() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                0, 0, true, "ok", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentExecutions[0].startedAt").isString())
                .andExpect(jsonPath("$.agentExecutions[0].completedAt").isString())
                .andExpect(jsonPath("$.agentExecutions[0].durationMs").isNumber());
    }

    @Test
    @DisplayName("errorCode is exposed for failed/timeout agents")
    void response_errorCodeExposed() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.PARTIAL, List.of(
                AgentResult.completed(AgentType.RESUME, "done"),
                AgentResult.failed(AgentType.CAREER_ADVISOR, "AI timed out", "TIMEOUT"),
                AgentResult.completed(AgentType.APPLICATION_ADVISOR, "done")
        ), 1, 0, false, "AI timeout occurred.", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentExecutions[1].errorCode").value("TIMEOUT"));
    }

    @Test
    @DisplayName("AI_BUDGET_EXHAUSTED error code is exposed when budget exhausted")
    void response_budgetExhaustedErrorCode() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.PARTIAL, List.of(
                AgentResult.completed(AgentType.RESUME, "done").withStartTime().withCompletionTime(),
                AgentResult.failed(AgentType.CAREER_ADVISOR, "AI budget exhausted", "AI_BUDGET_EXHAUSTED"),
                AgentResult.completed(AgentType.APPLICATION_ADVISOR, "done").withStartTime().withCompletionTime()
        ), 3, 0, false, "AI budget exhausted.", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentExecutions[1].errorCode").value("AI_BUDGET_EXHAUSTED"));
    }

    // ─── 23. Phase 9 timeline fields exposed ─────────────────────────────────

    @Test
    @DisplayName("response exposes totalDurationMs and a PII-free execution timeline")
    void response_exposesTimeline() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                0, 0, true, "ok", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDurationMs").isNumber())
                .andExpect(jsonPath("$.totalDurationMs").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.executionTimeline").isArray())
                .andExpect(jsonPath("$.executionTimeline.length()").value(5))
                .andExpect(jsonPath("$.executionTimeline[0].agentType").value("RESUME"))
                .andExpect(jsonPath("$.executionTimeline[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.executionTimeline[0].durationMs").isNumber())
                .andExpect(jsonPath("$.executionTimeline[0].startedAt").isString())
                .andExpect(jsonPath("$.executionTimeline[0].completedAt").isString())
                .andExpect(jsonPath("$.executionTimeline[0].errorCode").value("NONE"));
    }

    @Test
    @DisplayName("execution timeline entries never carry message or output payloads")
    void response_timelineNoInternals() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.PARTIAL, List.of(
                AgentResult.completed(AgentType.RESUME, "Resume summary with my private details."),
                AgentResult.failed(AgentType.MATCHING, "Matching failed.", "AGENT_OPTIONAL_FAILURE"),
                AgentResult.skipped(AgentType.APPLICATION_ADVISOR, "Skipped.")
        ), 1, 0, false, "Some stages failed.", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(withEvaluation(run));

        String raw = mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Check the executionTimeline array in isolation — entries must carry only
        // type/status/timing/errorCode, never the message text or output payloads.
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(raw).get("executionTimeline");
        for (com.fasterxml.jackson.databind.JsonNode entry : node) {
            for (String forbidden : new String[]{"message", "output", "success"}) {
                org.junit.jupiter.api.Assertions.assertFalse(entry.has(forbidden),
                        "timeline entry must not carry field: " + forbidden);
            }
            org.junit.jupiter.api.Assertions.assertTrue(entry.has("agentType"));
            org.junit.jupiter.api.Assertions.assertTrue(entry.has("status"));
            org.junit.jupiter.api.Assertions.assertTrue(entry.has("durationMs"));
            org.junit.jupiter.api.Assertions.assertTrue(entry.has("errorCode"));
        }
        // Messages live ONLY in the separate agentExecutions array, not the timeline.
        org.junit.jupiter.api.Assertions.assertTrue(raw.contains("private details"),
                "message text belongs in agentExecutions, not the timeline");
    }

    // ─── 24. Phase 10 evaluation fields exposed ────────────────────────────────

    @Test
    @DisplayName("response exposes evaluation with pipeline health and resource metrics")
    void response_exposesEvaluation() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                2, 3, true, "ok", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                run,
                EvaluationResult.from(run, null)
        ));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation").exists())
                .andExpect(jsonPath("$.evaluation.pipelineCompleted").value(true))
                .andExpect(jsonPath("$.evaluation.agentsCompleted").value(5))
                .andExpect(jsonPath("$.evaluation.agentsFailed").value(0))
                .andExpect(jsonPath("$.evaluation.agentsSkipped").value(0))
                .andExpect(jsonPath("$.evaluation.aiCallsUsed").value(2))
                .andExpect(jsonPath("$.evaluation.toolCallsUsed").value(3))
                .andExpect(jsonPath("$.evaluation.totalDurationMs").isNumber());
    }

    @Test
    @DisplayName("evaluation has null matching/gap/advisor fields when those stages are skipped")
    void evaluation_nullForSkippedStages() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.PARTIAL, List.of(
                AgentResult.completed(AgentType.RESUME, "done").withStartTime().withCompletionTime(),
                AgentResult.completed(AgentType.JOB_DISCOVERY, "done").withStartTime().withCompletionTime(),
                AgentResult.skipped(AgentType.MATCHING, "no job"),
                AgentResult.skipped(AgentType.CAREER_ADVISOR, "no job"),
                AgentResult.skipped(AgentType.APPLICATION_ADVISOR, "no job")
        ), 0, 0, false, "partial", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                run,
                EvaluationResult.from(run, null)
        ));

        mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluation.pipelineCompleted").value(false))
                .andExpect(jsonPath("$.evaluation.matchScore").doesNotExist())
                .andExpect(jsonPath("$.evaluation.gapSeverity").doesNotExist())
                .andExpect(jsonPath("$.evaluation.appRecommendation").doesNotExist());
    }

    @Test
    @DisplayName("evaluation is deterministic for identical inputs")
    void evaluation_deterministic() throws Exception {
        OrchestrationRun run = new OrchestrationRun(RunStatus.COMPLETED, FULL_COMPLETED,
                1, 2, true, "ok", null);
        when(orchestrationService.orchestrateWithEvaluation(1L, "job-1")).thenReturn(new OrchestrationService.OrchestrationWithEvaluation(
                run,
                EvaluationResult.from(run, null)
        ));

        String a = mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String b = mockMvc.perform(post("/api/v1/agent/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1L, "job-1")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(a, b, "evaluation must be deterministic");
    }
}
