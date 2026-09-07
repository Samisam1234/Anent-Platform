package com.agentplatform.rag.service;

import java.util.List;

/**
 * Result of assembling retrieved chunks into an LLM-ready context block.
 *
 * @param query      the query the context was built for
 * @param context    the deterministic, relevance-ordered context text
 * @param sources    the chunks that fit within the token budget
 * @param chunkCount number of chunks in {@code sources}
 */
public record ContextResult(String query,
                            String context,
                            List<ScoredChunk> sources,
                            int chunkCount) {
}