package com.agentplatform.tools;

import java.util.List;
import java.util.Map;

/**
 * Builds the canonical {@link ToolMethod} bindings from the tool beans so the
 * orchestrator can hand them to a {@link DefaultToolExecutor}.
 *
 * <p>This lets the tool-calling loop run against the exact same
 * {@code @Tool} implementations the model is described as having — a single
 * source of truth for the agent's capabilities.</p>
 */
public final class ToolRegistry {

    private ToolRegistry() {
    }

    /**
     * Returns the default tool set. {@code emailTools} may be {@code null} (the
     * email tool then returns a "not configured" message, which is safe).
     */
    public static List<ToolMethod> defaultTools(WhatsAppTools whatsAppTools,
                                                ImageTools imageTools,
                                                EmailTools emailTools) {
        return List.of(
                ToolMethod.named("sendWhatsAppMessage", args ->
                        whatsAppTools.sendWhatsAppMessage(
                                stringArg(args, "phone"),
                                stringArg(args, "message"))),
                ToolMethod.named("generateImage", args ->
                        imageTools.generateImage(stringArg(args, "prompt"))),
                ToolMethod.named("sendEmail", args ->
                        emailTools.sendEmail(
                                stringArg(args, "recipient"),
                                stringArg(args, "subject"),
                                stringArg(args, "body")))
        );
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }
}