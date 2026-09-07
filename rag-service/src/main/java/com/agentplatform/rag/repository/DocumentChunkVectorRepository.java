package com.agentplatform.rag.repository;

import com.agentplatform.rag.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Custom repository for pgvector cosine-similarity search over document chunks.
 *
 * <p>Reuses the SQL/operator pattern from
 * {@code ConversationMessageVectorRepository}: the {@code <=>} cosine-distance
 * operator with similarity expressed as {@code 1 - cosine_distance}, and the
 * query vector passed as an unchecked raw {@code float[]} bound with
 * {@code CAST(:queryVector AS vector)}.</p>
 *
 * <p>The optional filter parameters are encoded with the PostgreSQL idiom
 * {@code (:param IS NULL OR col = :param)} so a single query serves global,
 * source- and content-type-scoped retrieval. The metadata filter runs against
 * the chunk's JSON text via {@code ::jsonb ->>}.</p>
 */
@Repository
public interface DocumentChunkVectorRepository extends JpaRepository<DocumentChunkEntity, Long> {

    /**
     * Finds chunks similar to the query vector across all (optionally filtered)
     * documents, ordered by score (highest first).
     *
     * @param queryVector   the query embedding vector
     * @param source        optional document source filter, or {@code null}
     * @param contentType   optional document content type filter, or {@code null}
     * @param metadataKey   optional chunk metadata JSON key to filter on, or {@code null}
     * @param metadataValue required value for {@code metadataKey}, or {@code null}
     * @param limit         maximum number of results
     * @return rows of [id, document_id, title, chunk_index, content, metadata, similarity]
     */
    @Query(value = """
            SELECT dc.id, dc.document_id, d.title, dc.chunk_index, dc.content, dc.metadata,
                   1 - (dc.embedding <=> CAST(:queryVector AS vector)) as similarity
            FROM document_chunks dc
            JOIN documents d ON d.id = dc.document_id
            WHERE dc.embedding IS NOT NULL
              AND (:source IS NULL OR d.source = :source)
              AND (:contentType IS NULL OR d.content_type = :contentType)
              AND (:metadataKey IS NULL
                   OR (dc.metadata IS NOT NULL AND (dc.metadata::jsonb ->> :metadataKey) = :metadataValue))
            ORDER BY dc.embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findSimilar(
            @Param("queryVector") float[] queryVector,
            @Param("source") String source,
            @Param("contentType") String contentType,
            @Param("metadataKey") String metadataKey,
            @Param("metadataValue") String metadataValue,
            @Param("limit") int limit);
}