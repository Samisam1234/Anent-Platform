package com.agentplatform.orchestrator.agent;

import com.agentplatform.tools.EmailTools;
import com.agentplatform.tools.ImageTools;
import com.agentplatform.tools.WhatsAppTools;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Verifies the controlled {@link AgentToolOrchestrator}: policy gating, argument
 * validation, bounds, safe structured results, email safety, reuse of the existing
 * tool infrastructure, and that inputs are never mutated.
 */
@DisplayName("AgentToolOrchestrator — controlled tool execution")
class AgentToolOrchestratorTest {

    private static AgentToolOrchestrator realOrchestrator() {
        return new AgentToolOrchestrator(new AgentToolPolicy(),
                new WhatsAppTools(), new ImageTools(), new EmailTools(null));
    }

    private static AgentContext context() {
        return new AgentContext();
    }

    @Test
    @DisplayName("allowed tool execution succeeds (RESUME + generateImage)")
    void allowedToolSucceeds() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage",
                Map.of("prompt", "a robot"), "need an illustration");
        AgentToolResult result = orchestrator.request(req, context());
        assertTrue(result.success());
        assertNotNull(result.output());
        assertTrue(result.output().contains("<img"));
        assertEquals(AgentToolResult.NO_ERROR, result.errorCode());
    }

    @Test
    @DisplayName("tool failure is safe and structured, never a thrown stack trace")
    void toolFailureIsSafe() {
        ImageTools throwing = mock(ImageTools.class);
        whenGenerateImageThrows(throwing);
        AgentToolOrchestrator orchestrator = new AgentToolOrchestrator(
                new AgentToolPolicy(), new WhatsAppTools(), throwing, new EmailTools(null));
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage",
                Map.of("prompt", "boom"), "r");
        AgentToolResult result = orchestrator.request(req, context());
        assertFalse(result.success());
        assertNotNull(result.errorCode());
        assertFalse(result.message().contains("Exception"));
        assertFalse(result.message().contains("boom".substring(0, 2) + "RootCause"));
    }

    private static void whenGenerateImageThrows(ImageTools imageTools) {
        org.mockito.Mockito.doThrow(new RuntimeException("catastrophic internal failure"))
                .when(imageTools).generateImage(anyString());
    }

    @Test
    @DisplayName("tool arguments are validated (blank prompt rejected, budget not consumed)")
    void argumentsValidated() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentContext ctx = context();
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage",
                Map.of("prompt", "   "), "r");
        AgentToolResult result = orchestrator.request(req, ctx);
        assertEquals(AgentToolResult.ERR_INVALID_ARGUMENTS, result.errorCode());
        assertFalse(result.success());
        assertEquals(0, ctx.agentToolCalls(AgentType.RESUME), "invalid args must not consume budget");
    }

    @Test
    @DisplayName("oversized argument rejected")
    void oversizedArgumentRejected() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        String huge = "x".repeat(AgentToolRequest.MAX_ARGUMENT_LENGTH + 1);
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "generateImage",
                Map.of("prompt", huge), "r");
        AgentToolResult result = orchestrator.request(req, context());
        assertEquals(AgentToolResult.ERR_INVALID_ARGUMENTS, result.errorCode());
    }

    @Test
    @DisplayName("per-agent tool call limit is enforced")
    void perAgentLimitEnforced() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentContext ctx = context();
        for (int i = 0; i < AgentToolOrchestrator.MAX_TOOL_CALLS_PER_AGENT; i++) {
            AgentToolResult ok = orchestrator.request(
                    new AgentToolRequest(AgentType.RESUME, "generateImage",
                            Map.of("prompt", "image " + i), "r"), ctx);
            assertTrue(ok.success(), "call " + i + " within limit should succeed");
        }
        AgentToolResult over = orchestrator.request(
                new AgentToolRequest(AgentType.RESUME, "generateImage",
                        Map.of("prompt", "too many"), "r"), ctx);
        assertEquals(AgentToolResult.ERR_TOOL_BUDGET_EXCEEDED, over.errorCode());
        assertEquals(AgentToolOrchestrator.MAX_TOOL_CALLS_PER_AGENT, ctx.agentToolCalls(AgentType.RESUME));
    }

    @Test
    @DisplayName("orchestration-wide tool budget is enforced")
    void orchestrationBudgetEnforced() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentContext ctx = context();
        ctx.setToolCallsRemaining(0);
        AgentToolResult result = orchestrator.request(
                new AgentToolRequest(AgentType.RESUME, "generateImage",
                        Map.of("prompt", "anything"), "r"), ctx);
        assertEquals(AgentToolResult.ERR_TOOL_BUDGET_EXCEEDED, result.errorCode());
        assertEquals(0, ctx.agentToolCalls(AgentType.RESUME));
    }

    @Test
    @DisplayName("JobDiscoveryAgent cannot use email")
    void jobDiscoveryCannotEmail() {
        assertEmailDeniedFor(AgentType.JOB_DISCOVERY);
    }

    @Test
    @DisplayName("MatchingAgent cannot use email")
    void matchingCannotEmail() {
        assertEmailDeniedFor(AgentType.MATCHING);
    }

    @Test
    @DisplayName("CareerAdvisorAgent cannot use email")
    void careerAdvisorCannotEmail() {
        assertEmailDeniedFor(AgentType.CAREER_ADVISOR);
    }

    @Test
    @DisplayName("ApplicationAdvisorAgent cannot send email via tools")
    void applicationAdvisorCannotSendEmail() {
        assertEmailDeniedFor(AgentType.APPLICATION_ADVISOR);
    }

    @Test
    @DisplayName("ResumeAgent cannot use email either")
    void resumeCannotEmail() {
        assertEmailDeniedFor(AgentType.RESUME);
    }

    private static void assertEmailDeniedFor(AgentType type) {
        EmailTools emailTools = mock(EmailTools.class);
        AgentToolOrchestrator orchestrator = new AgentToolOrchestrator(
                new AgentToolPolicy(), new WhatsAppTools(), new ImageTools(), emailTools);
        AgentContext ctx = context();
        AgentToolResult result = orchestrator.request(
                new AgentToolRequest(type, "sendEmail",
                        Map.of("recipient", "x@y.z", "subject", "s", "body", "b"), "send it"), ctx);
        assertFalse(result.success());
        assertEquals(AgentToolResult.ERR_TOOL_DENIED, result.errorCode());
        verify(emailTools, never()).sendEmail(any(), any(), any());
        verify(emailTools, never()).sendEmail(anyString(), anyString(), anyString());
        assertEquals(0, ctx.agentToolCalls(type), "denied calls must not consume budget");
    }

    @Test
    @DisplayName("structural + advisor agents cannot use WhatsApp or image tools either")
    void structuralAgentsNoTools() {
        assertToolDenied(AgentType.JOB_DISCOVERY, AgentToolPolicy.TOOL_GENERATE_IMAGE);
        assertToolDenied(AgentType.MATCHING, AgentToolPolicy.TOOL_GENERATE_IMAGE);
        assertToolDenied(AgentType.CAREER_ADVISOR, AgentToolPolicy.TOOL_GENERATE_IMAGE);
        assertToolDenied(AgentType.APPLICATION_ADVISOR, AgentToolPolicy.TOOL_GENERATE_IMAGE);
        assertToolDenied(AgentType.JOB_DISCOVERY, AgentToolPolicy.TOOL_SEND_WHATSAPP);
    }

    private static void assertToolDenied(AgentType type, String tool) {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentToolResult result = orchestrator.request(
                new AgentToolRequest(type, tool, Map.of("prompt", "x"), "r"), context());
        assertEquals(AgentToolResult.ERR_TOOL_DENIED, result.errorCode());
    }

    @Test
    @DisplayName("undisclosed email body is not repeated in a denied result (no PII leakage)")
    void deniedResultDoesNotLeakSensitiveContent() {
        EmailTools emailTools = mock(EmailTools.class);
        AgentToolOrchestrator orchestrator = new AgentToolOrchestrator(
                new AgentToolPolicy(), new WhatsAppTools(), new ImageTools(), emailTools);
        String secretBody = "ssn=123-45-6789 private@example.com";
        AgentToolResult result = orchestrator.request(
                new AgentToolRequest(AgentType.APPLICATION_ADVISOR, "sendEmail",
                        Map.of("recipient", "someone@example.com", "subject", "app", "body", secretBody),
                        "please send"),
                context());
        assertFalse(result.success());
        assertNotEquals(secretBody, result.message());
        assertFalse(result.message().contains("123-45-6789"));
        assertFalse(result.output().contains(secretBody));
    }

    @Test
    @DisplayName("unknown tool name is rejected")
    void unknownToolRejected() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentToolRequest req = new AgentToolRequest(AgentType.RESUME, "shellExec",
                Map.of("cmd", "rm -rf /"), "r");
        AgentToolResult result = orchestrator.request(req, context());
        assertEquals(AgentToolResult.ERR_TOOL_DENIED, result.errorCode());
        assertFalse(result.success());
    }

    @Test
    @DisplayName("candidate profile and job are never mutated by the tool layer")
    void inputsNotMutated() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentContext ctx = context();
        ctx.setCandidateProfile(null);
        ctx.setToolCallsRemaining(4);
        AgentToolResult ok = orchestrator.request(
                new AgentToolRequest(AgentType.RESUME, "generateImage",
                        Map.of("prompt", "diagram"), "r"), ctx);
        assertTrue(ok.success());
        assertEquals(null, ctx.candidateProfile());
        assertTrue(ctx.agentResults().isEmpty(), "tool orchestration must not record agent results");
    }

    @Test
    @DisplayName("tool result errors never crash the orchestrator")
    void toolErrorsDoNotCrash() {
        ImageTools throwing = mock(ImageTools.class);
        whenGenerateImageThrows(throwing);
        AgentToolOrchestrator orchestrator = new AgentToolOrchestrator(
                new AgentToolPolicy(), new WhatsAppTools(), throwing, new EmailTools(null));
        for (int i = 0; i < 3; i++) {
            AgentToolResult result = orchestrator.request(
                    new AgentToolRequest(AgentType.RESUME, "generateImage",
                            Map.of("prompt", "p" + i), "r"), context());
            assertFalse(result.success());
            assertNotNull(result.errorCode());
        }
    }

    @Test
    @DisplayName("existing ToolRegistry and DefaultToolExecutor are reused for execution")
    void reusesExistingToolInfrastructure() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentToolResult result = orchestrator.request(
                new AgentToolRequest(AgentType.RESUME, "generateImage",
                        Map.of("prompt", "skyline"), "r"), context());
        assertTrue(result.success());
        assertTrue(result.output().contains("picsum.photos"), "output from the existing ImageTools");
    }

    @Test
    @DisplayName("full orchestration stays bounded for mixed requests")
    void orchestrationStaysBounded() {
        AgentToolOrchestrator orchestrator = realOrchestrator();
        AgentContext ctx = context();
        int denials = 0;
        for (AgentType type : List.of(AgentType.JOB_DISCOVERY, AgentType.MATCHING,
                AgentType.CAREER_ADVISOR, AgentType.APPLICATION_ADVISOR, AgentType.RESUME)) {
            AgentToolResult r = orchestrator.request(
                    new AgentToolRequest(type, "sendEmail",
                            Map.of("recipient", "x@y.z", "subject", "s", "body", "b"), "send"), ctx);
            if (r.errorCode() == AgentToolResult.ERR_TOOL_DENIED) {
                denials++;
            }
        }
        assertEquals(5, denials, "all email send requests denied");
        assertEquals(AgentToolOrchestrator.MAX_TOOL_CALLS_PER_ORCHESTRATION,
                ctx.toolCallsRemaining(),
                "denied requests must not consume the orchestration tool budget");
    }
}
