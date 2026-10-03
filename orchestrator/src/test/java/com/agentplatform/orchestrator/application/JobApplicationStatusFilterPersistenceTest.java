package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slice-3 list-contract tests against the real in-memory H2 repository.
 *
 * <p>Phase 12.9 requires the candidate list to be deterministically ordered
 * {@code updatedAt DESC, id DESC} and, when filtered, scoped both by candidate and
 * by status. These tests seed rows with controlled timestamps/id ordering
 * (including two rows sharing the same {@code updatedAt} so the id DESC tie-break
 * is exercised) and assert the derived finder queries return exactly those rows
 * for the requesting candidate alone.</p>
 */
@DataJpaTest
@DisplayName("JobApplication — status-filtered, deterministically ordered candidate list")
class JobApplicationStatusFilterPersistenceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Autowired
    private JobApplicationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private JobApplication appForCandidate1GeneratedOld;
    private JobApplication appForCandidate1GeneratedNew;
    private JobApplication appForCandidate1Draft;
    private JobApplication appForCandidate2Generated;

    @BeforeEach
    void seed() {
        appForCandidate1GeneratedOld = application(1L, ApplicationStatus.GENERATED, BASE.minusHours(2));
        appForCandidate1GeneratedNew = application(1L, ApplicationStatus.GENERATED, BASE.minusHours(1));
        // Same updatedAt as the newest generated row → id DESC must break the tie.
        appForCandidate1Draft = application(1L, ApplicationStatus.DRAFT, BASE.minusHours(1));
        appForCandidate2Generated = application(2L, ApplicationStatus.GENERATED, BASE);

        entityManager.persistAndFlush(appForCandidate1GeneratedOld);
        entityManager.persistAndFlush(appForCandidate1GeneratedNew);
        entityManager.persistAndFlush(appForCandidate1Draft);
        entityManager.persistAndFlush(appForCandidate2Generated);
        entityManager.clear();
    }

    private static JobApplication application(long candidateId, ApplicationStatus status, LocalDateTime updatedAt) {
        JobApplication app = new JobApplication(candidateId, "job-" + candidateId, "Software Engineer", "Test Corp", "Hyderabad");
        app.setApplicationStatus(status);
        app.setUpdatedAt(updatedAt);
        return app;
    }

    private static List<Long> ids(List<JobApplication> apps) {
        return apps.stream().map(JobApplication::getId).toList();
    }

    @Test
    @DisplayName("unfiltered list is scoped to the candidate and ordered updatedAt DESC, id DESC")
    void unfilteredIsScopedAndOrderedNewestFirst() {
        List<JobApplication> apps = repository.findByCandidateIdOrderByUpdatedAtDescIdDesc(1L);

        // Newest updatedAt first; the two rows sharing an updatedAt fall back to id DESC.
        assertEquals(List.of(
                appForCandidate1Draft.getId(),
                appForCandidate1GeneratedNew.getId(),
                appForCandidate1GeneratedOld.getId()), ids(apps));
    }

    @Test
    @DisplayName("status-filtered list keeps candidate scoping and ordering")
    void statusFilteredIsScopedAndOrdered() {
        List<JobApplication> apps = repository
                .findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(1L, ApplicationStatus.GENERATED);

        assertEquals(List.of(
                appForCandidate1GeneratedNew.getId(),
                appForCandidate1GeneratedOld.getId()), ids(apps));
    }

    @Test
    @DisplayName("status filter never leaks another candidate's applications")
    void statusFilterDoesNotLeakOtherCandidates() {
        List<JobApplication> candidate1Generated = repository
                .findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(1L, ApplicationStatus.GENERATED);
        List<JobApplication> candidate2Generated = repository
                .findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(2L, ApplicationStatus.GENERATED);

        assertEquals(2, candidate1Generated.size());
        assertEquals(List.of(appForCandidate2Generated.getId()), ids(candidate2Generated));
    }

    @Test
    @DisplayName("filtered list for a status with no matches is empty")
    void filterWithNoMatchesIsEmpty() {
        List<JobApplication> apps = repository
                .findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(2L, ApplicationStatus.DRAFT);

        assertEquals(List.of(), ids(apps));
    }
}