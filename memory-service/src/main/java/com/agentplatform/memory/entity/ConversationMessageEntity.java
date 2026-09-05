package com.agentplatform.memory.entity;

import jakarta.persistence.*;

/**
 * Persistent JPA entity for a single conversation message.
 */
@Entity
@Table(name = "conversation_messages")
public class ConversationMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    public ConversationMessageEntity() {
    }

    public ConversationMessageEntity(ConversationEntity conversation, int sequence, String role, String content) {
        this.conversation = conversation;
        this.sequence = sequence;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = java.time.Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ConversationEntity getConversation() {
        return conversation;
    }

    public void setConversation(ConversationEntity conversation) {
        this.conversation = conversation;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.Instant createdAt) {
        this.createdAt = createdAt;
    }
}