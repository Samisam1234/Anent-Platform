package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hermetic unit tests for {@link PublicApiJobSource}.
 *
 * <p>The HTTP layer is mocked via a {@link RestTemplate} stub — no live internet,
 * no database, no external provider. Covers valid mapping, provenance, URL safety,
 * normalization, and every failure mode (malformed/empty/4xx/5xx/timeout) plus the
 * disabled-by-default no-network guarantee.</p>
 */
@DisplayName("PublicApiJobSource — public job API adapter")
class PublicApiJobSourceTest {

    private PublicApiJobProperties properties;
    private RestTemplate restTemplate;
    private PublicApiJobSource source;

    private static final String VALID_JSON = """
            {"jobs":[
              {"id":2091101,"url":"https://remotive.com/remote-jobs/software-development/senior-react-full-stack-developer-2091101",
               "title":"Senior React Full-stack Developer","company_name":"Lemon.io",
               "candidate_required_location":"Remote","description":"<p><strong>Java</strong> &amp; <strong>Spring</strong> developer needed.</p>",
               "tags":["Java","Spring Boot","React"],"category":"Software Development",
               "job_type":"full_time","publication_date":"2026-08-27T14:36:09"},
              {"id":2091102,"url":"https://remotive.com/remote-jobs/software-development/java-backend-engineer-2091102",
               "title":"Java Backend Engineer","company_name":"StartupX",
               "candidate_required_location":"Europe","description":"Backend role.<div>PostgreSQL</div>",
               "tags":["Java","PostgreSQL","Docker"],"category":"Software Development",
               "job_type":"full_time","publication_date":"2026-08-28T09:00:00"}
            ]}
            """;

    private static final String VALID_JOB_1 = """
            {"id":2091101,"url":"https://remotive.com/remote-jobs/job-1","title":"Job One","company_name":"Acme","candidate_required_location":"Remote","description":"D1","tags":["Java"],"category":"Software","job_type":"full_time","publication_date":"2026-08-27T14:36:09"}""";

    private static final String MULTI_JSON = String.format("""
            {"jobs":[
              %s,
              {"id":2091102,"url":"https://remotive.com/remote-jobs/job-2","title":"Job Two","company_name":"Corp","candidate_required_location":"Remote","description":"D2","job_type":"full_time","publication_date":"2026-08-28T09:00:00"}
            ]}
            """, VALID_JOB_1);

    @BeforeEach
    void setUp() {
        properties = new PublicApiJobProperties();
        properties.setEnabled(true);
        restTemplate = mock(RestTemplate.class);
        source = new PublicApiJobSource(properties, restTemplate, new ObjectMapper());
    }

