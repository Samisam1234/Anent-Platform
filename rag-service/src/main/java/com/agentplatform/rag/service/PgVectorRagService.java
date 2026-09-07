package com.agentplatform.rag.service;

import com.agentplatform.rag.chunking.TextChunker;
import com.agentplatform.rag.entity.DocumentChunkEntity;
import com.agentplatform.rag.entity.DocumentEntity;
import com.agentplatform.rag.repository.DocumentChunkRepository;
import com.agentplatform.rag.repository.DocumentChunkVectorRepository;
import com.agentplatform.rag.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PostgreSQL/pgvector-backed {@link RagService}.
 *
 * <p>Uses the existing {@link EmbeddingModel} bean (nomic-embed-text, 768
 * dimensions) to embed each chunk, persists document and chunks through JPA,
 * and searches via the {@code <=>} cosine-distance operator reusing the Phase
 * 8.1 pgvector pattern.</p>
 *
 * <p>Activated when {@code rag.enabled=true} and the {@code postgres} Spring
 * profile is active (see {@code RagStoreConfig}).</p>
 */
public class PgVectorRagService implements RagService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorRagService.class);

    /** Retrieval cap for context building; the token budget decides how many fit. */
    private static final int MAX_RETRIEVE_CAP = 50;

    /** Rough character-to-token estimate used for context budgeting. */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    /** Tokens of header/overhead attributed to each chunk in the context block. */
    private static final int OVERHEAD_TOKENS = 8;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentChunkVectorRepository vectorRepository;
    private final EmbeddingModel embeddingModel;
    private final TextChunker chunker;
    private final double similarityThreshold;

    public PgVectorRagService(DocumentRepository documentRepository,
                              DocumentChunkRepository chunkRepository,
                              DocumentChunkVectorRepository vectorRepository,
                              EmbeddingModel embeddingModel,
                              TextChunker chunker,
                              double similarityThreshold) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorRepository = vectorRepository;
        this.embeddingModel = embeddingModel;
        this.chunker = chunker;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    @Transactional
    public DocumentIndexResult indexDocument(String title, String content, Map<String, String> metadata) {
        return indexDocument(title, null, null, content, metadata);
    }

    @Override
    @Transactional
    public DocumentIndexResult indexDocument(String title, String source, String contentType,
                                             String content, Map<String, String> metadata) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        List<String> chunks = chunker.chunk(content);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("content produced no chunks");
        }

        DocumentEntity document = new DocumentEntity(title, source, contentType);
        String metadataJson = toMetadataJson(metadata);
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunkEntity chunk = new DocumentChunkEntity(document, i, chunks.get(i));
            chunk.setMetadata(metadataJson);
            chunk.setEmbedding(embed(chunks.get(i)));
            document.getChunks().add(chunk);
        }

        DocumentEntity saved = documentRepository.save(document);
        log.info("Indexed document '{}' as id={} with {} chunks", title, saved.getId(), chunks.size());
        return new DocumentIndexResult(saved.getId(), title, chunks.size(), saved.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoredChunk> retrieve(String query, int topK, RetrievalFilter filters) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }

        RetrievalFilter f = filters != null ? filters : RetrievalFilter.none();
        boolean metadataFiltered = f.metadataKey() != null && f.metadataValue() != null;

        float[] queryEmbedding = embed(query);
        List<Object[]> rows = vectorRepository.findSimilar(
                queryEmbedding,
                f.source(),
                f.contentType(),
                metadataFiltered ? f.metadataKey() : null,
                metadataFiltered ? f.metadataValue() : null,
                topK);

        return rows.stream()
                .map(this::toScoredChunk)
                .filter(c -> c.score() >= similarityThreshold)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ContextResult retrieveForContext(String query, int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }

        List<ScoredChunk> chunks = retrieve(query, MAX_RETRIEVE_CAP, RetrievalFilter.none());

        StringBuilder context = new StringBuilder();
        List<ScoredChunk> included = new ArrayList<>();
        int usedTokens = 0;
        for (ScoredChunk chunk : chunks) {
            int tokens = estimateTokens(chunk.content()) + OVERHEAD_TOKENS;
            if (!included.isEmpty() && usedTokens + tokens > maxTokens) {
                break;
            }
            if (!included.isEmpty()) {
                context.append("\n\n");
            }
            context.append("Source: ").append(chunk.documentTitle())
                    .append(" [doc=").append(chunk.documentId())
                    .append(", chunk=").append(chunk.chunkIndex())
                    .append(", score=").append(String.format(Locale.ROOT, "%.3f", chunk.score()))
                    .append("]\n")
                    .append(chunk.content());
            usedTokens += tokens;
            included.add(chunk);
        }

        return new ContextResult(query, context.toString(), List.copyOf(included), included.size());
    }

    private int estimateTokens(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN_ESTIMATE));
    }

    private ScoredChunk toScoredChunk(Object[] row) {
        // row: [id, document_id, title, chunk_index, content, metadata, similarity]
        long id = ((Number) row[0]).longValue();
        long documentId = ((Number) row[1]).longValue();
        String title = row[2] != null ? row[2].toString() : "";
        int chunkIndex = ((Number) row[3]).intValue();
        String content = row[4] != null ? row[4].toString() : "";
        String metadata = row[5] != null ? row[5].toString() : null;
        double score = ((Number) row[6]).doubleValue();
        return new ScoredChunk(id, documentId, title, chunkIndex, content, score, metadata);
    }

    /**
     * Generates an embedding for the given text using the configured
     * {@link EmbeddingModel}.
     */
    private float[] embed(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        Embedding embedding = response.content();
        return embedding.vector();
    }

    /**
     * Serializes the metadata map to a compact JSON object string (keys sorted
     * for determinism), or {@code null} when empty.
     */
    private static String toMetadataJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(metadata.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Map.Entry<String, String> e = entries.get(i);
            sb.append('"').append(escapeJson(e.getKey())).append("\":\"")
                    .append(escapeJson(e.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}