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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hermetic tests for {@link AdzunaJobSourceProvider}.
 *
 * <p>The fixture mirrors Adzuna's documented search response ({@code count} +
 * {@code results[]}, with {@code company.display_name}, {@code location.area} and
 * {@code redirect_url}). The HTTP layer is mocked, and credentials are supplied via
 * configuration — never hard-coded in production code.</p>
 */
@DisplayName("AdzunaJobSourceProvider — Adzuna job search adapter")
class AdzunaJobSourceProviderTest {

    private static final String VALID_JSON = """
            {
              "count": 2,
              "results": [
                {
                  "id": "129698749",
                  "title": "Senior Java Engineer",
                  "description": "We are looking for strong Java and Spring Boot skills, with PostgreSQL and Docker.",
                  "redirect_url": "https://www.adzuna.co.uk/jobs/land/ad/129698749?v=abc123",
                  "created": "2026-08-27T14:36:09Z",
                  "contract_time": "full_time",
                  "contract_type": "permanent",
                  "salary_min": 60000,
                  "salary_max": 75000,
                  "company": { "display_name": "Acme Systems Ltd" },
                  "location": {
                    "area": ["UK", "South East England", "Buckinghamshire", "Marlow"],
                    "display_name": "Marlow, Buckinghamshire"
                  },
                  "category": { "label": "IT Jobs", "tag": "it-jobs" }
                },
                {
                  "id": "129698750",
                  "title": "Backend Developer",
                  "description": "A short advert with no recognizable technology mentioned.",
                  "redirect_url": "https://www.adzuna.co.uk/jobs/land/ad/129698750?v=def456",
                  "created": "2026-08-28T09:00:00Z",
                  "company": { "display_name": "Beta Ltd" },
                  "location": { "area": ["UK", "London"], "display_name": "" },
                  "category": { "label": "IT Jobs", "tag": "it-jobs" }
                }
              ]
            }
            """;

    private AdzunaJobProperties properties;
    private RestTemplate restTemplate;
    private AdzunaJobSourceProvider provider;

    @BeforeEach
    void setUp() {
        properties = new AdzunaJobProperties();
        properties.setEnabled(true);
        properties.setAppId("test-app-id");
        properties.setAppKey("test-app-key");
        properties.setCountry("gb");
        restTemplate = mock(RestTemplate.class);
        provider = new AdzunaJobSourceProvider(properties, restTemplate, new ObjectMapper());
    }

