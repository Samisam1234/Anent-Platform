package com.agentplatform.rag.config;

import com.agentplatform.rag.chunking.TextChunker;
import com.agentplatform.rag.repository.DocumentChunkRepository;
import com.agentplatform.rag.repository.DocumentChunkVectorRepository;
import com.agentplatform.rag.repository.DocumentRepository;
import com.agentplatform.rag.service.PgVectorRagService;
import com.agentplatform.rag.service.RagService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration that wires the RAG service.
 *
 * <p>Beans are created only when {@code rag.enabled=true} AND the
 * {@code postgres} Spring profile is active — mirroring the Phase 8.1
 * {@code ConversationStoreConfig} pattern. RAG stays completely dormant
 * otherwise.</p>
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagStoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true")
    @Profile("postgres")
    public TextChunker ragTextChunker(RagProperties props) {
        return new TextChunker(props.getChunk().getMaxSize(), props.getChunk().getOverlap());
    }

    @Bean
    @ConditionalOnProperty(prefix = "rag", name = "enabled", havingValue = "true")
    @Profile("postgres")
    public RagService pgVectorRagService(DocumentRepository documentRepository,
                                         DocumentChunkRepository chunkRepository,
                                         DocumentChunkVectorRepository vectorRepository,
                                         EmbeddingModel embeddingModel,
                                         TextChunker chunker,
                                         RagProperties props) {
        return new PgVectorRagService(documentRepository, chunkRepository, vectorRepository,
                embeddingModel, chunker, props.getRetrieval().getSimilarityThreshold());
    }
}