package com.agentplatform.rag.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent JPA entity for an ingested RAG document.
 *
 * <p>A document is a coarse-grained unit (title, source, content type) whose
 * full text lives in {@link DocumentChunkEntity} children in chunk order.</p>
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "source")
    private String source;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("chunkIndex ASC")
    private List<DocumentChunkEntity> chunks = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DocumentEntity() {
    }

    public DocumentEntity(String title, String source, String contentType) {
        this.title = title;
        this.source = source;
        this.contentType = contentType;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public List<DocumentChunkEntity> getChunks() {
        return chunks;
    }

    public void setChunks(List<DocumentChunkEntity> chunks) {
        this.chunks = chunks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}