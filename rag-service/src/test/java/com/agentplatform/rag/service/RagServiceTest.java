package com.agentplatform.rag.service;

import com.agentplatform.rag.chunking.TextChunker;
import com.agentplatform.rag.entity.DocumentChunkEntity;
import com.agentplatform.rag.repository.DocumentChunkRepository;
import com.agentplatform.rag.repository.DocumentChunkVectorRepository;
import com.agentplatform.rag.repository.DocumentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link PgVectorRagService} using Testcontainers with a
 * real PostgreSQL/pgvector database. Mirrors the Phase 8.1 pgvector test pattern:
 * deterministic mocked embeddings with known cosine relationships.
 *
 * <p>The mocked {@link EmbeddingModel} routes any text containing
 * "retrieval" to vector A (dims [0,100)), any text containing "inventory" to
 * vector B (dims [400,100)), and anything else to vector C — so two such
 * vectors have cosine 1.0 when identical and 0.0 when disjoint.</p>
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres")
@DisplayName("PgVectorRagService — pgvector-backed document indexing and retrieval")
// Requires Docker to run (Testcontainers). Enable by setting -Dtestcontainers.docker.enabled=true
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "testcontainers.docker.enabled", matches = "true")
class RagServiceTest {

    private static final double THRESHOLD = 0.001;

    // Document contents: every sentence of A contains "retrieval", every
    // sentence of B and C contains "inventory", so each chunk routes to one
    // deterministic embedding.
    private static final String DOC_A = String.join(" ",
            "Retrieval systems rank documents by relevance.",
            "Effective retrieval balances speed and accuracy.",
            "Modern retrieval uses learned embeddings for similarity search.",
            "Retrieval evaluation measures precision and recall.",
            "Dense retrieval is the dominant paradigm today.",
            "Retrieval quality drives end-to-end performance in search.");

    private static final String DOC_B = String.join(" ",
            "Inventory tracking prevents stockouts at scale.",
            "Warehouse inventory systems alert on low stock.",
            "Inventory forecasting plans replenishment ahead of demand.",
            "Accurate inventory counts reduce carrying costs.",
            "Inventory analytics uncover slow-moving stock quickly.",
            "Retail inventory management balances availability and cost.");

    private static final String DOC_C = String.join(" ",
            "Inventory visibility improves supply chain outcomes.",
            "Inventory management reconciles warehouse stock.");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("agentdb")
            .withUsername("agent")
            .withPassword("agentpassword")
            .withInitScript("init.sql");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "com.agentplatform.memory.dialect.PostgresVectorDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Autowired
    private DocumentChunkVectorRepository vectorRepository;

    @MockBean
    private EmbeddingModel embeddingModel;

    private PgVectorRagService ragService;

