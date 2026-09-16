package com.agentplatform.memory;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationRepository;
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
 * Persistent, database-backed conversation store implementation.
 * Uses JPA repositories to persist conversations and messages to the database.
 * 
 * <p>Bounds: each conversation is kept to at most {@link #MAX_MESSAGES_PER_CONVERSATION}
 * messages and the store to at most {@link #MAX_CONVERSATIONS} conversations
 * (oldest-updated evicted first).</p>
 */
public class PersistentConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(PersistentConversationStore.class);

    public static final int MAX_MESSAGES_PER_CONVERSATION = 60;
    public static final int MAX_CONVERSATIONS = 200;

    private final ConversationRepository repository;

    // Keep a small in-memory cache for performance
    private final Map<String, ConversationEntity> conversationCache = new ConcurrentHashMap<>();

    public PersistentConversationStore(ConversationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void append(String conversationId, ConversationMessage message) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        
        ConversationEntity conversation = getOrCreateConversation(conversationId);
        List<ConversationMessageEntity> messages = conversation.getMessages();
        messages.add(new ConversationMessageEntity(conversation, messages.size(), message.role(), message.content()));
        conversation.setUpdatedAt(Instant.now());
        conversation = repository.save(conversation);

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
            conversation.getMessages().add(new ConversationMessageEntity(conversation, i, msg.role(), msg.content()));
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
                .ifPresent(conversation -> trimMessages(conversation, maxMessages));
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

    /**
     * Gets an existing conversation or creates a new one.
     *
     * <p>Always returns the entity loaded from the current persistence context so
     * that the caller mutates the authoritative managed instance (its collection
     * is a Hibernate-managed {@code PersistentBag}). The {@link #conversationCache}
     * is used only as a cheap existence hint; stale cached references are never
     * returned, which keeps orphanRemoval/cascade correct.</p>
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
     * the {@code sequence} of the survivors from zero. Does nothing when the conversation
     * is already within the limit.
     *
     * <p>The managed {@code messages} collection is never reordered. {@link List#sort}
     * repositions every element through {@code ListIterator.set}, and a Hibernate
     * {@code PersistentBag} has no notion of index, so it records each repositioning as a
     * removal plus a re-addition. With {@code orphanRemoval = true} that queues orphan
     * deletes for objects that remain managed in the session and still point at their
     * parent, leaving persistent messages unreachable from the parent's collection. Such a
     * message survives the cascade from a {@code deleteAll} of its parent while still
     * referencing it, and the flush then fails with
     * {@code TransientObjectException: persistent instance references an unsaved transient
     * instance of ConversationEntity} — which is what surfaced in the test's
     * {@code tearDown}, after the assertions had already passed.</p>
     *
     * <p>The in-place sort bought nothing anyway: the collection is mapped with
     * {@code @OrderBy("sequence ASC")}, so Hibernate hands it back in sequence order, and
     * every read path sorts by {@code sequence} as well. Sorting a copy is purely to pick
     * which entries are oldest.</p>
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
            // Removal by identity: ConversationMessageEntity declares no equals/hashCode,
            // so PersistentBag.remove drops exactly this instance and queues its orphan
            // delete, which is the removal path the mapping expects.
            messages.remove(oldestFirst.get(i));
        }
        for (int i = 0; i < maxMessages; i++) {
            oldestFirst.get(excess + i).setSequence(i);
        }

        conversation.setUpdatedAt(Instant.now());
        repository.save(conversation);
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
}