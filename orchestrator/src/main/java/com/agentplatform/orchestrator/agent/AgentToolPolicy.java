package com.agentplatform.orchestrator.agent;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic access-control policy mapping each logical agent to the set of
 * external tools it may request through the controlled tool layer.
 *
 * <p>The policy is deliberately restrictive. It exists to let an agent request a
 * permitted tool <em>operation</em> — never unrestricted autonomous tool use.
 * Most agents have no external tools at all, and email sending is NOT allowed for
 * any agent: application email sending remains exclusively the responsibility of
 * {@code ApplicationEmailService} (explicit user approval → {@code EmailTools}).</p>
 *
 * <p>PDF NOTES ON EMAIL: because {@code EmailTools} only exposes a sending
 * operation (there is no draft-specific tool in the shared tool set) and sending
 * is a reviewed, user-approved application action, {@code sendEmail} is denied for
 * every agent in the initial policy. This is enforced here and again before any
 * execution in {@link AgentToolOrchestrator}.</p>
 *
 * <p>Rules are derived per {@link AgentType} and the tool name (invariant over
 * time), so {@link #isAllowed} is deterministic and repeatable.</p>
 */
@Component
public class AgentToolPolicy {

    /** Tool name registered by {@link com.agentplatform.tools.ToolRegistry}. */
    public static final String TOOL_GENERATE_IMAGE = "generateImage";

    /** Tool name registered by {@link com.agentplatform.tools.ToolRegistry}. */
    public static final String TOOL_SEND_EMAIL = "sendEmail";

    /** Tool name registered by {@link com.agentplatform.tools.ToolRegistry}. */
    public static final String TOOL_SEND_WHATSAPP = "sendWhatsAppMessage";

    /** All known registered tool names (used to reject unknown tools). */
    public static final Set<String> KNOWN_TOOLS = Set.of(
            TOOL_GENERATE_IMAGE, TOOL_SEND_EMAIL, TOOL_SEND_WHATSAPP);

    /** Tools allowed per agent, in the initial policy. */
    private static final Map<AgentType, Set<String>> ALLOWED = buildAllowedMap();

    private static Map<AgentType, Set<String>> buildAllowedMap() {
        Map<AgentType, Set<String>> map = new LinkedHashMap<>();
        map.put(AgentType.RESUME, Set.of(TOOL_GENERATE_IMAGE));
        map.put(AgentType.JOB_DISCOVERY, Set.of());
        map.put(AgentType.MATCHING, Set.of());
        map.put(AgentType.CAREER_ADVISOR, Set.of());
        map.put(AgentType.APPLICATION_ADVISOR, Set.of());
        return Map.copyOf(map);
    }

    /**
     * Whether {@code agentType} may request {@code toolName}.
     *
     * <p>Returns {@code false} for a {@code null} agentType, a {@code null}/blank
     * tool name, an unknown tool, and any tool outside the agent's allowed set.
     * Denials are deterministic and never consume tool-call budget.</p>
     */
    public boolean isAllowed(AgentType agentType, String toolName) {
        if (agentType == null || toolName == null || toolName.isBlank()) {
            return false;
        }
        if (!KNOWN_TOOLS.contains(toolName)) {
            return false;
        }
        Set<String> allowed = ALLOWED.get(agentType);
        return allowed != null && allowed.contains(toolName);
    }
}
