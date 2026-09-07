package com.agentplatform.ui.controller;

import com.agentplatform.logging.LoggingContext;
import com.agentplatform.logging.PiiSanitizer;
import com.agentplatform.orchestrator.service.AgentChatException;
import com.agentplatform.orchestrator.service.AgentChatService;
import com.agentplatform.ui.dto.AgentProcessResponse;
import com.agentplatform.ui.dto.AgentRequest;
import com.agentplatform.ui.dto.CustomStatusResponse;
import com.agentplatform.ui.model.StructuredAgentResult;
import com.agentplatform.ui.model.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * REST controller exposing the custom agent endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/custom/status} — JSON status message</li>
 *   <li>{@code POST /api/v1/custom/process} — structured {@link AgentRequest}
 *   → model response parsed into a {@link StructuredAgentResult}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/custom")
public class CustomAgentController {

    private static final Logger log = LoggerFactory.getLogger(CustomAgentController.class);

    private final AgentChatService agentChatService;

    public CustomAgentController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * Returns a JSON status message.
     *
     * @return 200 OK with a status message
     */
    @GetMapping("/status")
    public ResponseEntity<CustomStatusResponse> status() {
        return ResponseEntity.ok(new CustomStatusResponse("OK", "Custom agent endpoint is running"));
    }

    /**
     * Accepts a structured {@link AgentRequest} and returns the AI model's
     * response as a structured {@link StructuredAgentResult}.
     *
     * <p>The registered {@link TaskType} determines which system prompt is
     * sent to the model (via LangChain4j) so the output lands in the right
     * structured fields. {@code taskType} defaults to
     * {@link TaskType#GENERAL} when omitted, and {@code model} (llama3 /
     * qwen2.5) defaults to the configured Ollama model when omitted.</p>
     *
     * <p>Execution time from request receipt to parsed result is tracked and
     * returned as {@code executionTimeMs}.</p>
     *
     * <p>Error responses are handled by {@link GlobalExceptionHandler}.</p>
     *
     * @param request the process request containing the prompt and task type
     * @return 200 OK with the structured response
     */
    @PostMapping("/process")
    public ResponseEntity<AgentProcessResponse> process(@RequestBody AgentRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }

        LoggingContext.setRunId(UUID.randomUUID().toString());
        long startNanos = System.nanoTime();
        TaskType taskType = request.taskType() != null ? request.taskType() : TaskType.GENERAL;
        log.info("Custom process START: runId={}, taskType={}, promptChars={}",
                LoggingContext.getRunId(), taskType, request.prompt().length());

        try {
            String modelOutput = agentChatService.chat(taskType.systemPrompt(), request.prompt(), request.model());
            StructuredAgentResult result = StructuredAgentResult.fromJson(modelOutput);
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            log.info("Custom process COMPLETE: taskType={}, executionTimeMs={}", taskType, executionTimeMs);
            return ResponseEntity.ok(new AgentProcessResponse(request.prompt(), taskType, result, executionTimeMs));
        } catch (AgentChatException e) {
            log.error("Custom process FAILED: taskType={}, executionTimeMs={}, error={}",
                    taskType, elapsedMs(startNanos), PiiSanitizer.sanitize(e.getMessage()));
            throw e;
        } catch (Exception e) {
            log.error("Custom process FAILED: taskType={}, cause={}, error={}",
                    taskType, e.getClass().getSimpleName(), PiiSanitizer.sanitize(e.getMessage()));
            throw new AgentChatException("The AI model returned an invalid structured response.", e);
        } finally {
            LoggingContext.clear();
        }
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}