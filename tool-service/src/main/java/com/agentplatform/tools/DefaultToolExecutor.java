package com.agentplatform.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes a model-requested tool call against the registered tools and
 * produces a {@link ToolExecutionResultMessage} to feed back to the model.
 *
 * <p>Unknown/hallucinated tool names do <em>not</em> fail the turn — they are
 * returned to the model as a {@code "unknown tool '…'"} result listing the
 * available tools so the model can self-correct. The bounded call loop that
 * uses this executor lives in the orchestrator's {@code AgentChatService}
 * ({@code MAX_TOOL_ROUNDS} rounds).</p>
 *
 * <p>JSON argument parsing tolerates malformed payloads (returns an explanatory
 * result string rather than throwing).</p>
 */
public class DefaultToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> STRING_MAP = new TypeReference<>() {
    };

    private final Map<String, ToolMethod> tools;

    public DefaultToolExecutor(List<ToolMethod> tools) {
        Map<String, ToolMethod> byName = new HashMap<>();
        for (ToolMethod tool : tools) {
            byName.put(tool.name(), tool);
        }
        this.tools = Map.copyOf(byName);
    }

    /**
     * Executes {@code request} against the registered tools and returns the
     * result message to append to the conversation.
     */
    public ToolExecutionResultMessage execute(ToolExecutionRequest request) {
        if (request == null) {
            return ToolExecutionResultMessage.from(
                    "", "", "Tool execution request was null.");
        }
        String toolName = request.name() == null ? "" : request.name();
        ToolMethod tool = tools.get(toolName);
        if (tool == null) {
            return ToolExecutionResultMessage.from(request, unknownToolMessage(toolName));
        }
        try {
            Map<String, Object> args = parseArguments(request.arguments());
            String result = tool.execute(args);
            return ToolExecutionResultMessage.from(request, result);
        } catch (Exception ex) {
            log.warn("Tool '{}' failed: {}", toolName, ex.getMessage());
            return ToolExecutionResultMessage.from(
                    request, "Tool '" + toolName + "' failed: " + ex.getMessage());
        }
    }

    /** The registered tool names, ordered for deterministic diagnostics. */
    public List<String> toolNames() {
        return tools.keySet().stream().sorted().collect(Collectors.toUnmodifiableList());
    }

    public int size() {
        return tools.size();
    }

    private String unknownToolMessage(String toolName) {
        String available = "Available tools: " + String.join(", ", toolNames());
        return "unknown tool '" + toolName + "'. " + available;
    }

    private static Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, STRING_MAP);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            Map<String, Object> result = new HashMap<>();
            result.put("raw", json);
            return result;
        }
    }
}