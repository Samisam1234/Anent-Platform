CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS memory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(768),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_memory_session ON memory(session_id);

-- pgvector support for conversation_messages (created by Hibernate ddl-auto: update)
-- Add embedding column if not exists (Hibernate may not add vector type automatically)
ALTER TABLE conversation_messages ADD COLUMN IF NOT EXISTS embedding vector(768);
-- Create IVFFLAT index for cosine similarity search
CREATE INDEX IF NOT EXISTS idx_conversation_messages_embedding
    ON conversation_messages USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
