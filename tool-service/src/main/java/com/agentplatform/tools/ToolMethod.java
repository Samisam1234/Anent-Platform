package com.agentplatform.tools;

import java.util.Map;
import java.util.function.Function;

/**
 * A single executable tool binding: a stable name plus the logic that turns a
 * JSON-encoded argument object into a string result for the LLM.
 *
 * <p>Instances are built from the {@code @Tool}-annotated beans in
 * {@code com.agentplatform.tools} (e.g. {@link WhatsAppTools},
 * {@link ImageTools}, {@link EmailTools}) so the orchestrator can execute model
 * tool calls against the same implementations the model itself is offered.</p>
 */
public class ToolMethod {

    private final String name;

    private final Function<Map<String, Object>, String> delegate;

    /**
     * @param name     the tool name the model uses to request it
     * @param delegate receives the parsed argument map and produces the result
     */
    public ToolMethod(String name, Function<Map<String, Object>, String> delegate) {
        this.name = name;
        this.delegate = delegate;
    }

    public String name() {
        return name;
    }

    public String execute(Map<String, Object> arguments) {
        return delegate.apply(arguments);
    }

    /** Convenience factory mirroring the original functional-interface usage. */
    public static ToolMethod named(String name, Function<Map<String, Object>, String> delegate) {
        return new ToolMethod(name, delegate);
    }
}