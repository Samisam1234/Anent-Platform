package com.agentplatform.orchestrator.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Persistent storage for job applications using JPA.
 * Used by both JobApplicationController and ApplicationEmailController
 * to ensure consistent access to prepared applications.
 */
@Service
public class ApplicationStorageService {

    private final JobApplicationRepository repository;

    public ApplicationStorageService(JobApplicationRepository repository) {
        this.repository = repository;
    }

    /**
     * Stores a new application and assigns it an ID.
     *
     * @param application the application to store (must not have an ID set)
     * @return the stored application with assigned ID
     */
    @Transactional
    public JobApplication store(JobApplication application) {
        application.setCreatedAt(application.getCreatedAt() != null ? application.getCreatedAt() : LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        return repository.save(application);
    }

    /**
     * Retrieves an application by ID.
     *
     * @param applicationId the application ID
     * @return the application if found, empty otherwise
     */
    @Transactional(readOnly = true)
    public Optional<JobApplication> findById(Long applicationId) {
        return repository.findById(applicationId);
    }

    /**
     * Retrieves applications for a candidate, optionally filtered by status.
     *
     * <p>Phase 12.9 list contract: absent/blank {@code status} returns all of the
     * candidate's applications; a known status name (case-insensitive) filters to it;
     * anything else throws {@link IllegalArgumentException} (RFC 7807 400 — a filter
     * is never silently treated as "all"). Results are deterministically ordered
     * {@code updatedAt DESC, id DESC}. Scoping is always by the given candidate — a
     * status filter can never expose another candidate's records.</p>
     *
     * @param candidateId the candidate ID
     * @param status the status name to filter by, or null/blank for all
     * @return the matching applications, newest-update first
     */
    @Transactional(readOnly = true)
    public List<JobApplication> findByCandidateId(Long candidateId, String status) {
        if (status == null || status.isBlank()) {
            return repository.findByCandidateIdOrderByUpdatedAtDescIdDesc(candidateId);
        }
        ApplicationStatus parsed = parseStatus(status);
        return repository.findByCandidateIdAndApplicationStatusOrderByUpdatedAtDescIdDesc(candidateId, parsed);
    }

    private ApplicationStatus parseStatus(String status) {
        try {
            return ApplicationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown application status '" + status + "'. Valid statuses: " + Arrays.toString(ApplicationStatus.values()) + ".");
        }
    }

    /**
     * Updates an existing application.
     *
     * @param applicationId the application ID
     * @param updates the updates to apply
     * @return the updated application if found, empty otherwise
     */
    @Transactional
    public Optional<JobApplication> update(Long applicationId, ApplicationUpdates updates) {
        return repository.findById(applicationId)
                .map(existing -> {
                    if (updates.getCoverLetter() != null) {
                        existing.setCoverLetter(updates.getCoverLetter());
                    }
                    if (updates.getProfessionalSummary() != null) {
                        existing.setGeneratedResumeSummary(updates.getProfessionalSummary());
                    }
                    if (updates.getApplicationAnswers() != null) {
                        existing.setApplicationAnswers(updates.getApplicationAnswers());
                    }
                    existing.setUpdatedAt(LocalDateTime.now());
                    return repository.save(existing);
                });
    }

    /**
     * Approves an application (Phase 12.9 status-transition model).
     *
     * <p>Allowed from {@code GENERATED} / {@code UNDER_REVIEW}; idempotent from
     * {@code APPROVED_FOR_APPLICATION} (entity returned unchanged, {@code approvedAt}
     * not rewritten). Any other source status — including the terminal
     * {@code REJECTED} / {@code ARCHIVED} / {@code EMAIL_SENT} — throws
     * {@link IllegalArgumentException}, surfaced as RFC 7807 400.</p>
     *
     * @param applicationId the application ID
     * @return the updated application if found, empty otherwise
     */
    @Transactional
    public Optional<JobApplication> approve(Long applicationId) {
        return repository.findById(applicationId)
                .map(existing -> {
                    ApplicationStatus current = existing.getApplicationStatus();
                    if (current == ApplicationStatus.APPROVED_FOR_APPLICATION) {
                        return existing;
                    }
                    if (current != ApplicationStatus.GENERATED && current != ApplicationStatus.UNDER_REVIEW) {
                        throw new IllegalArgumentException(
                                "Application " + applicationId + " cannot be approved from status " + current + ".");
                    }
                    existing.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
                    existing.setApprovedAt(LocalDateTime.now());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return repository.save(existing);
                });
    }

    /**
     * Rejects an application (Phase 12.9 status-transition model).
     *
     * <p>Allowed from {@code GENERATED} / {@code UNDER_REVIEW} /
     * {@code APPROVED_FOR_APPLICATION}; idempotent from {@code REJECTED}. Any other
     * source status — including the terminal {@code ARCHIVED} / {@code EMAIL_SENT} —
     * throws {@link IllegalArgumentException}, surfaced as RFC 7807 400.</p>
     *
     * @param applicationId the application ID
     * @return the updated application if found, empty otherwise
     */
    @Transactional
    public Optional<JobApplication> reject(Long applicationId) {
        return repository.findById(applicationId)
                .map(existing -> {
                    ApplicationStatus current = existing.getApplicationStatus();
                    if (current == ApplicationStatus.REJECTED) {
                        return existing;
                    }
                    if (current != ApplicationStatus.GENERATED && current != ApplicationStatus.UNDER_REVIEW
                            && current != ApplicationStatus.APPROVED_FOR_APPLICATION) {
                        throw new IllegalArgumentException(
                                "Application " + applicationId + " cannot be rejected from status " + current + ".");
                    }
                    existing.setApplicationStatus(ApplicationStatus.REJECTED);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return repository.save(existing);
                });
    }

    /**
     * Persists an accepted email-send attempt and applies the Phase 12.9 outcome.
     *
     * <p>The transport outcome and the status transition commit in one transaction
     * (the transport itself runs before this call, outside any transaction). A real
     * (non-simulated) send whose transport accepted it sets status {@code EMAIL_SENT}
     * (terminal); a simulated send is recorded without a status change, so re-sending
     * stays possible while no SMTP transport is configured. Persisted result is
     * {@code SENT} (real) or {@code SENT_SIMULATED} (nothing mailed) — delivery is
     * never represented.</p>
     *
     * <p>Residual window: a crash between transport acceptance and this commit loses
     * the outcome — the row stays {@code APPROVED_FOR_APPLICATION} and the user can
     * re-send. Nothing here ever reports delivery.</p>
     *
     * @param applicationId the application ID
     * @param simulated true when the transport was simulated (no SMTP configured)
     * @return the updated application if found, empty otherwise
     */
    @Transactional
    public Optional<JobApplication> recordEmailSendOutcome(Long applicationId, boolean simulated) {
        return repository.findById(applicationId)
                .map(existing -> {
                    ApplicationStatus current = existing.getApplicationStatus();
                    if (current == ApplicationStatus.EMAIL_SENT) {
                        throw new IllegalArgumentException(
                                "Email already sent for application " + applicationId + ".");
                    }
                    if (current != ApplicationStatus.APPROVED_FOR_APPLICATION) {
                        throw new IllegalArgumentException(
                                "Application " + applicationId + " must be approved before sending; status is " + current + ".");
                    }
                    existing.setEmailSendAttemptedAt(LocalDateTime.now());
                    existing.setEmailSendResult(simulated ? "SENT_SIMULATED" : "SENT");
                    existing.setUpdatedAt(LocalDateTime.now());
                    if (!simulated) {
                        existing.setApplicationStatus(ApplicationStatus.EMAIL_SENT);
                    }
                    return repository.save(existing);
                });
    }

    /**
     * Records the employer-site handoff event (Phase 12.9, §6).
     *
     * <p>Eligibility: application must exist and be {@code APPROVED_FOR_APPLICATION}
     * (else {@link IllegalArgumentException}, surfaced as RFC 7807 400). The URL is only
     * sanity-checked server-side (non-blank, absolute {@code http}/{@code https} with a
     * host) — an approximation of the kit's client-side {@code jobLink.safeUrl}; the
     * server never fetches or reaches the employer origin. Recording sets
     * {@code employerOpenedAt} (overwritten on every re-open — "last opened") and
     * {@code employerUrl}, bumps {@code updatedAt}. No status change, and never a
     * submission claim: the platform cannot observe a submission.</p>
     *
     * @param applicationId the application ID
     * @param url the employer application URL (already validated client-side)
     * @return the updated application if found, empty otherwise
     */
    @Transactional
    public Optional<JobApplication> recordHandoff(Long applicationId, String url) {
        String validated = validateHandoffUrl(url);
        return repository.findById(applicationId)
                .map(existing -> {
                    if (existing.getApplicationStatus() != ApplicationStatus.APPROVED_FOR_APPLICATION) {
                        throw new IllegalArgumentException(
                                "Application " + applicationId + " must be approved before handoff; status is "
                                        + existing.getApplicationStatus() + ".");
                    }
                    existing.setEmployerOpenedAt(LocalDateTime.now());
                    existing.setEmployerUrl(validated);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return repository.save(existing);
                });
    }

    private String validateHandoffUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A valid employer URL is required to record a handoff.");
        }
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!uri.isAbsolute() || scheme == null
                    || !(scheme.equals("https") || scheme.equals("http"))
                    || host == null || host.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid employer URL: must be an absolute http(s) URL with a host.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "Invalid employer URL: must be an absolute http(s) URL with a host.");
        }
        return trimmed;
    }

    /**
     * Request payload for updating an application.
     * Mirrors the DTO used in JobApplicationController.
     */
    public static class ApplicationUpdates {
        private String coverLetter;
        private String professionalSummary;
        private String applicationAnswers;

        public ApplicationUpdates() {
        }

        public String getCoverLetter() {
            return coverLetter;
        }

        public void setCoverLetter(String coverLetter) {
            this.coverLetter = coverLetter;
        }

        public String getProfessionalSummary() {
            return professionalSummary;
        }

        public void setProfessionalSummary(String professionalSummary) {
            this.professionalSummary = professionalSummary;
        }

        public String getApplicationAnswers() {
            return applicationAnswers;
        }

        public void setApplicationAnswers(String applicationAnswers) {
            this.applicationAnswers = applicationAnswers;
        }
    }
}