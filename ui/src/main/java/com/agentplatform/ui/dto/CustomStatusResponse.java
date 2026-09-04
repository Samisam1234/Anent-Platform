package com.agentplatform.ui.dto;

/**
 * Response body for GET /api/v1/custom/status.
 *
 * <pre>{@code
 * {
 *   "status": "OK",
 *   "message": "Custom agent endpoint is running"
 * }
 * }</pre>
 */
public record CustomStatusResponse(String status, String message) {
}