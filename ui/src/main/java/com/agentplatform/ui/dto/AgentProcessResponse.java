package com.agentplatform.ui.dto;

import com.agentplatform.ui.model.StructuredAgentResult;
import com.agentplatform.ui.model.TaskType;

/**
 * Response body for POST /api/v1/custom/process.
 *
 * <pre>{@code
 * {
 *   "prompt": "string",
 *   "taskType": "SUMMARIZE",
 *   "result": {
 *     "summary": "...",
 *     "keyPoints": null,
 *     "category": null,
 *     "confidence": null
 *   },
 *   "executionTimeMs": 1234
 * }
 * }</pre>
 */
public record AgentProcessResponse(
        String prompt,
        TaskType taskType,
        StructuredAgentResult result,
        long executionTimeMs
) {
}