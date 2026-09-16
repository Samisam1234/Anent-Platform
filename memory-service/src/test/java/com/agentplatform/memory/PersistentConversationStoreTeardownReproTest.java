package com.agentplatform.memory;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEMPORARY REPRODUCTION HARNESS — delete once the cause is established.
 *
 * <p>{@code PersistentConversationStoreDiagnosticsTest.diagnostic1_failingLifecycle} runs the
 * same production lifecycle as {@code PersistentConversationStoreTest.trim_keepsMostRecent} and
 * <em>does not reproduce</em> the failure — its flush succeeds. So the defect is not in
 * {@code PersistentConversationStore}; it is in how the failing test class cleans up. Comparing
 * the two classes leaves exactly two differences:</p>
 *
 * <ol>
 *   <li>the real test cleans up in {@code @AfterEach}; the diagnostic cleans up inline at the
 *       end of the test body;</li>
 *   <li>the real test class has 19 {@code @Test} methods; the diagnostic has 5.</li>
 * </ol>
 *
 * <p>Everything else — {@code @DataJpaTest}, {@code @Import(ConversationStoreConfig.class)},
 * {@code @ActiveProfiles("test")}, and {@code setUp()} — is identical.</p>
 *
 * <p>This class is a byte-for-byte mirror of the real test's lifecycle for the trim scenario,
 * {@code @AfterEach} included, but with only that scenario in it. It carries two identical
 * methods so a single run separates the two candidates:</p>
 *
 * <ul>
 *   <li><b>both fail</b> &rArr; the {@code @AfterEach} cleanup pattern is the cause, independent
 *       of neighbouring tests;</li>
 *   <li><b>exactly one fails</b> &rArr; state survives between test methods in the same class;</li>
 *   <li><b>neither fails</b> &rArr; the other 18 methods in the real class are implicated.</li>
 * </ul>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@DisplayName("REPRO — trim lifecycle under the real test's @AfterEach cleanup")
class PersistentConversationStoreTeardownReproTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository repository;

    private PersistentConversationStore store;

    @BeforeEach
    void setUp() {
        store = new PersistentConversationStore(repository);
    }

    /** Verbatim copy of {@code PersistentConversationStoreTest.tearDown()}. */
    @AfterEach
    void tearDown() {
        repository.deleteAll();
        entityManager.flush();
    }

    @Test
    @DisplayName("REPRO_A — trim lifecycle, cleaned up by @AfterEach")
    void reproA() {
        runTrimLifecycle("REPRO_A");
    }

    @Test
    @DisplayName("REPRO_B — the identical lifecycle a second time in the same class")
    void reproB() {
        runTrimLifecycle("REPRO_B");
    }

    /** Byte-for-byte the body of {@code PersistentConversationStoreTest.trim_keepsMostRecent}. */
    private void runTrimLifecycle(String label) {
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        store.trim("conv-1", 5);
        assertThat(store.messages("conv-1")).hasSize(5);
        assertThat(store.messages("conv-1").get(0).content()).isEqualTo("m45");
        assertThat(store.messages("conv-1").get(4).content()).isEqualTo("m49");
        System.out.println("[" + label + "] body completed cleanly; @AfterEach runs next");
    }
}
