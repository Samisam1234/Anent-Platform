package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OpeningsMcpJobSourceProvider — Openings MCP job board adapter")
class OpeningsMcpJobSourceProviderTest {

    private static final String VALID_SSE_RESPONSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"job-123","title":"Software Engineer","company":"Google","location":"Bangalore","url":"https://www.google.com/careers/job123","minimum_qualifications":["Bachelor's degree","3 years Java"],"experience_level":"Mid"}]}}}
            """;

    private static final String VALID_SSE_WITH_APPLY_URL = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"job-456","title":"Backend Engineer","company":"Amazon","location":"Hyderabad","url":"https://amazon.jobs/job456","apply_url":"https://amazon.jobs/apply456"}]}}}
            """;

    private static final String INVALID_URL_RESPONSE = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"content":[{"type":"text","text":"ok"}],"structuredContent":{"data":[{"id":"job-789","title":"DevOps","company":"Apple","location":"Remote","url":"javascript:alert(1)"}]}}}
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

    @Test
    @DisplayName("1. isAvailable returns false when disabled")
    void isAvailableWhenDisabled() {
        properties.setEnabled(false);
        assertFalse(provider.isAvailable());
    }

    @Test
    @DisplayName("2. isAvailable returns false when baseUrl is blank or invalid")
    void isAvailableWhenInvalidUrl() {
        properties.setBaseUrl("   ");
        assertFalse(provider.isAvailable());

        properties.setBaseUrl("ftp://invalid");
        assertFalse(provider.isAvailable());
    }

    @Test
    @DisplayName("3. isAvailable returns true when enabled and valid")
    void isAvailableWhenValid() {
        assertTrue(provider.isAvailable());
    }

    @Test
    @DisplayName("4. fetchJobs returns empty when keywords are blank")
    void fetchJobsEmptyKeywords() {
        List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of(), null, null, null, null, 10));
        assertTrue(jobs.isEmpty());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("5 & 6 & 7. fetchJobs maps SSE response into Job correctly with source and valid sourceUrl")
    void mapsSseResponseCorrectly() {
        stubResponse(VALID_SSE_RESPONSE);
        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));

        assertEquals(1, jobs.size());
        Job job = jobs.get(0);
        assertEquals("openings-google_search_jobs-job-123", job.id());
        assertEquals("Software Engineer", job.title());
        assertEquals("Google", job.company());
        assertEquals("Bangalore", job.location());
        assertEquals("OPENINGS_MCP", job.source());
        assertEquals("MCP", job.sourceType());
        assertEquals("https://www.google.com/careers/job123", job.sourceUrl());
        assertNotNull(job.description());
        assertTrue(job.description().contains("Minimum Qualifications:"));
    }

    @Test
    @DisplayName("8. invalid url in response -> dropped, sourceUrl is null")
    void invalidUrlDropped() {
        stubResponse(INVALID_URL_RESPONSE);
        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("devops"), null, null, null, null, 10));

        assertEquals(1, jobs.size());
        assertNull(jobs.get(0).sourceUrl());
    }

    @Test
    @DisplayName("9. applicationUrl is null unless tool explicitly returns apply_url")
    void applicationUrlHandling() {
        // Without apply_url
        stubResponse(VALID_SSE_RESPONSE);
        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        List<Job> jobs1 = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));
        assertNull(jobs1.get(0).applicationUrl());

        // With apply_url
        stubResponse(VALID_SSE_WITH_APPLY_URL);
        List<Job> jobs2 = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));
        assertEquals("https://amazon.jobs/apply456", jobs2.get(0).applicationUrl());
    }

    @Test
    @DisplayName("10. HTTP 500 / 429 -> empty list, no exception")
    void httpErrorHandledSafely() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new HttpStatusCodeException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error") {});

        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));
        assertTrue(jobs.isEmpty());
    }

    @Test
    @DisplayName("11. malformed SSE -> empty list, no exception")
    void malformedSseHandledSafely() {
        stubResponse("garbage response body without data prefix");
        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));
        assertTrue(jobs.isEmpty());
    }

    @Test
    @DisplayName("12. JSON-RPC error object -> empty list, no exception")
    void jsonRpcErrorHandledSafely() {
        stubResponse(JSON_RPC_ERROR_RESPONSE);
        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));
        assertTrue(jobs.isEmpty());
    }

    @Test
    @DisplayName("13. parallel calls: one tool failing does not break the other tools")
    void partialFailureDoesNotBreakOthers() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenAnswer(invocation -> {
                    HttpEntity<String> entity = invocation.getArgument(2);
                    String body = entity.getBody();
                    if (body != null && body.contains("google_search_jobs")) {
                        throw new RuntimeException("Google tool down");
                    } else {
                        return ResponseEntity.ok(VALID_SSE_RESPONSE);
                    }
                });

        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(true);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        List<Job> jobs = provider.fetchJobs(JobSearchRequest.of(List.of("java"), null, null, null, null, 10));
        assertEquals(1, jobs.size());
        boolean hasAmazonJob = jobs.stream().anyMatch(j -> j.id().contains("amazon_search_jobs"));
        assertTrue(hasAmazonJob, "Successful Amazon job should be present despite Google failing");
    }

    @Test
    @DisplayName("14. deterministic repeat: same input -> same output")
    void deterministicRepeat() {
        stubResponse(VALID_SSE_RESPONSE);
        properties.setIncludeGoogle(true);
        properties.setIncludeAmazon(false);
        properties.setIncludeApple(false);
        properties.setIncludeMeta(false);

        JobSearchRequest req = JobSearchRequest.of(List.of("java"), null, null, null, null, 10);
        List<Job> first = provider.fetchJobs(req);
        List<Job> second = provider.fetchJobs(req);

        assertEquals(first.size(), second.size());
        assertEquals(first.get(0).title(), second.get(0).title());
        assertEquals(first.get(0).sourceUrl(), second.get(0).sourceUrl());
    }
}
