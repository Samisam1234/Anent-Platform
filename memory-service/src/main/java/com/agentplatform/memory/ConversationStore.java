package com.agentplatform.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Conversational memory abstraction shared by agent services.
 *
 * <p>Conversations are keyed by a caller-supplied {@code conversationId}.
 * Callers that do not yet have an id ask the backend to generate one; servers
 * should {@link Optional}-ise lookups so a fresh id starts empty.</p>
 *
 * <p>Implementations are responsible for thread-safety, bounds (trim size) and
 * lifecycle. The default implementation is in-memory (see
 * {@link InMemoryConversationStore}); a durable/pgvector-backed implementation
 * can be swapped in later without changing agent services.</p>
 */
public interface ConversationStore {

    /**
     * Appends a single message to a conversation, creating the conversation on
     * first write.
     */
    void append(String conversationId, ConversationMessage message);

    /**
     * Replaces the conversation history with {@code messages}.
     */
    void save(String conversationId, List<ConversationMessage> messages);

    /**
     * Returns the latest snapshot, or empty when the conversation does not exist.
     */
    Optional<ConversationSnapshot> snapshot(String conversationId);

    /**
     * Returns the ordered message list (oldest first), or an empty list when the
     * conversation does not exist.
     */
    List<ConversationMessage> messages(String conversationId);

    /**
     * Whether the store currently holds {@code conversationId}.
     */
    boolean exists(String conversationId);

    /**
     * Trims a conversation so that at most {@code maxMessages} remain (keeps the
     * most recent ones). No-op for smaller conversations.
     */
    void trim(String conversationId, int maxMessages);

    /**
     * Removes the conversation entirely.
     */
    void clear(String conversationId);

    /**
     * All conversation ids currently held. Useful for diagnostics and tests.
     */
    Set<String> conversationIds();

    /**
     * Total number of stored conversations.
     */
    int count();

    /**
     * Records when a conversation was last written.
     */
    Instant lastUpdated(String conversationId);
}