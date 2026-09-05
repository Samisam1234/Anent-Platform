package com.agentplatform.orchestrator.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
     * Retrieves all applications for a candidate.
     *
     * @param candidateId the candidate ID
     * @return list of applications for the candidate
     */
    @Transactional(readOnly = true)
    public List<JobApplication> findByCandidateId(Long candidateId) {
        return repository.findByCandidateId(candidateId);
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
     * Approves an application.
     *
     * @param applicationId the application ID
     * @return the updated application if found, empty otherwise
     */
    @Transactional
    public Optional<JobApplication> approve(Long applicationId) {
        return repository.findById(applicationId)
                .map(existing -> {
                    existing.setApplicationStatus(ApplicationStatus.APPROVED_FOR_APPLICATION);
                    existing.setApprovedAt(LocalDateTime.now());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return repository.save(existing);
                });
    }

    /**
     * Rejects an application.
     *
     * @param applicationId the application ID
     * @return the updated application if found, empty otherwise
     */
    @Transactional
    public Optional<JobApplication> reject(Long applicationId) {
        return repository.findById(applicationId)
                .map(existing -> {
                    existing.setApplicationStatus(ApplicationStatus.REJECTED);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return repository.save(existing);
                });
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