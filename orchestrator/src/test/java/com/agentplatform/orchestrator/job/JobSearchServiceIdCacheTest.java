package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exact-ID cache inside {@link JobSearchService}.
 *
 * <p>The gap it closes: a source is queried with the caller's keywords, so a listing that
 * came out of a keyword search cannot generally be re-fetched by id alone. A source that
 * requires a query term (the MCP adapter returns nothing for an empty keyword list)
 * therefore made {@code findById} fail for exactly the jobs the user had just seen.</p>
 *
 * <p>All tests are hermetic: stub sources, no network, no Spring context.</p>
 */
@DisplayName("JobSearchService exact-ID cache")
class JobSearchServiceIdCacheTest {

    private static final Instant NOW = Instant.now();

    /** Counts how often the source was actually asked for listings. */
    private static final class CountingSource implements JobSource {
        private final List<Job> jobs;
        private final AtomicInteger searches = new AtomicInteger();

        CountingSource(List<Job> jobs) {
            this.jobs = jobs;
        }

        @Override public String getSourceName() { return "COUNTING"; }
        @Override public boolean isLive() { return true; }
        @Override public boolean isAvailable() { return true; }

        @Override
        public List<Job> search(JobSearchRequest request) {
            searches.incrementAndGet();
            // Mirrors the real adapters: a query-less request yields nothing.
            if (request.keywords() == null || request.keywords().isEmpty()) {
                return List.of();
            }
            return jobs;
        }
    }

    private static Job job(String id, String applicationUrl) {
        return new Job(id, "Senior Java Engineer " + id, "Acme", "Remote", "Build things.",
                List.of("Java"), List.of(), "5+ years", "FULL_TIME", "2026-08-27",
                "OPENINGS_MCP",
                "https://www.amazon.jobs/en/jobs/" + id,
                "MCP", NOW, applicationUrl);
    }

    private static JobSearchRequest query(String keyword) {
        return new JobSearchRequest(List.of(keyword), null, null, null, null, 20);
    }

    @Nested
    @DisplayName("Search-to-id resolution")
    class ResolutionTests {

        @Test
        @DisplayName("a job returned by search resolves by id with zero extra provider calls")
        void resolvesFromCacheWithoutFurtherCalls() {
            CountingSource source = new CountingSource(List.of(job("a-1", null)));
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());

            service.search(query("java"));
            assertEquals(1, source.searches.get(), "the search itself made exactly one call");

            Optional<Job> found = service.findById("a-1");

            assertTrue(found.isPresent());
            assertEquals("a-1", found.get().id());
            assertEquals(1, source.searches.get(), "no additional provider call was needed");
        }

        @Test
        @DisplayName("applicationUrl survives the round trip through the cache")
        void preservesApplicationUrl() {
            String applyUrl = "https://account.amazon.jobs/jobs/10473779/apply";
            CountingSource source = new CountingSource(List.of(job("a-2", applyUrl)));
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());

            service.search(query("java"));

