package com.agentplatform.rag.service;

/**
 * A single retrieval hit: a chunk scored by cosine similarity.
 *
 * @param chunkId       persisted chunk id
 * @param documentId    owning document id
 * @param documentTitle owning document title
 * @param chunkIndex    chunk order within the document
 * @param content       chunk text
 * @param score         similarity score ({@code 1 - cosine distance})
 * @param metadata      raw JSON metadata string, or {@code null}
 */
public record ScoredChunk(long chunkId,
                          long documentId,
                          String documentTitle,
                          int chunkIndex,
                          String content,
                          double score,
                          String metadata) {
}