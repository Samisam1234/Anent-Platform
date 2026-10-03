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
        when(jobApplicationRepository.findByCandidateIdOrderByUpdatedAtDescIdDesc(1L)).thenReturn(List.of(savedApp));

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
        var stored = controller.getApplicationsByCandidate(1L, null);
        assertNotNull(stored.getBody());
        assertEquals(1, stored.getBody().size());
        var saved = stored.getBody().get(0);
        assertEquals("Software Engineer", saved.getJobTitle());
        assertEquals(88, saved.getMatchScore());
        assertEquals("Java, Spring Boot", saved.getMatchingSkills());
        
        // Verify the mock repository was called
        verify(jobApplicationRepository).findByCandidateIdOrderByUpdatedAtDescIdDesc(1L);
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

        when(jobApplicationRepository.findByCandidateIdOrderByUpdatedAtDescIdDesc(1L)).thenReturn(List.of(app1, app2));

        // Act
        var response = controller.getApplicationsByCandidate(1L, null);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
        verify(jobApplicationRepository).findByCandidateIdOrderByUpdatedAtDescIdDesc(1L);
    }

    @Test
    @DisplayName("Get applications by candidate with a status filter uses the filtered, ordered finder")
    void getApplicationsByCandidate_withStatusFiltersByStatus() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        app.setApplicationStatus(ApplicationStatus.GENERATED);
        when(jobApplicationRepository.findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(
                1L, ApplicationStatus.GENERATED)).thenReturn(List.of(app));

        // Act — case-insensitive status name, like the UI sends it
        var response = controller.getApplicationsByCandidate(1L, "generated");

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(ApplicationStatus.GENERATED, response.getBody().get(0).getApplicationStatus());
        verify(jobApplicationRepository).findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(
                1L, ApplicationStatus.GENERATED);
    }

    @Test
    @DisplayName("Get applications by candidate with an unknown status throws IllegalArgumentException (400)")
    void getApplicationsByCandidate_unknownStatus_shouldThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.getApplicationsByCandidate(1L, "NOT-A-STATUS"));
    }

    @Test
    @DisplayName("Get applications by candidate with a blank status returns all applications")
    void getApplicationsByCandidate_blankStatus_shouldReturnAll() {
        when(jobApplicationRepository.findByCandidateIdOrderByUpdatedAtDescIdDesc(1L)).thenReturn(List.of());

        var response = controller.getApplicationsByCandidate(1L, " ");

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        verify(jobApplicationRepository).findByCandidateIdOrderByUpdatedAtDescIdDesc(1L);
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
        app.setApplicationStatus(ApplicationStatus.GENERATED);
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
    @DisplayName("Approve application from a terminal status → 400 via IllegalArgumentException")
    void approveApplication_terminalStatus_shouldThrowIllegalArgument() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        app.setApplicationStatus(ApplicationStatus.REJECTED);
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));

        // Act & Assert: the controller lets the storage-service guard propagate;
        // GlobalExceptionHandler surfaces it as RFC 7807 400.
        assertThrows(IllegalArgumentException.class, () -> controller.approveApplication(1L));
        verify(jobApplicationRepository, never()).save(any());
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

        @Test
        @DisplayName("Reject application from EMAIL_SENT terminal status → 400 via IllegalArgumentException")
        void rejectApplication_terminalStatus_shouldThrowIllegalArgument() {
            // Arrange
            var app = new JobApplication();
            app.setId(1L);
            app.setCandidateId(1L);
            app.setApplicationStatus(ApplicationStatus.EMAIL_SENT);
            when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));

            // Act & Assert: storage-service guard propagates to GlobalExceptionHandler (RFC 7807 400).
            assertThrows(IllegalArgumentException.class, () -> controller.rejectApplication(1L));
            verify(jobApplicationRepository, never()).save(any());
        }

    // ─── 7. Employer handoff ─────────────────────────────

    @Test
    @DisplayName("Record handoff persists the event with no status change")
    void recordHandoff_shouldPersistEvent() {
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
        app.setApprovedAt(java.time.LocalDateTime.now().minusDays(1));
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(app)).thenReturn(app);

        var request = new JobApplicationController.HandoffRequest();
        request.setUrl("https://careers.example.com/apply");

        var response = controller.recordHandoff(1L, request);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, response.getBody().getApplicationStatus());
        assertEquals("https://careers.example.com/apply", response.getBody().getEmployerUrl());
        assertNotNull(response.getBody().getEmployerOpenedAt());
        verify(jobApplicationRepository).findById(1L);
        verify(jobApplicationRepository).save(app);
    }

    @Test
    @DisplayName("Record handoff returns 404 when application not found")
    void recordHandoff_notFound_shouldReturn404() {
        var request = new JobApplicationController.HandoffRequest();
        request.setUrl("https://careers.example.com/apply");

        var response = controller.recordHandoff(999L, request);

        assertEquals(404, response.getStatusCodeValue());
    }

    @Test
    @DisplayName("Record handoff for a non-approved application → 400 via IllegalArgumentException")
    void recordHandoff_notApproved_shouldThrowIllegalArgument() {
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        app.setApplicationStatus(ApplicationStatus.GENERATED);
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(app));

        var request = new JobApplicationController.HandoffRequest();
        request.setUrl("https://careers.example.com/apply");

        assertThrows(IllegalArgumentException.class, () -> controller.recordHandoff(1L, request));
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Record handoff with a blank URL → 400 via IllegalArgumentException")
    void recordHandoff_blankUrl_shouldThrowIllegalArgument() {
        var request = new JobApplicationController.HandoffRequest();
        request.setUrl("   ");

        assertThrows(IllegalArgumentException.class, () -> controller.recordHandoff(1L, request));
        verify(jobApplicationRepository, never()).findById(any());
    }
}