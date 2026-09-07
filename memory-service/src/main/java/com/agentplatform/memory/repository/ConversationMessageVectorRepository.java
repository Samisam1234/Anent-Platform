package com.agentplatform.memory.repository;

import com.agentplatform.memory.entity.ConversationMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom repository for pgvector similarity search on conversation messages.
 */
@Repository
public interface ConversationMessageVectorRepository extends JpaRepository<ConversationMessageEntity, Long> {

    /**
     * Finds messages similar to the query vector within a specific conversation.
     * Uses pgvector cosine distance (<=>) operator.
     *
     * @param conversationId the conversation to search within
     * @param queryVector    the query embedding vector
     * @param limit          maximum number of results
     * @return list of [ConversationMessageEntity, similarityScore] ordered by similarity (highest first)
     */
    @Query(value = """
            SELECT cm.id, cm.conversation_id, cm.role, cm.content, cm.sequence,
                   1 - (cm.embedding <=> CAST(:queryVector AS vector)) as similarity
            FROM conversation_messages cm
            WHERE cm.conversation_id = (SELECT id FROM conversations WHERE conversation_id = :conversationId)
              AND cm.embedding IS NOT NULL
            ORDER BY cm.embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilarByConversationId(
            @Param("conversationId") String conversationId,
            @Param("queryVector") float[] queryVector,
            @Param("limit") int limit);

    /**
     * Finds messages similar to the query vector across all conversations.
     * Uses pgvector cosine distance (<=>) operator.
     *
     * @param queryVector the query embedding vector
     * @param limit       maximum number of results
     * @return list of [id, conversationId, role, content, sequence, similarity] ordered by similarity (highest first)
     */
    @Query(value = """
            SELECT cm.id, cm.conversation_id, cm.role, cm.content, cm.sequence,
                   1 - (cm.embedding <=> CAST(:queryVector AS vector)) as similarity
            FROM conversation_messages cm
            WHERE cm.embedding IS NOT NULL
            ORDER BY cm.embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilarGlobal(
            @Param("queryVector") float[] queryVector,
            @Param("limit") int limit);
}