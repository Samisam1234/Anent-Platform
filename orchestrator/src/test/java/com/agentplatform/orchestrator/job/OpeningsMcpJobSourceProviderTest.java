package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Openings MCP adapter contract.
 *
 * <p>Every fixture below is a <b>recorded</b> response body from openings-mcp 0.16.2
 * (commit 3726ec09), served over streamable HTTP as an SSE-framed JSON-RPC payload. The
 * bodies are embedded rather than fetched, so these tests never touch the network and
 * never depend on a live server being up.</p>
 *
 * <p>Two verified response shapes are exercised: a search tool returning
 * {@code structuredContent.data} as an <em>array</em> of summaries, and a detail tool
 * returning the posting itself as a <em>flat</em> structured content — which is the only
 * place the server publishes {@code apply_url}.</p>
 */
@DisplayName("OpeningsMcpJobSourceProvider — Openings MCP job board adapter")
class OpeningsMcpJobSourceProviderTest {

    /** Recorded: google_search_jobs summary. No apply_url; url is Google's own careers page. */
    private static final String GOOGLE_SEARCH_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"120189543990076102","title":"Software Engineer, Early Career","company":"Google","location":"Bengaluru, Karnataka, India","url":"https://www.google.com/about/careers/applications/jobs/results/120189543990076102","minimum_qualifications":["Bachelor's degree","3 years Java"],"experience_level":"MID","description":"Build Google's products."}]}}}
            """;

    /** Recorded: linkedin_search_jobs summary. url is the aggregator's own page. */
    private static final String LINKEDIN_SEARCH_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"4461976064","title":"Senior Java Engineer","company":"Some Employer","location":"Berlin, Germany","url":"https://www.linkedin.com/jobs/view/4461976064","company_url":"https://www.linkedin.com/company/example"}]}}}
            """;

    /** Recorded: apple_search_jobs summary. Identifier is job_id; company is company_name. */
    private static final String APPLE_SEARCH_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"job_id":"200664320","title":"Software Engineering, Masters Internships","locations":["Cupertino, CA, US"],"url":"https://jobs.apple.com/en-us/details/200664320/software-engineering-masters-internships"}]}}}
            """;

    /** Recorded: amazon_search_jobs summary. company is company_name; no apply_url in summary. */
    private static final String AMAZON_SEARCH_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"10473779","title":"Security Engineer, AWS Security Incident Response","company_name":"AWS India - Karnataka","location":"IN, KA, Bengaluru","url":"https://www.amazon.jobs/en/jobs/10473779/security-engineer-aws-security-incident-response"}]}}}
            """;

    /** Recorded: amazon_get_job_detail. FLAT structured content — the only shape with apply_url. */
    private static final String AMAZON_DETAIL_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"apply_url":"https://account.amazon.jobs/jobs/10473779/apply","basic_qualifications":"- 3+ years of programming in Python, Ruby, Go, Swift, Java, .Net, C++ or similar object oriented language experience","business_category":"aws","company_name":"AWS India - Karnataka","country_code":"IND","description":"AWS Applied AI Solutions (AAIS) is building toward a future where every business innovates with Amazon AI teammates.","id":"10473779","location":"IN, KA, Bengaluru","posted_date":"July 14, 2026","schedule_type":"full-time","title":"Security Engineer, AWS Security Incident Response","url":"https://www.amazon.jobs/en/jobs/10473779/security-engineer-aws-security-incident-response"}}}
            """;

    /** Recorded: meta_get_job_detail. Flat, and genuinely has NO apply_url field. */
    private static final String META_DETAIL_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"description":"Build products that connect people.","job_id":"2152805521966105","locations":["Menlo Park, CA"],"title":"Software Engineer","url":"https://www.metacareers.com/jobs/2152805521966105/"}}}
            """;

    /**
     * Recorded summary shape whose url is a board page rather than a first-party employer
     * host, so the adapter has no application destination and the detail step is what
     * supplies one. Structurally identical to a real summary otherwise.
     */
    private static final String SUMMARY_WITHOUT_DESTINATION_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"10473779","title":"Security Engineer, AWS Security Incident Response","company_name":"AWS India - Karnataka","location":"IN, KA, Bengaluru","url":"https://www.linkedin.com/jobs/view/10473779"}]}}}
            """;

    private static final String INVALID_URL_RESPONSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"job-789","title":"DevOps","company":"Apple","location":"Remote","url":"javascript:alert(1)"}]}}}
            """;

    /** A first-party tool returning a host that is NOT on its verified allowlist. */
    private static final String FIRST_PARTY_HOST_MISMATCH_SSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"x-1","title":"Software Engineer","company":"Google","location":"Remote","url":"https://evil-aggregator.example.org/jobs/x-1"}]}}}
            """;

    private static final String JSON_RPC_ERROR_RESPONSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
            """;

    private OpeningsMcpJobProperties properties;
    private RestTemplate restTemplate;
    private OpeningsMcpJobSourceProvider provider;

    @BeforeEach
    void setUp() {
        properties = new OpeningsMcpJobProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:9000/");
        restTemplate = mock(RestTemplate.class);
        provider = new OpeningsMcpJobSourceProvider(properties, restTemplate, new ObjectMapper());
    }

    private void stubResponse(String responseBody) {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseBody));
    }

    private void onlyGoogle() {
        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);
    }

    /**
     * The first {@code structuredContent.data} entry of a recorded SSE body, so a fixture can
     * be handed straight to {@code mapItem} without going through the network stub.
     */
    private com.fasterxml.jackson.databind.JsonNode firstEntry(String sseBody) throws Exception {
        String json = sseBody.substring(sseBody.indexOf("data: ") + 6).trim();
        return new ObjectMapper().readTree(json)
                .path("result").path("structuredContent").path("data").get(0);
    }

    @Nested
    @DisplayName("Availability and query contract")
    class AvailabilityTests {

        @Test
        @DisplayName("isAvailable returns false when disabled")
        void isAvailableWhenDisabled() {
            properties.setEnabled(false);
            assertFalse(provider.isAvailable());
        }

        @Test
        @DisplayName("isAvailable returns false when baseUrl is blank or invalid")
        void isAvailableWhenInvalidUrl() {
            properties.setBaseUrl("   ");
            assertFalse(provider.isAvailable());

            properties.setBaseUrl("ftp://invalid");
            assertFalse(provider.isAvailable());
        }

        @Test
        @DisplayName("isAvailable returns true when enabled and valid")
        void isAvailableWhenValid() {
            assertTrue(provider.isAvailable());
        }

        @Test
        @DisplayName("fetchJobs returns empty without network call when keywords are blank")
        void fetchJobsEmptyKeywords() {
            List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of(), null, null, null, null, 10));
            assertTrue(jobs.isEmpty());
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("First-party employer career tools")
    class FirstPartyTests {

        @Test
        @DisplayName("google summary: employer careers page becomes applicationUrl, kept as sourceUrl")
        void googleFirstPartyUrl() {
            stubResponse(GOOGLE_SEARCH_SSE);
            onlyGoogle();

            List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

            assertEquals(1, jobs.size());
            Job job = jobs.get(0);
            assertEquals("openings-google_search_jobs-120189543990076102", job.id());
            assertEquals("Google", job.company());
            assertEquals("OPENINGS_MCP", job.source());
            assertEquals("MCP", job.sourceType());
            assertEquals("https://www.google.com/about/careers/applications/jobs/results/120189543990076102",
                    job.sourceUrl());
            assertEquals("https://www.google.com/about/careers/applications/jobs/results/120189543990076102",
                    job.applicationUrl());
            assertNotNull(job.description());
            assertTrue(job.description().contains("Minimum Qualifications:"));
            assertEquals("MID", job.experienceRequirement());
        }

        @Test
        @DisplayName("amazon summary: company_name variant read, employer page becomes applicationUrl")
        void amazonFirstPartyUrl() {
            stubResponse(AMAZON_SEARCH_SSE);
            properties.setIncludeGoogle(false);
            properties.setIncludeAmazon(true);
            properties.setIncludeApple(false);
            properties.setIncludeMeta(false);

            Job job = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).get(0);

            assertEquals("openings-amazon_search_jobs-10473779", job.id());
            assertEquals("AWS India - Karnataka", job.company());
            // The genuine employer posting page the server returned — no path invented.
            assertEquals("https://www.amazon.jobs/en/jobs/10473779/security-engineer-aws-security-incident-response",
                    job.applicationUrl());
            assertEquals(job.sourceUrl(), job.applicationUrl());
        }

        @Test
        @DisplayName("apple summary: job_id identifier variant used verbatim in the stable id format")
        void appleJobIdVariant() {
            stubResponse(APPLE_SEARCH_SSE);
            properties.setIncludeGoogle(false);
            properties.setIncludeAmazon(false);
            properties.setIncludeApple(true);
            properties.setIncludeMeta(false);

            Job job = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).get(0);

            assertEquals("openings-apple_search_jobs-200664320", job.id());
            assertEquals("https://jobs.apple.com/en-us/details/200664320/software-engineering-masters-internships",
                    job.applicationUrl());
        }

        @Test
        @DisplayName("first-party tool returning a non-allowlisted host keeps applicationUrl null")
        void firstPartyHostMismatchRefused() {
            stubResponse(FIRST_PARTY_HOST_MISMATCH_SSE);
            onlyGoogle();

            Job job = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).get(0);

            // example.org is refused by JobUrlValidator as a placeholder domain.
            assertNull(job.sourceUrl());
            assertNull(job.applicationUrl());
        }
    }

    @Nested
    @DisplayName("Aggregator boards are never an employer destination")
    class AggregatorTests {

        @Test
        @DisplayName("linkedin summary stays sourceUrl only, applicationUrl null")
        void linkedinIsNotAnApplicationDestination() throws Exception {
            Job job = provider.mapItem("linkedin_search_jobs", firstEntry(LINKEDIN_SEARCH_SSE));

            assertEquals("openings-linkedin_search_jobs-4461976064", job.id());
            assertEquals("https://www.linkedin.com/jobs/view/4461976064", job.sourceUrl());
            assertNull(job.applicationUrl(), "an aggregator page is never an employer destination");
        }

        @Test
        @DisplayName("an aggregator host is refused even when a first-party tool returns it")
        void aggregatorHostRefusedEvenForFirstPartyTool() throws Exception {
            // Tool/host mismatch: the host is an aggregator's, so the first-party allowlist
            // for this tool does not apply and the URL cannot become a destination.
            Job job = provider.mapItem("meta_search_jobs", firstEntry(LINKEDIN_SEARCH_SSE));

            assertEquals("https://www.linkedin.com/jobs/view/4461976064", job.sourceUrl());
            assertNull(job.applicationUrl());
        }

        @Test
        @DisplayName("an aggregator URL offered by a first-party tool is still refused")
        void aggregatorUrlFromFirstPartyToolRefused() {
            String sse = """
                    event: message
                    data: {"jsonrpc":"2.0","id":1,"result":{"structuredContent":{"data":[{"id":"9","title":"Engineer","url":"https://www.linkedin.com/jobs/view/9"}]}}}
                    """;
            stubResponse(sse);
            onlyGoogle();

            Job job = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).get(0);

            assertEquals("https://www.linkedin.com/jobs/view/9", job.sourceUrl());
            assertNull(job.applicationUrl());
        }

        @Test
        @DisplayName("malformed url in response -> dropped, both URLs null")
        void invalidUrlDropped() {
            stubResponse(INVALID_URL_RESPONSE);
            onlyGoogle();

            Job job = provider.fetchJobs(JobSearchRequest.of(List.of("devops"), null, null, null, null, 10)).get(0);

            assertNull(job.sourceUrl());
            assertNull(job.applicationUrl());
        }
    }

    @Nested
    @DisplayName("apply_url from the detail record")
    class DetailEnrichmentTests {

        @Test
        @DisplayName("detail enrichment (off by default) is a no-op")
        void enrichmentDisabledByDefault() {
            stubResponse(AMAZON_SEARCH_SSE);
            properties.setIncludeGoogle(false);
            properties.setIncludeAmazon(true);
            properties.setIncludeApple(false);
            properties.setIncludeMeta(false);

            provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

            verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
        }

        @Test
        @DisplayName("enabled enrichment asks the detail tool and adopts its apply_url")
        void enrichmentAdoptsApplyUrl() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenAnswer(inv -> {
                        HttpEntity<String> entity = inv.getArgument(2);
                        String body = entity.getBody() == null ? "" : entity.getBody();
                        if (body.contains("get_job_detail")) {
                            return ResponseEntity.ok(AMAZON_DETAIL_SSE);
                        }
                        // Recorded shape: a summary whose url is a board page, so no
                        // application destination exists yet and the detail step runs.
                        return ResponseEntity.ok(SUMMARY_WITHOUT_DESTINATION_SSE);
                    });
            properties.setIncludeGoogle(false);
            properties.setIncludeAmazon(true);
            properties.setIncludeApple(false);
            properties.setIncludeMeta(false);
            properties.setApplyUrlDetailLimit(3);

            List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

            assertEquals(1, jobs.size());
            assertEquals("https://account.amazon.jobs/jobs/10473779/apply", jobs.get(0).applicationUrl());
            // Identity and provenance are untouched by enrichment; only the destination is added.
            assertEquals("openings-amazon_search_jobs-10473779", jobs.get(0).id());
            assertEquals("https://www.linkedin.com/jobs/view/10473779", jobs.get(0).sourceUrl());
        }

        @Test
        @DisplayName("a listing that already has a destination is not re-fetched")
        void enrichmentSkipsListingsThatAlreadyHaveADestination() {
            stubResponse(AMAZON_SEARCH_SSE);
            properties.setIncludeGoogle(false);
            properties.setIncludeAmazon(true);
            properties.setIncludeApple(false);
            properties.setIncludeMeta(false);
            properties.setApplyUrlDetailLimit(5);

            List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

            assertEquals(1, jobs.size());
            // One search call, no detail call: the employer page is already the destination.
            verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
            assertEquals(jobs.get(0).sourceUrl(), jobs.get(0).applicationUrl());
        }

        @Test
        @DisplayName("flat detail record without apply_url leaves the listing unchanged")
        void flatDetailWithoutApplyUrl() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenAnswer(inv -> {
                        HttpEntity<String> entity = inv.getArgument(2);
                        String body = entity.getBody() == null ? "" : entity.getBody();
                        if (body.contains("get_job_detail")) {
                            return ResponseEntity.ok(META_DETAIL_SSE);
                        }
                        return ResponseEntity.ok("""
                                event: message
                                data: {"jsonrpc":"2.0","id":1,"result":{"structuredContent":{"data":[{"job_id":"2152805521966105","title":"Software Engineer","url":"https://www.metacareers.com/jobs/2152805521966105/"}]}}}
                                """);
                    });
            properties.setIncludeGoogle(false);
            properties.setIncludeAmazon(false);
            properties.setIncludeApple(false);
            properties.setIncludeMeta(true);
            properties.setApplyUrlDetailLimit(2);

            List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

            assertEquals(1, jobs.size());
            assertEquals("https://www.metacareers.com/jobs/2152805521966105/", jobs.get(0).applicationUrl());
        }
    }

    @Nested
    @DisplayName("Response parsing")
    class ParsingTests {

        @Test
        @DisplayName("both recorded shapes parse: search array and flat detail object")
        void bothShapesParse() {
            provider.parseStructuredContent(GOOGLE_SEARCH_SSE);
            assertTrue(provider.parseSseData(GOOGLE_SEARCH_SSE).isArray(),
                    "search shape exposes structuredContent.data");

            assertNull(provider.parseSseData(AMAZON_DETAIL_SSE),
                    "flat detail shape has no data array");

            var detail = provider.parseStructuredContent(AMAZON_DETAIL_SSE);
            assertEquals("https://account.amazon.jobs/jobs/10473779/apply", detail.path("apply_url").asText());
            assertEquals("10473779", detail.path("id").asText());
        }

        @Test
        @DisplayName("plain JSON body (no SSE framing) is accepted")
        void plainJsonAccepted() {
            assertNotNull(provider.parseSseData(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"structuredContent\":{\"data\":[{\"id\":\"1\",\"title\":\"T\"}]}}}"));
        }

        @Test
        @DisplayName("HTTP 500 / 429 -> empty list, no exception")
        void httpErrorHandledSafely() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenThrow(new HttpStatusCodeException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error") {});
            onlyGoogle();

            assertTrue(provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).isEmpty());
        }

        @Test
        @DisplayName("malformed SSE -> empty list, no exception")
        void malformedSseHandledSafely() {
            stubResponse("garbage response body without data prefix");
            onlyGoogle();

            assertTrue(provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).isEmpty());
        }

        @Test
        @DisplayName("JSON-RPC error object -> empty list, no exception")
        void jsonRpcErrorHandledSafely() {
            stubResponse(JSON_RPC_ERROR_RESPONSE);
            onlyGoogle();

            assertTrue(provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10)).isEmpty());
        }

        @Test
        @DisplayName("parallel calls: one tool failing does not break the other tools")
        void partialFailureDoesNotBreakOthers() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                    .thenAnswer(inv -> {
                        HttpEntity<String> entity = inv.getArgument(2);
                        String body = entity.getBody() == null ? "" : entity.getBody();
                        if (body.contains("google_search_jobs")) {
                            throw new RuntimeException("Google tool down");
                        }
                        return ResponseEntity.ok(AMAZON_SEARCH_SSE);
                    });
            properties.setIncludeGoogle(true);
            properties.setIncludeAmazon(true);
            properties.setIncludeApple(false);
            properties.setIncludeMeta(false);

            List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

            assertEquals(1, jobs.size());
            assertTrue(jobs.get(0).id().contains("amazon_search_jobs"));
        }

        @Test
        @DisplayName("deterministic repeat: same input -> same output")
        void deterministicRepeat() {
            stubResponse(GOOGLE_SEARCH_SSE);
            onlyGoogle();

            JobSearchRequest req = JobSearchRequest.of(List.of("java"), null, null, null, null, 10);
            List<Job> first = provider.fetchJobs(req);
            List<Job> second = provider.fetchJobs(req);

            assertEquals(first.size(), second.size());
            assertEquals(first.get(0).id(), second.get(0).id());
            assertEquals(first.get(0).sourceUrl(), second.get(0).sourceUrl());
            assertEquals(first.get(0).applicationUrl(), second.get(0).applicationUrl());
        }
    }
}
