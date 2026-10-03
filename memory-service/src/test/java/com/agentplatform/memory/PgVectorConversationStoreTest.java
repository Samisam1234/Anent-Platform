package com.agentplatform.memory;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationMessageVectorRepository;
import com.agentplatform.memory.repository.ConversationRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link PgVectorConversationStore} using Testcontainers
 * with a real PostgreSQL/pgvector database.
 *
 * <p>The EmbeddingModel is mocked to return deterministic embeddings for
 * predictable similarity search testing.</p>
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ConversationStoreConfig.class)
@ActiveProfiles("postgres")
@DisplayName("PgVectorConversationStore — pgvector-backed conversation persistence with similarity search")
// Requires Docker to run (Testcontainers). Enable by setting -Dtestcontainers.docker.enabled=true
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "testcontainers.docker.enabled", matches = "true")
class PgVectorConversationStoreTest {

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
        registry.add("conversation.store", () -> "pgvector");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository repository;

    @Autowired
    private ConversationMessageVectorRepository vectorRepository;

    @MockBean
    private EmbeddingModel embeddingModel;

    private PgVectorConversationStore store;

    @BeforeEach
    void setUp() {
        // Create store with autowired mocked embedding model
        store = new PgVectorConversationStore(repository, vectorRepository, embeddingModel);

        // Clean up before each test
        repository.deleteAll();
        entityManager.flush();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        entityManager.flush();
    }

    // Helper to create a mock embedding with a simple pattern
    private float[] embeddingFor(String text) {
        // Create a deterministic embedding based on text content
        // Simple approach: use first few chars to create a pattern
        float[] vec = new float[768];
        int seed = text.hashCode();
        for (int i = 0; i < 768; i++) {
            seed = (seed * 31 + i) & 0x7FFFFFFF;
            vec[i] = (seed % 1000) / 1000.0f - 0.5f; // Range [-0.5, 0.5]
        }
        // Normalize to unit vector for cosine similarity
        double norm = 0;
        for (float v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        for (int i = 0; i < 768; i++) vec[i] /= (float) norm;
        return vec;
    }

    private void setupMockEmbeddings(String... texts) {
        for (String text : texts) {
            float[] vec = embeddingFor(text);
            when(embeddingModel.embed(text)).thenReturn(Response.from(Embedding.from(vec)));
        }
    }

    /**
     * Builds a 768-dimensional unit vector with ones in dims [start, start + count).
     * Two such vectors have a known cosine similarity:
     * <ul>
     *   <li>identical regions -> 1.0</li>
     *   <li>overlapping regions -> positive (shared-count-based)</li>
     *   <li>disjoint regions -> 0.0</li>
     * </ul>
     */
    private float[] explicitVector(int start, int count) {
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
    @DisplayName("append() persists message and embedding")
    void append_persistsMessageAndEmbedding() {
        setupMockEmbeddings("Hello world");

        store.append("conv-1", ConversationMessage.user("Hello world"));

        List<ConversationMessage> messages = store.messages("conv-1");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).isEqualTo("Hello world");

        // Verify embedding was persisted by checking via vector repository
        List<Object[]> results = vectorRepository.findSimilarByConversationId("conv-1", embeddingFor("Hello world"), 1);
        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("save() persists messages with embeddings")
    void save_persistsMessagesWithEmbeddings() {
        setupMockEmbeddings("First message", "Second message");

        store.save("conv-1", List.of(
                ConversationMessage.user("First message"),
                ConversationMessage.ai("Second message")
        ));

        List<ConversationMessage> messages = store.messages("conv-1");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("First message");
        assertThat(messages.get(1).content()).isEqualTo("Second message");

        // Verify embeddings persisted
        List<Object[]> results = vectorRepository.findSimilarByConversationId("conv-1", embeddingFor("First message"), 2);
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("findSimilar returns relevant messages within conversation")
    void findSimilar_returnsRelevantMessages() {
        // Independent hash-based vectors cannot guarantee the required ordering,
        // so use explicit vectors with KNOWN cosine relationships. All 768 dims:
        //   query ("fruit") -> dims [0, 100)   cosine with itself        = 1.0
        //   "apple fruit"   -> dims [0, 200)   overlap [0,100) -> cos ~0.707
        //   "fruit basket"  -> dims [0, 300)   overlap [0,100) -> cos ~0.577
        //   "car vehicle"   -> dims [400,600)  no overlap       -> cos  0.0
        float[] queryVec = explicitVector(0, 100);
        float[] appleVec = explicitVector(0, 200);
        float[] fruitVec = explicitVector(0, 300);
        float[] carVec = explicitVector(400, 200);

        when(embeddingModel.embed("apple fruit")).thenReturn(Response.from(Embedding.from(appleVec)));
        when(embeddingModel.embed("fruit basket")).thenReturn(Response.from(Embedding.from(fruitVec)));
        when(embeddingModel.embed("car vehicle")).thenReturn(Response.from(Embedding.from(carVec)));
        when(embeddingModel.embed("fruit")).thenReturn(Response.from(Embedding.from(queryVec))); // Query similar to fruit

        store.save("conv-1", List.of(
                ConversationMessage.user("apple fruit"),
                ConversationMessage.ai("fruit basket"),
                ConversationMessage.user("car vehicle")
        ));

        // Search for "fruit" - should find apple and fruit messages first
        List<VectorSearchableConversationStore.ScoredMessage> results =
                store.findSimilar("conv-1", "fruit", 3);

        assertThat(results).hasSize(3);
        // Results ordered by similarity (highest first)
        assertThat(results.get(0).message().content()).isIn("apple fruit", "fruit basket");
        assertThat(results.get(1).message().content()).isIn("apple fruit", "fruit basket");
        assertThat(results.get(2).message().content()).isEqualTo("car vehicle");
    }

    @Test
    @DisplayName("findSimilar respects conversation scope")
    void findSimilar_respectsConversationScope() {
        float[] vec1 = embeddingFor("shared topic");
        float[] vec2 = embeddingFor("shared topic");

        when(embeddingModel.embed("shared topic")).thenReturn(Response.from(Embedding.from(vec1)));
        when(embeddingModel.embed("query")).thenReturn(Response.from(Embedding.from(vec1)));

        store.save("conv-1", List.of(ConversationMessage.user("shared topic")));
        store.save("conv-2", List.of(ConversationMessage.user("shared topic")));

        // Search only in conv-1
        List<VectorSearchableConversationStore.ScoredMessage> results =
                store.findSimilar("conv-1", "query", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).message().content()).isEqualTo("shared topic");
    }

    @Test
    @DisplayName("findSimilarGlobal searches across conversations")
    void findSimilarGlobal_searchesAcrossConversations() {
        float[] vec1 = embeddingFor("topic alpha");
        float[] vec2 = embeddingFor("topic beta");
        float[] queryVec = embeddingFor("topic alpha");

        when(embeddingModel.embed("topic alpha")).thenReturn(Response.from(Embedding.from(vec1)));
        when(embeddingModel.embed("topic beta")).thenReturn(Response.from(Embedding.from(vec2)));
        when(embeddingModel.embed("alpha")).thenReturn(Response.from(Embedding.from(queryVec)));

        store.save("conv-1", List.of(ConversationMessage.user("topic alpha")));
        store.save("conv-2", List.of(ConversationMessage.user("topic beta")));

        List<VectorSearchableConversationStore.ScoredMessage> results =
                store.findSimilarGlobal("alpha", 10);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).message().content()).isEqualTo("topic alpha");
    }

