package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 3 Step 3.2 — end-to-end job-search quality, filtering and provenance.
 *
 * <p>Drives a real {@link JobSearchService} with stub {@link JobSource}s (and a mocked
 * {@link RestTemplate} for the public adapter) so behavior is deterministic and hermetic:
 * no live network, no database, no AI.</p>
 */
@DisplayName("JobSearchService — quality, filtering, provenance (Step 3.2)")
class JobSearchServiceQualityTest {

    // ─── Fixture helpers ────────────────────────────────────────────────────

    private static final Instant NOW = Instant.now();

    private Job job(String id, String title, String company, String location, String source,
                    String sourceUrl, String sourceType) {
        return new Job(id, title, company, location, "desc " + title,
                List.of("Java", "Spring Boot"), List.of(), "1-3 years", "FULL_TIME",
                "2026-08-25", source, sourceUrl, sourceType, NOW);
    }

    /** Controllable, deterministic JobSource for hermetic tests. */
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

    private JobSearchService service(JobSource... sources) {
        return new JobSearchService(List.of(sources), new JobDeduplicationService());
    }

    // ─── 1. Provenance correctness ──────────────────────────────────────────

    @Nested
    @DisplayName("Provenance")
    class ProvenanceTests {

        @Test
        @DisplayName("mock listings carry source=MOCK_SOURCE, sourceType=MOCK, null sourceUrl")
        void mockProvenance() {
            JobSearchResult result = service(new MockJobSource())
                    .search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            assertFalse(result.jobs().isEmpty());
            for (Job j : result.jobs()) {
                assertEquals(MockJobSource.SOURCE_NAME, j.source());
                assertEquals("MOCK", j.sourceType());
                assertNull(j.sourceUrl());
            }
        }

        @Test
        @DisplayName("live listings carry a verified external sourceUrl and provenance")
        void liveProvenance() {
            JobSearchResult result = service(new StubSource("REMOTIVE", true, List.of(
                    job("remotive-1", "Java Engineer", "Acme", "Remote", "REMOTIVE",
                            "https://remotive.com/remote-jobs/software-development/java-engineer-1", "PUBLIC_API"),
                    job("remotive-2", "DevOps Engineer", "Corp", "Remote", "REMOTIVE",
                            "https://company.com/careers/devops", "PUBLIC_API"))))
                    .search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            assertEquals(2, result.jobs().size());
            for (Job j : result.jobs()) {
                assertEquals("REMOTIVE", j.source());
                assertEquals("PUBLIC_API", j.sourceType());
                assertNotNull(j.discoveredAt());
                assertTrue(JobUrlValidator.isValidExternalUrl(j.sourceUrl()));
            }
        }
    }

    // ─── 2/3/4/5. Filtering ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Filtering")
    class FilteringTests {

        private JobSearchService multiSource() {
            return service(
                    new StubSource("REMOTIVE", true, List.of(
                            job("remotive-1", "Senior Java Engineer", "Acme", "Remote", "REMOTIVE",
                                    "https://remotive.com/remote-jobs/software-development/java-1", "PUBLIC_API"),
                            job("remotive-2", "React Developer", "Corp", "Berlin, Germany", "REMOTIVE",
                                    "https://remotive.com/remote-jobs/software-development/react-2", "PUBLIC_API"))),
                    new MockJobSource());
        }

        @Test
        @DisplayName("source-only filter returns only that source")
        void sourceFilter() {
            JobSearchResult result = multiSource().search(
                    JobSearchRequest.of(List.of(), null, null, null, null, 100, "REMOTIVE"));
            assertFalse(result.jobs().isEmpty());
            assertTrue(result.jobs().stream().allMatch(j -> j.source().equals("REMOTIVE")));

            JobSearchResult mockOnly = multiSource().search(
                    JobSearchRequest.of(List.of(), null, null, null, null, 100, "MOCK_SOURCE"));
            assertTrue(mockOnly.jobs().stream().allMatch(j -> j.source().equals(MockJobSource.SOURCE_NAME)));
        }

        @Test
        @DisplayName("null / 'all' source filter imposes no restriction")
        void noSourceRestriction() {
            JobSearchResult result = multiSource().search(
                    JobSearchRequest.of(List.of(), null, null, null, null, 100));
            assertFalse(result.jobs().isEmpty());
            assertTrue(result.jobs().stream().anyMatch(j -> j.source().equals("REMOTIVE")));
        }

        @Test
        @DisplayName("keyword-only filter matches title/description")
        void keywordFilter() {
            JobSearchResult result = multiSource().search(
                    JobSearchRequest.of(List.of("react"), null, null, null, null, 100));
            // remotive-2 is "React Developer"; some mock catalog listings (e.g. Java Full
            // Stack with React listed) legitimately mention react, so it must be present.
            assertTrue(result.jobs().stream().anyMatch(j -> j.id().equals("remotive-2")));
        }

