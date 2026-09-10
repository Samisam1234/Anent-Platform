package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic tests for {@link JobAggregatorService}.
 *
 * <p>No network, no database: providers are in-process fakes. The behaviours that matter
 * are the ones the search depends on — parallel dispatch, per-provider failure isolation,
 * cross-provider deduplication, and above all that provenance ({@code source},
 * {@code sourceUrl}, {@code applicationUrl}) survives merging untouched, so a listing is
 * never attributed to the wrong board or pointed at a fabricated destination.</p>
 */
@DisplayName("JobAggregatorService — multi-provider aggregation")
class JobAggregatorServiceTest {

    private static final JobSearchRequest REQUEST =
            JobSearchRequest.of(List.of("java"), null, null, null, null, 50);

    private static Job job(String id, String title, String company, String source,
                           String sourceUrl, String applicationUrl) {
        return new Job(id, title, company, "Remote", "description",
                List.of("Java"), List.of(), null, "full_time", "2026-09-01",
                source, sourceUrl, "PUBLIC_API", Instant.now(), applicationUrl);
    }

    /** Minimal in-process provider. */
    private static class FakeProvider implements JobSourceProvider {
        private final String name;
        private final List<Job> jobs;
        private final RuntimeException failure;
        private final long delayMillis;
        private final boolean available;
        final AtomicInteger calls = new AtomicInteger();

        FakeProvider(String name, List<Job> jobs, boolean available) {
            this(name, jobs, null, 0, available);
        }

        FakeProvider(String name, List<Job> jobs, RuntimeException failure, long delayMillis,
                     boolean available) {
            this.name = name;
            this.jobs = jobs != null ? jobs : List.of();
            this.failure = failure;
            this.delayMillis = delayMillis;
            this.available = available;
        }

        @Override
        public List<Job> fetchJobs(JobSearchRequest criteria) {
            calls.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
            return jobs;
        }

        @Override
        public String getSourceName() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }

    private JobAggregatorService serviceWith(JobSourceProvider... providers) {
        return new JobAggregatorService(List.of(providers), new JobDeduplicationService());
    }

    @Nested
    @DisplayName("Merging and provenance")
    class MergingTests {

        @Test
        @DisplayName("listings from every available provider are merged")
        void mergesAllProviders() {
            JobAggregatorService service = serviceWith(
                    new FakeProvider("REMOTIVE", List.of(
                            job("remotive-1", "Java Engineer", "Acme", "REMOTIVE",
                                    "https://remotive.com/job/1", null)), true),
                    new FakeProvider("ARBEITNOW", List.of(
                            job("arbeitnow-a", "Python Engineer", "Beta", "ARBEITNOW",
                                    "https://arbeitnow.com/view/a", null)), true),
                    new FakeProvider("ADZUNA", List.of(
                            job("adzuna-9", "Go Engineer", "Gamma", "ADZUNA",
                                    "https://adzuna.co.uk/job/9", null)), true));

            List<Job> result = service.search(REQUEST);

            assertEquals(3, result.size());
            assertEquals(List.of("ADZUNA", "ARBEITNOW", "REMOTIVE"),
                    result.stream().map(Job::source).sorted().toList());
        }

        @Test
        @DisplayName("each job keeps its own originating source and source URL")
        void provenanceIsPreservedPerJob() {
            JobAggregatorService service = serviceWith(
                    new FakeProvider("REMOTIVE", List.of(
                            job("remotive-1", "Java Engineer", "Acme", "REMOTIVE",
                                    "https://remotive.com/job/1", null)), true),
                    new FakeProvider("ADZUNA", List.of(
                            job("adzuna-9", "Go Engineer", "Gamma", "ADZUNA",
                                    "https://www.adzuna.co.uk/jobs/land/ad/9", null)), true));

            List<Job> result = service.search(REQUEST);

            Job remotive = result.stream().filter(j -> j.id().equals("remotive-1")).findFirst().orElseThrow();
            Job adzuna = result.stream().filter(j -> j.id().equals("adzuna-9")).findFirst().orElseThrow();
            assertEquals("REMOTIVE", remotive.source());
            assertEquals("https://remotive.com/job/1", remotive.sourceUrl());
            assertEquals("ADZUNA", adzuna.source());
            assertEquals("https://www.adzuna.co.uk/jobs/land/ad/9", adzuna.sourceUrl());
            // Merging must not invent an employer application destination.
            assertNull(remotive.applicationUrl());
            assertNull(adzuna.applicationUrl());
        }

