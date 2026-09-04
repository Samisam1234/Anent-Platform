package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 Step 3.4 — {@link JobSearchService#findById(String)} behavior.
 *
 * <p>Uses hermetic stub {@link JobSource}s so no live network is touched: mock and
 * public-provenance retrieval, source-failure isolation, and unknown/blank handling
 * are all deterministic.</p>
 */
@DisplayName("JobSearchService.findById — job details lookup (Step 3.4)")
class JobSearchServiceFindByIdTest {

    private static final Instant NOW = Instant.now();

    private static final class StubSource implements JobSource {
        private final String name;
        private final boolean live;
        private final boolean available;
        private final List<Job> jobs;
        private RuntimeException failure;

        StubSource(String name, boolean live, List<Job> jobs) {
            this(name, live, true, jobs);
        }

        StubSource(String name, boolean live, boolean available, List<Job> jobs) {
            this.name = name;
            this.live = live;
            this.available = available;
            this.jobs = jobs;
        }

        StubSource failing(RuntimeException ex) {
            this.failure = ex;
            return this;
        }

        @Override public String getSourceName() { return name; }
        @Override public boolean isLive() { return live; }
        @Override public boolean isAvailable() { return available; }

        @Override
        public List<Job> search(JobSearchRequest request) {
            if (failure != null) throw failure;
            return jobs;
        }
    }

    private Job liveJob(String id) {
        return new Job(id, "Senior Java Engineer", "Acme", "Remote", "Desc.",
                List.of("Java", "Spring Boot"), List.of(), "5+ years", "FULL_TIME",
                "2026-08-27", "REMOTIVE",
                "https://remotive.com/remote-jobs/software-development/" + id, "PUBLIC_API", NOW);
    }

    private JobSearchService service(JobSource... sources) {
        return new JobSearchService(List.of(sources), new JobDeduplicationService());
    }

    @Nested
    @DisplayName("Retrieval")
    class RetrievalTests {

        @Test
        @DisplayName("existing mock job is found via MockJobSource")
        void existingMockFound() {
            Optional<Job> found = service(new MockJobSource()).findById("mock-sw-001");

            assertTrue(found.isPresent());
            assertEquals("mock-sw-001", found.get().id());
            assertEquals(MockJobSource.SOURCE_NAME, found.get().source());
            assertEquals("MOCK", found.get().sourceType());
        }

        @Test
        @DisplayName("existing public-source job is found across sources")
        void existingPublicFound() {
            Optional<Job> found = service(
                    new StubSource("REMOTIVE", true, List.of(liveJob("remotive-2091101"), liveJob("remotive-2"))),
                    new MockJobSource())
                    .findById("remotive-2091101");

            assertTrue(found.isPresent());
            assertEquals("REMOTIVE", found.get().source());
            assertEquals("PUBLIC_API", found.get().sourceType());
            assertTrue(JobUrlValidator.isValidExternalUrl(found.get().sourceUrl()));
        }
    }

    @Nested
    @DisplayName("Not found / blank")
    class NotFoundTests {

        @Test
        @DisplayName("unknown job id returns empty (404 root cause)")
        void unknownIdEmpty() {
            Optional<Job> found = service(
                    new StubSource("REMOTIVE", true, List.of(liveJob("remotive-1"))),
                    new MockJobSource())
                    .findById("does-not-exist");

            assertFalse(found.isPresent());
        }

        @Test
        @DisplayName("null and blank ids return empty, never throw")
        void blankIdEmpty() {
            JobSearchService service = service(new MockJobSource());

            assertFalse(service.findById(null).isPresent());
            assertFalse(service.findById("").isPresent());
            assertFalse(service.findById("   ").isPresent());
        }
    }

    @Nested
    @DisplayName("Source failure isolation")
    class FailureIsolationTests {

        @Test
        @DisplayName("a failing source does not prevent other sources being checked")
        void failingSourceIsolated() {
            JobSearchService service = service(
                    new StubSource("broken", true, List.of()).failing(new RuntimeException("boom")),
                    new MockJobSource());

            Optional<Job> found = service.findById("mock-sw-001");

            assertTrue(found.isPresent());
            assertEquals("mock-sw-001", found.get().id());
        }

        @Test
        @DisplayName("job found in the second source when the first lacks it")
        void foundInSecondSource() {
            JobSearchService service = service(
                    new StubSource("REMOTIVE", true, List.of(liveJob("remotive-1"))),
                    new MockJobSource());

            Optional<Job> found = service.findById("mock-sw-002");

            assertTrue(found.isPresent());
            assertEquals("mock-sw-002", found.get().id());
        }

        @Test
        @DisplayName("unavailable source is skipped without error")
        void unavailableSourceSkipped() {
            JobSearchService service = service(
                    new StubSource("offline", true, false, List.of(liveJob("hidden-1"))),
                    new MockJobSource());

            // hidden-1 would match if the offline source were consulted, but it is skipped;
            // the mock catalog still resolves.
            assertTrue(service.findById("mock-sw-003").isPresent());
            assertEquals("mock-sw-003", service.findById("mock-sw-003").get().id());
        }

        @Test
        @DisplayName("all sources fail or are empty → empty result, no crash")
        void allSourcesFailOrEmpty() {
            JobSearchService service = service(
                    new StubSource("a", true, List.of()).failing(new RuntimeException("x")),
                    new StubSource("b", true, List.of()).failing(new RuntimeException("y")));

            assertFalse(service.findById("anything").isPresent());
        }
    }
}