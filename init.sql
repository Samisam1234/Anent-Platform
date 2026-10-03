CREATE EXTENSION IF NOT EXISTS vector;

-- pgvector support for conversation_messages (created by Hibernate ddl-auto: update)
-- Add embedding column if not exists (Hibernate may not add vector type automatically)
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS embedding vector(768);
-- Create IVFFLAT index for cosine similarity search
CREATE INDEX IF NOT EXISTS idx_conversation_messages_embedding
    ON conversation_messages USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Phase 8.2: RAG document storage (also created by Hibernate ddl-auto: update; kept for cold-start)
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    source VARCHAR(255),
    content_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768),
    metadata TEXT,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_document_chunks_document ON document_chunks(document_id);
-- IVFFLAT index for cosine similarity search (same pattern as conversation_messages)
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
    ON document_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
