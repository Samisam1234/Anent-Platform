package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobDeduplicationService — cross-source deduplication")
class JobDeduplicationServiceTest {

    private final JobDeduplicationService service = new JobDeduplicationService();

    private static Job mockJob(String id, String title, String company, String location) {
        return new Job(id, title, company, location, "desc",
                List.of("Java"), List.of(), "2-4 years", "FULL_TIME",
                "2026-01-15", "MOCK_SOURCE", null, "MOCK", null);
    }

    private static Job liveJob(String id, String title, String company, String location, String sourceUrl) {
        return new Job(id, title, company, location, "desc",
                List.of("Java"), List.of(), "2-4 years", "FULL_TIME",
                "2026-01-15", "PublicApiJobSource", sourceUrl, "PUBLIC_API", null);
    }

    @Nested
    @DisplayName("Basic deduplication")
    class BasicTests {

        @Test
        @DisplayName("null and empty list → empty list")
        void nullAndEmpty() {
            assertEquals(List.of(), service.deduplicate(null));
            assertEquals(List.of(), service.deduplicate(List.of()));
        }

        @Test
        @DisplayName("single job → returned unchanged")
        void singleJob() {
            List<Job> result = service.deduplicate(List.of(mockJob("1", "Java Dev", "Google", "Remote")));
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("identical jobs from different sources → one kept")
        void identicalDifferentSources() {
            Job j1 = mockJob("mock-001", "Java Developer", "Google", "Remote");
            Job j2 = liveJob("live-001", "Java Developer", "Google", "Remote", "https://example.com/jobs/123");
            List<Job> result = service.deduplicate(List.of(j1, j2));
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("different titles → both kept")
        void differentTitles() {
            Job j1 = mockJob("1", "Java Developer", "Google", "Remote");
            Job j2 = mockJob("2", "Python Developer", "Google", "Remote");
            assertEquals(2, service.deduplicate(List.of(j1, j2)).size());
        }

        @Test
        @DisplayName("different companies → both kept")
        void differentCompanies() {
            Job j1 = mockJob("1", "Java Developer", "Google", "Remote");
            Job j2 = mockJob("2", "Java Developer", "Microsoft", "Remote");
            assertEquals(2, service.deduplicate(List.of(j1, j2)).size());
        }

        @Test
        @DisplayName("different locations → both kept")
        void differentLocations() {
            Job j1 = mockJob("1", "Java Developer", "Google", "Remote");
            Job j2 = mockJob("2", "Java Developer", "Google", "Hyderabad");
            assertEquals(2, service.deduplicate(List.of(j1, j2)).size());
        }
    }

    @Nested
    @DisplayName("Case and whitespace insensitivity")
    class CaseAndWhitespaceTests {

        @Test
        @DisplayName("case-insensitive dedup")
        void caseInsensitive() {
            Job j1 = mockJob("1", "Java Developer", "Google", "Remote");
            Job j2 = mockJob("2", "java developer", "GOOGLE", "REMOTE");
            assertEquals(1, service.deduplicate(List.of(j1, j2)).size());
        }

        @Test
        @DisplayName("whitespace-insensitive dedup")
        void whitespaceInsensitive() {
            Job j1 = mockJob("1", "Java Developer", "Google", "Remote");
            Job j2 = mockJob("2", "  Java  Developer  ", "  Google  ", "  Remote  ");
            assertEquals(1, service.deduplicate(List.of(j1, j2)).size());
        }
    }

    @Nested
    @DisplayName("URL-based deduplication")
    class UrlDedupTests {

        @Test
        @DisplayName("same URL → duplicate detected")
        void sameUrl() {
            Job j1 = liveJob("1", "Java Dev A", "Google", "Remote", "https://example.com/job/1");
            Job j2 = liveJob("2", "Java Dev B", "Microsoft", "NYC", "https://example.com/job/1");
            assertEquals(1, service.deduplicate(List.of(j1, j2)).size());
        }

        @Test
        @DisplayName("null URLs → URL dedup skipped")
        void nullUrls() {
            Job j1 = mockJob("1", "Java Dev", "Google", "Remote");
            Job j2 = mockJob("2", "Java Dev", "Google", "Remote");
            assertEquals(1, service.deduplicate(List.of(j1, j2)).size());
        }

        @Test
        @DisplayName("same title+company but different URLs → still deduped by composite key")
        void sameCompositeDiffUrl() {
            Job j1 = liveJob("1", "Java Developer", "Google", "Remote", "https://example.com/job/1");
            Job j2 = liveJob("2", "Java Developer", "Google", "Remote", "https://other.com/job/2");
            assertEquals(1, service.deduplicate(List.of(j1, j2)).size());
        }
    }

    @Nested
    @DisplayName("Preserves first occurrence")
    class OrderTests {

        @Test
        @DisplayName("first job wins, second is removed")
        void firstWins() {
            Job j1 = mockJob("first", "Java Developer", "Google", "Remote");
            Job j2 = liveJob("second", "Java Developer", "Google", "Remote", "https://example.com/job/1");
            List<Job> result = service.deduplicate(List.of(j1, j2));
            assertEquals(1, result.size());
            assertEquals("first", result.get(0).id());
        }
    }

    @Nested
    @DisplayName("Multi-source scenario")
    class MultiSourceTests {

        @Test
        @DisplayName("3 sources with overlap → unique jobs only")
        void multiSourceOverlap() {
            Job mock1  = mockJob("mock-1",  "Java Developer",  "Google",   "Remote");
            Job mock2  = mockJob("mock-2",  "Python Developer", "Meta",    "Menlo Park");
            Job live1  = liveJob("live-1",  "Java Developer",  "Google",   "Remote", "https://remotive.com/job/1");
            Job live2  = liveJob("live-2",  "Go Developer",    "Uber",     "SF",     "https://remotive.com/job/2");
            Job live3  = liveJob("live-3",  "Python Developer", "Meta",    "Menlo Park", "https://remotive.com/job/3");

            List<Job> result = service.deduplicate(List.of(mock1, mock2, live1, live2, live3));
            assertEquals(3, result.size());
        }
    }

    @Nested
    @DisplayName("Null job handling")
    class NullJobTests {

        @Test
        @DisplayName("null entries in list are skipped")
        void nullEntriesSkipped() {
            ArrayList<Job> input = new ArrayList<>();
            input.add(null);
            input.add(mockJob("1", "Java Dev", "Google", "Remote"));
            input.add(null);
            input.add(mockJob("2", "Python Dev", "Meta", "Remote"));
            input.add(null);
            assertEquals(2, service.deduplicate(input).size());
        }
    }

    @Nested
    @DisplayName("Duplicate merging — useful fields survive")
    class MergeTests {

        /** Same job as {@link #mockJob} but with the two URL fields under test. */
        private static Job jobWith(String id, String title, String company, String location,
                                   String sourceUrl, String applicationUrl) {
            return new Job(id, title, company, location, "desc",
                    List.of("Java"), List.of(), "2-4 years", "FULL_TIME",
                    "2026-01-15", "PublicApiJobSource", sourceUrl, "PUBLIC_API", null,
                    applicationUrl);
        }

        @Test
        @DisplayName("first record has no applicationUrl, duplicate does → the duplicate's URL is retained")
        void laterApplicationUrlIsRetained() {
            Job withoutUrl = jobWith("a-1", "Java Developer", "Acme", "Remote",
                    "https://board.example.com/a-1", null);
            Job withUrl = jobWith("b-2", "Java Developer", "Acme", "Remote",
                    "https://other-board.example.com/b-2", "https://acme.example.com/careers/apply/77");

            List<Job> result = service.deduplicate(List.of(withoutUrl, withUrl));

            assertEquals(1, result.size(), "the two listings describe the same job");
            assertEquals("https://acme.example.com/careers/apply/77", result.get(0).applicationUrl(),
                    "a duplicate carrying an applicationUrl must not be discarded with it");
        }

        @Test
        @DisplayName("first record has an applicationUrl, duplicate has none → the original URL remains")
        void earlierApplicationUrlIsNotErased() {
            Job withUrl = jobWith("a-1", "Java Developer", "Acme", "Remote",
                    "https://board.example.com/a-1", "https://acme.example.com/careers/apply/77");
            Job withoutUrl = jobWith("b-2", "Java Developer", "Acme", "Remote",
                    "https://other-board.example.com/b-2", null);

            List<Job> result = service.deduplicate(List.of(withUrl, withoutUrl));

            assertEquals(1, result.size());
            assertEquals("https://acme.example.com/careers/apply/77", result.get(0).applicationUrl(),
                    "a null in a later duplicate must never overwrite a real URL");
        }

        @Test
        @DisplayName("a blank applicationUrl in the duplicate does not replace a real one")
        void blankApplicationUrlDoesNotReplace() {
            Job withUrl = jobWith("a-1", "Java Developer", "Acme", "Remote",
                    "https://board.example.com/a-1", "https://acme.example.com/careers/apply/77");
            Job blankUrl = jobWith("b-2", "Java Developer", "Acme", "Remote",
                    "https://other-board.example.com/b-2", "   ");

            List<Job> result = service.deduplicate(List.of(withUrl, blankUrl));

            assertEquals("https://acme.example.com/careers/apply/77", result.get(0).applicationUrl());
        }

        @Test
        @DisplayName("missing sourceUrl is filled in from the duplicate, a present one is kept")
        void sourceUrlIsFilledNotReplaced() {
            Job missing = jobWith("a-1", "Java Developer", "Acme", "Remote", null, null);
            Job present = jobWith("b-2", "Java Developer", "Acme", "Remote",
                    "https://other-board.example.com/b-2", null);
            assertEquals("https://other-board.example.com/b-2",
                    service.deduplicate(List.of(missing, present)).get(0).sourceUrl(),
                    "a listing with no sourceUrl should gain the duplicate's");

            Job first = jobWith("a-1", "Java Developer", "Acme", "Remote",
                    "https://first-board.example.com/a-1", null);
            assertEquals("https://first-board.example.com/a-1",
                    service.deduplicate(List.of(first, present)).get(0).sourceUrl(),
                    "an existing sourceUrl must not be swapped for another board's link");
        }

        @Test
        @DisplayName("descriptive and structured gaps are filled from the more complete duplicate")
        void incompleteFieldsAreCompleted() {
            // Title, company and location must match on both records: they are what the
            // dedup key is built from, so only the other fields can differ here.
            Job sparse = new Job("a-1", "Java Developer", "Acme Corp", "Remote", null,
                    List.of(), List.of(), null, null,
                    null, "SOURCE_A", null, "PUBLIC_API", null, null);
            Job full = new Job("b-2", "Java Developer", "Acme Corp", "Remote",
                    "Build and maintain Java services.",
                    List.of("Java", "Spring Boot"), List.of("Docker"), "2-4 years", "FULL_TIME",
                    "2026-01-15", "SOURCE_B", "https://board.example.com/b-2", "PUBLIC_API",
                    null, null);

            List<Job> result = service.deduplicate(List.of(sparse, full));

            assertEquals(1, result.size(), "same title, company and location is one listing");
            Job merged = result.get(0);
            assertEquals("Build and maintain Java services.", merged.description());
            assertEquals(List.of("Java", "Spring Boot"), merged.requiredSkills());
            assertEquals(List.of("Docker"), merged.preferredSkills());
            assertEquals("2-4 years", merged.experienceRequirement());
            assertEquals("FULL_TIME", merged.employmentType());
            assertEquals("2026-01-15", merged.postingDate());
            assertEquals("https://board.example.com/b-2", merged.sourceUrl());
            assertEquals("a-1", merged.id(), "identity comes from the first record");
            assertEquals("SOURCE_A", merged.source(), "attribution comes from the first record");
        }

        @Test
        @DisplayName("a present field is never replaced by the duplicate's different value")
        void presentFieldsAreNotReplaced() {
            Job complete = new Job("a-1", "Java Developer", "Acme Corp", "Remote",
                    "Original description.",
                    List.of("Java"), List.of("Kafka"), "2-4 years", "FULL_TIME",
                    "2026-01-15", "SOURCE_A", "https://first.example.com/a-1", "PUBLIC_API",
                    null, "https://acme.example.com/apply/1");
            Job differing = new Job("b-2", "Java Developer", "Acme Corp", "Remote",
                    "A different description.",
                    List.of("Python"), List.of("Airflow"), "5+ years", "CONTRACT",
                    "2026-02-20", "SOURCE_B", "https://second.example.com/b-2", "PUBLIC_API",
                    null, "https://acme.example.com/apply/2");

            Job merged = service.deduplicate(List.of(complete, differing)).get(0);

            assertEquals("Original description.", merged.description());
            assertEquals(List.of("Java"), merged.requiredSkills());
            assertEquals(List.of("Kafka"), merged.preferredSkills());
            assertEquals("2-4 years", merged.experienceRequirement());
            assertEquals("FULL_TIME", merged.employmentType());
            assertEquals("2026-01-15", merged.postingDate());
            assertEquals("https://first.example.com/a-1", merged.sourceUrl());
            assertEquals("https://acme.example.com/apply/1", merged.applicationUrl());
        }

        @Test
        @DisplayName("identity and source attribution always come from the first record")
        void identityAndAttributionArePreserved() {
            Job first = jobWith("a-1", "Java Developer", "Acme", "Remote",
                    "https://first-board.example.com/a-1", null);
            Job later = jobWith("b-2", "Java Developer", "Acme", "Remote",
                    "https://other-board.example.com/b-2", "https://acme.example.com/apply/77");

            Job merged = service.deduplicate(List.of(first, later)).get(0);

            assertEquals("a-1", merged.id(), "the surviving listing keeps the first record's id");
            assertEquals("PublicApiJobSource", merged.source());
            assertEquals("PUBLIC_API", merged.sourceType());
        }

        @Test
        @DisplayName("merging is deterministic — the same input yields the same result")
        void mergeIsDeterministic() {
            Job a = jobWith("a-1", "Java Developer", "Acme", "Remote",
                    "https://board.example.com/a-1", null);
            Job b = jobWith("b-2", "Java Developer", "Acme", "Remote",
                    "https://other-board.example.com/b-2", "https://acme.example.com/apply/77");

            assertEquals(service.deduplicate(List.of(a, b)), service.deduplicate(List.of(a, b)));
        }

        @Test
        @DisplayName("a URL absorbed by a merge still dedupes a later listing carrying it")
        void absorbedUrlStillDedupes() {
            Job first = jobWith("a-1", "Java Developer", "Acme", "Remote",
                    "https://first.example.com/a", null);
            Job second = jobWith("b-2", "Java Dev", "OtherCo", "NYC",
                    "https://second.example.com/b", null);
            Job third = jobWith("c-3", "Third Title", "ThirdCo", "SF",
                    "https://second.example.com/b", null);

            List<Job> result = service.deduplicate(List.of(first, second, third));

            assertEquals(2, result.size(),
                    "the third listing carries a URL already absorbed into the second");
        }

        @Test
        @DisplayName("first-occurrence order is still preserved after merging")
        void orderIsPreserved() {
            Job first = jobWith("a-1", "Java Developer", "Acme", "Remote", null, null);
            Job other = jobWith("c-3", "Python Developer", "Meta", "Remote", null, null);
            Job dup = jobWith("b-2", "Java Developer", "Acme", "Remote", null,
                    "https://acme.example.com/apply/77");

            List<Job> result = service.deduplicate(List.of(first, other, dup));

            assertEquals(2, result.size());
            assertEquals("a-1", result.get(0).id());
            assertEquals("c-3", result.get(1).id());
            assertEquals("https://acme.example.com/apply/77", result.get(0).applicationUrl());
        }
    }
}
