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

        // Deliberately no repository.save() here. The conversation is already managed, so
        // dirty checking flushes updatedAt and CascadeType.PERSIST inserts the new message.
        // repository.save() would be em.merge() (the id is non-null by now), and merging a
        // parent whose orphanRemoval collection has removals queued from a previous trim
        // discards those deletions. See trimMessages for the full explanation.

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
     * <p><strong>This method must not call {@code repository.save(conversation)}.</strong>
     * The conversation arrives managed, so {@code save} resolves to {@code em.merge} rather
     * than {@code em.persist} — Spring Data picks between them on {@code id == null}, and
     * the id is populated by now. Hibernate does not carry pending orphan deletions across a
     * merge: merging a parent whose {@code orphanRemoval} collection has had elements
     * removed drops those deletions, so the removed messages stay managed in the session,
     * still referencing their parent, but are no longer reachable from the parent's
     * collection.</p>
     *
     * <p>That state is invisible until something deletes the parent. A {@code deleteAll}
     * cascades {@code remove} only down the collection it can reach, so the stranded
     * messages are left behind as persistent instances pointing at a parent that is now in
     * DELETED state — which Hibernate reports as an unsaved transient instance:</p>
     *
     * <pre>
     * org.hibernate.TransientObjectException: persistent instance references an unsaved
     * transient instance of ConversationEntity
     * </pre>
     *
     * <p>Dropping the merge is necessary but not sufficient. {@code orphanRemoval} does not
     * delete anything by itself: the deletes are produced during flush-time cascading, in
     * {@code AbstractFlushingEventListener.prepareEntityFlushes}, whose <em>first</em> loop
     * cascades {@code PERSIST_ON_FLUSH} (the only action whose {@code deleteOrphans()} is
     * {@code true}) and which walks only entries that are still {@code flushable()} —
     * {@code MANAGED}, {@code SAVING} or {@code READ_ONLY}. So the orphan deletes exist only
     * while {@code conversation} itself is managed.</p>
     *
     * <p>Every caller of this method is free to delete that conversation next
     * ({@link #clear}, {@link #evictIfNeeded}, the wholesale replacement in {@link #save},
     * or a caller's own {@code deleteAll}). Once {@code em.remove} has run, the parent's
     * {@code EntityEntry} is {@code Status.DELETED}, it is no longer {@code flushable()}, and
     * the first loop can never produce the orphan deletes again. The trimmed messages are
     * still {@code MANAGED} — orphan removal never touched them — and they are unreachable
     * from the parent, so nothing else deletes them either. The <em>second</em> loop of the
     * same method then runs {@code CHECK_ON_FLUSH} over every flushable entity, follows the
     * owning {@code @ManyToOne ConversationMessageEntity.conversation}, and
     * {@code CascadingActions.isChildTransient} returns true because the child's status
     * {@code isDeletedOrGone()}. The flush aborts with:</p>
     *
     * <pre>
     * org.hibernate.TransientObjectException: persistent instance references an unsaved
     * transient instance of ConversationEntity
     * </pre>
     *
     * <p>Hence the explicit {@code repository.flush()} below. It runs the first loop while
     * the parent is still managed, so the trimmed rows are gone before this method returns
     * and the persistence context never holds a managed message whose parent is deleted.
     * It is skipped entirely when nothing was trimmed (the early return above), which is the
     * common case.</p>
     *
     * <p>The collection is also never reordered in place. {@code @OrderBy("sequence ASC")}
     * already returns it in sequence order and every read path sorts by {@code sequence},
     * so the copy is sorted purely to identify the oldest entries.</p>
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
        // Deliberately no repository.save(): see the method Javadoc. Merging here is what
        // discarded the orphan deletions and left managed messages pointing at a parent
        // that a later delete had already removed.
        //
        // Flush instead, so the orphan deletes are produced while this conversation is still
        // MANAGED. Without it the trimmed messages stay managed and unreachable, and the next
        // delete of the conversation turns them into a TransientObjectException at flush.
        repository.flush();
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