        @Test
        @DisplayName("location-only filter matches location substring")
        void locationFilter() {
            JobSearchResult result = multiSource().search(
                    JobSearchRequest.of(List.of(), "Hyderabad", null, null, null, 100));
            assertFalse(result.jobs().isEmpty());
            assertTrue(result.jobs().stream()
                    .allMatch(j -> j.location() != null && j.location().toLowerCase().contains("hyderabad")));
        }

        @Test
        @DisplayName("combined filters narrow results deterministically")
        void combinedFilters() {
            JobSearchResult result = multiSource().search(
                    JobSearchRequest.of(List.of("java"), "Remote", null, "FULL_TIME", null, 100, "REMOTIVE"));
            assertEquals(List.of("remotive-1"), result.jobs().stream().map(Job::id).toList());
        }
    }

    // ─── 6. Invalid / null sourceUrl handling ───────────────────────────────

    @Nested
    @DisplayName("URL handling surfacing")
    class UrlSurfaceTests {

        @Test
        @DisplayName("unsafe sourceUrl is nulled by the real pipeline; verified URL preserved")
        void nullUrlPreservedNotNavigable() {
            PublicApiJobProperties props = new PublicApiJobProperties();
            props.setEnabled(true);
            RestTemplate restTemplate = mock(RestTemplate.class);
            String body = """
                    {"jobs":[
                      {"id":1,"url":"https://mockjobs.local/jobs/x","title":"Unsafe Link","company_name":"Acme","candidate_required_location":"Remote","description":"D","job_type":"full_time","publication_date":"2026-08-27T00:00:00"},
                      {"id":2,"url":"https://realcorp.com/careers/2","title":"Linked Job","company_name":"Corp","candidate_required_location":"Remote","description":"D","job_type":"full_time","publication_date":"2026-08-28T00:00:00"}
                    ]}
                    """;
            when(restTemplate.exchange(
                            anyString(),
                            eq(HttpMethod.GET),
                            any(),
                            eq(String.class)))
                    .thenReturn(ResponseEntity.ok(body));

            PublicApiJobSource publicSource =
                    new PublicApiJobSource(props, restTemplate, new ObjectMapper());
            JobSearchService service = service(publicSource);

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            assertEquals(2, result.jobs().size());
            Job unsafe = result.jobs().stream().filter(j -> j.title().equals("Unsafe Link")).findFirst().orElseThrow();
            Job safe = result.jobs().stream().filter(j -> j.title().equals("Linked Job")).findFirst().orElseThrow();
            assertNull(unsafe.sourceUrl());
            assertEquals("https://realcorp.com/careers/2", safe.sourceUrl());
        }
    }

    // ─── 7/8. Deduplication ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-source deduplication")
    class DeduplicationTests {

        @Test
        @DisplayName("same normalized title/company/location from two sources → one job")
        void crossSourceDedup() {
            Job mock = new MockJobSource().search(JobSearchRequest.of(List.of(), null, null, null, null, 100)).get(0);
            JobSearchService service = service(
                    new StubSource("MIRROR", true, List.of(new Job(
                            mock.id(), mock.title(), mock.company(), mock.location(), "same",
                            mock.requiredSkills(), mock.preferredSkills(), mock.experienceRequirement(),
                            mock.employmentType(), mock.postingDate(), "MIRROR",
                            "https://mirror-corp.com/careers/1", "PUBLIC_API", Instant.now()))),
                    new MockJobSource());

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            assertEquals(1, result.jobs().stream().filter(j -> j.title().equals(mock.title())).count());
        }

        @Test
        @DisplayName("same external URL with different title/company → deduplicated by URL")
        void urlDedup() {
            JobSearchService service = service(new StubSource("LIVE", true, List.of(
                    job("a-1", "Java Engineer", "Acme", "Remote", "LIVE",
                            "https://boards.greenhouse.io/jobs/99", "PUBLIC_API"),
                    job("a-2", "Senior Java Engineer", "Acme Group", "Remote", "LIVE",
                            "https://boards.greenhouse.io/jobs/99", "PUBLIC_API"),
                    job("a-3", "Different Job", "Other", "Berlin", "LIVE",
                            "https://boards.greenhouse.io/jobs/100", "PUBLIC_API"))));

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            // First two share a URL → one survives; third is unique.
            assertEquals(2, result.jobs().size());
        }
    }

    // ─── 9. Source failure isolation ────────────────────────────────────────

    @Nested
    @DisplayName("Source failure isolation")
    class FailureIsolationTests {

        @Test
        @DisplayName("one source throws → the other still serves results")
        void oneFailsOtherServes() {
            JobSearchService service = service(
                    new StubSource("broken", true, List.of()).failing(new RuntimeException("boom")),
                    new MockJobSource());

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 20));

