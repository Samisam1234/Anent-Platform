package com.agentplatform.memory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationMessageVectorRepository;
import com.agentplatform.memory.repository.ConversationRepository;

import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.persistence.EntityManagerFactory;

/**
 * Configuration for selecting the ConversationStore implementation.
 * 
 * <p>By default (when no property is set or set to "inmemory"), the in-memory
 * implementation is used. When set to "persistent", the JPA-backed implementation
 * is used (requires a configured datasource and JPA entities). When set to "pgvector",
 * the pgvector-backed implementation is used (requires PostgreSQL with pgvector
 * extension and the "postgres" profile active).</p>
 * 
 * <p>Usage in application.yml:</p>
 * <pre>
 * conversation:
 *   store: inmemory  # or "persistent" or "pgvector" (requires postgres profile)
 * </pre>
 */
@Configuration
public class ConversationStoreConfig {

    /**
     * In-memory conversation store (default).
     * Used when conversation.store is not set or set to "inmemory".
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "conversation.store", havingValue = "inmemory", matchIfMissing = true)
    public ConversationStore inMemoryConversationStore() {
        return new InMemoryConversationStore();
    }

    /**
     * Persistent JPA-backed conversation store.
     * Used when conversation.store=persistent.
     * Requires a configured datasource and JPA (ConversationEntity, ConversationMessageEntity).
     */
    @Bean
    @ConditionalOnProperty(name = "conversation.store", havingValue = "persistent")
    public ConversationStore persistentConversationStore(ConversationRepository repository) {
        return new PersistentConversationStore(repository);
    }

    /**
     * Ensures JPA entities are registered when using persistent store.
     * This is a no-op bean that forces entity registration when the persistent
     * profile is active.
     */
    @Bean
    @ConditionalOnProperty(name = "conversation.store", havingValue = "persistent")
    @ConditionalOnMissingBean
    public Object persistentConversationStoreEntityRegistrar(EntityManagerFactory entityManagerFactory) {
        // Force registration of ConversationEntity and ConversationMessageEntity
        // by accessing the metamodel
        entityManagerFactory.getMetamodel().entity(ConversationEntity.class);
        entityManagerFactory.getMetamodel().entity(ConversationMessageEntity.class);
        return new Object();
    }

    /**
     * pgvector-backed conversation store with similarity search.
     * Used when conversation.store=pgvector AND the "postgres" profile is active.
     * Requires PostgreSQL with pgvector extension, configured datasource,
     * and the EmbeddingModel bean (provided by agent-core OllamaConfig).
     */
    @Bean
    @ConditionalOnProperty(name = "conversation.store", havingValue = "pgvector")
    @Profile("postgres")
    public ConversationStore pgVectorConversationStore(ConversationRepository repository,
                                                        ConversationMessageVectorRepository vectorRepository,
                                                        EmbeddingModel embeddingModel) {
        return new PgVectorConversationStore(repository, vectorRepository, embeddingModel);
    }

    /**
     * Ensures JPA entities are registered when using pgvector store.
     */
    @Bean
    @ConditionalOnProperty(name = "conversation.store", havingValue = "pgvector")
    @Profile("postgres")
    @ConditionalOnMissingBean
    public Object pgVectorConversationStoreEntityRegistrar(EntityManagerFactory entityManagerFactory) {
        entityManagerFactory.getMetamodel().entity(ConversationEntity.class);
        entityManagerFactory.getMetamodel().entity(ConversationMessageEntity.class);
        return new Object();
    }
}