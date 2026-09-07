package com.agentplatform.rag.service;

import java.util.List;
import java.util.Map;

/**
 * Standalone retrieval-augmented generation foundation: document ingestion,
 * chunking, embedding, pgvector similarity search, and context assembly.
 *
 * <p>The default implementation is {@link PgVectorRagService}. RAG is not wired
 * into any chat pipeline in this phase — it is a reusable core.</p>
 */
public interface RagService {

    /**
     * Indexes a document with no source/content-type information.
     */
    DocumentIndexResult indexDocument(String title, String content, Map<String, String> metadata);

    /**
     * Indexes a document: chunks its content, embeds every chunk with the
     * configured {@code EmbeddingModel}, and persists document + chunks.
     */
    DocumentIndexResult indexDocument(String title, String source, String contentType,
                                      String content, Map<String, String> metadata);

    /**
     * Returns the top-{@code topK} chunks whose embeddings are most similar to
     * the query embedding, optionally restricted by {@code filters}.
     * Results are ordered by score (highest first) and filtered by the
     * configured similarity threshold.
     */
    List<ScoredChunk> retrieve(String query, int topK, RetrievalFilter filters);

    /**
     * Builds a deterministic, relevance-ordered context string from chunks
     * similar to the query, stopping when the {@code maxTokens} budget is
     * reached (approximate token estimate: 4 characters per token).
     */
    ContextResult retrieveForContext(String query, int maxTokens);
}