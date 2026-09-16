package com.agentplatform.memory;

import com.agentplatform.memory.repository.ConversationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEMPORARY BISECTION HARNESS — delete once the contaminating test is identified.
 *
 * <p>{@code trim_keepsMostRecent} passes on its own and in
 * {@code PersistentConversationStoreTeardownReproTest}, so the production trim lifecycle and the
 * {@code @AfterEach} cleanup pattern are both sound. The failure needs a predecessor from the
 * same class. This harness pins the ordering JUnit otherwise chooses for us: each nested class
 * runs one suspect immediately before a verbatim copy of {@code trim_keepsMostRecent}, using
 * {@code @TestMethodOrder(OrderAnnotation.class)} so the sequence is exact rather than
 * incidental.</p>
 *
 * <p>Every nested class is independent and carries the real class's {@code @BeforeEach} and
 * {@code @AfterEach}. {@code Control} has a trivial predecessor and must pass; if it fails, the
 * harness itself is wrong and the other results mean nothing. Whichever other nested classes
 * fail name the contaminating test.</p>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@DisplayName("DIAGNOSTIC — which predecessor contaminates trim_keepsMostRecent")
class PersistentConversationStoreContaminationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository repository;

    private PersistentConversationStore store;

    @BeforeEach
    void setUp() {
        store = new PersistentConversationStore(repository);
    }

    /** Verbatim copy of PersistentConversationStoreTest.tearDown(). */
    @AfterEach
    void tearDown() {
        repository.deleteAll();
        entityManager.flush();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("CONTROL — trivial predecessor, must PASS")
    class Control {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
            System.out.println("[Control] trivial predecessor, no persistence work");
        }

        @Test
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[Control] trim body completed; @AfterEach runs next");
        }
    }
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("after maxConversations_evictsOldest")
    class After_maxConversations_evictsOldest {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
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
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[After_maxConversations_evictsOldest] trim body completed; @AfterEach runs next");
        }
    }
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("after maxMessagesPerConversation_enforced")
    class After_maxMessagesPerConversation_enforced {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
            for (int i = 0; i < 70; i++) {
                store.append("conv-1", ConversationMessage.user("msg" + i));
            }
            // Should be trimmed to 60
            assertThat(store.messages("conv-1")).hasSize(PersistentConversationStore.MAX_MESSAGES_PER_CONVERSATION);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("msg10");
            assertThat(store.messages("conv-1").get(59).content()).isEqualTo("msg69");

        }

        @Test
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[After_maxMessagesPerConversation_enforced] trim body completed; @AfterEach runs next");
        }
    }
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("after maxMessagesPerConversation_enforcedOnSave")
    class After_maxMessagesPerConversation_enforcedOnSave {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
            List<ConversationMessage> many = java.util.stream.IntStream.range(0, 70)
                    .mapToObj(i -> ConversationMessage.user("msg" + i))
                    .toList();
            store.save("conv-1", many);
            assertThat(store.messages("conv-1")).hasSize(PersistentConversationStore.MAX_MESSAGES_PER_CONVERSATION);

        }

        @Test
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[After_maxMessagesPerConversation_enforcedOnSave] trim body completed; @AfterEach runs next");
        }
    }
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("after save_replacesHistory")
    class After_save_replacesHistory {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
            store.append("conv-1", ConversationMessage.user("old"));
            store.save("conv-1", List.of(ConversationMessage.user("new"), ConversationMessage.ai("x")));
            assertThat(store.messages("conv-1")).hasSize(2);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("new");

        }

        @Test
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[After_save_replacesHistory] trim body completed; @AfterEach runs next");
        }
    }
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("after clear_removesConversation")
    class After_clear_removesConversation {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
            store.append("conv-1", ConversationMessage.user("a"));
            store.clear("conv-1");
            assertThat(store.exists("conv-1")).isFalse();
            assertThat(store.count()).isZero();

        }

        @Test
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[After_clear_removesConversation] trim body completed; @AfterEach runs next");
        }
    }
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("after persistence_survivesStoreRecreation")
    class After_persistence_survivesStoreRecreation {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
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
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[After_persistence_survivesStoreRecreation] trim body completed; @AfterEach runs next");
        }
    }
    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("after blankId_rejected")
    class After_blankId_rejected {

        @Test
        @Order(1)
        @DisplayName("predecessor")
        void predecessor() {
            assertThatThrownBy(() -> store.append(null, ConversationMessage.user("a")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> store.append("  ", ConversationMessage.user("a")))
                    .isInstanceOf(IllegalArgumentException.class);

        }

        @Test
        @Order(2)
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

            System.out.println("[After_blankId_rejected] trim body completed; @AfterEach runs next");
        }
    }
}
