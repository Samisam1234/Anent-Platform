package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic contract tests for the development {@link MockJobSource} catalog.
 *
 * <p>These tests pin the "development mock data" guarantees the UI relies on:
 * every listing stays explicitly marked as {@code MOCK_SOURCE}, supplies the
 * fields the Job Details modal renders, and — critically — its sourceUrl points
 * at the non-resolvable {@code mockjobs.local} placeholder domain. Placeholder
 * URLs must never be opened by the browser; the frontend opens an internal
 * details modal for mock listings instead.</p>
 */
class MockJobSourceTest {

    private final MockJobSource source = new MockJobSource();

    private List<Job> allCatalogJobs() {
        return source.search(new JobSearchRequest(List.of(), null, null, null, null, 100));
    }

    @Test
    @DisplayName("All mock listings are explicitly marked as development mock data")
    void allListingsMarkedAsMock() {
        List<Job> jobs = allCatalogJobs();

        assertFalse(jobs.isEmpty());
        assertFalse(source.isLive());
        assertTrue(jobs.stream().allMatch(job ->
                job.source() != null && job.source().equals(MockJobSource.SOURCE_NAME)));
    }

    @Test
    @DisplayName("Every mock listing carries the fields the Job Details modal renders")
    void listingsCarryRenderableFields() {
        List<Job> jobs = allCatalogJobs();

        assertFalse(jobs.isEmpty());
        for (Job job : jobs) {
            assertNotNull(job.id(), "id must not be null");
            assertNotNull(job.title(), "title must not be null");
            assertNotNull(job.company(), "company must not be null");
            assertNotNull(job.location(), "location must not be null");
            assertNotNull(job.description(), "description must not be null");
            assertNotNull(job.experienceRequirement(), "experienceRequirement must not be null");
            assertNotNull(job.employmentType(), "employmentType must not be null");
            assertNotNull(job.postingDate(), "postingDate must not be null");
            assertFalse(job.requiredSkills().isEmpty(), "requiredSkills must not be empty");
        }
    }

    @Test
    @DisplayName("Mock listings have no external source URL")
    void mockListingsHaveNoExternalUrl() {
        List<Job> jobs = allCatalogJobs();

        assertFalse(jobs.isEmpty());
        assertTrue(jobs.stream().allMatch(job -> job.sourceUrl() == null || job.sourceUrl().isBlank()));
    }
}