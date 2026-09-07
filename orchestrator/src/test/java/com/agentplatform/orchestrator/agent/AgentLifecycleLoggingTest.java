package com.agentplatform.orchestrator.agent;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.agentplatform.logging.LoggingContext;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Phase 8.3 agent lifecycle logging contract:
 * orchestration run START/COMPLETE, agent START/COMPLETE/FAILED, durationMs
 * present, and a single runId carried through all events via MDC.
 */
@DisplayName("CareerAgentOrchestrator — lifecycle logging (Phase 8.3)")
class AgentLifecycleLoggingTest {

    private ListAppender<ILoggingEvent> appender;

    // ─── Per-test setup ─────────────────────────────────────────────────────

    @BeforeEach
    void attachAppender() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CareerAgentOrchestrator.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        LoggingContext.clear();
    }

    @AfterEach
    void detachAppender() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CareerAgentOrchestrator.class);
        logger.detachAppender(appender);
        LoggingContext.clear();
    }

    // ─── Stub support ───────────────────────────────────────────────────────

    private static class StubAgent implements CareerAgent {
        private final AgentType type;
        private final Supplier<AgentResult> behaviour;

        StubAgent(AgentType type, Supplier<AgentResult> behaviour) {
            this.type = type;
            this.behaviour = behaviour;
        }

        @Override public AgentType type() { return type; }
        @Override public boolean canExecute(AgentContext context) { return true; }
        @Override public AgentResult execute(AgentRequest request, AgentContext context) {
            return behaviour.get();
        }
    }

    private static List<CareerAgent> passingAgents() {
        List<CareerAgent> agents = new ArrayList<>();
        for (AgentType type : AgentType.values()) {
            agents.add(new StubAgent(type, () -> AgentResult.completed(type, "done")));
        }
        return agents;
    }

    private static List<CareerAgent> withReplaced(List<CareerAgent> base, AgentType type, CareerAgent replacement) {
        List<CareerAgent> out = new ArrayList<>();
        for (CareerAgent a : base) {
            out.add(a.type() == type ? replacement : a);
        }
        return out;
    }

    private AgentContext contextWithInputs() {
        AgentContext ctx = new AgentContext();
        ctx.setCandidateProfile(new CandidateProfile(
                "Alice", null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of("Java"), List.of(),
                List.of(), List.of()));
        ctx.setJob(new Job(
                "j1", "Engineer", null, null, null, List.of("Java"), List.of(),
                null, null, null, "mock", null, null, null));
        return ctx;
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    private List<ILoggingEvent> containing(String substring) {
        return events().stream()
                .filter(e -> e.getFormattedMessage().contains(substring))
                .toList();
    }

    private Set<String> runIdsFromEvents() {
        Set<String> ids = new HashSet<>();
        for (ILoggingEvent e : events()) {
            String rid = e.getMDCPropertyMap().get(LoggingContext.RUN_ID_KEY);
            assertNotNull(rid, () -> "every event must carry a runId: " + e.getFormattedMessage());
            ids.add(rid);
        }
        return ids;
    }

    // ─── D1. Successful run emits START + agent lifecycle with runId ────────

    @Test
    @DisplayName("successful orchestration emits START/COMPLETE with runId and durationMs")
    void successfulRunEmitsLifecycleLogs() {
        CareerAgentOrchestrator orchestrator = new CareerAgentOrchestrator(passingAgents());
        OrchestrationResult result = orchestrator.orchestrate(contextWithInputs());

        assertTrue(result.success());
        assertNotNull(result);

        assertThat(containing("Orchestration RUN START")).hasSize(1);
        assertThat(containing("Orchestration RUN COMPLETE")).hasSize(1);
        assertThat(containing("Orchestration RUN COMPLETE").getFirst().getFormattedMessage())
                .contains("totalDurationMs=");

        assertThat(containing("Agent START: type=RESUME")).isNotEmpty();
        assertThat(containing("Agent COMPLETE: type=RESUME")).isNotEmpty();
        assertThat(containing("Agent COMPLETE").getFirst().getFormattedMessage())
                .contains("durationMs=");

        Set<String> ids = runIdsFromEvents();
        assertEquals(1, ids.size(), "all events must share the same runId");
        assertFalse(ids.iterator().next().isBlank());

        assertNull(LoggingContext.getRunId(), "MDC must be cleared after orchestration");
    }

    // ─── D2. Failed blocking agent emits FAILED with runId and errorCode ────

    @Test
    @DisplayName("failed blocking agent emits FAILED log with runId and errorCode")
    void failedBlockingAgentEmitsFailureLogs() {
        List<CareerAgent> agents = withReplaced(
                passingAgents(), AgentType.RESUME,
                new StubAgent(AgentType.RESUME,
                        () -> AgentResult.failed(AgentType.RESUME, "boom", "RESUME_FAILED")));
        CareerAgentOrchestrator orchestrator = new CareerAgentOrchestrator(agents);

        OrchestrationResult result = orchestrator.orchestrate(contextWithInputs());

        assertFalse(result.success());
        assertTrue(result.blockingFailure());

        List<ILoggingEvent> failed = containing("Agent FAILED");
        assertThat(failed).isNotEmpty();
        assertThat(failed.getFirst().getFormattedMessage()).contains("errorCode=RESUME_FAILED");
        assertThat(failed.getFirst().getFormattedMessage()).contains("durationMs=");
        assertThat(failed.getFirst().getMDCPropertyMap().get(LoggingContext.RUN_ID_KEY))
                .isNotBlank();

        assertNull(LoggingContext.getRunId(), "MDC must be cleared after orchestration");
    }
}