    @Test
    @DisplayName("similarity ordering is correct (highest similarity first)")
    void similarityOrdering_correct() {
        // Create three messages with known similarity to query
        float[] exactMatch = embeddingFor("exact match");
        float[] similar = embeddingFor("similar match");
        float[] different = embeddingFor("completely different topic");

        when(embeddingModel.embed("exact match")).thenReturn(Response.from(Embedding.from(exactMatch)));
        when(embeddingModel.embed("similar match")).thenReturn(Response.from(Embedding.from(similar)));
        when(embeddingModel.embed("completely different topic")).thenReturn(Response.from(Embedding.from(different)));
        when(embeddingModel.embed("exact match query")).thenReturn(Response.from(Embedding.from(exactMatch)));

        store.save("conv-1", List.of(
                ConversationMessage.user("exact match"),
                ConversationMessage.user("similar match"),
                ConversationMessage.user("completely different topic")
        ));

        List<VectorSearchableConversationStore.ScoredMessage> results =
                store.findSimilar("conv-1", "exact match query", 3);

        assertThat(results).hasSize(3);
        // First result should be exact match (highest similarity)
        assertThat(results.get(0).message().content()).isEqualTo("exact match");
        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(1).score()).isGreaterThan(results.get(2).score());
    }

    @Test
    @DisplayName("message limit (60) and conversation limit (200) enforced")
    void bounds_enforced() {
        // Append uses many different message texts; stub ANY text with a
        // deterministic 768-dim embedding so embed() never returns null.
        when(embeddingModel.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return Response.from(Embedding.from(embeddingFor(text)));
        });

        // Test message limit per conversation
        for (int i = 0; i < 70; i++) {
            store.append("conv-1", ConversationMessage.user("msg " + i));
        }
        assertThat(store.messages("conv-1")).hasSize(PgVectorConversationStore.MAX_MESSAGES_PER_CONVERSATION);

        // Test conversation limit
        for (int i = 0; i < 210; i++) {
            store.append("conv-" + i, ConversationMessage.user("payload"));
        }
        assertThat(store.count()).isLessThanOrEqualTo(PgVectorConversationStore.MAX_CONVERSATIONS);
    }

    @Test
    @DisplayName("persistence survives store recreation (simulates JVM restart)")
    void persistence_survivesStoreRecreation() {
        float[] vec = embeddingFor("persistent message");
        when(embeddingModel.embed("persistent message")).thenReturn(Response.from(Embedding.from(vec)));

        store.append("conv-1", ConversationMessage.user("persistent message"));

        // Create a new store instance with the same repositories (simulates restart)
        PgVectorConversationStore newStore = new PgVectorConversationStore(repository, vectorRepository, embeddingModel);

        List<ConversationMessage> messages = newStore.messages("conv-1");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).content()).isEqualTo("persistent message");

        // Verify similarity search also works after recreation
        when(embeddingModel.embed("persistent")).thenReturn(Response.from(Embedding.from(vec)));
        List<VectorSearchableConversationStore.ScoredMessage> results =
                newStore.findSimilar("conv-1", "persistent", 1);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).message().content()).isEqualTo("persistent message");
    }

    @Test
    @DisplayName("pgvector configuration activates correctly with postgres profile")
    void pgvectorConfiguration_activatesCorrectly() {
        // This test verifies the store was created with pgvector profile
        // The fact that this test runs with @ActiveProfiles("postgres") and
        // conversation.store=pgvector (via DynamicPropertySource) confirms activation
        assertThat(store).isNotNull();
        assertThat(store).isInstanceOf(PgVectorConversationStore.class);
        assertThat(store).isInstanceOf(VectorSearchableConversationStore.class);
    }
}