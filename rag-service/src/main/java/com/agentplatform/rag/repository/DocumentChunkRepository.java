package com.agentplatform.rag.repository;

import com.agentplatform.rag.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {

    List<DocumentChunkEntity> findByDocumentIdOrderByChunkIndexAsc(Long documentId);
}