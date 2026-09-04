package com.agentplatform.memory;

/**
 * A single conversational message stored in conversation history.
 *
 * @param role    {@code "user"} or {@code "ai"} (mirrors LangChain4j roles)
 * @param content the message text
 */
public record ConversationMessage(String role, String content) {

    public static ConversationMessage user(String content) {
        return new ConversationMessage("user", content);
    }

    public static ConversationMessage ai(String content) {
        return new ConversationMessage("ai", content);
    }

    public boolean isUser() {
        return "user".equals(role);
    }
}