        @Test
        @DisplayName("a genuine application URL survives aggregation untouched")
        void applicationUrlSurvivesAggregation() {
            String employerUrl = "https://careers.acme.example/apply/123";
            JobAggregatorService service = serviceWith(
                    new FakeProvider("REMOTIVE", List.of(
                            job("remotive-1", "Java Engineer", "Acme", "REMOTIVE",
                                    "https://remotive.com/job/1", employerUrl)), true));

            List<Job> result = service.search(REQUEST);

            assertEquals(1, result.size());
            assertEquals(employerUrl, result.get(0).applicationUrl());
            assertEquals("https://remotive.com/job/1", result.get(0).sourceUrl());
        }

        @Test
        @DisplayName("the same listing syndicated to two boards is kept once")
        void dedupesAcrossProviders() {
            JobAggregatorService service = serviceWith(
                    new FakeProvider("REMOTIVE", List.of(
                            job("remotive-1", "Senior Java Engineer", "Acme Corp", "REMOTIVE",
                                    "https://remotive.com/job/1", null)), true),
                    new FakeProvider("ARBEITNOW", List.of(
                            job("arbeitnow-x", "senior java engineer", "acme corp", "ARBEITNOW",
                                    "https://arbeitnow.com/view/x", null)), true));

            List<Job> result = service.search(REQUEST);

            assertEquals(1, result.size(), "identical title+company+location must dedupe to one listing");
            // First-occurrence order is preserved, so the surviving row keeps real provenance.
            assertNotNull(result.get(0).source());
        }
    }

    @Nested
    @DisplayName("Failure isolation")
    class FailureTests {

        @Test
        @DisplayName("a provider that throws does not discard the others' results")
        void throwingProviderIsIsolated() {
            JobAggregatorService service = serviceWith(
                    new FakeProvider("BROKEN", List.of(), new IllegalStateException("boom"), 0, true),
                    new FakeProvider("REMOTIVE", List.of(
                            job("remotive-1", "Java Engineer", "Acme", "REMOTIVE",
                                    "https://remotive.com/job/1", null)), true));

            List<Job> result = service.search(REQUEST);

            assertEquals(1, result.size());
            assertEquals("REMOTIVE", result.get(0).source());
        }

        @Test
        @DisplayName("a provider that hangs is abandoned and the search still returns")
        void hangingProviderIsAbandoned() {
            JobAggregatorService service = serviceWith(
                    new FakeProvider("SLOW", List.of(), null, 60_000, true),
                    new FakeProvider("REMOTIVE", List.of(
                            job("remotive-1", "Java Engineer", "Acme", "REMOTIVE",
                                    "https://remotive.com/job/1", null)), true));

            long start = System.nanoTime();
            List<Job> result = service.search(REQUEST);
            long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

            assertEquals(1, result.size());
            assertTrue(elapsedSeconds < 45,
                    "search must not wait for the hung provider, took " + elapsedSeconds + "s");
        }

        @Test
        @DisplayName("a provider whose isAvailable() throws is skipped, not fatal")
        void brokenAvailabilityCheckIsSkipped() {
            JobSourceProvider explosive = new JobSourceProvider() {
                @Override
                public List<Job> fetchJobs(JobSearchRequest criteria) {
                    return List.of(job("x", "X", "Y", "EXPLOSIVE", null, null));
                }

                @Override
                public String getSourceName() {
                    return "EXPLOSIVE";
                }

                @Override
                public boolean isAvailable() {
                    throw new IllegalStateException("bad config");
                }
            };
            JobAggregatorService service = serviceWith(explosive,
                    new FakeProvider("REMOTIVE", List.of(
                            job("remotive-1", "Java Engineer", "Acme", "REMOTIVE",
                                    "https://remotive.com/job/1", null)), true));

            List<Job> result = service.search(REQUEST);

            assertEquals(1, result.size());
            assertEquals("REMOTIVE", result.get(0).source());
        }

