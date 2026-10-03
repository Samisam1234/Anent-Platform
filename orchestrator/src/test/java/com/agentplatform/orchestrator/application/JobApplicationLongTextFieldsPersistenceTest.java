package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the prepared-content TEXT columns.
 *
 * <p>The prepared application package routinely produces text longer than 255
 * characters in six fields ({@code generated_resume_summary}, {@code cover_letter},
 * {@code candidate_strengths}, {@code matching_skills}, {@code missing_skills},
 * {@code resume_highlights}). Before they were mapped as {@code TEXT}, persisting
 * such an application threw an H2 {@code 22001} (value too long for column) and
 * {@code POST /api/v1/applications/prepare} returned HTTP 500. This test drives the
 * exact persistence path the controller uses — {@link ApplicationStorageService#store}
 * followed by a repository reload — and asserts every field round-trips unchanged
 * with content comfortably above the former {@code VARCHAR(255)} boundary.</p>
 */
@DataJpaTest
@Import(ApplicationStorageService.class)
@DisplayName("JobApplication — long prepared-content fields persistence")
class JobApplicationLongTextFieldsPersistenceTest {

    private static final String LONG_SUMMARY = paragraph(
            "Expert software engineer with proven Spring Boot, REST, SQL and AWS experience "
                    + "delivering production services end to end. ");
    private static final String LONG_COVER_LETTER = paragraph(
            "I am excited to apply because this role matches my demonstrated software engineering "
                    + "experience and lets me design and operate production systems. ");
    private static final String LONG_CANDIDATE_STRENGTHS = paragraph(
            "Java and Spring Boot fluency, REST API design, SQL and PostgreSQL skills, and "
                    + "cross-team collaboration. ");
    private static final String LONG_MATCHING_SKILLS = String.join(", ",
            "Java", "Spring Boot", "Spring", "REST API", "PostgreSQL", "SQL", "Hibernate", "JPA", "Git",
            "Maven", "Java", "Spring Boot", "Spring", "REST API", "PostgreSQL", "SQL", "Hibernate", "JPA",
            "Git", "Maven", "Java", "Spring Boot", "Spring", "REST API", "PostgreSQL", "SQL", "Hibernate",
            "JPA", "Git", "Maven", "Java", "Spring Boot", "Spring", "REST API", "PostgreSQL", "SQL");
    private static final String LONG_MISSING_SKILLS = String.join(", ",
            "Kubernetes", "Docker", "CI/CD automation", "Observability tooling", "Message brokers",
            "Kubernetes", "Docker", "CI/CD automation", "Observability tooling", "Message brokers",
            "Kubernetes", "Docker", "CI/CD automation", "Observability tooling", "Message brokers",
            "Kubernetes", "Docker", "CI/CD automation", "Observability tooling", "Message brokers");
    private static final String LONG_RESUME_HIGHLIGHTS = paragraph(
            "Built a hardware-accurate neural network simulator in Java and a hospital management "
                    + "system on Spring Boot with PostgreSQL. ");

    @Autowired
    private ApplicationStorageService storageService;

    @Autowired
    private JobApplicationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static String paragraph(String stem) {
        return stem.repeat(8); // ~8x the stem, comfortably above the former 255-char limit
    }

    @Test
    @DisplayName("stores and reloads all prepared text fields over 255 chars without truncation or DataIntegrityViolation")
    void persistsLongPreparedFieldsWithoutTruncation() {
        assertTrue(LONG_SUMMARY.length() > 255);
        assertTrue(LONG_COVER_LETTER.length() > 255);
        assertTrue(LONG_CANDIDATE_STRENGTHS.length() > 255);
        assertTrue(LONG_MATCHING_SKILLS.length() > 255);
        assertTrue(LONG_MISSING_SKILLS.length() > 255);
        assertTrue(LONG_RESUME_HIGHLIGHTS.length() > 255);

        JobApplication app = new JobApplication(1L, "mock-sw-001", "Software Engineer", "Acme", "Remote");
        app.setGeneratedResumeSummary(LONG_SUMMARY);
        app.setCoverLetter(LONG_COVER_LETTER);
        app.setCandidateStrengths(LONG_CANDIDATE_STRENGTHS);
        app.setMatchingSkills(LONG_MATCHING_SKILLS);
        app.setMissingSkills(LONG_MISSING_SKILLS);
        app.setResumeHighlights(LONG_RESUME_HIGHLIGHTS);

        assertDoesNotThrow(() -> {
            storageService.store(app);
            entityManager.flush();
        });

        Long id = app.getId();
        entityManager.clear();

        JobApplication loaded = repository.findById(id).orElseThrow();
        assertEquals(LONG_SUMMARY, loaded.getGeneratedResumeSummary());
        assertEquals(LONG_COVER_LETTER, loaded.getCoverLetter());
        assertEquals(LONG_CANDIDATE_STRENGTHS, loaded.getCandidateStrengths());
        assertEquals(LONG_MATCHING_SKILLS, loaded.getMatchingSkills());
        assertEquals(LONG_MISSING_SKILLS, loaded.getMissingSkills());
        assertEquals(LONG_RESUME_HIGHLIGHTS, loaded.getResumeHighlights());
        assertEquals(LONG_SUMMARY.length(), loaded.getGeneratedResumeSummary().length());
        assertEquals(LONG_COVER_LETTER.length(), loaded.getCoverLetter().length());
        assertEquals(LONG_CANDIDATE_STRENGTHS.length(), loaded.getCandidateStrengths().length());
        assertEquals(LONG_MATCHING_SKILLS.length(), loaded.getMatchingSkills().length());
        assertEquals(LONG_MISSING_SKILLS.length(), loaded.getMissingSkills().length());
        assertEquals(LONG_RESUME_HIGHLIGHTS.length(), loaded.getResumeHighlights().length());
    }
}