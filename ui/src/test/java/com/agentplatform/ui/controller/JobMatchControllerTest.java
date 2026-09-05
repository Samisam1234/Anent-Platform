package com.agentplatform.ui.controller;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.matching.JobMatch;
import com.agentplatform.orchestrator.matching.JobMatchRequest;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingService;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice tests for {@link JobMatchController}.
 *
 * <p>{@code @WebMvcTest} loads only the web layer; {@link JobMatchingService} is replaced with
 * a Mockito mock so matching is fully deterministic and no database or LLM is touched.</p>
 */
@WebMvcTest(controllers = {JobMatchController.class, GlobalExceptionHandler.class})
class JobMatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobMatchingService jobMatchingService;

    private static final String MATCH_URL = "/api/v1/jobs/match";

    private JobMatchResult sampleResult() {
        Job job = new Job("mock-sw-001", "Java Developer", "TechNova Solutions", "Hyderabad",
                "Backend microservices.", List.of("Java", "Spring Boot", "PostgreSQL", "Git"),
                List.of("Docker"), "Fresher / 0-1 years", "Full-time", null, "mock", null, "MOCK", null);

        JobMatch match = new JobMatch(
                job, 87, RecommendationLevel.STRONG_MATCH,
                List.of("Java", "Spring Boot", "PostgreSQL"), List.of("Git"),
                List.of("Docker"), List.of(),
                true, true, ExperienceMatchLevel.STRONG_MATCH, CareerTrack.SOFTWARE,
                "This job is a strong match...",
                List.of("Matches key requirements: Java, Spring Boot and PostgreSQL."),
                List.of("Missing required: Git."),
                0.75, 1.0, 1.0, 1.0, 1.0, 1.0
        );

        return new JobMatchResult(7L, "Alice", 1, List.of(match), "mock", false,
                "Matched development mock job catalog against candidate profile.");
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/match — stored profile returns ranked matches as a clean DTO")
    void match_storedProfile_returnsRankedMatches() throws Exception {
        when(jobMatchingService.matchJobs(any(JobMatchRequest.class))).thenReturn(sampleResult());

        mockMvc.perform(post(MATCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateProfileId\":7,\"keywords\":[\"java\"],\"limit\":20}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.candidateProfileId").value(7))
                .andExpect(jsonPath("$.candidateName").value("Alice"))
                .andExpect(jsonPath("$.totalJobs").value(1))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.matches[0].job.title").value("Java Developer"))
                .andExpect(jsonPath("$.matches[0].matchScore").value(87))
                .andExpect(jsonPath("$.matches[0].recommendation").value("STRONG_MATCH"))
                .andExpect(jsonPath("$.matches[0].educationScore").value(1.0))
                .andExpect(jsonPath("$.matches[0].strengths[0]").value("Matches key requirements: Java, Spring Boot and PostgreSQL."))
                .andExpect(jsonPath("$.matches[0].concerns[0]").value("Missing required: Git."));
    }

    @Test
    @DisplayName("POST /api/v1/jobs/match — request DTO is converted to the domain request")
    void match_passesDomainRequestToService() throws Exception {
        when(jobMatchingService.matchJobs(any(JobMatchRequest.class))).thenReturn(sampleResult());

        mockMvc.perform(post(MATCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateProfileId\":7,\"keywords\":[\"java\"],\"location\":\"Hyderabad\",\"limit\":10,\"minScore\":60}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JobMatchRequest> captor = ArgumentCaptor.forClass(JobMatchRequest.class);
        verify(jobMatchingService).matchJobs(captor.capture());

        JobMatchRequest request = captor.getValue();
        assertEquals(7L, request.candidateProfileId());
        assertEquals(List.of("java"), request.keywords());
        assertEquals("Hyderabad", request.location());
        assertEquals(10, request.limit());
        assertEquals(60, request.minScore());
    }

    @Test
    @DisplayName("POST /api/v1/jobs/match — inline profile and jobs are accepted")
    void match_inlineProfileAndJobs_accepted() throws Exception {
        when(jobMatchingService.matchJobs(any(JobMatchRequest.class))).thenReturn(sampleResult());

        mockMvc.perform(post(MATCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"name\":\"Alice\",\"softwareSkills\":[\"Java\"]}," +
                                "\"jobs\":[{\"id\":\"j1\",\"title\":\"Java Developer\",\"company\":\"TechNova\"," +
                                "\"location\":\"Hyderabad\",\"requiredSkills\":[\"Java\"]}]}"))
                .andExpect(status().isOk());
    }

    // ─── Error mapping ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/jobs/match — missing candidate profile maps to 404 ProblemDetail")
    void match_missingProfile_returns404ProblemDetail() throws Exception {
        when(jobMatchingService.matchJobs(any(JobMatchRequest.class)))
                .thenThrow(new CandidateProfileNotFoundException(999L));

        mockMvc.perform(post(MATCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateProfileId\":999,\"keywords\":[\"java\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Candidate Profile Not Found"))
                .andExpect(jsonPath("$.detail").value("Candidate profile with ID 999 was not found. Please upload a resume first."));
    }

    @Test
    @DisplayName("POST /api/v1/jobs/match — Check Match single-job payload (stored profile + jobs) is forwarded")
    void match_checkMatchSingleJob_forwarded() throws Exception {
        when(jobMatchingService.matchJobs(any(JobMatchRequest.class))).thenReturn(sampleResult());

        mockMvc.perform(post(MATCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateProfileId\":7,\"limit\":1," +
                                "\"jobs\":[{\"id\":\"mock-sw-001\",\"title\":\"Java Developer\",\"company\":\"TechNova Solutions\"," +
                                "\"location\":\"Hyderabad\",\"requiredSkills\":[\"Java\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].job.title").value("Java Developer"));

        ArgumentCaptor<JobMatchRequest> captor = ArgumentCaptor.forClass(JobMatchRequest.class);
        verify(jobMatchingService).matchJobs(captor.capture());

        JobMatchRequest request = captor.getValue();
        assertEquals(7L, request.candidateProfileId());
        assertEquals(1, request.jobs().size());
        assertEquals("mock-sw-001", request.jobs().get(0).id());
        assertEquals(1, request.limit());
    }

    @Test
    @DisplayName("POST /api/v1/jobs/match — invalid minScore maps to 400 Bad Request")
    void match_invalidMinScore_returns400() throws Exception {
        mockMvc.perform(post(MATCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateProfileId\":7,\"minScore\":101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/jobs/match — invalid limit maps to 400 Bad Request")
    void match_invalidLimit_returns400() throws Exception {
        mockMvc.perform(post(MATCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateProfileId\":7,\"limit\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }
}