    private void stubBody(String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body == null ? "" : body));
    }

    private List<Job> fetch() {
        return provider.fetchJobs(JobSearchRequest.of(List.of("java"), "London", null, null, null, 50));
    }

    @Nested
    @DisplayName("Credentials")
    class CredentialTests {

        @Test
        @DisplayName("without app_id/app_key the provider is unavailable and makes no call")
        void missingCredentialsMakesNoNetworkCall() {
            properties.setAppId("");
            properties.setAppKey("");

            assertFalse(provider.isAvailable());
            assertTrue(provider.fetchJobs(
                    JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).isEmpty());
            verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(String.class));
        }

        @Test
        @DisplayName("a present app_id with a missing app_key is still treated as unconfigured")
        void partialCredentialsAreUnavailable() {
            properties.setAppId("only-id");
            properties.setAppKey("  ");

            assertFalse(provider.isAvailable());
            assertFalse(properties.hasCredentials());
        }

        @Test
        @DisplayName("a rejected credential pair logs and yields no listings, without throwing")
        void rejectedCredentialsIsEmpty() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN,
                            "Forbidden", null, null, null));

            assertTrue(fetch().isEmpty());
        }
    }

    @Nested
    @DisplayName("Mapping")
    class MappingTests {

        @Test
        @DisplayName("documented fields map onto the Job model")
        void mapsDocumentedFields() {
            stubBody(VALID_JSON);

            List<Job> jobs = fetch();

            assertEquals(2, jobs.size());
            Job first = jobs.get(0);
            assertEquals("adzuna-129698749", first.id());
            assertEquals("Senior Java Engineer", first.title());
            assertEquals("Acme Systems Ltd", first.company());
            assertEquals("Marlow, Buckinghamshire", first.location());
            assertEquals("full time, permanent", first.employmentType());
            assertEquals("2026-08-27", first.postingDate());
            assertEquals("ADZUNA", first.source());
            assertEquals("PUBLIC_API", first.sourceType());
            assertNotNull(first.discoveredAt());
        }

        @Test
        @DisplayName("location falls back to the area hierarchy when display_name is empty")
        void locationFallsBackToArea() {
            stubBody(VALID_JSON);

            assertEquals("UK, London", fetch().get(1).location());
        }

        @Test
        @DisplayName("sourceUrl is Adzuna's own listing link for that specific job")
        void sourceUrlIsTheOriginatingListing() {
            stubBody(VALID_JSON);

            assertEquals("https://www.adzuna.co.uk/jobs/land/ad/129698749?v=abc123",
                    fetch().get(0).sourceUrl());
        }

        /**
         * Adzuna publishes only its own redirect link. It does not expose the employer's
         * application destination, so none may be synthesized from it.
         */
        @Test
        @DisplayName("no employer application URL is ever fabricated")
        void applicationUrlIsNeverFabricated() {
            stubBody(VALID_JSON);

            for (Job job : fetch()) {
                assertNull(job.applicationUrl(),
                        "Adzuna supplies no employer destination; must stay null for " + job.id());
            }
        }

        @Test
        @DisplayName("skills are extracted from the listing text, not invented")
        void skillsAreExtractedFromListingText() {
            stubBody(VALID_JSON);

            List<String> skills = fetch().get(0).requiredSkills();

            assertTrue(skills.stream().anyMatch(s -> s.equalsIgnoreCase("Java")),
                    "Java is named in the description, was: " + skills);
            assertTrue(skills.stream().anyMatch(s -> s.equalsIgnoreCase("Spring Boot")),
                    "Spring Boot is named in the description, was: " + skills);
        }

        @Test
        @DisplayName("a listing that names no technology gets no skills rather than guessed ones")
        void listingWithoutTechnologyGetsNoSkills() {
            stubBody(VALID_JSON);

            List<String> skills = fetch().get(1).requiredSkills();

            assertTrue(skills.isEmpty(),
                    "no technology is mentioned, so nothing should be inferred; was: " + skills);
        }

        @Test
        @DisplayName("the coarse Adzuna category is not treated as a skill")
        void categoryIsNotASkill() {
            stubBody(VALID_JSON);

            List<String> skills = fetch().get(0).requiredSkills();

            assertFalse(skills.stream().anyMatch(s -> s.toLowerCase().contains("it jobs")),
                    "category label must not become a skill; was: " + skills);
        }
    }

    @Nested
    @DisplayName("Request shape and failure handling")
    class RequestAndFailureTests {

        @Test
        @DisplayName("the request targets the documented search endpoint with credentials")
        void requestsDocumentedEndpoint() {
            stubBody(VALID_JSON);

            fetch();

            verify(restTemplate).exchange(contains("/gb/search/1"),
                    eq(HttpMethod.GET), any(), eq(String.class));
            verify(restTemplate).exchange(contains("app_id=test-app-id"),
                    eq(HttpMethod.GET), any(), eq(String.class));
            verify(restTemplate).exchange(contains("app_key=test-app-key"),
                    eq(HttpMethod.GET), any(), eq(String.class));
            verify(restTemplate).exchange(contains("what=java"),
                    eq(HttpMethod.GET), any(), eq(String.class));
            verify(restTemplate).exchange(contains("where=London"),
                    eq(HttpMethod.GET), any(), eq(String.class));
        }

        @Test
        @DisplayName("results_per_page is capped at Adzuna's documented maximum of 50")
        void resultsPerPageIsCapped() {
            properties.setResultsPerPage(500);
            stubBody(VALID_JSON);

            fetch();

            verify(restTemplate).exchange(contains("results_per_page=50"),
                    eq(HttpMethod.GET), any(), eq(String.class));
        }

        @Test
        @DisplayName("a disabled provider makes no network call")
        void disabledMakesNoNetworkCall() {
            properties.setEnabled(false);

            assertFalse(provider.isAvailable());
            assertTrue(provider.fetchJobs(
                    JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).isEmpty());
            verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(String.class));
        }

        @Test
        @DisplayName("an unreachable host yields no listings and does not throw")
        void unreachableIsEmpty() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenThrow(new ResourceAccessException("connect timed out"));

            assertTrue(fetch().isEmpty());
        }

        @Test
        @DisplayName("a 429 rate-limit response yields no listings and does not throw")
        void rateLimitedIsEmpty() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                            "Too Many Requests", null, null, null));

            assertTrue(fetch().isEmpty());
        }

        @Test
        @DisplayName("a malformed payload yields no listings and does not throw")
        void malformedPayloadIsEmpty() {
            stubBody("not json at all");

            assertTrue(fetch().isEmpty());
        }

        @Test
        @DisplayName("a result without an id is dropped")
        void resultWithoutIdIsDropped() {
            stubBody("""
                    {"count":1,"results":[{"title":"No Id","description":"d",
                      "redirect_url":"https://www.adzuna.co.uk/jobs/land/ad/1",
                      "company":{"display_name":"Acme"},"location":{"display_name":"London"}}]}
                    """);

            assertTrue(fetch().isEmpty());
        }

        @Test
        @DisplayName("the source name is stable and uppercase")
        void sourceNameIsStable() {
            assertEquals("ADZUNA", provider.getSourceName());
        }
    }
}
