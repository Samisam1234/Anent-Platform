package com.agentplatform.memory;

import java.time.Instant;
import java.util.List;

/**
 * An immutable, point-in-time view of a conversation.
 * Implementations keep their own internal state and expose snapshots on demand.
 *
 * @param conversationId stable identifier echoed in API request/response pairs
 * @param messages       ordered conversation history (oldest first)
 * @param createdAt      when the conversation was first seen
 * @param updatedAt      when the conversation was last written to
 */
public record ConversationSnapshot(
        String conversationId,
        List<ConversationMessage> messages,
        Instant createdAt,
        Instant updatedAt) {

    public int size() {
        return messages.size();
    }
}