            assertFalse(result.jobs().isEmpty());
            assertTrue(result.jobs().stream().allMatch(j -> j.source().equals(MockJobSource.SOURCE_NAME)));
        }

        @Test
        @DisplayName("all sources fail → empty safe response, no crash")
        void allFailSafe() {
            JobSearchService service = service(
                    new StubSource("a", true, List.of()).failing(new RuntimeException("x")),
                    new StubSource("b", true, List.of()).failing(new RuntimeException("y")));

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 20));

            assertTrue(result.jobs().isEmpty());
            assertEquals(0, result.total());
            assertEquals("NONE", result.source());
            assertFalse(result.live());
            assertNotNull(result.message());
        }
    }

    // ─── 10. Malformed source job handling ─────────────────────────────────

    @Nested
    @DisplayName("Malformed source jobs")
    class MalformedJobTests {

        @Test
        @DisplayName("blank-title / null-id jobs are dropped; valid siblings survive")
        void malformedJobsDropped() {
            JobSearchService service = service(new StubSource("LIVE", true, List.of(
                    new Job(null, "No ID", "Acme", "Remote", "d", List.of(), List.of(), null, null, null,
                            "LIVE", null, "PUBLIC_API", NOW),
                    new Job("r-2", "   ", "Corp", "Remote", "blank title", List.of(), List.of(), null, null, null,
                            "LIVE", null, "PUBLIC_API", NOW),
                    job("r-3", "Good Job", "Good", "Remote", "LIVE",
                            "https://goodcorp.com/careers/3", "PUBLIC_API"))));

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            assertEquals(List.of("r-3"), result.jobs().stream().map(Job::id).toList());
        }
    }

    // ─── 11. Request handling ───────────────────────────────────────────────

    @Nested
    @DisplayName("Request handling")
    class RequestTests {

        @Test
        @DisplayName("null request → IllegalArgumentException")
        void nullRequestThrows() {
            JobSearchService service = service(new MockJobSource());
            assertThrows(IllegalArgumentException.class, () -> service.search(null));
        }

        @Test
        @DisplayName("blank query / no filters → returns everything (source not filtered)")
        void blankQueryNoFilters() {
            JobSearchService service = service(
                    new StubSource("REMOTIVE", true, List.of(
                            job("r-1", "Java", "Acme", "Remote", "REMOTIVE", "https://acme.com/c", "PUBLIC_API"))),
                    new MockJobSource());

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), "all", "all", "all", "all", null));
            assertFalse(result.jobs().isEmpty());
        }

        @Test
        @DisplayName("limit caps the returned jobs (pagination behavior)")
        void limitCapsResults() {
            JobSearchService service = service(new StubSource("LIVE", true, List.of(
                    job("1", "A", "Co", "Remote", "LIVE", "https://a.com/1", "PUBLIC_API"),
                    job("2", "B", "Co", "Remote", "LIVE", "https://a.com/2", "PUBLIC_API"),
                    job("3", "C", "Co", "Remote", "LIVE", "https://a.com/3", "PUBLIC_API"))));

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 2));
            assertEquals(2, result.jobs().size());
        }
    }

    // ─── 12. Frontend-compatible response behavior ─────────────────────────

    @Nested
    @DisplayName("Frontend contract")
    class FrontendContractTests {

        @Test
        @DisplayName("response shape matches what matches.js/jobDetails.js read")
        void responseShape() {
            JobSearchService service = service(new StubSource("REMOTIVE", true, List.of(
                    job("r-1", "Java", "Acme", "Remote", "REMOTIVE", "https://acme.com/c", "PUBLIC_API"))),
                    new MockJobSource());

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            assertNotNull(result.jobs());
            assertNotNull(result.source());
            assertTrue(result.live()); // a live source is configured

            Job nonMock = result.jobs().stream()
                    .filter(j -> j.source().equals("REMOTIVE")).findFirst().orElseThrow();
            // The frontend only renders a navigable link when !mock && hasValidUrl(job);
            // jobDetails.isMock checks source === 'MOCK_SOURCE'; mock stays non-navigable.
            assertTrue(JobUrlValidator.isValidExternalUrl(nonMock.sourceUrl()));
            Job mock = result.jobs().stream()
                    .filter(j -> j.source().equals(MockJobSource.SOURCE_NAME)).findFirst().orElseThrow();
            assertNull(mock.sourceUrl());
        }

        @Test
        @DisplayName("a live job lacking a URL is surfaceable but non-navigable (no fake link)")
        void liveJobWithoutUrlNonNavigable() {
            JobSearchService service = service(new StubSource("REMOTIVE", true, List.of(
                    new Job("r-9", "No URL", "Acme", "Remote", "d", List.of(), List.of(), null, null, null,
                            "REMOTIVE", null, "PUBLIC_API", NOW))));

            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            assertEquals(1, result.jobs().size());
            assertNull(result.jobs().get(0).sourceUrl());
        }
    }
}