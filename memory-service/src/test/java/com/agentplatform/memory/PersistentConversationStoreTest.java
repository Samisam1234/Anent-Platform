package com.agentplatform.memory;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link PersistentConversationStore} using a real
 * in-memory H2 database via Spring's {@code @DataJpaTest}.
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@DisplayName("PersistentConversationStore — JPA-backed conversation persistence")
class PersistentConversationStoreTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository repository;

    private PersistentConversationStore store;

    @BeforeEach
    void setUp() {
        store = new PersistentConversationStore(repository);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        entityManager.flush();
    }

    @Test
    @DisplayName("append() creates conversation on first write and persists messages")
    void append_createsAndPersists() {
        store.append("conv-1", ConversationMessage.user("Hello"));
        store.append("conv-1", ConversationMessage.ai("Hi there"));

        List<ConversationMessage> messages = store.messages("conv-1");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("user");
        assertThat(messages.get(0).content()).isEqualTo("Hello");
        assertThat(messages.get(1).role()).isEqualTo("ai");
        assertThat(messages.get(1).content()).isEqualTo("Hi there");

        assertThat(store.exists("conv-1")).isTrue();
        assertThat(store.count()).isEqualTo(1);
        assertThat(store.snapshot("conv-1")).isPresent();
    }

    @Test
    @DisplayName("fresh conversation is empty and does not exist")
    void unknown_conversationReturnsEmpty() {
        assertThat(store.exists("nope")).isFalse();
        assertThat(store.messages("nope")).isEmpty();
        assertThat(store.snapshot("nope")).isEmpty();
        assertThat(store.lastUpdated("nope")).isNull();
    }

    @Test
    @DisplayName("messages() returns messages in correct order (oldest first)")
    void messages_orderedCorrectly() {
        store.append("conv-1", ConversationMessage.user("first"));
        store.append("conv-1", ConversationMessage.ai("second"));
        store.append("conv-1", ConversationMessage.user("third"));

        List<ConversationMessage> messages = store.messages("conv-1");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).content()).isEqualTo("first");
        assertThat(messages.get(1).content()).isEqualTo("second");
        assertThat(messages.get(2).content()).isEqualTo("third");
    }

    @Test
    @DisplayName("save() replaces the entire history")
    void save_replacesHistory() {
        store.append("conv-1", ConversationMessage.user("old"));
        store.save("conv-1", List.of(ConversationMessage.user("new"), ConversationMessage.ai("x")));
        assertThat(store.messages("conv-1")).hasSize(2);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("new");
    }

    @Test
    @DisplayName("trim() keeps only the most recent messages")
    void trim_keepsMostRecent() {
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        store.trim("conv-1", 5);
        assertThat(store.messages("conv-1")).hasSize(5);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
        assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");
    }

    @Test
    @DisplayName("trim() deletes the removed messages before their conversation can be deleted")
    void trim_deletesRemovedMessagesBeforeConversationIsDeleted() {
        for (int i = 0; i < 10; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }

        // Hold the managed parent first, then trim, then remove the parent — with no query in
        // between, so nothing can auto-flush and paper over a missed orphan delete.
        ConversationEntity conversation = repository.findByConversationId("conv-1").orElseThrow();
        store.trim("conv-1", 3);
        repository.delete(conversation);

        // Regression guard: the 7 trimmed messages must already be deleted. If they are still
        // MANAGED while the parent is DELETED, this flush fails with
        // TransientObjectException: persistent instance references an unsaved transient
        // instance of ConversationEntity.
        entityManager.flush();

        assertThat(store.messages("conv-1")).isEmpty();
        assertThat(store.exists("conv-1")).isFalse();
        assertThat(store.count()).isZero();
    }

    @Test
    @DisplayName("clear() removes the conversation from database")
    void clear_removesConversation() {
        store.append("conv-1", ConversationMessage.user("a"));
        store.clear("conv-1");
        assertThat(store.exists("conv-1")).isFalse();
        assertThat(store.count()).isZero();
    }

    @Test
    @DisplayName("lastUpdated() reflects the latest write time")
    void lastUpdated_reflectsLatestWrite() {
        store.append("conv-1", ConversationMessage.user("a"));
        Instant first = store.lastUpdated("conv-1");
        store.append("conv-1", ConversationMessage.user("b"));
        Instant second = store.lastUpdated("conv-1");
        assertThat(second).isNotNull().isAfterOrEqualTo(first);
    }

    @Test
    @DisplayName("conversationIds() exposes created conversation ids")
    void conversationIds_exposesKeys() {
        store.append("c1", ConversationMessage.user("a"));
        store.append("c2", ConversationMessage.user("b"));
        assertThat(store.conversationIds()).containsExactlyInAnyOrder("c1", "c2");
        assertThat(store.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("null/blank conversationId is rejected")
    void blankId_rejected() {
        assertThatThrownBy(() -> store.append(null, ConversationMessage.user("a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.append("  ", ConversationMessage.user("a")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("max 60 messages per conversation enforced on append")
    void maxMessagesPerConversation_enforced() {
        for (int i = 0; i < 70; i++) {
            store.append("conv-1", ConversationMessage.user("msg" + i));
        }
        // Should be trimmed to 60
        assertThat(store.messages("conv-1")).hasSize(PersistentConversationStore.MAX_MESSAGES_PER_CONVERSATION);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("msg10");
        assertThat(store.messages("conv-1").get(59).content()).isEqualTo("msg69");
    }

    @Test
    @DisplayName("max 60 messages per conversation enforced on save")
    void maxMessagesPerConversation_enforcedOnSave() {
        List<ConversationMessage> many = java.util.stream.IntStream.range(0, 70)
                .mapToObj(i -> ConversationMessage.user("msg" + i))
                .toList();
        store.save("conv-1", many);
        assertThat(store.messages("conv-1")).hasSize(PersistentConversationStore.MAX_MESSAGES_PER_CONVERSATION);
    }

    @Test
    @DisplayName("max 200 conversations enforced with oldest eviction")
    void maxConversations_evictsOldest() {
        int max = PersistentConversationStore.MAX_CONVERSATIONS;
        for (int i = 0; i < max + 10; i++) {
            String id = "conv-" + i;
            store.append(id, ConversationMessage.user("payload"));
        }
        assertThat(store.count()).isLessThanOrEqualTo(max);
        // The oldest ids should have been evicted first
        assertThat(store.exists("conv-0")).isFalse();
        assertThat(store.exists("conv-" + (max + 9))).isTrue();
    }

    @Test
    @DisplayName("persistence survives store recreation (simulates JVM restart)")
    void persistence_survivesStoreRecreation() {
        store.append("conv-1", ConversationMessage.user("Hello"));
        store.append("conv-1", ConversationMessage.ai("Hi"));

        // Create a new store instance with the same repository (simulates restart)
        PersistentConversationStore newStore = new PersistentConversationStore(repository);

        List<ConversationMessage> messages = newStore.messages("conv-1");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("Hello");
        assertThat(messages.get(1).content()).isEqualTo("Hi");
    }

    @Test
    @DisplayName("snapshot() returns correct createdAt and updatedAt")
    void snapshot_returnsTimestamps() {
        store.append("conv-1", ConversationMessage.user("Hello"));
        Instant createdAt = store.snapshot("conv-1").get().createdAt();
        Instant updatedAt = store.snapshot("conv-1").get().updatedAt();

        store.append("conv-1", ConversationMessage.ai("Hi"));
        Instant newUpdatedAt = store.snapshot("conv-1").get().updatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();
        assertThat(newUpdatedAt).isAfterOrEqualTo(updatedAt);
    }

    @Test
    @DisplayName("append() preserves message order across multiple calls")
    void append_preservesOrder() {
        store.append("conv-1", ConversationMessage.user("1"));
        store.append("conv-1", ConversationMessage.ai("2"));
        store.append("conv-1", ConversationMessage.user("3"));
        store.append("conv-1", ConversationMessage.ai("4"));

        List<ConversationMessage> messages = store.messages("conv-1");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).content()).isEqualTo("1");
        assertThat(messages.get(1).content()).isEqualTo("2");
        assertThat(messages.get(2).content()).isEqualTo("3");
        assertThat(messages.get(3).content()).isEqualTo("4");
    }

    @Test
    @DisplayName("multiple conversations are isolated")
    void multipleConversations_isolated() {
        store.append("conv-a", ConversationMessage.user("A1"));
        store.append("conv-b", ConversationMessage.user("B1"));
        store.append("conv-a", ConversationMessage.ai("A2"));

        assertThat(store.messages("conv-a")).hasSize(2);
        assertThat(store.messages("conv-b")).hasSize(1);
        assertThat(store.messages("conv-a").get(0).content()).isEqualTo("A1");
        assertThat(store.messages("conv-b").get(0).content()).isEqualTo("B1");
    }

    @Test
    @DisplayName("exists() returns true after append, false after clear")
    void exists_returnsCorrectly() {
        assertThat(store.exists("conv-new")).isFalse();
        store.append("conv-new", ConversationMessage.user("test"));
        assertThat(store.exists("conv-new")).isTrue();
        store.clear("conv-new");
        assertThat(store.exists("conv-new")).isFalse();
    }

    @Test
    @DisplayName("trim() on non-existent conversation is no-op")
    void trim_nonexistent_noop() {
        store.trim("nonexistent", 10);
        assertThat(store.count()).isZero();
    }

    @Test
    @DisplayName("clear() on non-existent conversation is no-op")
    void clear_nonexistent_noop() {
        store.clear("nonexistent");
        assertThat(store.count()).isZero();
    }
}