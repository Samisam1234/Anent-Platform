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
 * TEMPORARY ORDERED REPRODUCTION — C: binary split — the first 6 methods of the prefix, then trim. Delete when the investigation is done.
 *
 * <p>First half. Compared against D, this halves the search space.</p>
 *
 * <p>The order is not guessed. JUnit Jupiter 5.11.4 sorts a class's test methods via
 * {@code ReflectionUtils.toSortedMutableList} ({@code :1766-1773}) using
 * {@code defaultMethodSorter} ({@code :1787-1798}), which compares
 * {@code Integer.compare(name1.hashCode(), name2.hashCode())} and breaks ties by name — ascending
 * 32-bit {@code String.hashCode()}. The {@code @Order} values below reproduce that order exactly,
 * so the sequence is explicit rather than incidental.</p>
 *
 * <p>Every body is copied verbatim from {@code PersistentConversationStoreTest}, not delegated to
 * it, so the lifecycle is equivalent rather than simulated: same annotations, same injected
 * fields, same {@code @BeforeEach}, and the same unguarded {@code @AfterEach}, so a failure
 * surfaces with the original signature.</p>
 *
 * <p>Order: lastUpdated_reflectsLatestWrite > exists_returnsCorrectly > append_preservesOrder > conversationIds_exposesKeys > messages_orderedCorrectly > append_createsAndPersists > trim_keepsMostRecent</p>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("REPRO C — first half of the prefix (methods 1-6) + trim")
class PersistentConversationStoreSplitFirstHalfReproTest {

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
    @DisplayName("lastUpdated_reflectsLatestWrite")
    void lastUpdated_reflectsLatestWrite() {
        store.append("conv-1", ConversationMessage.user("a"));
        Instant first = store.lastUpdated("conv-1");
        store.append("conv-1", ConversationMessage.user("b"));
        Instant second = store.lastUpdated("conv-1");
        assertThat(second).isNotNull().isAfterOrEqualTo(first);

    }

    @Test
    @Order(2)
    @DisplayName("exists_returnsCorrectly")
    void exists_returnsCorrectly() {
        assertThat(store.exists("conv-new")).isFalse();
        store.append("conv-new", ConversationMessage.user("test"));
        assertThat(store.exists("conv-new")).isTrue();
        store.clear("conv-new");
        assertThat(store.exists("conv-new")).isFalse();

    }

    @Test
    @Order(3)
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
    @Order(4)
    @DisplayName("conversationIds_exposesKeys")
    void conversationIds_exposesKeys() {
        store.append("c1", ConversationMessage.user("a"));
        store.append("c2", ConversationMessage.user("b"));
        assertThat(store.conversationIds()).containsExactlyInAnyOrder("c1", "c2");
        assertThat(store.count()).isEqualTo(2);

    }

    @Test
    @Order(5)
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
    @Order(6)
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
    @Order(7)
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
