package com.agentplatform.memory;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEMPORARY FULL-CLASS MIRROR — delete once the minimal failing sequence is known.
 *
 * <p>Single-suspect pairs do not reproduce the failure, so the cause needs the cumulative
 * lifecycle of the whole class. This mirror contains all 19 methods of
 * {@code PersistentConversationStoreTest} with their <em>original names</em> and, deliberately,
 * <em>no</em> {@code @TestMethodOrder}. JUnit's default ordering is a deterministic function of
 * the method-name set, so an identical name set yields the identical execution order — this
 * reproduces the real class without anyone having to know that order in advance.
 * {@link OrderReporter} then prints the order as it actually happens.</p>
 *
 * <p>{@code @BeforeEach} and {@code @AfterEach} are the real class's, with a query-free
 * persistence-context report added <em>before</em> the verbatim cleanup. It must stay
 * query-free: a repository call would trigger an auto-flush and could mask the very failure
 * under investigation. Row counts are only taken after the cleanup, where a flush has already
 * happened.</p>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@ExtendWith(PersistentConversationStoreSequenceTest.OrderReporter.class)
@DisplayName("DIAGNOSTIC — full mirror of PersistentConversationStoreTest, ordered as the real class")
class PersistentConversationStoreSequenceTest {

    /** Prints the real execution order and each outcome, which no source reading can reveal. */
    static class OrderReporter implements TestWatcher {
        private static int n = 0;
        @Override public void testSuccessful(ExtensionContext ctx) {
            System.out.printf("[ORDER %2d] PASS  %s%n", ++n, ctx.getDisplayName());
        }
        @Override public void testFailed(ExtensionContext ctx, Throwable cause) {
            System.out.printf("[ORDER %2d] FAIL  %s  -> %s: %s%n",
                    ++n, ctx.getDisplayName(), cause.getClass().getSimpleName(), cause.getMessage());
        }
        @Override public void testAborted(ExtensionContext ctx, Throwable cause) {
            System.out.printf("[ORDER %2d] SKIP  %s%n", ++n, ctx.getDisplayName());
        }
        @Override public void testDisabled(ExtensionContext ctx, Optional<String> reason) {
            System.out.printf("[ORDER --] DISABLED %s%n", ctx.getDisplayName());
        }
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository repository;

    private PersistentConversationStore store;

    @BeforeEach
    void setUp() {
        report("BEFORE_TEST");
        store = new PersistentConversationStore(repository);
    }

    /** The real class's tearDown, verbatim, preceded by a query-free state report. */
    @AfterEach
    void tearDown() {
        report("BEFORE_CLEANUP");
        repository.deleteAll();
        entityManager.flush();
        System.out.printf("[SEQ] AFTER_CLEANUP conversations=%d messages=%d%n",
                countRows("select count(c) from ConversationEntity c"),
                countRows("select count(m) from ConversationMessageEntity m"));
    }

