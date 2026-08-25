package com.agentplatform.orchestrator.service;

/**
 * Thrown when the orchestrator cannot complete a chat interaction,
 * typically due to an Ollama connectivity problem or model error.
 */
public class AgentChatException extends RuntimeException {

    public AgentChatException(String message) {
        super(message);
    }

    public AgentChatException(String message, Throwable cause) {
        super(message, cause);
    }
}
