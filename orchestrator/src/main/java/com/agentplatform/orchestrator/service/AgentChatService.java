package com.agentplatform.orchestrator.service;

import com.agentplatform.core.ai.AiErrorClassifier;
import com.agentplatform.core.config.OllamaChatModelFactory;
import com.agentplatform.memory.ConversationMessage;
import com.agentplatform.memory.ConversationStore;
import com.agentplatform.tools.DefaultToolExecutor;
import com.agentplatform.tools.EmailTools;
import com.agentplatform.tools.ImageTools;
import com.agentplatform.tools.ToolMethod;
import com.agentplatform.tools.ToolRegistry;
import com.agentplatform.tools.WhatsAppTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core agent chat service.
 *
 * <p>Coordinates conversation-aware chat (history threading + persistent
 * transcript via {@link ConversationStore}), stateless backward-compatible
 * calls, per-request dynamic model selection through
 * {@link OllamaChatModelFactory}, image-intent short-circuits, a bounded
 * tool-calling loop over the {@code com.agentplatform.tools} beans, and
 * provider-aware failure classification ({@link AiErrorClassifier}, labelled
 * "Ollama").</p>
 *
 * <p>A turn is persisted only after the model call (and any tool loop)
 * completes successfully; failures leave no trace in history.</p>
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    /** Maximum model↔tool round-trips before a turn is declared non-convergent. */
    public static final int MAX_TOOL_ROUNDS = 6;

    private final OllamaChatModelFactory modelFactory;

    private final ConversationStore store;

    private final ImageTools imageTools;

    private final DefaultToolExecutor toolExecutor;

    private final List<ToolSpecification> toolSpecifications;

    public AgentChatService(OllamaChatModelFactory modelFactory, ConversationStore store) {
        this.modelFactory = modelFactory;
        this.store = store;

        WhatsAppTools whatsAppTools = new WhatsAppTools();
        this.imageTools = new ImageTools();
        EmailTools emailTools = new EmailTools(null);
        List<ToolMethod> tools = ToolRegistry.defaultTools(whatsAppTools, this.imageTools, emailTools);
        this.toolExecutor = new DefaultToolExecutor(tools);

        List<ToolSpecification> specs = new ArrayList<>();
        try {
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(whatsAppTools));
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(this.imageTools));
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(emailTools));
        } catch (Exception ex) {
            log.warn("Failed to introspect tool specifications; tool-calling offered to the model only: {}",
                    ex.getMessage());
        }
        this.toolSpecifications = List.copyOf(specs);
    }

    // ─── Conversation-aware entry point ──────────────────────────────────────

    /**
     * Chats with conversation history: loads the stored transcript for
     * {@code conversationId} (generating one when missing), threads the new
     * user turn in, and persists both audio turns only on success.
     */
    public AgentChatResult chatWithHistory(String conversationId, String systemPrompt, String userMessage) {
        return chatWithHistory(conversationId, systemPrompt, userMessage, null);
    }

    /**
     * As {@link #chatWithHistory(String, String, String)} but with an explicit
     * {@code model} forwarded to the factory (null → provider default).
     */
    public AgentChatResult chatWithHistory(String conversationId, String systemPrompt,
                                           String userMessage, String model) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("User message must not be blank");
        }

        String effectiveId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        // Image-intent prompts are short-circuited so the <img> HTML always
        // renders regardless of whether the selected model supports tools.
        if (ImageTools.isImageRequest(userMessage)) {
            String html = this.imageTools.generateImage(userMessage);
            persistTurn(effectiveId, userMessage, html);
            return new AgentChatResult(effectiveId, html);
        }

        List<ChatMessage> messages = buildRequestMessages(systemPrompt, userMessage, effectiveId);

        try {
            String responseText = runModelWithTools(messages, model);
            persistTurn(effectiveId, userMessage, responseText);
            return new AgentChatResult(effectiveId, responseText);
        } catch (AgentChatException ex) {
            throw ex;
        } catch (Exception ex) {
            throw classifiedFailure(ex);
        }
    }

    // ─── Backward-compatible stateless entry points ──────────────────────────

    /** Stateless chat with no system prompt. */
    public String chat(String userMessage) {
        return chat(null, userMessage, null);
    }

    /** Stateless chat with a system prompt. */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, null);
    }

    /**
     * Stateless chat with a system prompt and an explicit model. Never writes
     * conversation history.
     */
    public String chat(String systemPrompt, String userMessage, String model) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("User message must not be blank");
        }
        if (ImageTools.isImageRequest(userMessage)) {
            return this.imageTools.generateImage(userMessage);
        }

        List<ChatMessage> messages = buildRequestMessages(systemPrompt, userMessage, null);
        try {
            return runModelWithTools(messages, model);
        } catch (AgentChatException ex) {
            throw ex;
        } catch (Exception ex) {
            throw classifiedFailure(ex);
        }
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private List<ChatMessage> buildRequestMessages(String systemPrompt, String userMessage, String conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        if (conversationId != null) {
            for (ConversationMessage stored : store.messages(conversationId)) {
                if (stored.isUser()) {
                    messages.add(UserMessage.from(stored.content()));
                } else {
                    messages.add(AiMessage.from(stored.content()));
                }
            }
        }
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(0, SystemMessage.from(systemPrompt));
        }
        messages.add(UserMessage.from(userMessage));
        return messages;
    }

    private String runModelWithTools(List<ChatMessage> messages, String model) {
        ChatModel chatModel = modelFactory.chatModel(model);
        int toolRounds = 0;
        ChatResponse response;
        while (true) {
            response = chatModel.chat(ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecifications)
                    .build());
            AiMessage aiMessage = response.aiMessage();
            if (aiMessage == null || !aiMessage.hasToolExecutionRequests()) {
                return extractResponseText(aiMessage);
            }
            if (++toolRounds >= MAX_TOOL_ROUNDS) {
                throw new AgentChatException(
                        "The model did not converge after " + MAX_TOOL_ROUNDS
                                + " tool rounds; aborting the turn.");
            }
            messages.add(aiMessage);
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                ToolExecutionResultMessage result = toolExecutor.execute(request);
                log.debug("Executed tool '{}' (id={})", request.name(), request.id());
                messages.add(result);
            }
        }
    }

    private static String extractResponseText(AiMessage aiMessage) {
        String text = aiMessage == null ? null : aiMessage.text();
        if (text == null || text.isBlank()) {
            throw new AgentChatException("The model returned an empty response.");
        }
        return text.trim();
    }

    private void persistTurn(String conversationId, String userMessage, String response) {
        store.append(conversationId, ConversationMessage.user(userMessage));
        store.append(conversationId, ConversationMessage.ai(response));
    }

    private static AgentChatException classifiedFailure(Exception ex) {
        String message = AiErrorClassifier.classify(ex, "Ollama").message();
        log.error("Agent chat failed ({}): {}", "Ollama", ex.getMessage());
        return new AgentChatException(message, ex);
    }
}