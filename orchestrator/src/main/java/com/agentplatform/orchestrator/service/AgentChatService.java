package com.agentplatform.orchestrator.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core agent chat service.
 *
 * <p>Receives a plain user query string, delegates it to the configured
 * {@link ChatLanguageModel} (Ollama), and returns the model's final text
 * response.</p>
 *
 * <p>This service intentionally has no RAG, memory, tool-calling, or
 * multi-agent orchestration logic — those will be added in future phases.</p>
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    private final ChatLanguageModel chatLanguageModel;

    public AgentChatService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * Sends {@code userMessage} to the LLM and returns the response text.
     *
     * @param userMessage the user's query (must not be blank)
     * @return the model's final response text
     * @throws IllegalArgumentException if {@code userMessage} is null or blank
     * @throws AgentChatException       if the Ollama backend is unreachable or
     *                                  returns an error
     */
    public String chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("User message must not be blank");
        }

        log.debug("Sending query to chat model: {}", userMessage);

        try {
            String response = chatLanguageModel.chat(userMessage);
            log.debug("Received response from chat model (length={})", response.length());
            return response;
        } catch (Exception ex) {
            log.error("Failed to get response from Ollama chat model: {}", ex.getMessage());
            throw new AgentChatException(
                    "Failed to communicate with the AI model. " +
                    "Please ensure Ollama is running at the configured URL.", ex);
        }
    }
}
