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
}
