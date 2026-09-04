package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchRequest;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.job.JobSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link JobSearchController}.
 *
 * <p>{@code @WebMvcTest} loads only the web layer; {@link JobSearchService} is a
 * Mockito mock so no source, database or LLM is touched. Each path asserts the
 * binding, delegation and the existing {@code JobSearchResult}-compatible JSON.</p>
 */
@WebMvcTest(controllers = {JobSearchController.class, GlobalExceptionHandler.class})
class JobSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobSearchService jobSearchService;

    private static final String SEARCH_URL = "/api/v1/jobs/search";

    private Job mockJob() {
        return new Job("mock-sw-001", "Java Developer", "TechNova Solutions", "Hyderabad, India",
                "Backend microservices.", List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of("Docker"), "Fresher / 0-1 years", "FULL_TIME", "2026-08-25",
                "MOCK_SOURCE", null, "MOCK", null);
    }

    private Job liveJob() {
        return new Job("remotive-2091101", "Senior React Full-stack Developer", "Lemon.io", "Remote",
                "Remote full-stack role.", List.of("Java", "Spring Boot"), List.of(),
                null, "full_time", "2026-08-27", "REMOTIVE",
                "https://remotive.com/remote-jobs/software-development/senior-react-full-stack-developer-2091101",
                "PUBLIC_API", Instant.parse("2026-08-27T14:36:09Z"));
    }

    private JobSearchResult result(Job... jobs) {
        return new JobSearchResult(List.of(jobs), jobs.length, "MOCK_SOURCE, REMOTIVE", true,
                "Live job search completed successfully.");
    }

    // ─── 1. valid request ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — valid request returns jobs + provenance JSON")
    void search_validRequest_returnsJobs() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob(), liveJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":[\"java\"],\"location\":\"Hyderabad\",\"limit\":20}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.live").value(true))
                .andExpect(jsonPath("$.source").value("MOCK_SOURCE, REMOTIVE"))
                .andExpect(jsonPath("$.message").value("Live job search completed successfully."))
                .andExpect(jsonPath("$.jobs[0].source").value("MOCK_SOURCE"))
                .andExpect(jsonPath("$.jobs[0].sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.jobs[1].source").value("REMOTIVE"))
                .andExpect(jsonPath("$.jobs[1].sourceUrl").value(
                        "https://remotive.com/remote-jobs/software-development/senior-react-full-stack-developer-2091101"))
                .andExpect(jsonPath("$.jobs[1].discoveredAt").value("2026-08-27T14:36:09Z"));
    }

    // ─── 8. safe response mapping ────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — response is the existing JobSearchResult-compatible shape only")
    void search_responseShape_matchesJobSearchResult() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.source").isString())
                .andExpect(jsonPath("$.live").isBoolean())
                .andExpect(jsonPath("$.message").isString());
    }

    // ─── 2. blank query ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — blank query is passed through, not rejected")
    void search_blankQuery_passesThrough() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":[],\"location\":\"\",\"source\":\"\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JobSearchRequest> captor = ArgumentCaptor.forClass(JobSearchRequest.class);
        verify(jobSearchService).search(captor.capture());
        assertEquals(List.of(), captor.getValue().keywords());
        assertEquals("", captor.getValue().location());
    }

    // ─── 3. source filter ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — source filter is bound and forwarded")
    void search_sourceFilter_boundToDomain() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"MOCK_SOURCE\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JobSearchRequest> captor = ArgumentCaptor.forClass(JobSearchRequest.class);
        verify(jobSearchService).search(captor.capture());
        assertEquals("MOCK_SOURCE", captor.getValue().source());
    }

    // ─── 4. location filter ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — location filter is bound and forwarded")
    void search_locationFilter_boundToDomain() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"Hyderabad\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JobSearchRequest> captor = ArgumentCaptor.forClass(JobSearchRequest.class);
        verify(jobSearchService).search(captor.capture());
        assertEquals("Hyderabad", captor.getValue().location());
    }

    // ─── 5. keyword filter ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — keyword filter is bound and forwarded")
    void search_keywordFilter_boundToDomain() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":[\"java\"],\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JobSearchRequest> captor = ArgumentCaptor.forClass(JobSearchRequest.class);
        verify(jobSearchService).search(captor.capture());
        assertEquals(List.of("java"), captor.getValue().keywords());
        assertEquals("FULL_TIME", captor.getValue().employmentType());
    }

    // ─── 6. combined filters ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — combined filters are all bound and forwarded")
    void search_combinedFilters_allBound() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":[\"java\",\"postgres\"],\"location\":\"Hyderabad\"," +
                                "\"experience\":\"1-3 years\",\"employmentType\":\"FULL_TIME\"," +
                                "\"datePosted\":\"week\",\"limit\":50,\"source\":\"MOCK_SOURCE\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JobSearchRequest> captor = ArgumentCaptor.forClass(JobSearchRequest.class);
        verify(jobSearchService).search(captor.capture());

        JobSearchRequest req = captor.getValue();
        assertEquals(List.of("java", "postgres"), req.keywords());
        assertEquals("Hyderabad", req.location());
        assertEquals("1-3 years", req.experience());
        assertEquals("FULL_TIME", req.employmentType());
        assertEquals("week", req.datePosted());
        assertEquals(50, req.limit());
        assertEquals("MOCK_SOURCE", req.source());
    }

    // ─── 7. null-safe request ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — empty/null body maps to an empty request, not an error")
    void search_nullBody_usesEmptyRequest() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        ArgumentCaptor<JobSearchRequest> captor = ArgumentCaptor.forClass(JobSearchRequest.class);
        verify(jobSearchService).search(captor.capture());
        assertEquals(List.of(), captor.getValue().keywords());
        assertNull(captor.getValue().source());
    }

    // ─── 9. malformed request ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — malformed JSON maps to 4xx")
    void search_malformedJson_returns4xx() throws Exception {
        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /api/v1/jobs/search — invalid limit maps to 400 ProblemDetail via service contract")
    void search_invalidLimit_returns400() throws Exception {
        // The service throws IllegalArgumentException for out-of-range limits; the DTO
        // forwards it so GlobalExceptionHandler maps it to a clean 400.
        when(jobSearchService.search(any(JobSearchRequest.class)))
                .thenThrow(new IllegalArgumentException("Limit must be between 1 and 100"));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limit\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Limit must be between 1 and 100"));
    }

    // ─── 10. service/source failure ──────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — unexpected service failure returns safe plain 500, no stack trace")
    void search_serviceFailure_returnsSafe500() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keywords\":[\"java\"]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().string("Job search failed; please try again later."));
    }

    // ─── 11. unsafe URL never returned as navigable ──────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — unsafe URL is never present as a navigable sourceUrl")
    void search_unsafeUrlNeverNavigable() throws Exception {
        // A live job whose source could not verify a URL carries sourceUrl = null,
        // mirroring what PublicApiJobSource + JobSearchService produce.
        Job unsafe = new Job("remotive-9", "No Link", "Acme", "Remote", "No link.",
                List.of("Java"), List.of(), null, "full_time", "2026-08-27", "REMOTIVE",
                null, "PUBLIC_API", Instant.now());
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(unsafe));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.jobs[0].source").value("REMOTIVE"));
    }

    // ─── 12. frontend-compatible JSON structure ──────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/search — jobs carry the fields matches.js / jobDetails.js read")
    void search_frontendCompatibleJobFields() throws Exception {
        when(jobSearchService.search(any(JobSearchRequest.class))).thenReturn(result(mockJob()));

        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].id").value("mock-sw-001"))
                .andExpect(jsonPath("$.jobs[0].title").value("Java Developer"))
                .andExpect(jsonPath("$.jobs[0].company").value("TechNova Solutions"))
                .andExpect(jsonPath("$.jobs[0].location").value("Hyderabad, India"))
                .andExpect(jsonPath("$.jobs[0].description").value("Backend microservices."))
                .andExpect(jsonPath("$.jobs[0].requiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.jobs[0].employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.jobs[0].source").value("MOCK_SOURCE"))
                // Mock jobs keep sourceUrl null (frontend renders them non-navigable).
                .andExpect(jsonPath("$.jobs[0].sourceType").value("MOCK"));
    }
}