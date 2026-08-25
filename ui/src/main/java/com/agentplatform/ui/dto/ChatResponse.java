package com.agentplatform.ui.dto;

/**
 * Response body for POST /api/v1/agent/chat.
 *
 * <pre>{@code
 * {
 *   "response": "I'm doing well, thank you for asking!"
 * }
 * }</pre>
 */
public record ChatResponse(String response) {
}
