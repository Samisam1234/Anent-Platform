package com.agentplatform.memory;

import com.agentplatform.memory.entity.ConversationEntity;
import com.agentplatform.memory.entity.ConversationMessageEntity;
import com.agentplatform.memory.repository.ConversationRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DIAGNOSTIC harness for {@code PersistentConversationStoreTest.trim_keepsMostRecent}, which
 * fails in {@code tearDown} with
 * {@code TransientObjectException: persistent instance references an unsaved transient
 * instance of ConversationEntity}.
 *
 * <p>Each case reproduces the same lifecycle (50 appends, then a trim to 5) and dumps the
 * Hibernate persistence context at the three points that matter: immediately after the trim,
 * immediately after {@code repository.deleteAll()}, and around the {@code entityManager.flush()}
 * that throws. The cases are variants chosen so that their pass/fail pattern identifies the
 * cause rather than merely describing it:</p>
 *
 * <ol>
 *   <li><b>DIAGNOSTIC_1</b> — the failing lifecycle. Expected to throw.</li>
 *   <li><b>DIAGNOSTIC_2</b> — same, but {@code entityManager.clear()} before {@code deleteAll()}.
 *       Passes &rArr; the defect is persistence-context state; throws &rArr; it is the
 *       data/lifecycle.</li>
 *   <li><b>DIAGNOSTIC_3</b> — same appends, no trim, so no orphan is ever created.</li>
 *   <li><b>DIAGNOSTIC_4</b> — the same removals done incrementally, one flush per removal,
 *       which is the timing {@code trimIfNeeded} already uses inside {@code append()}.</li>
 *   <li><b>DIAGNOSTIC_5</b> — the batch trim followed by an explicit {@code repository.flush()}
 *       before cleanup. Passes &rArr; forcing the orphan deletes while the parent is still
 *       persistent is sufficient, which is the smallest possible production fix.</li>
 * </ol>
 *
 * <p>Delete this file once the cause is established and the fix is in.</p>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@DisplayName("DIAGNOSTIC — trim_keepsMostRecent entity-state dump")
class PersistentConversationStoreDiagnosticsTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private ConversationRepository repository;

    private PersistentConversationStore store;

    @BeforeEach
    void setUp() {
        store = new PersistentConversationStore(repository);
    }

    private EntityManager em() {
        return testEntityManager.getEntityManager();
    }

    private PersistenceContext persistenceContext() {
        return em().unwrap(SessionImplementor.class).getPersistenceContextInternal();
    }

    private static int idHash(Object o) {
        return o == null ? 0 : System.identityHashCode(o);
    }

    private String statusOf(Object entity) {
        if (entity == null) {
            return "null";
        }
        EntityEntry entry = persistenceContext().getEntry(entity);
        return entry == null ? "NOT-IN-SESSION" : String.valueOf(entry.getStatus());
    }

    /**
     * Dumps every ConversationEntity and ConversationMessageEntity Hibernate currently knows
     * about, plus the contents of every conversation's collection.
     */
    /**
     * {@link #dump(String)} guarded so that a failure inside the dump itself can never abort
     * the harness and hide the evidence it was meant to produce. A session that has just
     * failed a flush is not guaranteed to still answer inspection calls.
     */
    private void safeDump(String when) {
        try {
            dump(when);
        } catch (RuntimeException ex) {
            System.out.println("[DIAG " + when + "] DUMP FAILED: "
                    + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    private void dump(String when) {
        PersistenceContext pc = persistenceContext();
        List<ConversationEntity> conversations = new ArrayList<>();
        List<ConversationMessageEntity> messages = new ArrayList<>();

        for (Map.Entry<Object, EntityEntry> e : pc.reentrantSafeEntityEntries()) {
            Object entity = e.getKey();
            EntityEntry entry = e.getValue();
            if (entity instanceof ConversationEntity c) {
                conversations.add(c);
            } else if (entity instanceof ConversationMessageEntity m) {
                messages.add(m);
            }
        }

        System.out.println("==== [DIAG " + when + "] conversations in session: " + conversations.size()
                + ", messages in session: " + messages.size() + " ====");

        for (ConversationEntity c : conversations) {
            List<ConversationMessageEntity> coll = c.getMessages();
            System.out.printf(
                    "[DIAG %s] Conversation hash=%s dbId=%s businessId=%s status=%s emContains=%s collClass=%s collSize=%s%n",
                    when, idHash(c), c.getId(), c.getConversationId(), statusOf(c), em().contains(c),
                    coll.getClass().getSimpleName(), coll.size());
            System.out.printf("[DIAG %s]   collection message ids/seqs = %s%n", when,
                    coll.stream()
                            .map(m -> "dbId=" + m.getId() + "/seq=" + m.getSequence()
                                    + "/hash=" + idHash(m) + "/" + m.getContent())
                            .toList());

            // Bidirectional consistency: is each collection element's back-reference the very
            // same ConversationEntity instance that owns the collection?
            for (ConversationMessageEntity m : coll) {
                ConversationEntity parent = m.getConversation();
                System.out.printf(
                        "[DIAG %s]   collElement seq=%s hash=%s -> parentHash=%s sameInstance=%s%n",
                        when, m.getSequence(), idHash(m), idHash(parent), parent == c);
            }
        }

        for (ConversationMessageEntity m : messages) {
            ConversationEntity parent = m.getConversation();
            System.out.printf(
                    "[DIAG %s] Message hash=%s dbId=%s seq=%s content=%s status=%s emContains=%s"
                            + " | parent=%s parentDbId=%s parentHash=%s parentStatus=%s parentEmContains=%s%n",
                    when, idHash(m), m.getId(), m.getSequence(), m.getContent(), statusOf(m),
                    em().contains(m),
                    parent == null ? "NULL" : parent.getClass().getSimpleName(),
                    parent == null ? "NULL" : String.valueOf(parent.getId()),
                    idHash(parent), statusOf(parent),
                    parent != null && em().contains(parent));
        }
    }

    /** The exact lifecycle of trim_keepsMostRecent. */
    private void buildFiftyThenTrimToFive() {
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        store.trim("conv-1", 5);
    }

    @Test
    @DisplayName("DIAGNOSTIC_1 — the failing lifecycle, dumped end to end")
    void diagnostic1_failingLifecycle() {
        // The expected TransientObjectException is deliberately caught rather than allowed to
        // error the method, so that this case completes and its entity-state evidence is
        // printed. The outcome is reported on its own [DIAG_1] line either way.
        buildFiftyThenTrimToFive();
        assertThat(store.messages("conv-1")).hasSize(5);

        safeDump("AFTER_TRIM");

        System.out.println("[DIAG] -> repository.deleteAll()");
        repository.deleteAll();
        safeDump("AFTER_DELETE_ALL");

        safeDump("PRE_FLUSH");
        System.out.println("[DIAG] -> entityManager.flush()");

        String outcome;
        try {
            em().flush();
            outcome = "DID NOT REPRODUCE - flush() succeeded";
        } catch (RuntimeException ex) {
            outcome = "REPRODUCED " + ex.getClass().getName() + ": " + ex.getMessage();
            System.out.println("[DIAG] FLUSH THREW: " + ex.getClass().getName()
                    + ": " + ex.getMessage());
            safeDump("AFTER_FAILED_FLUSH");
        }

        safeDump("FINAL");
        System.out.println("[DIAG_1] " + outcome);
    }

    @Test
    @DisplayName("DIAGNOSTIC_2 — same lifecycle, entityManager.clear() before deleteAll()")
    void diagnostic2_clearBeforeDeleteAll() {
        buildFiftyThenTrimToFive();
        assertThat(store.messages("conv-1")).hasSize(5);
        dump("BEFORE_CLEAR");

        em().clear();
        repository.deleteAll();
        em().flush();
        System.out.println("[DIAG_2] PASSED — clearing the persistence context avoided the failure");
    }

    @Test
    @DisplayName("DIAGNOSTIC_3 — 50 appends, no trim (no orphan ever created)")
    void diagnostic3_noTrim() {
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        assertThat(store.messages("conv-1")).hasSize(50);

        repository.deleteAll();
        em().flush();
        System.out.println("[DIAG_3] PASSED — without a trim the same cleanup succeeds");
    }

    @Test
    @DisplayName("DIAGNOSTIC_4 — the same removals done incrementally, one flush at a time")
    void diagnostic4_incrementalTrim() {
        // Deliberately does NOT call buildFiftyThenTrimToFive(): the point of this case is to
        // reach the same end state (5 messages, 45 removals) by removing five at a time with a
        // read — and therefore an auto-flush — between each batch. Trimming to 5 up front would
        // leave nothing to remove and the size assertions below could never hold.
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        assertThat(store.messages("conv-1")).hasSize(50);

        for (int target = 45; target >= 5; target -= 5) {
            store.trim("conv-1", target);
            // The read forces the auto-flush between removals, which is the timing
            // trimIfNeeded already relies on inside append().
            assertThat(store.messages("conv-1")).hasSize(target);
        }
        assertThat(store.messages("conv-1")).hasSize(5);
        safeDump("AFTER_INCREMENTAL_TRIM");

        repository.deleteAll();
        em().flush();
        System.out.println("[DIAG_4] PASSED — incremental removals clean up correctly");
    }

    @Test
    @DisplayName("DIAGNOSTIC_5 — batch trim followed by an explicit flush before cleanup")
    void diagnostic5_flushAfterTrim() {
        buildFiftyThenTrimToFive();
        assertThat(store.messages("conv-1")).hasSize(5);
        safeDump("BEFORE_EXPLICIT_FLUSH");

        repository.flush();
        safeDump("AFTER_EXPLICIT_FLUSH");

        repository.deleteAll();
        em().flush();
        System.out.println("[DIAG_5] PASSED — flushing the orphan deletes before cleanup avoids the failure");
    }
}
