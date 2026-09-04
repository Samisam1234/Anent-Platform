package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private JobApplicationController controller;

    @BeforeEach
    void setUp() {
        controller = new JobApplicationController(preparationService);
        try {
            Field field = JobApplicationController.class.getDeclaredField("applicationStore");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, JobApplication> testStore = new ConcurrentHashMap<>();
            field.set(controller, testStore);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        putAppInStore(1L, app);

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
        putAppInStore(1L, app1);

        var app2 = new JobApplication();
        app2.setId(2L);
        app2.setCandidateId(1L);
        putAppInStore(2L, app2);

        // Act
        var response = controller.getApplicationsByCandidate(1L);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());
    }

    // ─── 4. Update application ─────────────────────────────

    @Test
    @DisplayName("Update application modifies the fields")
    void updateApplication_modifiesFields() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        putAppInStore(1L, app);

        var updates = new JobApplicationController.ApplicationUpdates();
        updates.setCoverLetter("New cover letter");
        updates.setProfessionalSummary("New summary");

        // Act
        var response = controller.updateApplication(1L, updates);

        // Assert
        assertNotNull(response.getBody());
        assertEquals("New cover letter", response.getBody().getCoverLetter());
        assertEquals("New summary", response.getBody().getGeneratedResumeSummary());
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

    // ─── 5. Approve application ────────────────────────────

    @Test
    @DisplayName("Approve application sets status to APPROVED_FOR_APPLICATION")
    void approveApplication_setsStatus() {
        // Arrange
        var app = new JobApplication();
        app.setId(1L);
        app.setCandidateId(1L);
        putAppInStore(1L, app);

        // Act
        var response = controller.approveApplication(1L);

        // Assert
        assertNotNull(response.getBody());
        assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION.name(),
                response.getBody().getApplicationStatus().name());
        assertNotNull(response.getBody().getApprovedAt());
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
        putAppInStore(1L, app);

        // Act
        var response = controller.rejectApplication(1L);

        // Assert
        assertNotNull(response.getBody());
        assertEquals(ApplicationStatus.REJECTED.name(),
                response.getBody().getApplicationStatus().name());
    }

    @Test
    @DisplayName("Reject application returns 404 when not found")
    void rejectApplication_notFound_shouldReturn404() {
        // Act
        var response = controller.rejectApplication(999L);

        // Assert
        assertEquals(404, response.getStatusCodeValue());
    }

    // ─── Helper ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void putAppInStore(Long id, JobApplication app) {
        try {
            Field field = JobApplicationController.class.getDeclaredField("applicationStore");
            field.setAccessible(true);
            Map<Long, JobApplication> store = (Map<Long, JobApplication>) field.get(controller);
            store.put(id, app);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}