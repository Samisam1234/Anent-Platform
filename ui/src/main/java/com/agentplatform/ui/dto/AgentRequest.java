package com.agentplatform.ui.dto;

import com.agentplatform.ui.model.TaskType;

/**
 * Request body for POST /api/v1/custom/process.
 *
 * <p>{@code taskType} is optional — when null/omitted the endpoint defaults
 * to {@link TaskType#GENERAL}.</p>
 *
 * <p>{@code model} is optional — when null/omitted the backend uses the
 * configured default Ollama model (llama3). Selectable values: {@code llama3}
 * (general) or {@code qwen2.5} (code &amp; JSON).</p>
 *
 * <pre>{@code
 * {
 *   "prompt": "string",
 *   "taskType": "SUMMARIZE",
 *   "model": "qwen2.5"
 * }
 * }</pre>
 */
public record AgentRequest(String prompt, TaskType taskType, String model) {
}