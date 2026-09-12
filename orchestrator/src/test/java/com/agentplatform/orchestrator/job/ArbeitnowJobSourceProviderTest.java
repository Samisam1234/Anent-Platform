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
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hermetic tests for {@link ArbeitnowJobSourceProvider}.
 *
 * <p>The fixture below is the documented Arbeitnow job-board response shape
 * ({@code data} / {@code links} / {@code meta}, with snake_case listing fields) — it is not
 * an invented schema. The HTTP layer is a mocked {@link RestTemplate}, so there is no live
 * network, database or external provider.</p>
 */
@DisplayName("ArbeitnowJobSourceProvider — Arbeitnow job board adapter")
class ArbeitnowJobSourceProviderTest {

    /** Mirrors the documented response: data[] + links + meta. */
    private static final String VALID_JSON = """
            {
              "data": [
                {
                  "slug": "backend-engineer-acme-178727",
                  "company_name": "Acme GmbH",
                  "title": "Backend Engineer",
                  "description": "<p>We need <strong>Java</strong> and Spring Boot experience.</p>",
                  "remote": true,
                  "url": "https://arbeitnow.com/view/backend-engineer-acme-178727",
                  "tags": ["Information technology", "software development"],
                  "job_types": ["Full time", "Mid-senior"],
                  "location": "Berlin",
                  "created_at": 1767225600
                },
                {
                  "slug": "frontend-engineer-beta-178728",
                  "company_name": "Beta Ltd",
                  "title": "Frontend Engineer",
                  "description": "",
                  "remote": false,
                  "url": "https://arbeitnow.com/view/frontend-engineer-beta-178728",
                  "tags": ["sales"],
                  "job_types": ["part time"],
                  "location": "",
                  "created_at": 1767312000
                }
              ],
              "links": {
                "first": "https://arbeitnow.com/api/job-board-api?page=1",
                "last": null,
                "prev": null,
                "next": "https://arbeitnow.com/api/job-board-api?page=2"
              },
              "meta": { "current_page": 1, "from": 1, "per_page": 100, "to": 100 }
            }
            """;

    private ArbeitnowJobProperties properties;
    private RestTemplate restTemplate;
    private ArbeitnowJobSourceProvider provider;

    @BeforeEach
    void setUp() {
        properties = new ArbeitnowJobProperties();
        properties.setEnabled(true);
        restTemplate = mock(RestTemplate.class);
        provider = new ArbeitnowJobSourceProvider(properties, restTemplate, new ObjectMapper());
    }

