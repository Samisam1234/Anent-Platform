package com.agentplatform.rag.service;

import java.time.Instant;

/**
 * Result of ingesting a document into the RAG index.
 *
 * @param documentId persisted document id
 * @param title      document title
 * @param chunkCount number of chunks created and embedded
 * @param createdAt  when the document was persisted
 */
public record DocumentIndexResult(long documentId, String title, int chunkCount, Instant createdAt) {
}