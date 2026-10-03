package com.agentplatform.orchestrator.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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
        @DisplayName("findByCandidateId() with no status returns all applications for candidate")
        void findByCandidateIdReturnsMatches() {
            JobApplication app1 = newApp();
            app1.setId(1L);
            JobApplication app2 = newApp();
            app2.setId(2L);

            JobApplication app3 = new JobApplication(2L, "job-456", "Data Scientist", "Other Corp", "Bangalore");
            app3.setApplicationStatus(ApplicationStatus.GENERATED);
            app3.setId(3L);

            when(repository.findByCandidateIdOrderByUpdatedAtDescIdDesc(1L)).thenReturn(List.of(app1, app2));
            when(repository.findByCandidateIdOrderByUpdatedAtDescIdDesc(2L)).thenReturn(List.of(app3));

            List<JobApplication> apps = storage.findByCandidateId(1L, null);

            assertEquals(2, apps.size());
            assertTrue(apps.stream().allMatch(a -> a.getCandidateId().equals(1L)));
            verify(repository).findByCandidateIdOrderByUpdatedAtDescIdDesc(1L);
        }

        @Test
        @DisplayName("findByCandidateId() with blank status returns all applications")
        void findByCandidateIdBlankStatusReturnsAll() {
            when(repository.findByCandidateIdOrderByUpdatedAtDescIdDesc(1L)).thenReturn(List.of(newApp()));

            List<JobApplication> apps = storage.findByCandidateId(1L, "   ");

            assertEquals(1, apps.size());
            verify(repository).findByCandidateIdOrderByUpdatedAtDescIdDesc(1L);
        }

        @Test
        @DisplayName("findByCandidateId() with a known status filters to that status (case-insensitive)")
        void findByCandidateIdWithStatusFilters() {
            JobApplication app1 = newApp();
            app1.setApplicationStatus(ApplicationStatus.GENERATED);
            when(repository.findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(1L, ApplicationStatus.GENERATED))
                    .thenReturn(List.of(app1));

            List<JobApplication> apps = storage.findByCandidateId(1L, "  generated ");

            assertEquals(1, apps.size());
            assertEquals(ApplicationStatus.GENERATED, apps.get(0).getApplicationStatus());
            verify(repository).findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(1L, ApplicationStatus.GENERATED);
        }

        @Test
        @DisplayName("findByCandidateId() with an unknown status throws IllegalArgumentException")
        void findByCandidateIdUnknownStatusThrows() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> storage.findByCandidateId(1L, "SENT-BY-GHOST"));

            assertTrue(ex.getMessage().contains("Unknown application status 'SENT-BY-GHOST'"));
            verifyNoInteractions(repository);
        }

        @Test
        @DisplayName("findByCandidateId() returns empty list for unknown candidate")
        void findByCandidateIdUnknown() {
            when(repository.findByCandidateIdOrderByUpdatedAtDescIdDesc(999L)).thenReturn(List.of());

            List<JobApplication> apps = storage.findByCandidateId(999L, null);
            assertTrue(apps.isEmpty());
            verify(repository).findByCandidateIdOrderByUpdatedAtDescIdDesc(999L);
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

        @Test
        @DisplayName("approve() from UNDER_REVIEW → APPROVED_FOR_APPLICATION")
        void approveFromUnderReview() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.UNDER_REVIEW);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> approved = storage.approve(1L);

            assertTrue(approved.isPresent());
            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, approved.get().getApplicationStatus());
            assertNotNull(approved.get().getApprovedAt());
        }

        @Test
        @DisplayName("approve() is idempotent from APPROVED_FOR_APPLICATION (no re-write, no save)")
        void approveIdempotentFromApproved() {
            LocalDateTime approvedAt = LocalDateTime.now().minusDays(2);
            LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
            app.setApprovedAt(approvedAt);
            app.setUpdatedAt(updatedAt);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            Optional<JobApplication> approved = storage.approve(1L);

            assertTrue(approved.isPresent());
            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, approved.get().getApplicationStatus());
            assertEquals(approvedAt, approved.get().getApprovedAt(), "approvedAt must not be rewritten");
            assertEquals(updatedAt, approved.get().getUpdatedAt(), "updatedAt must not be rewritten");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("approve() from REJECTED throws (terminal, no re-approval of rejected rows)")
        void approveFromRejected() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.REJECTED);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storage.approve(1L));
            assertTrue(ex.getMessage().contains("cannot be approved"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("approve() from ARCHIVED throws (terminal)")
        void approveFromArchived() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.ARCHIVED);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            assertThrows(IllegalArgumentException.class, () -> storage.approve(1L));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("approve() from EMAIL_SENT throws (terminal)")
        void approveFromEmailSent() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.EMAIL_SENT);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            assertThrows(IllegalArgumentException.class, () -> storage.approve(1L));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("approve() from DRAFT throws (must be prepared first)")
        void approveFromDraft() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.DRAFT);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            assertThrows(IllegalArgumentException.class, () -> storage.approve(1L));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("approve() propagates an optimistic-lock conflict (→ 409 at the boundary)")
        void approvePropagatesOptimisticLock() {
            JobApplication app = newApp();
            app.setId(1L);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app))
                    .thenThrow(new ObjectOptimisticLockingFailureException(JobApplication.class, 1L));

            assertThrows(ObjectOptimisticLockingFailureException.class, () -> storage.approve(1L));
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

        @Test
        @DisplayName("reject() from UNDER_REVIEW → REJECTED")
        void rejectFromUnderReview() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.UNDER_REVIEW);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> rejected = storage.reject(1L);

            assertTrue(rejected.isPresent());
            assertEquals(ApplicationStatus.REJECTED, rejected.get().getApplicationStatus());
        }

        @Test
        @DisplayName("reject() from APPROVED_FOR_APPLICATION → REJECTED")
        void rejectFromApproved() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> rejected = storage.reject(1L);

            assertTrue(rejected.isPresent());
            assertEquals(ApplicationStatus.REJECTED, rejected.get().getApplicationStatus());
        }

        @Test
        @DisplayName("reject() is idempotent from REJECTED (no re-write, no save)")
        void rejectIdempotentFromRejected() {
            LocalDateTime updatedAt = LocalDateTime.now().minusDays(1);
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.REJECTED);
            app.setUpdatedAt(updatedAt);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            Optional<JobApplication> rejected = storage.reject(1L);

            assertTrue(rejected.isPresent());
            assertEquals(ApplicationStatus.REJECTED, rejected.get().getApplicationStatus());
            assertEquals(updatedAt, rejected.get().getUpdatedAt(), "updatedAt must not be rewritten");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("reject() from ARCHIVED throws (terminal)")
        void rejectFromArchived() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.ARCHIVED);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> storage.reject(1L));
            assertTrue(ex.getMessage().contains("cannot be rejected"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("reject() from EMAIL_SENT throws (terminal)")
        void rejectFromEmailSent() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.EMAIL_SENT);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            assertThrows(IllegalArgumentException.class, () -> storage.reject(1L));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("reject() from DRAFT throws (must be prepared first)")
        void rejectFromDraft() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.DRAFT);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            assertThrows(IllegalArgumentException.class, () -> storage.reject(1L));
            verify(repository, never()).save(any());
        }
    }

    // ─── Email-send outcome persistence (Phase 12.9) ─────────────────────────
    @Nested
    @DisplayName("Email-send outcome persistence")
    class EmailOutcomeTests {

        @Test
        @DisplayName("real SENT persists SENT + transitions to EMAIL_SENT, keeps approvedAt")
        void realSendTransitionsToEmailSent() {
            LocalDateTime approvedAt = LocalDateTime.now().minusDays(1);
            LocalDateTime updatedAt = LocalDateTime.now().minusHours(1);
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
            app.setApprovedAt(approvedAt);
            app.setUpdatedAt(updatedAt);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> updated = storage.recordEmailSendOutcome(1L, false);

            assertTrue(updated.isPresent());
            assertEquals(ApplicationStatus.EMAIL_SENT, updated.get().getApplicationStatus());
            assertEquals("SENT", updated.get().getEmailSendResult());
            assertNotNull(updated.get().getEmailSendAttemptedAt());
            assertEquals(approvedAt, updated.get().getApprovedAt(), "approvedAt must not be rewritten");
            assertTrue(!updated.get().getUpdatedAt().isBefore(updatedAt), "updatedAt must advance");
            verify(repository).save(app);
        }

        @Test
        @DisplayName("simulated send persists SENT_SIMULATED, status stays APPROVED_FOR_APPLICATION")
        void simulatedSendKeepsStatusApproved() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> updated = storage.recordEmailSendOutcome(1L, true);

            assertTrue(updated.isPresent());
            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, updated.get().getApplicationStatus());
            assertEquals("SENT_SIMULATED", updated.get().getEmailSendResult());
            assertNotNull(updated.get().getEmailSendAttemptedAt());
            assertNull(updated.get().getApprovedAt());
            verify(repository).save(app);
        }

        @Test
        @DisplayName("repeated simulated send overwrites the recorded attempt timestamp")
        void simulatedResendOverwritesAttempt() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            storage.recordEmailSendOutcome(1L, true);
            LocalDateTime firstAttempt = app.getEmailSendAttemptedAt();
            storage.recordEmailSendOutcome(1L, true);

            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, app.getApplicationStatus());
            assertEquals("SENT_SIMULATED", app.getEmailSendResult());
            assertFalse(app.getEmailSendAttemptedAt().isBefore(firstAttempt),
                    "second accepted attempt must not move the timestamp backwards");
        }

        @Test
        @DisplayName("real send from EMAIL_SENT throws (duplicate send guarded)")
        void realSendFromEmailSent() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.EMAIL_SENT);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> storage.recordEmailSendOutcome(1L, false));
            assertTrue(ex.getMessage().toLowerCase().contains("already sent"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("send outcome from a not-approved status throws (approval-gate defense)")
        void sendFromNotApproved() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.GENERATED);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> storage.recordEmailSendOutcome(1L, false));
            assertTrue(ex.getMessage().contains("must be approved"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("recordEmailSendOutcome() returns empty for non-existent application")
        void outcomeForMissingApp() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<JobApplication> updated = storage.recordEmailSendOutcome(999L, false);

            assertFalse(updated.isPresent());
            verify(repository, never()).save(any());
        }
    }

    // ─── Employer handoff recording (Phase 12.9, Slice 2) ────────────────────
    @Nested
    @DisplayName("Employer handoff recording")
    class HandoffTests {

        private JobApplication approvedApp() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
            app.setApprovedAt(LocalDateTime.now().minusDays(1));
            return app;
        }

        @Test
        @DisplayName("recordHandoff() records the event without status change or submission fields")
        void handoffRecordsEvent() {
            JobApplication app = approvedApp();
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            Optional<JobApplication> updated = storage.recordHandoff(1L, "https://careers.example.com/apply?ref=agent");

            assertTrue(updated.isPresent());
            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, updated.get().getApplicationStatus());
            assertEquals("https://careers.example.com/apply?ref=agent", updated.get().getEmployerUrl());
            assertNotNull(updated.get().getEmployerOpenedAt());
            assertNotNull(updated.get().getUpdatedAt());
            verify(repository).save(app);
        }

        @Test
        @DisplayName("recordHandoff() accepts http and https schemes with a host")
        void handoffAcceptsHttpSchemes() {
            JobApplication app = approvedApp();
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            storage.recordHandoff(1L, "http://apply.example.org/job");
            String httpUrl = app.getEmployerUrl();
            storage.recordHandoff(1L, "https://apply.example.org/job");

            assertEquals("http://apply.example.org/job", httpUrl, "http URL accepted on first handoff");
            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, app.getApplicationStatus());
            assertEquals("https://apply.example.org/job", app.getEmployerUrl(), "https URL accepted on re-open");
            verify(repository, times(2)).save(app);
        }

        @Test
        @DisplayName("recordHandoff() returns empty for non-existent application")
        void handoffMissingApp() {
            when(repository.findById(999L)).thenReturn(Optional.empty());

            Optional<JobApplication> updated = storage.recordHandoff(999L, "https://careers.example.com/apply");

            assertFalse(updated.isPresent());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("recordHandoff() from a non-approved status throws (approval gate)")
        void handoffNotApproved() {
            JobApplication app = newApp();
            app.setId(1L);
            app.setApplicationStatus(ApplicationStatus.GENERATED);
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> storage.recordHandoff(1L, "https://careers.example.com/apply"));
            assertTrue(ex.getMessage().contains("must be approved"));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("recordHandoff() rejects invalid URLs")
        void handoffInvalidUrls() {
            JobApplication app = approvedApp();
            when(repository.findById(1L)).thenReturn(Optional.of(app));

            String[] invalid = { null, "", "   ", "not a url", "ftp://example.com/f",
                    "https://", "javascript:alert(1)", "example.com/apply" };
            for (String url : invalid) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                        () -> storage.recordHandoff(1L, url),
                        "expected rejection of: " + url);
                assertTrue(ex.getMessage().contains("URL"));
            }
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("recordHandoff() re-open overwrites the recorded timestamp, no status change")
        void handoffReopenOverwrites() {
            JobApplication app = approvedApp();
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app)).thenReturn(app);

            storage.recordHandoff(1L, "https://careers.example.com/apply");
            LocalDateTime first = app.getEmployerOpenedAt();
            storage.recordHandoff(1L, "https://careers.example.com/apply");

            assertEquals(ApplicationStatus.APPROVED_FOR_APPLICATION, app.getApplicationStatus());
            assertEquals("https://careers.example.com/apply", app.getEmployerUrl());
            assertFalse(app.getEmployerOpenedAt().isBefore(first),
                    "re-open must not move the recorded timestamp backwards");
        }

        @Test
        @DisplayName("recordHandoff() propagates an optimistic-lock conflict (→ 409 at the boundary)")
        void handoffPropagatesOptimisticLock() {
            JobApplication app = approvedApp();
            when(repository.findById(1L)).thenReturn(Optional.of(app));
            when(repository.save(app))
                    .thenThrow(new ObjectOptimisticLockingFailureException(JobApplication.class, 1L));

            assertThrows(ObjectOptimisticLockingFailureException.class,
                    () -> storage.recordHandoff(1L, "https://careers.example.com/apply"));
        }
    }
}