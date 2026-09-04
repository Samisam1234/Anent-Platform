package com.agentplatform.orchestrator.agent;

import com.agentplatform.tools.DefaultToolExecutor;
import com.agentplatform.tools.EmailTools;
import com.agentplatform.tools.ImageTools;
import com.agentplatform.tools.ToolMethod;
import com.agentplatform.tools.ToolRegistry;
import com.agentplatform.tools.WhatsAppTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Controlled tool-orchestration layer for the logical agents.
 *
 * <p>Runs a requested tool operation through a strict gate: validate the
 * requesting agent and tool against the {@link AgentToolPolicy}, validate the
 * arguments, enforce per-agent and per-orchestration call budgets, then execute
 * through the EXISTING {@link DefaultToolExecutor}/{@link ToolRegistry}
 * infrastructure (which in turn invokes the same {@code @Tool} beans —
 * {@link EmailTools}, {@link ImageTools}, {@link WhatsAppTools} — the LLM is
 * described as having). It is NOT unrestricted autonomous tool use.</p>
 *
 * <p>Only {@code RESUME → generateImage} is permitted in the initial policy.
 * Critically, {@code sendEmail} is denied for every agent: application email
 * sending remains exclusively the reviewed, user-approved path
 * ({@code ApplicationEmailService} → {@code EmailTools}) and this layer can never
 * bypass it.</p>
 *
 * <p>The layer is bounded: no recursive tool calls, no tool-triggered agent
 * recursion, no background execution, no retry loops. Every result is a safe
 * structured {@link AgentToolResult} — no stack traces, credentials, private
 * configuration, or full PII are ever surfaced.</p>
 */
@Component
public class AgentToolOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentToolOrchestrator.class);

    /** Maximum tool calls any single agent may consume per orchestration run. */
    public static final int MAX_TOOL_CALLS_PER_AGENT = 2;

    /** Maximum tool calls across an entire orchestration run. */
    public static final int MAX_TOOL_CALLS_PER_ORCHESTRATION = 4;

    private final AgentToolPolicy policy;

    private final DefaultToolExecutor toolExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentToolOrchestrator(AgentToolPolicy policy,
                                 WhatsAppTools whatsAppTools,
                                 ImageTools imageTools,
                                 EmailTools emailTools) {
        this.policy = policy;
        List<ToolMethod> tools = ToolRegistry.defaultTools(whatsAppTools, imageTools, emailTools);
        this.toolExecutor = new DefaultToolExecutor(tools);
    }

    /**
     * Requests a permitted tool operation for an agent. Returns a safe, structured
     * result; never throws for tool failures.
     */
    public AgentToolResult request(AgentToolRequest request, AgentContext context) {
        if (request == null) {
            return AgentToolResult.failure("", "No tool request was supplied.", AgentToolResult.ERR_INVALID_ARGUMENTS);
        }
        if (context == null) {
            return AgentToolResult.failure(request.toolName(),
                    "No orchestration context was supplied.", AgentToolResult.ERR_INVALID_ARGUMENTS);
        }

        // 1. Deterministic policy check (denials never consume budget).
        if (!policy.isAllowed(request.agentType(), request.toolName())) {
            log.info("Tool '{}' denied for agent {}", request.toolName(), request.agentType());
            return AgentToolResult.failure(request.toolName(),
                    "Tool '" + request.toolName() + "' is not permitted for agent " + request.agentType() + ".",
                    AgentToolResult.ERR_TOOL_DENIED);
        }

        // 2. Argument validation (before budget consumption).
        String argError = argumentError(request);
        if (argError != null) {
            return AgentToolResult.failure(request.toolName(),
                    "Tool arguments are invalid.", AgentToolResult.ERR_INVALID_ARGUMENTS);
        }

        // 3. Budget enforcement (per-agent + per-orchestration).
        if (!context.consumeToolCall(request.agentType())) {
            log.info("Tool budget exceeded for agent {}", request.agentType());
            return AgentToolResult.failure(request.toolName(),
                    "Tool call budget exceeded for agent " + request.agentType() + ".",
                    AgentToolResult.ERR_TOOL_BUDGET_EXCEEDED);
        }

        // 4. Execute through the existing executor/registry.
        return execute(request);
    }

    private AgentToolResult execute(AgentToolRequest request) {
        String toolName = request.toolName();
        if (!toolExecutor.toolNames().contains(toolName)) {
            return AgentToolResult.failure(toolName,
                    "Tool '" + toolName + "' is not registered.", AgentToolResult.ERR_TOOL_UNKNOWN);
        }
        try {
            String argsJson = objectMapper.writeValueAsString(request.safeArguments());
            ToolExecutionRequest execRequest = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(argsJson)
                    .build();
            ToolExecutionResultMessage message = toolExecutor.execute(execRequest);
            String text = message.text() == null ? "" : message.text();
            if (isExecutionError(text, toolName)) {
                return AgentToolResult.failure(toolName, safeErrorText(text), AgentToolResult.ERR_TOOL_UNAVAILABLE);
            }
            return AgentToolResult.success(toolName, "Tool '" + toolName + "' completed.", text);
        } catch (Exception e) {
            log.warn("Tool '{}' orchestration failed safely: {}", toolName, e.getMessage());
            return AgentToolResult.failure(toolName,
                    "Tool '" + toolName + "' could not be executed.", AgentToolResult.ERR_TOOL_EXECUTION);
        }
    }

    /**
     * Arg-shape validation for the (few) known tools. No sensitive values are
     * logged or returned. Returns a non-null error code when invalid.
     */
    private static String argumentError(AgentToolRequest request) {
        String requestError = request.validationError();
        if (requestError != null) {
            return requestError;
        }
        String tool = request.toolName();
        Map<String, Object> args = request.arguments();
        switch (tool) {
            case AgentToolPolicy.TOOL_GENERATE_IMAGE -> {
                String prompt = stringArg(args, "prompt");
                if (prompt == null || prompt.isBlank()) {
                    return AgentToolResult.ERR_INVALID_ARGUMENTS;
                }
            }
            case AgentToolPolicy.TOOL_SEND_EMAIL -> {
                String recipient = stringArg(args, "recipient");
                if (recipient == null || recipient.isBlank()) {
                    return AgentToolResult.ERR_INVALID_ARGUMENTS;
                }
            }
            case AgentToolPolicy.TOOL_SEND_WHATSAPP -> {
                String phone = stringArg(args, "phone");
                if (phone == null || phone.isBlank()) {
                    return AgentToolResult.ERR_INVALID_ARGUMENTS;
                }
            }
            default -> {
                return AgentToolResult.ERR_TOOL_UNKNOWN;
            }
        }
        return null;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return null;
        }
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isExecutionError(String text, String toolName) {
        return text.startsWith("Tool '" + toolName + "' failed:")
                || text.startsWith("unknown tool '");
    }

    private static String safeErrorText(String text) {
        String trimmed = text == null ? "Tool execution failed." : text.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }
}
