package com.agentplatform.memory;

import com.agentplatform.memory.repository.ConversationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEMPORARY BISECTION CLASS 05 — delete once the sequence is known.
 *
 * <p>{@code PersistentConversationStoreContaminationTest} ordered the two methods inside each
 * nested class but never ordered the nested classes themselves: {@code @TestMethodOrder} governs
 * methods only. So it is unknown whether {@code Control} ran first there, and that bisect could
 * not be read. {@code @TestClassOrder(ClassOrderer.OrderAnnotation.class)} with {@code @Order} on
 * the nested classes fixes it, and each class here is self-contained: exactly one predecessor,
 * then {@code Control.trimKeepsMostRecent}, in that order.</p>
 *
 * <p>Predecessor: <strong>clear_removesConversation</strong>. Class {@code 00} is the no-op baseline and must
 * pass; if it fails, the contamination is not coming from any predecessor and the other results
 * mean nothing. {@code @AfterEach} is the real class's, unguarded, so a failure surfaces with the
 * same signature as the reported one.</p>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@DisplayName("BISECT 05 — clear_removesConversation, then Control.trimKeepsMostRecent")
class ContaminationBisect05Test {

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

    @Nested
    @Order(1)
    @DisplayName("predecessor")
    class Predecessor {

        @Test
        @DisplayName("clear_removesConversation")
        void predecessor() {
            store.append("conv-1", ConversationMessage.user("a"));
            store.clear("conv-1");
            assertThat(store.exists("conv-1")).isFalse();
            assertThat(store.count()).isZero();

        }
    }

    @Nested
    @Order(2)
    @DisplayName("Control")
    class Control {

        @Test
        @DisplayName("trim_keepsMostRecent")
        void trimKeepsMostRecent() {
            for (int i = 0; i < 50; i++) {
                store.append("conv-1", ConversationMessage.user("m" + i));
            }
            store.trim("conv-1", 5);
            assertThat(store.messages("conv-1")).hasSize(5);
            assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
            assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");

        }
    }
}
