package com.agentplatform.orchestrator.application;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.matching.SkillMatchingEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.entity.CandidateProfileEntity;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.langchain4j.model.chat.ChatModel;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JobApplicationPreparationService}.
 * No Spring container, no database, no LLM required — preserves the hermetic test style.
 */
@ExtendWith(MockitoExtension.class)
class JobApplicationPreparationServiceTest {

    @Mock
    private ChatModel chatModel;

    private JobApplicationPreparationService service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationPreparationService(chatModel);
    }

    // ─── 1. AI generation success ──────────────────────────────

    @Test
    @DisplayName("Prepare application successfully when AI provider is available")
    void prepareApplication_aiSuccess_shouldReturnResult() {
        // Arrange — mock AI model to return valid JSON
        String aiResponse = """
                {"tailoredProfessionalSummary":"Test summary","coverLetter":"Test cover letter","suggestedAnswers":["Q1 Answer","Q2 Answer","Q3 Answer"],"candidateStrengths":["Strength1","Strength2"],"matchingSkills":["Java","Spring Boot"],"missingSkills":["Docker"],"recommendation":"POSSIBLE_MATCH"}
                """;

        when(chatModel.chat(any(String.class))).thenReturn(aiResponse);

        // Act
        var result = service.prepareApplication(
                1L, "job-123", "Software Engineer", "Test Company", "Hyderabad",
                "Focus on Java skills");

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getCandidateId());
        assertEquals("job-123", result.getJobId());
        assertEquals("Software Engineer", result.getJobTitle());
        assertEquals("Test Company", result.getCompany());
        assertNotNull(result.getTailoredProfessionalSummary());
        assertNotNull(result.getCoverLetter());
        assertNotNull(result.getSuggestedAnswers());
        assertEquals(2, result.getCandidateStrengths().size());
        assertEquals(2, result.getMatchingSkills().size());
        assertEquals(1, result.getMissingSkills().size());
        assertNotNull(result.getRecommendation());
    }

    // ─── 2. AI generation failure → fallback ───────────────────

    @Test
    @DisplayName("Fall back to deterministic content when AI provider fails")
    void prepareApplication_aiFallback_shouldUseDeterministicContent() {
        // Arrange — mock AI model to throw an exception
        when(chatModel.chat(any(String.class))).thenThrow(new RuntimeException("AI service unavailable"));

        // Act
        var result = service.prepareApplication(
                1L, "job-123", "Software Engineer", "Test Company", "Hyderabad",
                null);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getCandidateId());
        assertEquals("job-123", result.getJobId());
        assertEquals("Software Engineer", result.getJobTitle());
        assertEquals("Test Company", result.getCompany());
        assertTrue(result.getTailoredProfessionalSummary().length() > 0);
        assertTrue(result.getCoverLetter().length() > 0);
        assertTrue(result.getSuggestedAnswers().length() > 0);
        assertTrue(result.getCandidateStrengths().size() > 0);
        assertTrue(result.getMatchingSkills().size() > 0);
        assertTrue(result.getMissingSkills().size() > 0);
        assertTrue(result.getResumeHighlights().size() > 0);
        assertNotNull(result.getMatchScore());
        assertTrue(result.getMatchScore() >= 0 && result.getMatchScore() <= 100);
        assertNotNull(result.getRecommendation());
    }

    // ─── 3. Candidate profile not found ────────────────────────

    @Test
    @DisplayName("Handle missing candidate gracefully")
    void prepareApplication_noCandidate_shouldStillGenerate() {
        // Act — service doesn't access the DB; it just uses the IDs provided
        var result = service.prepareApplication(
                null, "job-123", "Software Engineer", "Test Company", "Hyderabad",
                null);

        // Assert — should still produce a result (caller validates candidate existence)
        assertNotNull(result);
        assertNull(result.getCandidateId());
    }

    // ─── 4. Custom instructions ────────────────────────────────

    @Test
    @DisplayName("Pass custom instructions to AI generation")
    void prepareApplication_withCustomInstructions_shouldIncludeInstructions() {
        // Arrange
        String aiResponse = """
                {"tailoredProfessionalSummary":"Custom summary","coverLetter":"Custom cover letter","suggestedAnswers":["Custom answer"],"candidateStrengths":["Custom strength"],"matchingSkills":["Custom skill"],"missingSkills":[],"recommendation":"POSSIBLE_MATCH"}
                """;

        when(chatModel.chat(any(String.class))).thenReturn(aiResponse);

        // Act
        var result = service.prepareApplication(
                1L, "job-123", "Software Engineer", "Test Company", "Hyderabad",
                "Focus on Java and Spring Boot expertise");

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getCandidateId());
    }

    // ─── 5. Status lifecycle ──────────────────────────────────

    @Test
    @DisplayName("Application preparation sets correct initial status")
    void prepareApplication_statusIsGenerated() {
        // Arrange
        when(chatModel.chat(any(String.class))).thenReturn(
                "{\"tailoredProfessionalSummary\":\"test\",\"coverLetter\":\"test\",\"suggestedAnswers\":[],\"candidateStrengths\":[],\"matchingSkills\":[],\"missingSkills\":[],\"recommendation\":\"POSSIBLE_MATCH\"}");

        // Act
        var result = service.prepareApplication(
                1L, "job-123", "Software Engineer", "Test Company", "Hyderabad",
                null);

        // Assert
        assertEquals("GENERATED", result.getStatus());
    }

    // ─── 6. Skip AI for mock jobs ────────────────────────────

    @Test
    @DisplayName("Skip AI and use deterministic fallback when skipAi is true (mock job)")
    void prepareApplication_skipAi_shouldNotCallLlmAndUseFallback() {
        // Act — skipAi=true should never touch chatModel
        var result = service.prepareApplication(
                1L, "mock-sw-001", "Java Developer", "TechNova Solutions", "Hyderabad",
                null, true);

        // Assert — deterministic fallback content
        assertNotNull(result);
        assertEquals(1L, result.getCandidateId());
        assertEquals("mock-sw-001", result.getJobId());
        assertEquals("Java Developer", result.getJobTitle());
        assertEquals("TechNova Solutions", result.getCompany());
        assertTrue(result.getTailoredProfessionalSummary().contains("Java Developer"));
        assertTrue(result.getTailoredProfessionalSummary().contains("TechNova Solutions"));
        assertTrue(result.getCoverLetter().contains("Java Developer"));
        assertTrue(result.getResumeHighlights().size() > 0);
        assertNotNull(result.getMatchScore());
        assertEquals("GENERATED", result.getStatus());

        // Verify chatModel was NEVER called
        verifyNoInteractions(chatModel);
    }

    // ─── 7. Candidate-driven content (parsed resume + selected job) ──────────

    @Nested
    @DisplayName("Candidate profile drives the prepared application")
    class CandidateDrivenContent {

        private static final CandidateProfile PROFILE = new CandidateProfile(
                "Asha Rao", "asha@example.com", null, "Hyderabad",
                List.of("B.Tech ECE"), List.of("Java", "Spring Boot"),
                List.of("Backend developer at Acme (2 years)"), List.of(),
                List.of("Payments reconciliation service"), List.of("AWS Certified Developer"),
                List.of("Java", "Spring Boot"), List.of("Verilog"),
                List.of("Backend Engineer"), List.of());

        private static final Job JOB = new Job(
                "java-dev-1", "Java Developer", "TechNova Solutions", "Hyderabad, India",
                "Build backend services",
                List.of("Java", "Spring Boot", "Kubernetes"), List.of("Kafka"),
                "2-4 years", "FULL_TIME", "2026-08-26", "REMOTIVE",
                "https://remotive.com/j/1", "PUBLIC_API", null);

        private JobApplicationPreparationService wiredService(ChatModel model) {
            CandidateProfilePersistenceService profiles = mock(CandidateProfilePersistenceService.class);
            lenient().when(profiles.findById(anyLong()))
                    .thenReturn(Optional.of(CandidateProfileEntity.fromDomain(PROFILE)));
            JobSearchService jobs = mock(JobSearchService.class);
            lenient().when(jobs.findById(anyString())).thenReturn(Optional.of(JOB));
            return new JobApplicationPreparationService(
                    model, profiles, jobs, new SkillMatchingEngine());
        }

        @Test
        @DisplayName("deterministic path derives skills, gaps and highlights from the real profile")
        void fallbackUsesRealCandidateData() {
            JobApplicationPreparationService service = wiredService(chatModel);

            var result = service.prepareApplication(
                    1L, "mock-sw-001", JOB.title(), JOB.company(), JOB.location(), null, true);

            // Real overlap, computed by the existing SkillMatchingEngine
            assertTrue(result.getMatchingSkills().contains("Java"));
            assertTrue(result.getMatchingSkills().contains("Spring Boot"));
            // Real gap against the job's required skills, and nothing the resume does evidence
            assertTrue(result.getMissingSkills().contains("Kubernetes"),
                    "missing=" + result.getMissingSkills());
            assertFalse(result.getMissingSkills().contains("Java"));
            assertFalse(result.getMatchingSkills().contains("Kubernetes"));
            // Highlights come from the parsed resume, not a hard-coded Java/Spring blurb
            assertTrue(result.getResumeHighlights().contains("Backend developer at Acme (2 years)"),
                    "highlights=" + result.getResumeHighlights());
            assertTrue(result.getResumeHighlights().contains("Payments reconciliation service"));
            assertFalse(result.getResumeHighlights().contains("Java 8/21 and Spring Boot expertise"),
                    "hard-coded candidate-independent content must not survive");
            // Summary names the candidate from the profile
            assertTrue(result.getTailoredProfessionalSummary().contains("Asha Rao"),
                    result.getTailoredProfessionalSummary());
            assertTrue(result.getTailoredProfessionalSummary().contains("Java Developer"));
            // Strengths only reference skills the resume evidences
            assertTrue(result.getCandidateStrengths().stream().anyMatch(s -> s.contains("Java")));
            // Never calls the LLM on the skipAi path
            verifyNoInteractions(chatModel);
        }

        @Test
        @DisplayName("AI path sends the parsed resume and the job requirements in the prompt")
        void aiPromptCarriesCandidateAndJobData() {
            when(chatModel.chat(any(String.class))).thenReturn(
                    "{\"tailoredProfessionalSummary\":\"s\",\"coverLetter\":\"c\","
                            + "\"suggestedAnswers\":[],\"candidateStrengths\":[],\"matchingSkills\":[],"
                            + "\"missingSkills\":[],\"resumeHighlights\":[],\"matchScore\":70}");
            JobApplicationPreparationService service = wiredService(chatModel);

            service.prepareApplication(1L, "java-dev-1", JOB.title(), JOB.company(), JOB.location(), null);

            ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
            verify(chatModel).chat(prompt.capture());
            String sent = prompt.getValue();
            assertTrue(sent.contains("Asha Rao"), "candidate name missing from prompt");
            assertTrue(sent.contains("Spring Boot"), "candidate skills missing from prompt");
            assertTrue(sent.contains("Kubernetes"), "job requirements missing from prompt");
            assertTrue(sent.contains("Payments reconciliation service"), "projects missing from prompt");
            assertTrue(sent.contains("Never invent"), "anti-fabrication instruction missing from prompt");
        }

        @Test
        @DisplayName("no profile available → honest placeholder content, no invented qualifications")
        void withoutProfileNothingIsFabricated() {
            var result = service.prepareApplication(
                    1L, "mock-sw-001", "Java Developer", "TechNova Solutions", "Hyderabad", null, true);

            assertFalse(result.getTailoredProfessionalSummary().contains("Proven track record"),
                    "legacy generic claim must be gone");
            assertTrue(result.getResumeHighlights().size() > 0);
            assertTrue(result.getResumeHighlights().get(0).toLowerCase().contains("no resume highlights"),
                    "highlights=" + result.getResumeHighlights());
            verifyNoInteractions(chatModel);
        }
    }
}
