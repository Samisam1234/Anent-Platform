package com.agentplatform.memory;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationRepository;
import com.agentplatform.memory.repository.ConversationMessageVectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PostgreSQL/pgvector-backed conversation store implementation.
 *
 * <p>Extends the JPA-backed persistence with vector similarity search capabilities.
 * Uses the existing {@link EmbeddingModel} bean (nomic-embed-text, 768 dimensions)
 * to generate embeddings for message content.</p>
 *
 * <p>Activated when {@code conversation.store=pgvector} and the {@code postgres}
 * Spring profile is active.</p>
 */
public class PgVectorConversationStore implements ConversationStore, VectorSearchableConversationStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorConversationStore.class);

    public static final int MAX_MESSAGES_PER_CONVERSATION = 60;
    public static final int MAX_CONVERSATIONS = 200;

    private final ConversationRepository repository;
    private final ConversationMessageVectorRepository vectorRepository;
    private final EmbeddingModel embeddingModel;

    // Keep a small in-memory cache for performance
    private final Map<String, ConversationEntity> conversationCache = new ConcurrentHashMap<>();

    public PgVectorConversationStore(ConversationRepository repository,
                                     ConversationMessageVectorRepository vectorRepository,
                                     EmbeddingModel embeddingModel) {
        this.repository = repository;
        this.vectorRepository = vectorRepository;
        this.embeddingModel = embeddingModel;
    }

    @Override
    @Transactional
    public void append(String conversationId, ConversationMessage message) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }

        ConversationEntity conversation = getOrCreateConversation(conversationId);
        List<ConversationMessageEntity> messages = conversation.getMessages();
        ConversationMessageEntity newMessage = new ConversationMessageEntity(conversation, messages.size(), message.role(), message.content());
        newMessage.setEmbedding(embed(message.content()));
        messages.add(newMessage);
        conversation.setUpdatedAt(Instant.now());

        // Deliberately no repository.save(): the conversation is managed, so save would be
        // em.merge(), and merge resets the collection's stored snapshot, which cancels the
        // orphan deletion of anything a previous trim removed. See trimMessages.

        // Update cache
        conversationCache.put(conversationId, conversation);

        // Trim if needed
        trimIfNeeded(conversation);
    }

    @Override
    @Transactional
    public void save(String conversationId, List<ConversationMessage> messages) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }

        Instant now = Instant.now();

        // Wholesale replacement: remove any existing conversation (cascade removes
        // its messages), then build a fresh conversation with the full history.
        repository.findByConversationId(conversationId).ifPresent(repository::delete);
        repository.flush();

        ConversationEntity conversation = new ConversationEntity(conversationId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage msg = messages.get(i);
            ConversationMessageEntity messageEntity = new ConversationMessageEntity(conversation, i, msg.role(), msg.content());
            messageEntity.setEmbedding(embed(msg.content()));
            conversation.getMessages().add(messageEntity);
        }

        ConversationEntity saved = repository.saveAndFlush(conversation);
        conversationCache.put(conversationId, saved);

        // Re-fetch so trim operates on a Hibernate-managed PersistentBag
        ConversationEntity managed = repository.findByConversationId(conversationId).orElseThrow();
        trimIfNeeded(managed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationSnapshot> snapshot(String conversationId) {
        return repository.findByConversationId(conversationId)
                .map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessage> messages(String conversationId) {
        return repository.findByConversationId(conversationId)
                .map(c -> c.getMessages().stream()
                        .sorted(Comparator.comparingInt(ConversationMessageEntity::getSequence))
                        .map(m -> new ConversationMessage(m.getRole(), m.getContent()))
                        .collect(Collectors.toList()))
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String conversationId) {
        return repository.findByConversationId(conversationId).isPresent();
    }

    @Override
    @Transactional
    public void trim(String conversationId, int maxMessages) {
        repository.findByConversationId(conversationId)
                .ifPresent(conversation -> {
                    trimMessages(conversation, maxMessages);
                    repository.flush();
                });
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        repository.findByConversationId(conversationId).ifPresent(repository::delete);
        conversationCache.remove(conversationId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> conversationIds() {
        return repository.findAll().stream()
                .map(ConversationEntity::getConversationId)
                .collect(Collectors.toCollection(() -> new java.util.LinkedHashSet<>()));
    }

    @Override
    @Transactional(readOnly = true)
    public int count() {
        return (int) repository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Instant lastUpdated(String conversationId) {
        return repository.findByConversationId(conversationId)
                .map(ConversationEntity::getUpdatedAt)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VectorSearchableConversationStore.ScoredMessage> findSimilar(String conversationId, String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        float[] queryEmbedding = embed(query);

        List<Object[]> results = vectorRepository.findSimilarByConversationId(conversationId, queryEmbedding, limit);

        return results.stream()
                .map(row -> {
                    // row: [id (Long), conversation_id (Long), role (String), content (String), sequence (Integer), similarity (Number)]
                    String role = row[2] != null ? row[2].toString() : "";
                    String content = row[3] != null ? row[3].toString() : "";
                    double score = ((Number) row[5]).doubleValue();
                    ConversationMessage message = new ConversationMessage(role, content);
                    return new VectorSearchableConversationStore.ScoredMessage(message, score);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VectorSearchableConversationStore.ScoredMessage> findSimilarGlobal(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        float[] queryEmbedding = embed(query);

        List<Object[]> results = vectorRepository.findSimilarGlobal(queryEmbedding, limit);

        return results.stream()
                .map(row -> {
                    // row: [id, conversation_id, role, content, sequence, similarity]
                    String role = (String) row[2];
                    String content = (String) row[3];
                    double score = ((Number) row[5]).doubleValue();
                    ConversationMessage message = new ConversationMessage(role, content);
                    return new VectorSearchableConversationStore.ScoredMessage(message, score);
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets an existing conversation or creates a new one.
     */
    private ConversationEntity getOrCreateConversation(String conversationId) {
        return repository.findByConversationId(conversationId)
                .orElseGet(() -> {
                    ConversationEntity newConv = new ConversationEntity(conversationId);
                    ConversationEntity saved = repository.saveAndFlush(newConv);
                    conversationCache.put(conversationId, saved);
                    return saved;
                });
    }

    /**
     * Trims conversation if it exceeds max messages.
     */
    private void trimIfNeeded(ConversationEntity conversation) {
        trimMessages(conversation, MAX_MESSAGES_PER_CONVERSATION);

        // Check if we need to evict oldest conversations
        evictIfNeeded();
    }

    /**
     * Drops the oldest messages so that at most {@code maxMessages} remain, then renumbers
     * the {@code sequence} of the survivors from zero.
     *
     * <p><strong>Must not call {@code repository.save(conversation)}.</strong> The parent is
     * managed, so that call resolves to {@code em.merge()}. Hibernate 6.6 realigns the
     * collection's stored snapshot on merge
     * ({@code DefaultMergeEventListener.CollectionVisitor} calls
     * {@code CollectionEntry.resetStoredSnapshot(coll, coll.getSnapshot(persister))}), and
     * orphan removal is computed as a diff of that snapshot against the current contents
     * ({@code AbstractPersistentCollection.getOrphans}). Once the snapshot equals the
     * current contents the diff yields no orphans, so the removed messages are never
     * deleted: they stay persistent while still referencing their parent, and a later
     * delete of that parent fails the flush with
     * {@code TransientObjectException}. Merging also has {@code deleteOrphans() == false}
     * in {@code CascadingActions}, so it never deletes orphans itself. Dirty checking and
     * {@code orphanRemoval} handle it at flush, where the cascade is
     * {@code PERSIST_ON_FLUSH}, whose {@code deleteOrphans()} is {@code true}.</p>
     *
     * <p>Elements are dropped with {@code remove(Object)} rather than {@code remove(int)}:
     * {@code PersistentBag.remove(int)} does not mark the collection dirty, while
     * {@code remove(Object)} does. The collection is never reordered in place, since
     * {@code @OrderBy("sequence ASC")} already returns it in sequence order.</p>
     */
    private void trimMessages(ConversationEntity conversation, int maxMessages) {
        List<ConversationMessageEntity> messages = conversation.getMessages();
        if (messages.size() <= maxMessages) {
            return;
        }

        List<ConversationMessageEntity> oldestFirst = new ArrayList<>(messages);
        oldestFirst.sort(Comparator.comparingInt(ConversationMessageEntity::getSequence));

        int excess = oldestFirst.size() - maxMessages;
        for (int i = 0; i < excess; i++) {
            messages.remove(oldestFirst.get(i));
        }
        for (int i = 0; i < maxMessages; i++) {
            oldestFirst.get(excess + i).setSequence(i);
        }

        conversation.setUpdatedAt(Instant.now());
        log.debug("Trimmed conversation {} to {} messages",
                conversation.getConversationId(), maxMessages);
    }

    /**
     * Evicts oldest conversations if store exceeds capacity.
     */
    private void evictIfNeeded() {
        long count = repository.count();
        if (count <= MAX_CONVERSATIONS) {
            return;
        }

        // Find and delete oldest conversations
        List<ConversationEntity> all = repository.findAll();
        all.sort(Comparator.comparing(ConversationEntity::getUpdatedAt)
                .thenComparing(ConversationEntity::getCreatedAt)
                .thenComparing(ConversationEntity::getConversationId));

        int toEvict = (int) (count - MAX_CONVERSATIONS);
        for (int i = 0; i < toEvict; i++) {
            ConversationEntity toRemove = all.get(i);
            repository.delete(toRemove);
            conversationCache.remove(toRemove.getConversationId());
            log.debug("Evicted oldest conversation {} (store at capacity {})",
                    toRemove.getConversationId(), MAX_CONVERSATIONS);
        }
    }

    private ConversationSnapshot toSnapshot(ConversationEntity entity) {
        List<ConversationMessage> messages = entity.getMessages().stream()
                .sorted(Comparator.comparingInt(ConversationMessageEntity::getSequence))
                .map(m -> new ConversationMessage(m.getRole(), m.getContent()))
                .collect(Collectors.toList());
        return new ConversationSnapshot(
                entity.getConversationId(),
                messages,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Generates an embedding for the given text using the configured EmbeddingModel.
     */
    private float[] embed(String text) {
        dev.langchain4j.model.output.Response<Embedding> response = embeddingModel.embed(text);
        Embedding embedding = response.content();
        return embedding.vector();
    }
}