    private long countRows(String jpql) {
        try {
            Number n = (Number) entityManager.getEntityManager().createQuery(jpql).getSingleResult();
            return n.longValue();
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /**
     * Query-free snapshot of the persistence context. Deliberately issues no JPQL and touches no
     * repository, so it cannot auto-flush and alter the state being examined.
     */
    private void report(String when) {
        try {
            EntityManager em = entityManager.getEntityManager();
            PersistenceContext pc = em.unwrap(SessionImplementor.class).getPersistenceContextInternal();
            int conversations = 0, messages = 0, deletedMessages = 0, managedMessages = 0;
            Map<String, Integer> byStatus = new LinkedHashMap<>();
            for (Map.Entry<Object, EntityEntry> e : pc.reentrantSafeEntityEntries()) {
                Object entity = e.getKey();
                String status = String.valueOf(e.getValue().getStatus());
                if (entity instanceof ConversationEntity) {
                    conversations++;
                } else if (entity instanceof ConversationMessageEntity) {
                    messages++;
                    byStatus.merge(status, 1, Integer::sum);
                    if (status.startsWith("DELETED") || status.startsWith("GONE")) {
                        deletedMessages++;
                    } else {
                        managedMessages++;
                    }
                }
            }
            System.out.printf("[SEQ %s] conversationsInContext=%d messagesInContext=%d managed=%d deleted=%d byStatus=%s%n",
                    when, conversations, messages, managedMessages, deletedMessages, byStatus);
        } catch (RuntimeException ex) {
            System.out.println("[SEQ " + when + "] REPORT FAILED: " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }
    }

    @Test
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
    @DisplayName("unknown_conversationReturnsEmpty")
    void unknown_conversationReturnsEmpty() {
        assertThat(store.exists("nope")).isFalse();
        assertThat(store.messages("nope")).isEmpty();
        assertThat(store.snapshot("nope")).isEmpty();
        assertThat(store.lastUpdated("nope")).isNull();

    }

    @Test
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
    @DisplayName("save_replacesHistory")
    void save_replacesHistory() {
        store.append("conv-1", ConversationMessage.user("old"));
        store.save("conv-1", List.of(ConversationMessage.user("new"), ConversationMessage.ai("x")));
        assertThat(store.messages("conv-1")).hasSize(2);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("new");

    }

    @Test
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

    @Test
    @DisplayName("clear_removesConversation")
    void clear_removesConversation() {
        store.append("conv-1", ConversationMessage.user("a"));
        store.clear("conv-1");
        assertThat(store.exists("conv-1")).isFalse();
        assertThat(store.count()).isZero();

    }

    @Test
    @DisplayName("lastUpdated_reflectsLatestWrite")
    void lastUpdated_reflectsLatestWrite() {
        store.append("conv-1", ConversationMessage.user("a"));
        Instant first = store.lastUpdated("conv-1");
        store.append("conv-1", ConversationMessage.user("b"));
        Instant second = store.lastUpdated("conv-1");
        assertThat(second).isNotNull().isAfterOrEqualTo(first);

    }

    @Test
    @DisplayName("conversationIds_exposesKeys")
    void conversationIds_exposesKeys() {
        store.append("c1", ConversationMessage.user("a"));
        store.append("c2", ConversationMessage.user("b"));
        assertThat(store.conversationIds()).containsExactlyInAnyOrder("c1", "c2");
        assertThat(store.count()).isEqualTo(2);

    }

    @Test
    @DisplayName("blankId_rejected")
    void blankId_rejected() {
        assertThatThrownBy(() -> store.append(null, ConversationMessage.user("a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.append("  ", ConversationMessage.user("a")))
                .isInstanceOf(IllegalArgumentException.class);

    }

    @Test
    @DisplayName("maxMessagesPerConversation_enforced")
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
    @DisplayName("maxMessagesPerConversation_enforcedOnSave")
    void maxMessagesPerConversation_enforcedOnSave() {
        List<ConversationMessage> many = java.util.stream.IntStream.range(0, 70)
                .mapToObj(i -> ConversationMessage.user("msg" + i))
                .toList();
        store.save("conv-1", many);
        assertThat(store.messages("conv-1")).hasSize(PersistentConversationStore.MAX_MESSAGES_PER_CONVERSATION);

    }

    @Test
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
    @DisplayName("persistence_survivesStoreRecreation")
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
    @DisplayName("append_preservesOrder")
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
    @DisplayName("exists_returnsCorrectly")
    void exists_returnsCorrectly() {
        assertThat(store.exists("conv-new")).isFalse();
        store.append("conv-new", ConversationMessage.user("test"));
        assertThat(store.exists("conv-new")).isTrue();
        store.clear("conv-new");
        assertThat(store.exists("conv-new")).isFalse();

    }

    @Test
    @DisplayName("trim_nonexistent_noop")
    void trim_nonexistent_noop() {
        store.trim("nonexistent", 10);
        assertThat(store.count()).isZero();

    }

    @Test
    @DisplayName("clear_nonexistent_noop")
    void clear_nonexistent_noop() {
        store.clear("nonexistent");
        assertThat(store.count()).isZero();

    }

}
