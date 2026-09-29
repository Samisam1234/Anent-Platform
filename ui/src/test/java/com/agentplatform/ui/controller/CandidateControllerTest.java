package com.agentplatform.ui.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.exception.CandidateProfileNotFoundException;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CandidateController.class, GlobalExceptionHandler.class})
class CandidateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandidateProfilePersistenceService persistenceService;

    private static CandidateProfile profile() {
        return new CandidateProfile(
                "Jane Doe", "jane@example.com", "555-1234", "Berlin",
                List.of("B.Sc. Computer Science"),
                List.of("Java", "Spring"),
                List.of("Java Developer at Acme (2020-2023)"),
                List.of(), List.of(), List.of(),
                List.of("Java", "Spring"), List.of(),
                List.of("Software Engineer", "Backend Developer"),
                List.of("Berlin", "Remote"),
                List.of(), List.of());
    }

    @Test
    @DisplayName("GET /api/v1/candidate/{id} returns the allowlisted kit projection only")
    void returnsAllowlist() throws Exception {
        CandidateProfileEntity entity = mock(CandidateProfileEntity.class);
        when(entity.getId()).thenReturn(3L);
        when(entity.toDomain()).thenReturn(profile());
        when(persistenceService.getByIdOrThrow(3L)).thenReturn(entity);

        mockMvc.perform(get("/api/v1/candidate/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(3))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.phone").value("555-1234"))
                .andExpect(jsonPath("$.location").value("Berlin"))
                .andExpect(jsonPath("$.preferredLocation").value("Berlin"))
                .andExpect(jsonPath("$.headline").value("Software Engineer, Backend Developer"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.skills[1]").value("Spring"))
                .andExpect(jsonPath("$.education").doesNotExist())
                .andExpect(jsonPath("$.experience").doesNotExist())
                .andExpect(jsonPath("$.projects").doesNotExist())
                .andExpect(jsonPath("$.certifications").doesNotExist())
                .andExpect(jsonPath("$.resumeEvidence").doesNotExist())
                .andExpect(jsonPath("$.careerTrackEvidence").doesNotExist());
    }

    @Test
    @DisplayName("Missing candidate maps to RFC 7807 404 via GlobalExceptionHandler")
    void missingCandidateIs404() throws Exception {
        when(persistenceService.getByIdOrThrow(999L))
                .thenThrow(new CandidateProfileNotFoundException(999L));

        mockMvc.perform(get("/api/v1/candidate/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Candidate Profile Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("Non-numeric candidate id maps to 400 Bad Request")
    void nonNumericIdIs400() throws Exception {
        mockMvc.perform(get("/api/v1/candidate/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400));
    }
}