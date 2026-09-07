package com.agentplatform.rag.entity;

import com.agentplatform.memory.entity.VectorUserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

import java.time.Instant;

/**
 * Persistent JPA entity for a single chunk of an ingested RAG document.
 *
 * <p>The {@code embedding} column reuses {@link VectorUserType}, so the
 * PostgreSQL column is emitted as {@code vector(768)} by
 * {@code PostgresVectorDialect} (and {@code varbinary} on H2 by
 * {@code H2VectorDialect}). This mirrors the Phase 8.1 conversation-message
 * mapping exactly.</p>
 *
 * <p>{@code metadata} holds a compact JSON object string (e.g.
 * {@code {"topic":"search"}}) that can be filtered on at query time via
 * PostgreSQL's {@code jsonb} operators.</p>
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "embedding")
    @Type(VectorUserType.class)
    private float[] embedding;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DocumentChunkEntity() {
    }

    public DocumentChunkEntity(DocumentEntity document, int chunkIndex, String content) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DocumentEntity getDocument() {
        return document;
    }

    public void setDocument(DocumentEntity document) {
        this.document = document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}