    @BeforeEach
    void setUp() {
        // Deterministic embeddings: keyword -> known unit vector region.
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
            String text = ((String) invocation.getArgument(0)).toLowerCase();
            float[] vector;
            if (text.contains("retrieval")) {
                vector = explicitVector(0, 100);
            } else if (text.contains("inventory")) {
                vector = explicitVector(400, 100);
            } else {
                vector = explicitVector(700, 100);
            }
            return Response.from(Embedding.from(vector));
        });

        ragService = new PgVectorRagService(documentRepository, chunkRepository, vectorRepository,
                embeddingModel, new TextChunker(200, 10), THRESHOLD);

        chunkRepository.deleteAll();
        documentRepository.deleteAll();
        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        chunkRepository.deleteAll();
        documentRepository.deleteAll();
        entityManager.flush();
    }

    /**
     * Builds a 768-dimensional unit vector with ones in dims [start, start + count).
     * Two such vectors have a known cosine similarity (1.0 identical, 0.0 disjoint).
     */
    private static float[] explicitVector(int start, int count) {
        float[] vec = new float[768];
        double norm = 0;
        for (int i = start; i < start + count && i < 768; i++) {
            vec[i] = 1.0f;
            norm += 1.0;
        }
        norm = Math.sqrt(norm);
        for (int i = 0; i < 768; i++) {
            vec[i] /= (float) norm;
        }
        return vec;
    }

    @Test
    @DisplayName("indexDocument creates chunks with ordered indices and persisted embeddings")
    void indexDocument_createsChunksAndEmbeddings() {
        DocumentIndexResult result = ragService.indexDocument("Retrieval Guide", DOC_A, Map.of("topic", "search"));

        assertThat(result.documentId()).isPositive();
        assertThat(result.title()).isEqualTo("Retrieval Guide");
        assertThat(result.chunkCount()).isGreaterThanOrEqualTo(2);

        var chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(result.documentId());
        assertThat(chunks).hasSize(result.chunkCount());
        assertThat(chunks).extracting(DocumentChunkEntity::getChunkIndex)
                .containsExactlyElementsOf(IntStream.range(0, result.chunkCount()).boxed().toList());
        assertThat(chunks.get(0).getContent()).contains("Retrieval systems");
        assertThat(chunks.get(0).getMetadata()).isEqualTo("{\"topic\":\"search\"}");
        chunks.forEach(c -> assertThat(c.getEmbedding()).isNotNull());

        // Embeddings are persisted and searchable through the raw vector repository.
        List<Object[]> matches = vectorRepository.findSimilar(explicitVector(0, 100), null, null, null, null, 10);
        assertThat(matches).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("retrieve returns only relevant chunks, ordered by score")
    void retrieve_returnsOnlyRelevantChunks() {
        ragService.indexDocument("Retrieval Guide", "wiki", "article", DOC_A, Map.of("topic", "search"));
        ragService.indexDocument("Inventory Manual", "manual", "guide", DOC_B, Map.of("topic", "ops"));

        List<ScoredChunk> results = ragService.retrieve("retrieval", 5, RetrievalFilter.none());

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(c -> assertThat(c.content()).containsIgnoringCase("retrieval"));
        assertThat(results).extracting(ScoredChunk::documentId)
                .allMatch(id -> id.longValue() == results.get(0).documentId());
        assertThat(results).allSatisfy(c -> assertThat(c.score()).isGreaterThanOrEqualTo(THRESHOLD));
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).score()).isGreaterThanOrEqualTo(results.get(i).score());
        }
    }

    @Test
    @DisplayName("retrieve respects topK")
    void retrieve_respectsTopK() {
        ragService.indexDocument("Retrieval Guide", "wiki", "article", DOC_A, Map.of("topic", "search"));
        ragService.indexDocument("Inventory Manual", "manual", "guide", DOC_B, Map.of("topic", "ops"));

        assertThat(ragService.retrieve("retrieval", 1, RetrievalFilter.none())).hasSize(1);

        List<ScoredChunk> capped = ragService.retrieve("retrieval", 10, RetrievalFilter.none());
        assertThat(capped).hasSize(2);
        assertThat(capped).extracting(ScoredChunk::chunkId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("retrieve applies source and metadata filters")
    void retrieve_metadataFilterWorks() {
        long docA = ragService.indexDocument("Retrieval Guide", "wiki", "article", DOC_A, Map.of("topic", "search")).documentId();
        long docB = ragService.indexDocument("Inventory Manual", "manual", "guide", DOC_B, Map.of("topic", "ops")).documentId();
        ragService.indexDocument("Inventory Notes", "docs", "notes", DOC_C, Map.of("topic", "ops"));

        // Source filter restricts to the manual only (doc B), excluding docs.
        List<ScoredChunk> bySource = ragService.retrieve("inventory", 10, RetrievalFilter.bySource("manual"));
        assertThat(bySource).isNotEmpty();
        assertThat(bySource).extracting(ScoredChunk::documentId)
                .allMatch(id -> id.longValue() == docB);

        // Metadata filter (topic=ops) returns both ops documents, never doc A.
        List<ScoredChunk> byMetadata = ragService.retrieve("inventory", 10, RetrievalFilter.metadata("topic", "ops"));
        assertThat(byMetadata).hasSize(3);
        assertThat(byMetadata).extracting(ScoredChunk::documentId)
                .allMatch(id -> id.longValue() != docA);
        assertThat(byMetadata).extracting(ScoredChunk::documentId)
                .anyMatch(id -> id.longValue() == docB);
        assertThat(byMetadata).extracting(ScoredChunk::documentId)
                .anyMatch(id -> id.longValue() != docB && id.longValue() != docA);
    }

    @Test
    @DisplayName("unfiltered retrieval searches across all documents")
    void retrieve_globalSearch_worksAcrossDocuments() {
        long docB = ragService.indexDocument("Inventory Manual", "manual", "guide", DOC_B, Map.of("topic", "ops")).documentId();
        long docC = ragService.indexDocument("Inventory Notes", "docs", "notes", DOC_C, Map.of("topic", "ops")).documentId();

        List<ScoredChunk> results = ragService.retrieve("inventory", 10, RetrievalFilter.none());

        assertThat(results).isNotEmpty();
        assertThat(results).extracting(ScoredChunk::documentId)
                .anyMatch(id -> id.longValue() == docB);
        assertThat(results).extracting(ScoredChunk::documentId)
                .anyMatch(id -> id.longValue() == docC);
        assertThat(results).extracting(ScoredChunk::documentId)
                .allMatch(id -> id.longValue() == docB || id.longValue() == docC);
    }

    @Test
    @DisplayName("retrieveForContext assembles a deterministic context block")
    void retrieveForContext_buildsDeterministicContext() {
        ragService.indexDocument("Retrieval Guide", "wiki", "article", DOC_A, Map.of("topic", "search"));

        ContextResult first = ragService.retrieveForContext("retrieval", 500);
        assertThat(first.chunkCount()).isGreaterThanOrEqualTo(1);
        assertThat(first.context()).contains("Source: Retrieval Guide");
        assertThat(first.context()).containsIgnoringCase("retrieval");
        assertThat(first.sources()).hasSize(first.chunkCount());

        ContextResult second = ragService.retrieveForContext("retrieval", 500);
        assertThat(second.context()).isEqualTo(first.context());
        assertThat(second.sources()).isEqualTo(first.sources());
    }
}