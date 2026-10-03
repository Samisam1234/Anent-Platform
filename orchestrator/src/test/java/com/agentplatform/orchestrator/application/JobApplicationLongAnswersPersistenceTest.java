package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the {@code application_answers} column width.
 *
 * <p>Prepared application answers are the three AI/deterministic suggested answers
 * joined into a single string and routinely exceed 255 characters. When the column
 * was a default {@code VARCHAR(255)}, persisting such an application threw an H2
 * {@code 22001} (value too long for column) and {@code POST /api/v1/applications/prepare}
 * returned HTTP 500. The column is {@code TEXT}, so the full answer must round-trip
 * unchanged through the exact JPA/repository path the controller uses.</p>
 */
@DataJpaTest
@DisplayName("JobApplication — long application_answers persistence")
class JobApplicationLongAnswersPersistenceTest {

    /** Realistic sample answers, comfortably above the old VARCHAR(255) limit. */
    private static final String LONG_ANSWERS = String.join(" || ",
            "I am interested in this role because it combines my interest in Software Engineering "
                    + "with the opportunity to design and operate production services at Acme.",
            "I am a good fit for this position because my parsed resume evidences Java, Spring Boot, "
                    + "SQL and AWS, which map directly to the role's required skills.",
            "My relevant experience includes building REST APIs, CI/CD pipelines and monitoring "
                    + "dashboards, and I enjoy the cross-team collaboration this position describes."
                    + " This sentence is padded to make the combined answer comfortably longer than "
                    + "the former 255-character column limit and prove no truncation occurs.");

    @Autowired
    private JobApplicationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("persists and reloads answers over 255 characters without truncation or DataIntegrityViolation")
    void persistsLongAnswersWithoutTruncation() {
        assertTrue(LONG_ANSWERS.length() > 255,
                "test fixture must exceed the former VARCHAR(255) limit");

        JobApplication app = new JobApplication(1L, "mock-sw-001", "Software Engineer", "Acme", "Remote");
        app.setApplicationAnswers(LONG_ANSWERS);

        assertDoesNotThrow(() -> repository.saveAndFlush(app));

        entityManager.clear();

        JobApplication loaded = repository.findById(app.getId()).orElseThrow();
        assertEquals(LONG_ANSWERS, loaded.getApplicationAnswers());
        assertEquals(LONG_ANSWERS.length(), loaded.getApplicationAnswers().length());
    }
}