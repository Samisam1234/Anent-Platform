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

/**
 * TEMPORARY CROSS-CLASS HARNESS — delete once the reactor failure is explained.
 *
 * <p>{@code PersistentConversationStoreSequenceTest} passes 19/19 on its own but fails at
 * {@code tearDown} in a full {@code mvn clean test}. So the trigger is another test class running
 * earlier in the same surefire JVM, not anything inside the mirror. Three classes sort ahead of
 * it: {@code InMemoryConversationStoreTest} (a plain unit test, no JPA context at all),
 * {@code PersistentConversationStoreContaminationTest}, and
 * {@code PersistentConversationStoreDiagnosticsTest}.</p>
 *
 * <p>The last of those contains {@code DIAGNOSTIC_1}, which wraps {@code flush()} in
 * {@code try/catch (RuntimeException)} and then carries on. That is the one operation in the whole
 * suite that swallows a flush failure: Hibernate treats a session that has thrown as unusable, yet
 * this test keeps using it and reports success. This class reduces that to a single method so the
 * pair can be run without the rest of the diagnostics suite.</p>
 *
 * <p>It carries the same annotations, the same {@code @BeforeEach} and the same cleanup as the
 * mirror, so the two classes share one cached Spring context and one database exactly as they do
 * in the reactor run. The name sorts before {@code SequenceTest}, and
 * {@code -Dsurefire.runOrder=alphabetical} makes that order explicit rather than leaving it to
 * filesystem discovery.</p>
 */
@DataJpaTest
@Import(ConversationStoreConfig.class)
@ActiveProfiles("test")
@DisplayName("DIAGNOSTIC — a swallowed flush failure, isolated to one method")
class PersistentConversationStoreFlushAbortTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository repository;

    private PersistentConversationStore store;

    @BeforeEach
    void setUp() {
        store = new PersistentConversationStore(repository);
    }

    @Test
    @DisplayName("flush failure is swallowed and the session keeps being used")
    void swallowedFlushFailure() {
        for (int i = 0; i < 50; i++) {
            store.append("conv-1", ConversationMessage.user("m" + i));
        }
        store.trim("conv-1", 5);

        try {
            entityManager.flush();
            System.out.println("[FLUSH-ABORT] flush succeeded — nothing left behind to contaminate");
        } catch (RuntimeException ex) {
            // This is the shape of DIAGNOSTIC_1: the failure is caught, the method completes,
            // and surefire records a pass even though the session threw.
            System.out.println("[FLUSH-ABORT] SWALLOWED " + ex.getClass().getName()
                    + ": " + ex.getMessage());
        }
    }

    /** Same cleanup as the mirror. Caught so this class reports and the JVM moves on. */
    @AfterEach
    void tearDown() {
        try {
            repository.deleteAll();
            entityManager.flush();
            System.out.println("[FLUSH-ABORT] tearDown cleanup succeeded");
        } catch (RuntimeException ex) {
            System.out.println("[FLUSH-ABORT] tearDown cleanup THREW " + ex.getClass().getName()
                    + ": " + ex.getMessage());
        }
    }
}