        @Test
        @DisplayName("every provider failing yields an empty result, not an exception")
        void allProvidersFailingIsEmptyNotFatal() {
            JobAggregatorService service = serviceWith(
                    new FakeProvider("A", List.of(), new IllegalStateException("boom"), 0, true),
                    new FakeProvider("B", List.of(), new IllegalStateException("boom"), 0, true));

            List<Job> result = service.search(REQUEST);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Availability and reporting")
    class AvailabilityTests {

        @Test
        @DisplayName("disabled providers are never called")
        void disabledProvidersAreNeverCalled() {
            FakeProvider disabled = new FakeProvider("ADZUNA", List.of(
                    job("adzuna-1", "Java Engineer", "Acme", "ADZUNA", null, null)), false);
            FakeProvider enabled = new FakeProvider("REMOTIVE", List.of(
                    job("remotive-1", "Java Engineer", "Acme", "REMOTIVE", null, null)), true);

            List<Job> result = serviceWith(disabled, enabled).search(REQUEST);

            assertEquals(0, disabled.calls.get(), "unavailable provider must not be invoked");
            assertEquals(1, enabled.calls.get());
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("no configured providers returns empty and reports NONE")
        void noProvidersIsEmpty() {
            JobAggregatorService service = serviceWith();

            assertTrue(service.search(REQUEST).isEmpty());
            assertFalse(service.isAvailable());
            assertFalse(service.isLive());
            assertEquals(JobAggregatorService.NO_PROVIDERS, service.getSourceName());
        }

        @Test
        @DisplayName("getSourceName names the providers that actually participated")
        void sourceNameListsParticipatingProviders() {
            JobAggregatorService service = serviceWith(
                    new FakeProvider("REMOTIVE", List.of(), true),
                    new FakeProvider("ADZUNA", List.of(), false),
                    new FakeProvider("ARBEITNOW", List.of(), true));

            String name = service.getSourceName();

            assertTrue(name.contains("REMOTIVE"), "was: " + name);
            assertTrue(name.contains("ARBEITNOW"), "was: " + name);
            assertFalse(name.contains("ADZUNA"), "unavailable provider must not be advertised: " + name);
        }

        @Test
        @DisplayName("isLive reflects whether any provider is available")
        void livenessTracksProviders() {
            assertTrue(serviceWith(new FakeProvider("A", List.of(), true)).isLive());
            assertFalse(serviceWith(new FakeProvider("A", List.of(), false)).isLive());
        }
    }

    @Nested
    @DisplayName("Normalization")
    class NormalizationTests {

        @Test
        @DisplayName("listings without an id or title are dropped before matching")
        void unusableListingsAreDropped() {
            List<Job> raw = new ArrayList<>();
            raw.add(job("remotive-1", "Java Engineer", "Acme", "REMOTIVE", null, null));
            raw.add(job("   ", "No Id", "Acme", "REMOTIVE", null, null));
            raw.add(job("remotive-3", "  ", "Acme", "REMOTIVE", null, null));
            raw.add(null);

            List<Job> result = serviceWith(new FakeProvider("REMOTIVE", raw, true)).search(REQUEST);

            assertEquals(1, result.size());
            assertEquals("remotive-1", result.get(0).id());
        }
    }

    @Nested
    @DisplayName("Parallel dispatch")
    class ParallelTests {

        @Test
        @DisplayName("providers are queried concurrently, not sequentially")
        void providersRunInParallel() {
            // Three providers that each block on the same latch: if the aggregator called
            // them one after another the latch would never reach zero and this would hang.
            CountDownLatch allStarted = new CountDownLatch(3);
            JobSourceProvider blocking = new JobSourceProvider() {
                @Override
                public List<Job> fetchJobs(JobSearchRequest criteria) {
                    allStarted.countDown();
                    try {
                        assertTrue(allStarted.await(10, TimeUnit.SECONDS),
                                "providers must be dispatched concurrently");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                }

                @Override
                public String getSourceName() {
                    return "P";
                }

                @Override
                public boolean isAvailable() {
                    return true;
                }
            };

            List<Job> result = serviceWith(blocking, blocking, blocking).search(REQUEST);

            assertTrue(result.isEmpty());
            assertEquals(0, allStarted.getCount(), "all three providers must have started together");
        }
    }
}
