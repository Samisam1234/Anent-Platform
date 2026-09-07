package com.agentplatform.memory;

import java.util.List;

/**
 * Extended conversation store interface adding vector similarity search capabilities.
 *
 * <p>Implementations backed by pgvector should implement this interface in addition to
 * {@link ConversationStore}. The default in-memory and JPA-backed implementations do not
 * support vector search and therefore do not implement this interface.</p>
 */
public interface VectorSearchableConversationStore {

    /**
     * A message with its similarity score.
     *
     * @param message the conversation message
     * @param score   cosine similarity score (higher = more similar, range [-1, 1])
     */
    record ScoredMessage(ConversationMessage message, double score) {
    }

    /**
     * Finds messages similar to the query within a specific conversation.
     *
     * @param conversationId the conversation to search within
     * @param query          the query text to embed and search for
     * @param limit          maximum number of results to return
     * @return list of scored messages ordered by similarity (highest first)
     */
    List<ScoredMessage> findSimilar(String conversationId, String query, int limit);

    /**
     * Finds messages similar to the query across all conversations.
     *
     * @param query the query text to embed and search for
     * @param limit maximum number of results to return
     * @return list of scored messages ordered by similarity (highest first)
     */
    List<ScoredMessage> findSimilarGlobal(String query, int limit);
}