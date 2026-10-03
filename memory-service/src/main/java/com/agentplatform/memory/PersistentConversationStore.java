package com.agentplatform.memory;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        trimIfNeeded(conversationId, conversation);
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
        trimIfNeeded(conversationId, managed);
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
        repository.findByConversationId(conversationId).ifPresent(conversation -> {
            List<ConversationMessageEntity> messages = conversation.getMessages();
            if (messages.size() > maxMessages) {
                messages.sort(Comparator.comparingInt(ConversationMessageEntity::getSequence));
                while (messages.size() > maxMessages) {
                    messages.remove(0);
                }
                for (int i = 0; i < messages.size(); i++) {
                    messages.get(i).setSequence(i);
                }
                conversation.setUpdatedAt(Instant.now());
                repository.save(conversation);
                log.debug("Trimmed conversation {} to {} messages", conversationId, maxMessages);
            }
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
    private void trimIfNeeded(String conversationId, ConversationEntity conversation) {
        List<ConversationMessageEntity> messages = conversation.getMessages();
        if (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            messages.sort(Comparator.comparingInt(ConversationMessageEntity::getSequence));
            while (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
                messages.remove(0);
            }
            for (int i = 0; i < messages.size(); i++) {
                messages.get(i).setSequence(i);
            }
            conversation.setUpdatedAt(Instant.now());
            repository.save(conversation);
            log.debug("Trimmed conversation {} to {} messages", conversationId, MAX_MESSAGES_PER_CONVERSATION);
        }

        // Check if we need to evict oldest conversations
        evictIfNeeded();
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