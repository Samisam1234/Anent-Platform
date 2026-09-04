package com.agentplatform.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-process conversation store (default implementation).
 *
 * <p>This is intentionally a clean incremental memory layer: it survives page
 * navigation and restarted browsers (the UI mirrors the transcript in
 * localStorage), and it provides real server-side history that the agent uses
 * to build follow-up prompts. It does <em>not</em> survive a full JVM restart —
 * a durable/pgvector implementation can replace it later behind the same
 * {@link ConversationStore} interface.</p>
 *
 * <p>Bounds: each conversation is kept to at most {@link #MAX_MESSAGES_PER_CONVERSATION}
 * messages and the store to at most {@link #MAX_CONVERSATIONS} conversations
 * (oldest-updated evicted first).</p>
 */
@Service
public class InMemoryConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryConversationStore.class);

    public static final int MAX_MESSAGES_PER_CONVERSATION = 60;
    public static final int MAX_CONVERSATIONS = 200;

    private final Map<String, ConversationSnapshot> conversations = new ConcurrentHashMap<>();

    @Override
    public void append(String conversationId, ConversationMessage message) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        List<ConversationMessage> messages = new ArrayList<>(messages(conversationId));
        messages.add(message);
        save(conversationId, messages);
    }

    @Override
    public void save(String conversationId, List<ConversationMessage> messages) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        Instant now = Instant.now();
        Instant createdAt = conversations.containsKey(conversationId)
                ? conversations.get(conversationId).createdAt()
                : now;

        conversations.put(conversationId,
                new ConversationSnapshot(conversationId, List.copyOf(messages), createdAt, now));

        evictIfNeeded(conversationId);
    }

    @Override
    public Optional<ConversationSnapshot> snapshot(String conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }

    @Override
    public List<ConversationMessage> messages(String conversationId) {
        return snapshot(conversationId)
                .map(ConversationSnapshot::messages)
                .orElseGet(List::of);
    }

    @Override
    public boolean exists(String conversationId) {
        return conversations.containsKey(conversationId);
    }

    @Override
    public void trim(String conversationId, int maxMessages) {
        snapshot(conversationId).ifPresent(snapshot -> {
            List<ConversationMessage> current = snapshot.messages();
            if (current.size() > maxMessages) {
                List<ConversationMessage> trimmed = new ArrayList<>(
                        current.subList(current.size() - maxMessages, current.size()));
                save(conversationId, trimmed);
                log.debug("Trimmed conversation {} to {} messages", conversationId, maxMessages);
            }
        });
    }

    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }

    @Override
    public Set<String> conversationIds() {
        return new LinkedHashSet<>(conversations.keySet());
    }

    @Override
    public int count() {
        return conversations.size();
    }

    @Override
    public Instant lastUpdated(String conversationId) {
        return snapshot(conversationId).map(ConversationSnapshot::updatedAt).orElse(null);
    }

    private void evictIfNeeded(String retained) {
        if (conversations.size() <= MAX_CONVERSATIONS) {
            return;
        }
        // Evict oldest-updated conversation(s), never the one just written.
        // Deterministic total ordering: updatedAt, then createdAt, then id.
        conversations.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(retained))
                .sorted(Map.Entry.<String, ConversationSnapshot>comparingByValue(
                        Comparator.comparing(ConversationSnapshot::updatedAt)
                                .thenComparing(ConversationSnapshot::createdAt)
                                .thenComparing(ConversationSnapshot::conversationId)))
                .limit(1)
                .forEach(entry -> {
                    conversations.remove(entry.getKey());
                    log.debug("Evicted oldest conversation {} (store at capacity {})",
                            entry.getKey(), MAX_CONVERSATIONS);
                });
    }
}