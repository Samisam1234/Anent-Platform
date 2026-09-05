package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Deterministic unit tests for {@link ApplicationStorageService}.
 */
class ApplicationStorageServiceTest {

    @Mock
    private JobApplicationRepository repository;

    private ApplicationStorageService storage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        storage = new ApplicationStorageService(repository);
    }

    private JobApplication newApp() {
        JobApplication app = new JobApplication(1L, "job-123", "Software Engineer", "Test Corp", "Hyderabad");
        app.setCoverLetter("Original cover letter");
        app.setGeneratedResumeSummary("Original summary");
        app.setApplicationAnswers("Original answers");
        app.setApplicationStatus(ApplicationStatus.GENERATED);
        return app;
    }

    // ─── Store & ID assignment ────────────────────────────────────────────────
    @Nested
    @DisplayName("Store and ID assignment")
    class StoreTests {

        @Test
        @DisplayName("store() saves application and assigns ID from repository")
        void storeAssignsId() {
            JobApplication app1 = newApp();
            JobApplication app2 = newApp();

            when(repository.save(app1)).thenAnswer(inv -> {
                JobApplication saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(repository.save(app2)).thenAnswer(inv -> {
                JobApplication saved = inv.getArgument(0);
                saved.setId(2L);
                return saved;
            });

            JobApplication stored1 = storage.store(app1);
            JobApplication stored2 = storage.store(app2);

            assertEquals(1L, stored1.getId());
            assertEquals(2L, stored2.getId());
            verify(repository, times(2)).save(any());
        }

        @Test
        @DisplayName("store() sets createdAt and updatedAt if not present")
        void storeSetsTimestamps() {
            JobApplication app = new JobApplication(1L, "job-1", "Title", "Company", "Location");
            app.setCreatedAt(null);
            app.setUpdatedAt(null);

            when(repository.save(app)).thenAnswer(inv -> {
                JobApplication saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            JobApplication stored = storage.store(app);

            assertNotNull(stored.getCreatedAt());
            assertNotNull(stored.getUpdatedAt());
            verify(repository).save(app);
        }

        @Test
        @DisplayName("store() preserves createdAt but updates updatedAt to now")
        void storePreservesCreatedAtUpdatesUpdatedAt() {
            LocalDateTime created = LocalDateTime.now().minusDays(1);
            LocalDateTime oldUpdated = LocalDateTime.now().minusHours(1);
            JobApplication app = newApp();
            app.setCreatedAt(created);
            app.setUpdatedAt(oldUpdated);

            when(repository.save(app)).thenAnswer(inv -> {
                JobApplication saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            JobApplication stored = storage.store(app);

            // createdAt should be preserved
            assertEquals(created, stored.getCreatedAt());
            // updatedAt should be set to now (within a small tolerance)
            assertNotNull(stored.getUpdatedAt());
            assertTrue(stored.getUpdatedAt().isAfter(oldUpdated) || stored.getUpdatedAt().equals(oldUpdated));
            verify(repository).save(app);
        }
    }

    // ─── Find by ID ───────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Find by ID")
    class FindByIdTests {

        @Test
        @DisplayName("findById() returns application from repository")
        void findByIdReturnsStored() {
            JobApplication app = newApp();
            app.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            Optional<JobApplication> found = storage.findById(1L);

            assertTrue(found.isPresent());
            assertEquals(app.getId(), found.get().getId());
            verify(repository).findById(1L);
        }

        @Test
        @DisplayName("findById() returns empty for non-existent ID")
        void findByIdNonExistent() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<JobApplication> found = storage.findById(999L);
            assertFalse(found.isPresent());
            verify(repository).findById(999L);
        }
    }

    // ─── Find by Candidate ID ─────────────────────────────────────────────────
    @Nested
    @DisplayName("Find by candidate ID")
    class FindByCandidateIdTests {

        @Test
        @DisplayName("findByCandidateId() returns all applications for candidate")
        void findByCandidateIdReturnsMatches() {
            JobApplication app1 = newApp();
            app1.setId(1L);
            JobApplication app2 = newApp();
            app2.setId(2L);

            JobApplication app3 = new JobApplication(2L, "job-456", "Data Scientist", "Other Corp", "Bangalore");
            app3.setApplicationStatus(ApplicationStatus.GENERATED);
            app3.setId(3L);

            when(repository.findByCandidateId(1L)).thenReturn(List.of(app1, app2));
            when(repository.findByCandidateId(2L)).thenReturn(List.of(app3));

            List<JobApplication> apps = storage.findByCandidateId(1L);

            assertEquals(2, apps.size());
            assertTrue(apps.stream().allMatch(a -> a.getCandidateId().equals(1L)));
            verify(repository).findByCandidateId(1L);
        }

        @Test
        @DisplayName("findByCandidateId() returns empty list for unknown candidate")
        void findByCandidateIdUnknown() {
            when(repository.findByCandidateId(999L)).thenReturn(List.of());

            List<JobApplication> apps = storage.findByCandidateId(999L);
            assertTrue(apps.isEmpty());
            verify(repository).findByCandidateId(999L);
        }
    }

    // ─── Update ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Update application")
    class UpdateTests {

        @Test
        @DisplayName("update() modifies editable fields and updates timestamp")
        void updateModifiesFields() {
            JobApplication app = newApp();
            app.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            ApplicationStorageService.ApplicationUpdates updates = new ApplicationStorageService.ApplicationUpdates();
            updates.setCoverLetter("New cover letter");
            updates.setProfessionalSummary("New summary");
            updates.setApplicationAnswers("New answers");

            Optional<JobApplication> updated = storage.update(1L, updates);

            assertTrue(updated.isPresent());
            assertEquals("New cover letter", updated.get().getCoverLetter());
            assertEquals("New summary", updated.get().getGeneratedResumeSummary());
            assertEquals("New answers", updated.get().getApplicationAnswers());
            verify(repository).findById(1L);
            verify(repository).save(app);
        }

        @Test
        @DisplayName("update() with partial fields only changes provided fields")
        void updatePartialFields() {
            JobApplication app = newApp();
            app.setId(1L);
            String originalCover = app.getCoverLetter();
            String originalAnswers = app.getApplicationAnswers();
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            ApplicationStorageService.ApplicationUpdates updates = new ApplicationStorageService.ApplicationUpdates();
            updates.setProfessionalSummary("Only summary changed");

            Optional<JobApplication> updated = storage.update(1L, updates);

            assertTrue(updated.isPresent());
            assertEquals(originalCover, updated.get().getCoverLetter());
            assertEquals("Only summary changed", updated.get().getGeneratedResumeSummary());
            assertEquals(originalAnswers, updated.get().getApplicationAnswers());
            verify(repository).save(app);
        }

        @Test
        @DisplayName("update() returns empty for non-existent application")
        void updateNonExistent() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            ApplicationStorageService.ApplicationUpdates updates = new ApplicationStorageService.ApplicationUpdates();
            updates.setCoverLetter("New");

            Optional<JobApplication> updated = storage.update(999L, updates);
            assertFalse(updated.isPresent());
            verify(repository).findById(999L);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("update() with null updates object returns stored app unchanged")
        void updateWithNullFields() {
            JobApplication app = newApp();
            app.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            ApplicationStorageService.ApplicationUpdates updates = new ApplicationStorageService.ApplicationUpdates();

            Optional<JobApplication> updated = storage.update(1L, updates);

            assertTrue(updated.isPresent());
            assertEquals(app.getCoverLetter(), updated.get().getCoverLetter());
            assertEquals(app.getGeneratedResumeSummary(), updated.get().getGeneratedResumeSummary());
            assertEquals(app.getApplicationAnswers(), updated.get().getApplicationAnswers());
            verify(repository).save(app);
        }
    }

    // ─── Approve ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Approve application")
    class ApproveTests {

        @Test
        @DisplayName("approve() sets status to APPROVED_FOR_APPLICATION and sets approvedAt")
        void approveSetsStatus() {
            JobApplication app = newApp();
            app.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> approved = storage.approve(1L);

            assertTrue(approved.isPresent());
            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, approved.get().getApplicationStatus());
            assertNotNull(approved.get().getApprovedAt());
            verify(repository).findById(1L);
            verify(repository).save(app);
        }

        @Test
        @DisplayName("approve() returns empty for non-existent application")
        void approveNonExistent() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<JobApplication> approved = storage.approve(999L);
            assertFalse(approved.isPresent());
            verify(repository).findById(999L);
            verify(repository, never()).save(any());
        }
    }

    // ─── Reject ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Reject application")
    class RejectTests {

        @Test
        @DisplayName("reject() sets status to REJECTED")
        void rejectSetsStatus() {
            JobApplication app = newApp();
            app.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> rejected = storage.reject(1L);

            assertTrue(rejected.isPresent());
            assertEquals(ApplicationStatus.REJECTED, rejected.get().getApplicationStatus());
            verify(repository).findById(1L);
            verify(repository).save(app);
        }

        @Test
        @DisplayName("reject() returns empty for non-existent application")
        void rejectNonExistent() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<JobApplication> rejected = storage.reject(999L);
            assertFalse(rejected.isPresent());
            verify(repository).findById(999L);
            verify(repository, never()).save(any());
        }
    }
}