    private void stubBody(String body) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body == null ? "" : body));
    }

    private List<Job> fetch() {
        return provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 50));
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
            assertEquals("arbeitnow-backend-engineer-acme-178727", first.id());
            assertEquals("Backend Engineer", first.title());
            assertEquals("Acme GmbH", first.company());
            assertEquals("Berlin (Remote)", first.location());
            assertEquals("Full time, Mid-senior", first.employmentType());
            assertEquals("ARBEITNOW", first.source());
            assertEquals("PUBLIC_API", first.sourceType());
            assertNotNull(first.discoveredAt());
        }

        @Test
        @DisplayName("provenance URL is the Arbeitnow listing page for that specific job")
        void sourceUrlIsTheOriginatingListing() {
            stubBody(VALID_JSON);

            Job first = fetch().get(0);

            assertEquals("https://arbeitnow.com/view/backend-engineer-acme-178727", first.sourceUrl());
        }

        @Test
        @DisplayName("HTML descriptions are normalized to text")
        void descriptionIsNormalized() {
            stubBody(VALID_JSON);

            String description = fetch().get(0).description();

            assertNotNull(description);
            assertFalse(description.contains("<p>"), "HTML tags must be stripped, was: " + description);
            assertTrue(description.contains("Java"), "text content must survive, was: " + description);
        }

        @Test
        @DisplayName("created_at (Unix seconds) becomes the yyyy-MM-dd the app filters on")
        void createdAtBecomesIsoDate() {
            stubBody(VALID_JSON);

            assertEquals("2026-01-01", fetch().get(0).postingDate());
        }

        @Test
        @DisplayName("a blank location on a remote listing still reports Remote")
        void remoteWithoutLocationIsRemote() {
            // The shared VALID_JSON has no listing that is both remote and location-less,
            // so this case gets its own payload (same pattern as the tests below).
            stubBody("""
                    {
                      "data": [
                        {
                          "slug": "data-engineer-gamma-178729",
                          "company_name": "Gamma AG",
                          "title": "Data Engineer",
                          "description": "",
                          "remote": true,
                          "url": "https://arbeitnow.com/view/data-engineer-gamma-178729",
                          "tags": ["Data engineering"],
                          "job_types": ["Full time"],
                          "location": "",
                          "created_at": 1767398400
                        },
                        {
                          "slug": "field-engineer-delta-178730",
                          "company_name": "Delta Ltd",
                          "title": "Field Engineer",
                          "description": "",
                          "remote": false,
                          "url": "https://arbeitnow.com/view/field-engineer-delta-178730",
                          "tags": ["Field service"],
                          "job_types": ["Full time"],
                          "location": null,
                          "created_at": 1767484800
                        }
                      ],
                      "links": { "first": null, "last": null, "prev": null, "next": null },
                      "meta": { "current_page": 1, "from": 1, "per_page": 100, "to": 2 }
                    }
                    """);

            List<Job> jobs = fetch();

            assertEquals(2, jobs.size());
            // The feed explicitly says remote, so "Remote" is reported instead of nothing.
            assertEquals("Remote", jobs.get(0).location());
            // A listing the feed does NOT mark remote must not be given a location it never
            // had: absence of a location is not evidence of remote work.
            assertNull(jobs.get(1).location());
        }

        /**
         * Arbeitnow exposes only its own listing page. It does not publish an employer
         * application destination, so none may be synthesized from {@code sourceUrl}.
         */
        @Test
        @DisplayName("no employer application URL is ever fabricated")
        void applicationUrlIsNeverFabricated() {
            stubBody(VALID_JSON);

            for (Job job : fetch()) {
                assertNull(job.applicationUrl(),
                        "Arbeitnow supplies no application destination; must stay null for " + job.id());
            }
        }

        @Test
        @DisplayName("an unsafe listing URL is nulled rather than passed through")
        void unsafeUrlIsRejected() {
            stubBody("""
                    {"data":[{"slug":"x-1","company_name":"Acme","title":"Engineer",
                      "description":"d","remote":false,"url":"http://localhost:8080/job/1",
                      "tags":[],"job_types":[],"location":"Berlin","created_at":1767225600}],
                     "meta":{"current_page":1,"per_page":100}}
                    """);

            assertNull(fetch().get(0).sourceUrl());
        }

        @Test
        @DisplayName("a listing without a slug is dropped")
        void listingWithoutSlugIsDropped() {
            stubBody("""
                    {"data":[{"company_name":"Acme","title":"No Slug","description":"d",
                      "remote":false,"url":"https://arbeitnow.com/view/none",
                      "tags":[],"job_types":[],"location":"Berlin","created_at":1767225600}],
                     "meta":{"current_page":1,"per_page":100}}
                    """);

            assertTrue(fetch().isEmpty());
        }
    }

    @Nested
    @DisplayName("Availability and failure handling")
    class FailureTests {

        @Test
        @DisplayName("disabled by default: no network call is made")
        void disabledMakesNoNetworkCall() {
            properties.setEnabled(false);

            assertTrue(provider.fetchJobs(JobSearchRequest.of(List.of(), null, null, null, null, 10)).isEmpty());
            assertFalse(provider.isAvailable());
            verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(), eq(String.class));
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
        @DisplayName("an unreachable host yields no listings and does not throw")
        void unreachableIsEmpty() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(String.class)))
                    .thenThrow(new ResourceAccessException("connect timed out"));

            assertTrue(fetch().isEmpty());
        }

        @Test
        @DisplayName("a malformed payload yields no listings and does not throw")
        void malformedPayloadIsEmpty() {
            stubBody("{ this is not json");

            assertTrue(fetch().isEmpty());
        }

        @Test
        @DisplayName("an empty body yields no listings")
        void emptyBodyIsEmpty() {
            stubBody("");

            assertTrue(fetch().isEmpty());
        }

        @Test
        @DisplayName("the source name is stable and uppercase")
        void sourceNameIsStable() {
            assertEquals("ARBEITNOW", provider.getSourceName());
        }

        @Test
        @DisplayName("the request targets the documented endpoint with page=1")
        void requestsDocumentedEndpoint() {
            stubBody(VALID_JSON);

            fetch();

            verify(restTemplate).exchange(contains("arbeitnow.com/api/job-board-api"),
                    eq(HttpMethod.GET), any(), eq(String.class));
            verify(restTemplate).exchange(contains("page=1"),
                    eq(HttpMethod.GET), any(), eq(String.class));
        }

        /**
         * L: the Arbeitnow job-board API documents only pagination — there is no keyword,
         * query or category parameter in its published interface. The provider therefore
         * fetches the board and lets the backend relevance pipeline do the filtering,
         * rather than inventing an unsupported parameter that the API would ignore or
         * reject. This test pins that behaviour so a future "optimisation" cannot quietly
         * add a parameter that does not exist.
         */
        @Test
        @DisplayName("keywords are deliberately not sent: the API documents only pagination")
        void keywordsAreNotSentToTheApi() {
            stubBody(VALID_JSON);

            provider.fetchJobs(JobSearchRequest.of(
                    List.of("verilog", "vlsi"), null, null, null, null, 50));

            ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
            verify(restTemplate).exchange(url.capture(), eq(HttpMethod.GET), any(), eq(String.class));
            String requested = url.getValue();

            assertTrue(requested.contains("page=1"), "pagination must still be sent: " + requested);
            assertFalse(requested.contains("verilog"), "no keyword may be sent: " + requested);
            assertFalse(requested.contains("vlsi"), "no keyword may be sent: " + requested);
            assertFalse(requested.contains("search="), "no undocumented search parameter: " + requested);
            assertFalse(requested.contains("what="), "no undocumented query parameter: " + requested);
        }

        /**
         * Because nothing is filtered at the source, the board comes back whole and the
         * backend relevance pipeline is what makes the results usable. This asserts the
         * provider hands back every listing it was given so that filtering happens in one
         * place, downstream, where it can be tested.
         */
        @Test
        @DisplayName("the whole board is returned for downstream relevance filtering")
        void wholeBoardIsReturnedForDownstreamFiltering() {
            stubBody(VALID_JSON);

            List<Job> jobs = provider.fetchJobs(
                    JobSearchRequest.of(List.of("verilog"), null, null, null, null, 50));

            assertEquals(2, jobs.size(),
                    "the provider must not pre-filter; the relevance pipeline owns that");
        }

        @Test
        @DisplayName("pagination stops once the requested limit is satisfied")
        void paginationRespectsLimit() {
            properties.setMaxPages(5);
            stubBody(VALID_JSON);

            List<Job> jobs = provider.fetchJobs(
                    JobSearchRequest.of(List.of("java"), null, null, null, null, 2));

            assertEquals(2, jobs.size());
        }
    }
}
