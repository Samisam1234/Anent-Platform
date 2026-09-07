package com.agentplatform.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the standalone RAG foundation, bound to {@code rag.*}.
 *
 * <p>Defaults are documented inline. RAG remains dormant unless
 * {@code rag.enabled=true} <em>and</em> the {@code postgres} profile is active
 * (see {@link RagStoreConfig}).</p>
 */
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /**
     * Master switch. RAG beans load only when {@code true} and the
     * {@code postgres} Spring profile is active.
     */
    private boolean enabled = false;

    private final Chunk chunk = new Chunk();

    private final Retrieval retrieval = new Retrieval();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    /**
     * Chunking settings used to build the {@link com.agentplatform.rag.chunking.TextChunker}.
     */
    public static class Chunk {

        /** Maximum chunk length in characters. Default {@code 1000}. */
        private int maxSize = 1000;

        /** Characters of overlap carried between consecutive chunks. Default {@code 100}. */
        private int overlap = 100;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }

    /**
     * Retrieval settings.
     */
    public static class Retrieval {

        /**
         * Minimum similarity score ({@code 1 - cosine distance}) a chunk must
         * have to be returned. Default {@code 0.0} (return everything within
         * top-K).
         */
        private double similarityThreshold = 0.0;

        /**
         * Token budget for {@code retrieveForContext}. Default {@code 2000}.
         */
        private int maxContextTokens = 2000;

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getMaxContextTokens() {
            return maxContextTokens;
        }

        public void setMaxContextTokens(int maxContextTokens) {
            this.maxContextTokens = maxContextTokens;
        }
    }
}