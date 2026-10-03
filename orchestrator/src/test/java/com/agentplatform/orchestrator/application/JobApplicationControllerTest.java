package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JobApplicationController}.
 * No Spring container, no database — pure JUnit 5 + Mockito, matching the project style.
 */
@ExtendWith(MockitoExtension.class)
class JobApplicationControllerTest {

    @Mock
    private JobApplicationPreparationService preparationService;

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    private JobApplicationController controller;

    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        controller = new JobApplicationController(preparationService, null, null, jobApplicationRepository);
        // Configure mock repository to assign IDs when saving - lenient to avoid unnecessary stubbing errors
        lenient().when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication app = inv.getArgument(0);
            if (app.getId() == null) {
                app.setId(nextId.getAndIncrement());
            }
            return app;
        });
    }

    // ─── 1. Prepare application ────────────────────────────

    @Test
    @DisplayName("Prepare application forwards request to preparation service")
    void prepareApplication_forwardsToService() {
        // Arrange
        var request = new JobApplicationController.ApplicationPrepareRequest();
        request.setCandidateId(1L);
        request.setJobId("job-123");
        request.setJobTitle("Software Engineer");
        request.setCompany("Test Company");
        request.setLocation("Hyderabad");
        request.setCustomInstructions("Focus on Java");

        var expectedResult = new ApplicationPreparationResult();
        expectedResult.setCandidateId(1L);
        expectedResult.setJobId("job-123");
        expectedResult.setJobTitle("Software Engineer");
        expectedResult.setCompany("Test Company");
        expectedResult.setTailoredProfessionalSummary("Summary");
        expectedResult.setCoverLetter("Cover");
        expectedResult.setSuggestedAnswers("Answer 1 || Answer 2");
        expectedResult.setCandidateStrengths(java.util.List.of("Java"));
        expectedResult.setMatchingSkills(java.util.List.of("Java", "Spring Boot"));
        expectedResult.setMissingSkills(java.util.List.of("Kafka"));
        expectedResult.setResumeHighlights(java.util.List.of("REST APIs"));
        expectedResult.setMatchScore(88);
        expectedResult.setRecommendation("STRONG_MATCH");
        when(preparationService.prepareApplication(
                eq(1L), eq("job-123"), eq("Software Engineer"), eq("Test Company"),
                eq("Hyderabad"), eq("Focus on Java"), eq(false)))
                .thenReturn(expectedResult);

        // The mock repository should return the stored application when queried by candidate
        var savedApp = new JobApplication();
        savedApp.setId(1L);
        savedApp.setCandidateId(1L);
        savedApp.setJobTitle("Software Engineer");
        savedApp.setCompany("Test Company");
        savedApp.setMatchScore(88);
        savedApp.setMatchingSkills("Java, Spring Boot");
        when(jobApplicationRepository.findByCandidateId(1L)).thenReturn(List.of(savedApp));

        // Act
        var response = controller.prepareApplication(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        // The result now carries a stored application id, proving persistence.
        assertNotNull(response.getBody().getApplicationId());
        assertEquals(88, response.getBody().getMatchScore());
        verify(preparationService).prepareApplication(
                eq(1L), eq("job-123"), eq("Software Engineer"), eq("Test Company"),
                eq("Hyderabad"), eq("Focus on Java"), eq(false));

        // The prepared application was persisted and is retrievable by candidate.
        var stored = controller.getApplicationsByCandidate(1L);
        assertNotNull(stored.getBody());
        assertEquals(1, stored.getBody().size());
        var saved = stored.getBody().get(0);
        assertEquals("Software Engineer", saved.getJobTitle());
        assertEquals(88, saved.getMatchScore());
        assertEquals("Java, Spring Boot", saved.getMatchingSkills());
        
        // Verify the mock repository was called
        verify(jobApplicationRepository).findByCandidateId(1L);
    }

    // ─── 2. Get application by ID ──────────────────────────

    @Test
    @DisplayName("Get application returns 404 when not found")
    void getApplication_notFound_shouldReturn404() {
        // Act
        var response = controller.getApplication(999L);

        // Assert
        assertNotNull(response);
        assertEquals(404, response.getStatusCodeValue());
    }

    @Test
    @DisplayName("Get application returns 200 when found")
    void getApplication_found_shouldReturn200() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));

        // Act
        var response = controller.getApplication(1L);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
    }

    // ─── 3. Get applications by candidate ──────────────────

    @Test
    @DisplayName("Get applications by candidate returns matching applications")
    void getApplicationsByCandidate_returnsMatches() {
        // Arrange
        var app1 = new JobApplication();
        app1.setId(1L);
        app1.setCandidateId(1L);

        var app2 = new JobApplication();
        app2.setId(2L);
        app2.setCandidateId(1L);

        when(jobApplicationRepository.findByCandidateId(1L)).thenReturn(List.of(app1, app2));

        // Act
        var response = controller.getApplicationsByCandidate(1L);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
        verify(jobApplicationRepository).findByCandidateId(1L);
    }

    // ─── 4. Update application ─────────────────────────────

    @Test
    @DisplayName("Update application modifies the fields")
    void updateApplication_modifiesFields() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(app)).thenReturn(app);

        var updates = new JobApplicationController.ApplicationUpdates();
        updates.setCoverLetter("New cover letter");
        updates.setProfessionalSummary("New summary");

        // Act
        var response = controller.updateApplication(1L, updates);

        // Assert
        assertNotNull(response.getBody());
        assertEquals("New cover letter", response.getBody().getCoverLetter());
        assertEquals("New summary", response.getBody().getGeneratedResumeSummary());
        verify(jobApplicationRepository).findById(1L);
        verify(jobApplicationRepository).save(app);
    }

    @Test
    @DisplayName("Update application returns 404 when not found")
    void updateApplication_notFound_shouldReturn404() {
        // Act
        var updates = new JobApplicationController.ApplicationUpdates();
        var response = controller.updateApplication(999L, updates);

        // Assert
        assertEquals(404, response.getStatusCodeValue());
    }

    // ─── 5. Approve application ─────────────────────────────

    @Test
    @DisplayName("Approve application sets status to APPROVED_FOR_APPLICATION")
    void approveApplication_setsStatus() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(app)).thenReturn(app);

        // Act
        var response = controller.approveApplication(1L);

        // Assert
        assertNotNull(response.getBody());
        assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION.name(),
                response.getBody().getApplicationStatus().name());
        assertNotNull(response.getBody().getApprovedAt());
        verify(jobApplicationRepository).findById(1L);
        verify(jobApplicationRepository).save(app);
    }

    @Test
    @DisplayName("Approve application returns 404 when not found")
    void approveApplication_notFound_shouldReturn404() {
        // Act
        var response = controller.approveApplication(999L);

        // Assert
        assertEquals(404, response.getStatusCodeValue());
    }

    // ─── 6. Reject application ─────────────────────────────

    @Test
    @DisplayName("Reject application sets status to REJECTED")
    void rejectApplication_setsStatus() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        app.setApplicationStatus(ApplicationStatus.GENERATED);
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(app)).thenReturn(app);

        // Act
        var response = controller.rejectApplication(1L);

        // Assert
        assertNotNull(response.getBody());
        assertEquals(ApplicationStatus.REJECTED.name(),
                response.getBody().getApplicationStatus().name());
        verify(jobApplicationRepository).findById(1L);
        verify(jobApplicationRepository).save(app);
    }

    @Test
    @DisplayName("Reject application returns 404 when not found")
    void rejectApplication_notFound_shouldReturn404() {
        // Act
        var response = controller.rejectApplication(999L);

        // Assert
        assertEquals(404, response.getStatusCodeValue());
    }
}