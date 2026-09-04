package com.agentplatform.orchestrator.agent;

/**
 * A safe, structured outcome of a controlled tool operation.
 *
 * <p>Never carries internal exceptions, stack traces, SMTP credentials, private
 * configuration, or full PII — only a stable {@code errorCode}, a short safe
 * {@code message}, and the tool's {@code output} (a string) on success.</p>
 */
public record AgentToolResult(
        String toolName,
        boolean success,
        String message,
        String output,
        String errorCode
) {

    public static final String NO_ERROR = null;

    /** Tool denied by the {@link AgentToolPolicy}. */
    public static final String ERR_TOOL_DENIED = "TOOL_DENIED";

    /** Tool call budget exhausted (per-agent or per-orchestration). */
    public static final String ERR_TOOL_BUDGET_EXCEEDED = "TOOL_BUDGET_EXCEEDED";

    /** Unknown or unregistered tool name. */
    public static final String ERR_TOOL_UNKNOWN = "TOOL_UNKNOWN";

    /** Invalid / missing / oversized arguments for the tool. */
    public static final String ERR_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";

    /** Underlying tool execution returned an error. */
    public static final String ERR_TOOL_EXECUTION = "TOOL_EXECUTION_ERROR";

    /** Tool unavailable (not configured / not present). */
    public static final String ERR_TOOL_UNAVAILABLE = "TOOL_UNAVAILABLE";

    public AgentToolResult {
        toolName = toolName == null ? "" : toolName.trim();
        message = message == null ? "" : message.trim();
        output = output == null ? "" : output;
        errorCode = errorCode; // keep stable; may be NO_ERROR
    }

    public static AgentToolResult success(String toolName, String message, String output) {
        return new AgentToolResult(toolName, true, message, output, NO_ERROR);
    }

    public static AgentToolResult failure(String toolName, String message, String errorCode) {
        return new AgentToolResult(toolName, false, message, "", errorCode);
    }
}
