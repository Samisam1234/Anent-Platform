package com.agentplatform.memory;

import com.agentplatform.memory.repository.ConversationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * TEMPORARY ORDERED REPRODUCTION — F: methods 4-12 of the verified order, then trim_keepsMostRecent. Delete when the investigation is done.
 *
 * <p>Narrows from B (1-12, FAIL) against D (7-12, PASS). If F fails, the trigger lies in methods 4-6 combined with 7-12; if F passes, methods 1-3 are required.</p>
 *
 * <p>Method order is the verified JUnit Jupiter 5.11.4 default: {@code ReflectionUtils
 * .toSortedMutableList} ({@code :1766-1773}) sorts with {@code defaultMethodSorter}
 * ({@code :1787-1798}), comparing {@code Integer.compare(name1.hashCode(), name2.hashCode())}
 * and breaking ties by name. {@code @Order} makes that sequence explicit.</p>
 *
 * <p>Bodies are copied verbatim from {@code PersistentConversationStoreTest} — no delegation —
 * with the same annotations, the same injected fields, the same {@code @BeforeEach} and the same
 * unguarded {@code @AfterEach}, so a failure surfaces with the original signature.</p>
 *
 * <p>Order: conversationIds_exposesKeys > messages_orderedCorrectly > append_createsAndPersists > maxConversations_evictsOldest > snapshot_returnsTimestamps > trim_nonexistent_noop > multipleConversations_isolated > unknown_conversationReturnsEmpty > maxMessagesPerConversation_enforcedOnSave > trim_keepsMostRecent</p>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("REPRO F — methods 4-12 + trim_keepsMostRecent")
class PersistentConversationStoreReproF4To12Test {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository repository;

    private PersistentConversationStore store;

    @BeforeEach
    void setUp() {
        store = new PersistentConversationStore(repository);
    }

    /** Verbatim from PersistentConversationStoreTest.tearDown(); deliberately unguarded. */
    @AfterEach
    void tearDown() {
        repository.deleteAll();
        entityManager.flush();
    }

    @Test
    @Order(1)
    @DisplayName("conversationIds_exposesKeys")
    void conversationIds_exposesKeys() {
        store.append("c1", ConversationMessage.user("a"));
        store.append("c2", ConversationMessage.user("b"));
        assertThat(store.conversationIds()).containsExactlyInAnyOrder("c1", "c2");
        assertThat(store.count()).isEqualTo(2);

    }

    @Test
    @Order(2)
    @DisplayName("messages_orderedCorrectly")
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
    @Order(3)
    @DisplayName("append_createsAndPersists")
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
    @Order(4)
    @DisplayName("maxConversations_evictsOldest")
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
    @Order(5)
    @DisplayName("snapshot_returnsTimestamps")
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
    @Order(6)
    @DisplayName("trim_nonexistent_noop")
    void trim_nonexistent_noop() {
        store.trim("nonexistent", 10);
        assertThat(store.count()).isZero();

    }

    @Test
    @Order(7)
    @DisplayName("multipleConversations_isolated")
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
    @Order(8)
    @DisplayName("unknown_conversationReturnsEmpty")
    void unknown_conversationReturnsEmpty() {
        assertThat(store.exists("nope")).isFalse();
        assertThat(store.messages("nope")).isEmpty();
        assertThat(store.snapshot("nope")).isEmpty();
        assertThat(store.lastUpdated("nope")).isNull();

    }

    @Test
    @Order(9)
    @DisplayName("maxMessagesPerConversation_enforcedOnSave")
    void maxMessagesPerConversation_enforcedOnSave() {
        List<ConversationMessage> many = java.util.stream.IntStream.range(0, 70)
                .mapToObj(i -> ConversationMessage.user("msg" + i))
                .toList();
        store.save("conv-1", many);
        assertThat(store.messages("conv-1")).hasSize(PersistentConversationStore.MAX_MESSAGES_PER_CONVERSATION);

    }

    @Test
    @Order(10)
    @DisplayName("trim_keepsMostRecent")
    void trim_keepsMostRecent() {
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        store.trim("conv-1", 5);
        assertThat(store.messages("conv-1")).hasSize(5);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
        assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

    }

}