    private void stubBody(String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body == null ? "" : body));
    }

    private void stubThrow(RuntimeException ex) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenThrow(ex);
    }

    private List<Job> search() {
        return source.search(JobSearchRequest.of(List.of("java"), "Remote", null, null, null, 10));
    }

    @Nested
    @DisplayName("Mapping")
    class MappingTests {

        @Test
        @DisplayName("valid API response → correct Job objects")
        void validResponse() {
            stubBody(VALID_JSON);
            List<Job> jobs = search();

            assertEquals(2, jobs.size());
            Job first = jobs.get(0);
            assertEquals("remotive-2091101", first.id());
            assertEquals("Senior React Full-stack Developer", first.title());
            assertEquals("Lemon.io", first.company());
            assertEquals("Remote", first.location());
            assertEquals("full_time", first.employmentType());
        }

        @Test
        @DisplayName("multiple jobs mapped correctly")
        void multipleJobs() {
            stubBody(MULTI_JSON);
            List<Job> jobs = search();
            assertEquals(2, jobs.size());
            assertEquals(List.of("remotive-2091101", "remotive-2091102"),
                    jobs.stream().map(Job::id).toList());
        }

        @Test
        @DisplayName("HTML description is normalized to plaintext via JobNormalizer")
        void normalizationOccurs() {
            stubBody(VALID_JSON);
            List<Job> jobs = search();
            String desc = jobs.get(0).description();
            assertFalse(desc.contains("<"));
            assertTrue(desc.contains("Java"));
            assertTrue(desc.contains("Spring"));
        }

        @Test
        @DisplayName("publication date collapsed to yyyy-MM-dd for date filtering")
        void publicationDateCollapsed() {
            stubBody(VALID_JSON);
            Job first = search().get(0);
            assertEquals("2026-08-27", first.postingDate());
        }
    }

    @Nested
    @DisplayName("Provenance")
    class ProvenanceTests {

        @Test
        @DisplayName("provenance fields are populated on every mapped job")
        void provenancePopulated() {
            stubBody(VALID_JSON);
            List<Job> jobs = search();

            assertEquals(PublicApiJobSource.SOURCE_NAME, source.getSourceName());
            assertTrue(source.isLive());
            assertTrue(source.isAvailable());
            assertFalse(jobs.isEmpty());
            for (Job job : jobs) {
                assertEquals(PublicApiJobSource.SOURCE_NAME, job.source());
                assertEquals(PublicApiJobSource.SOURCE_TYPE, job.sourceType());
                assertNotNull(job.sourceUrl());
                assertNotNull(job.discoveredAt());
            }
        }

        @Test
        @DisplayName("sourceUrl points back at the Remotive listing")
        void sourceUrlIsRemotiveUrl() {
            stubBody(VALID_JSON);
            Job first = search().get(0);
            assertEquals("https://remotive.com/remote-jobs/software-development/senior-react-full-stack-developer-2091101",
                    first.sourceUrl());
        }
    }

    @Nested
    @DisplayName("URL safety")
    class UrlSafetyTests {

        @Test
        @DisplayName("unsafe or missing sourceUrl → job preserved but not navigable (null sourceUrl)")
        void unsafeUrlNulled() {
            String body = """
                    {"jobs":[
                      {"id":1,"url":"https://mockjobs.local/jobs/fake","title":"T1","company_name":"X","candidate_required_location":"Remote","description":"D","job_type":"full_time","publication_date":"2026-08-27T00:00:00"},
                      {"id":2,"url":"not-a-real-url","title":"T2","company_name":"Y","candidate_required_location":"Remote","description":"D","job_type":"full_time","publication_date":"2026-08-27T00:00:00"},
                      {"id":3,"url":"http://127.0.0.1/jobs","title":"T3","company_name":"Z","candidate_required_location":"Remote","description":"D","job_type":"full_time","publication_date":"2026-08-27T00:00:00"},
                      {"id":4,"url":"https://company.com/careers","title":"T4","company_name":"OK","candidate_required_location":"Remote","description":"D","job_type":"full_time","publication_date":"2026-08-27T00:00:00"}
                    ]}
                    """;
            stubBody(body);
            List<Job> jobs = search();

            // All jobs are preserved; only the URL-valid one is navigable (non-null sourceUrl).
            assertEquals(4, jobs.size());
            assertEquals(List.of("remotive-1", "remotive-2", "remotive-3", "remotive-4"),
                    jobs.stream().map(Job::id).toList());
            assertNull(jobs.get(0).sourceUrl());
            assertNull(jobs.get(1).sourceUrl());
            assertNull(jobs.get(2).sourceUrl());
            assertEquals("https://company.com/careers", jobs.get(3).sourceUrl());
        }

        @Test
        @DisplayName("missing URL field → job preserved with null sourceUrl, no exception")
        void missingUrl() {
            String body = """
                    {"jobs":[{"id":5,"title":"NoUrl","company_name":"X","candidate_required_location":"Remote","description":"D","job_type":"full_time","publication_date":"2026-08-27T00:00:00"}]}
                    """;
            stubBody(body);
            List<Job> jobs = search();
            assertEquals(1, jobs.size());
            assertEquals("remotive-5", jobs.get(0).id());
            assertNull(jobs.get(0).sourceUrl());
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class FailureTests {

        @Test
        @DisplayName("malformed API response → empty result, no exception")
        void malformedResponse() {
            stubBody("{ this is not valid json !!! ");
            assertTrue(search().isEmpty());
        }

        @Test
        @DisplayName("empty API response → empty result")
        void emptyResponse() {
            stubBody("");
            assertTrue(search().isEmpty());
        }

        @Test
        @DisplayName("no jobs key → empty result")
        void noJobsKey() {
            stubBody("{\"unexpected\": true}");
            assertTrue(search().isEmpty());
        }

        @Test
        @DisplayName("HTTP 4xx → empty result, no exception")
        void http4xx() {
            stubThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
            assertTrue(search().isEmpty());
        }

        @Test
        @DisplayName("HTTP 5xx → empty result, no exception")
        void http5xx() {
            stubThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
            assertTrue(search().isEmpty());
        }

        @Test
        @DisplayName("timeout/network failure → empty result, no exception")
        void timeoutOrNetworkFailure() {
            stubThrow(new ResourceAccessException("Connection timed out"));
            assertTrue(search().isEmpty());
        }

        @Test
        @DisplayName("null body → empty result")
        void nullBody() {
            stubBody(null);
            assertTrue(search().isEmpty());
        }
    }

    @Nested
    @DisplayName("Disabled by default")
    class DisabledTests {

        @Test
        @DisplayName("API disabled → source does not call the network")
        void disabledDoesNotCallNetwork() {
            properties.setEnabled(false);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenThrow(new AssertionError("network must not be called when disabled"));

            List<Job> jobs = source.search(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

            assertTrue(jobs.isEmpty());
            assertFalse(source.isAvailable());
            assertFalse(source.isLive());
            verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class));
        }
    }
}