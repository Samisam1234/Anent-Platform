package com.agentplatform.orchestrator.service;

/**
 * Result of a conversation-aware chat call: the model's response plus the
 * stable {@code conversationId} that was used (generated on first call) so the
 * client and the conversation store stay in sync.
 */
public record AgentChatResult(String conversationId, String response) {
}