            Job resolved = service.findById("a-2").orElseThrow();
            assertEquals(applyUrl, resolved.applicationUrl());
            assertEquals("https://www.amazon.jobs/en/jobs/a-2", resolved.sourceUrl());
            assertEquals("OPENINGS_MCP", resolved.source());
        }

        @Test
        @DisplayName("repeated lookups of the same id stay on the cached record")
        void repeatedLookupsAreStable() {
            CountingSource source = new CountingSource(List.of(job("a-3", null)));
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());
            service.search(query("java"));

            for (int i = 0; i < 5; i++) {
                assertEquals("a-3", service.findById("a-3").orElseThrow().id());
            }
            assertEquals(1, source.searches.get());
        }

        @Test
        @DisplayName("only the returned page is cached, and the id returned is the one asked for")
        void returnsExactIdOnly() {
            Job inWindow = job("a-4", null);
            Job beyondLimit = job("a-5", null);
            CountingSource source = new CountingSource(List.of(inWindow, beyondLimit));
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());

            JobSearchResult result = service.search(new JobSearchRequest(
                    List.of("java"), null, null, null, null, 1));

            assertEquals(1, result.jobs().size());
            assertTrue(service.findById("a-4").isPresent());
            // Never truncated out of the response, so not cached: the provider fallback runs.
            assertFalse(service.findById("a-5").isPresent());
        }
    }

    @Nested
    @DisplayName("Misses never masquerade as a hit")
    class MissTests {

        @Test
        @DisplayName("an unknown id falls back to the sources and returns empty")
        void unknownIdStillEmpty() {
            CountingSource source = new CountingSource(List.of(job("a-1", null)));
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());
            service.search(query("java"));

            Optional<Job> found = service.findById("not-a-real-id");

            assertFalse(found.isPresent());
            assertEquals(2, source.searches.get(), "a miss still consults the sources, once");
        }

        @Test
        @DisplayName("an empty-keyword lookup is not answered from a stale cache entry")
        void emptyKeywordQueryDoesNotFabricate() {
            CountingSource source = new CountingSource(List.of());
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());

            // Anonymous search: nothing to derive, so no keyword is pushed upstream and no
            // listing exists to cache.
            JobSearchResult result = service.search(query("java"));

            assertTrue(result.jobs().isEmpty());
            assertFalse(service.findById("a-1").isPresent());
        }

        @Test
        @DisplayName("blank and null ids return empty without touching the cache")
        void blankIdsEmpty() {
            CountingSource source = new CountingSource(List.of(job("a-1", null)));
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());
            service.search(query("java"));

            assertFalse(service.findById(null).isPresent());
            assertFalse(service.findById("").isPresent());
            assertFalse(service.findById("   ").isPresent());
            assertEquals(1, source.searches.get());
        }
    }

    @Nested
    @DisplayName("Capacity, eviction and thread safety")
    class BoundsTests {

        @Test
        @DisplayName("eviction is deterministic: the eldest inserted id is dropped first")
        void evictionDropsEldestFirst() {
            JobIdCache cache = new JobIdCache(3);

            cache.put(job("1", null));
            cache.put(job("2", null));
            cache.put(job("3", null));
            cache.put(job("4", null));

            assertEquals(3, cache.size());
            assertNull(cache.get("1"), "eldest inserted is evicted");
            assertNotNull(cache.get("2"));
            assertNotNull(cache.get("3"));
            assertNotNull(cache.get("4"));
        }

        @Test
        @DisplayName("re-putting a known id refreshes it instead of growing the cache")
        void rePutDoesNotGrow() {
            JobIdCache cache = new JobIdCache(2);

            cache.put(job("1", null));
            cache.put(job("2", null));
            cache.put(job("1", null));

            assertEquals(2, cache.size());
            assertNotNull(cache.get("1"));
        }

        @Test
        @DisplayName("entries with a blank id are never stored")
        void blankIdNeverStored() {
            JobIdCache cache = new JobIdCache(10);

            cache.put(null);
            cache.put(job(null, null));
            cache.put(job("  ", null));

            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("concurrent writers and readers never corrupt the cache")
        void concurrentAccessIsSafe() throws Exception {
            JobIdCache cache = new JobIdCache(64);
            int writers = 8;
            int perWriter = 200;
            ExecutorService pool = Executors.newFixedThreadPool(writers + 2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            try {
                for (int w = 0; w < writers; w++) {
                    final int writer = w;
                    pool.submit(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < perWriter; i++) {
                                cache.put(job(writer + "-" + i, null));
                                cache.get(writer + "-" + i);
                            }
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        }
                    });
                }
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perWriter; i++) {
                            cache.snapshotValues();
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });

                start.countDown();
                pool.shutdown();
                assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "workers finished");
            } finally {
                pool.shutdownNow();
            }

            assertNull(failure.get(), "no concurrent failure: " + failure.get());
            assertEquals(64, cache.size(), "capacity is respected under concurrency");
        }

        @Test
        @DisplayName("the service never exceeds its cache capacity across searches")
        void serviceCacheStaysBounded() {
            List<Job> many = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                many.add(job("bulk-" + i, null));
            }
            CountingSource source = new CountingSource(many);
            JobSearchService service =
                    new JobSearchService(List.of(source), new JobDeduplicationService());

            for (int i = 0; i < 10; i++) {
                service.search(query("java"));
            }

            // 1000-entry default capacity, 10 searches x 100 returned listings.
            assertTrue(service.findById("bulk-0").isPresent(), "recent ids are still resolvable");
        }
    }
}
