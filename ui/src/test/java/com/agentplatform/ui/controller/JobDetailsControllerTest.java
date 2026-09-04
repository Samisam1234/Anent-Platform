package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link JobDetailsController} GET /api/v1/jobs/{jobId}.
 *
 * <p>{@code @WebMvcTest} loads only the web layer; {@link JobSearchService} is a
 * Mockito mock so no source, database or LLM is touched. URL-safety guarantees
 * (mock → null, invalid/unsafe external → null, validated external → preserved) are
 * reproduced via the Job fixtures the mocked service returns from the real pipeline.</p>
 */
@WebMvcTest(controllers = {JobDetailsController.class, GlobalExceptionHandler.class})
class JobDetailsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobSearchService jobSearchService;

    private static final String BASE = "/api/v1/jobs/";

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

    @Test
    @DisplayName("GET /api/v1/jobs/{id} — existing mock job returns 200 with null sourceUrl")
    void getJob_existingMock_returns200() throws Exception {
        when(jobSearchService.findById("mock-sw-001")).thenReturn(Optional.of(mockJob()));

        mockMvc.perform(get(BASE + "mock-sw-001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.id").value("mock-sw-001"))
                .andExpect(jsonPath("$.title").value("Java Developer"))
                .andExpect(jsonPath("$.source").value("MOCK_SOURCE"))
                .andExpect(jsonPath("$.sourceType").value("MOCK"))
                .andExpect(jsonPath("$.sourceUrl").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} — existing public job returns 200 with validated external URL preserved")
    void getJob_existingPublic_returns200() throws Exception {
        when(jobSearchService.findById("remotive-2091101")).thenReturn(Optional.of(liveJob()));

        mockMvc.perform(get(BASE + "remotive-2091101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("remotive-2091101"))
                .andExpect(jsonPath("$.source").value("REMOTIVE"))
                .andExpect(jsonPath("$.sourceType").value("PUBLIC_API"))
                .andExpect(jsonPath("$.sourceUrl").value(
                        "https://remotive.com/remote-jobs/software-development/senior-react-full-stack-developer-2091101"))
                .andExpect(jsonPath("$.discoveredAt").value("2026-08-27T14:36:09Z"));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} — unknown job id returns 404 with empty body")
    void getJob_unknownId_returns404() throws Exception {
        when(jobSearchService.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} — blank id yields 404 (never a stack trace)")
    void getJob_blankId_returns404() throws Exception {
        when(jobSearchService.findById("   ")).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "%20%20%20"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} — unsafe/invalid sourceUrl is never exposed as navigable")
    void getJob_unsafeUrlNotExposed() throws Exception {
        Job unsafe = new Job("remotive-9", "No Link", "Acme", "Remote", "No link.",
                List.of("Java"), List.of(), null, "full_time", "2026-08-27", "REMOTIVE",
                null, "PUBLIC_API", Instant.now());
        when(jobSearchService.findById("remotive-9")).thenReturn(Optional.of(unsafe));

        mockMvc.perform(get(BASE + "remotive-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist())
                .andExpect(jsonPath("$.source").value("REMOTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} — response carries every field jobDetails.js renders")
    void getJob_responseHasFrontendFields() throws Exception {
        when(jobSearchService.findById("mock-sw-001")).thenReturn(Optional.of(mockJob()));

        mockMvc.perform(get(BASE + "mock-sw-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("mock-sw-001"))
                .andExpect(jsonPath("$.title").value("Java Developer"))
                .andExpect(jsonPath("$.company").value("TechNova Solutions"))
                .andExpect(jsonPath("$.location").value("Hyderabad, India"))
                .andExpect(jsonPath("$.description").value("Backend microservices."))
                .andExpect(jsonPath("$.requiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.requiredSkills[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.preferredSkills[0]").value("Docker"))
                .andExpect(jsonPath("$.experienceRequirement").value("Fresher / 0-1 years"))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.postingDate").value("2026-08-25"))
                .andExpect(jsonPath("$.source").value("MOCK_SOURCE"))
                .andExpect(jsonPath("$.sourceType").value("MOCK"))
                // Mock jobs must stay non-navigable frontend-side (isMock => internal).
                .andExpect(jsonPath("$.sourceUrl").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} — unexpected service failure yields a safe plain 500 with no stack trace")
    void getJob_noInternalLeakage() throws Exception {
        when(jobSearchService.findById("explode"))
                .thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get(BASE + "explode"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Job lookup failed; please try again later."));
    }
}