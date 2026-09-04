package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link JobSearchService} keeps working when a public {@link JobSource}
 * fails, and that the existing {@link JobDeduplicationService} still runs across the
 * combined source outputs. HTTP is mocked — no live internet.
 */
@DisplayName("JobSearchService with a public source mixed alongside the mock source")
class JobSearchServiceWithPublicSourceTest {

    private PublicApiJobSource failingPublicSource() {
        PublicApiJobProperties props = new PublicApiJobProperties();
        props.setEnabled(true);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), eq(org.springframework.http.HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("connection refused"));
        return new PublicApiJobSource(props, restTemplate, new ObjectMapper());
    }

    private JobSearchService service(JobSource... sources) {
        return new JobSearchService(List.of(sources), new JobDeduplicationService());
    }

    @Nested
    @DisplayName("Source failure isolation")
    class SourceFailureTests {

        @Test
        @DisplayName("public source failing does NOT break the whole search (mock still serves)")
        void failingPublicSourceDoesNotBreakSearch() {
            JobSearchService service = service(failingPublicSource(), new MockJobSource());

            JobSearchResult result = service.search(JobSearchRequest.of(List.of("java"), "Hyderabad", null, null, null, 20));

            // Mock data still flows through — no JobSearchException, and the safe
            // (empty) public degradation is transparent to the caller.
            assertFalse(result.jobs().isEmpty());
            assertTrue(result.jobs().stream().allMatch(j -> j.source().equals(MockJobSource.SOURCE_NAME)));
        }
    }

    @Nested
    @DisplayName("Deduplication across sources")
    class DeduplicationTests {

        @Test
        @DisplayName("JobDeduplicationService removes a duplicate returned by two sources")
        void deduplicatesAcrossSources() {
            Job mockFirst = new MockJobSource().search(
                    JobSearchRequest.of(List.of(), null, null, null, null, 100)).get(0);

            JobSource mirror = new JobSource() {
                @Override public String getSourceName() { return "MIRROR"; }
                @Override public boolean isLive() { return true; }
                @Override public boolean isAvailable() { return true; }
                @Override public List<Job> search(JobSearchRequest req) {
                    return List.of(new Job(
                            mockFirst.id(), mockFirst.title(), mockFirst.company(), mockFirst.location(),
                            mockFirst.description(), mockFirst.requiredSkills(), mockFirst.preferredSkills(),
                            mockFirst.experienceRequirement(), mockFirst.employmentType(), mockFirst.postingDate(),
                            "MIRROR", "https://mirror-corp.test/careers" + mockFirst.id(), "PUBLIC_API",
                            java.time.Instant.now()));
                }
            };

            JobSearchService service = service(mirror, new MockJobSource());
            JobSearchResult result = service.search(JobSearchRequest.of(List.of(), null, null, null, null, 100));

            // The mirror job is a composite-key duplicate of the mock listing → only one survives.
            assertEquals(1,
                    result.jobs().stream().filter(j -> j.title().equals(mockFirst.title())).count());
            assertTrue(result.live());
        }
    }
}