package com.agentplatform.ui.dto;

/**
 * Request body for POST /api/v1/agent/chat.
 *
 * <pre>{@code
 * {
 *   "query": "Hello, how are you?"
 * }
 * }</pre>
 */
public record ChatRequest(String query) {
}
