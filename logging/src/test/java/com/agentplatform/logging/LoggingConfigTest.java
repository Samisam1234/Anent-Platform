package com.agentplatform.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.URL;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the Phase 8.3 Logback contract:
 * <ul>
 *   <li>{@code logback-spring.xml} loads cleanly into a fresh context with the
 *       correct application/third-party log levels.</li>
 *   <li>An MDC {@code runId} set via {@link LoggingContext} is propagated into
 *       the console output through the configured pattern.</li>
 * </ul>
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("LoggingConfig — logback configuration and MDC console output")
class LoggingConfigTest {

    @AfterEach
    void cleanup() {
        LoggingContext.clear();
        ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
    }

    // ─── A. logback-spring.xml parses cleanly ───────────────────────────────

    @Test
    @DisplayName("logback-spring.xml loads into a fresh LoggerContext without errors")
    void logbackSpringXmlConfigLoads() throws Exception {
        LoggerContext fresh = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(fresh);
        URL config = getClass().getClassLoader().getResource("logback-spring.xml");
        assertNotNull(config, "logback-spring.xml must be on the test classpath");
        configurator.doConfigure(config);

        StatusManager statusManager = fresh.getStatusManager();
        assertFalse(
                statusManager.getCopyOfStatusList().stream().anyMatch(s -> s.getLevel() == Status.ERROR),
                "logback-spring.xml must parse without ERROR status entries");

        assertEquals(Level.INFO, fresh.getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
        assertEquals(Level.INFO, fresh.getLogger("com.agentplatform").getLevel());
        assertEquals(Level.WARN, fresh.getLogger("dev.langchain4j").getLevel());
    }

    // ─── B. MDC runId appears in captured console output ────────────────────

    @Test
    @DisplayName("a runId in the MDC appears in the formatted console output")
    void mdcRunIdAppearsInCapturedConsoleOutput(CapturedOutput output) throws Exception {
        // Load our pattern into the global context so the console appender uses it.
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(ctx);
        URL config = getClass().getClassLoader().getResource("logback-spring.xml");
        assertNotNull(config);
        configurator.doConfigure(config);

        String runId = "run-" + UUID.randomUUID();
        try {
            LoggingContext.setRunId(runId);
            Logger probe = ctx.getLogger("com.agentplatform.logging.LoggingConfigTest.MdcProbe");
            probe.setLevel(Level.INFO);
            probe.info("probe message");
        } finally {
            LoggingContext.clear();
        }

        assertTrue(output.getAll().contains(runId),
                "the captured console output should contain the runId value");
    }
}