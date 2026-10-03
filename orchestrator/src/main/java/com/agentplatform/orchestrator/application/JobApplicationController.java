package com.agentplatform.orchestrator.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/applications")
public class JobApplicationController {

    private static final Logger log = LoggerFactory.getLogger(JobApplicationController.class);

    private final JobApplicationPreparationService preparationService;
    private final com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService candidateProfiles;
    private final com.agentplatform.orchestrator.job.JobSearchService jobs;
    private final ApplicationStorageService storageService;

    public JobApplicationController(JobApplicationPreparationService preparationService) {
        this(preparationService, null, null, null);
    }

    @Autowired
    public JobApplicationController(
            JobApplicationPreparationService preparationService,
            com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService candidateProfiles,
            com.agentplatform.orchestrator.job.JobSearchService jobs,
            JobApplicationRepository jobApplicationRepository) {
        this.preparationService = preparationService;
        this.candidateProfiles = candidateProfiles;
        this.jobs = jobs;
        this.storageService = new ApplicationStorageService(jobApplicationRepository);
    }

    /**
     * Prepares a job application for the given candidate and job.
     * The prepared package is persisted so it shows up on the Applications page,
     * and the response includes the full reviewable package plus the stored id.
     *
     * @param request contains candidateId, jobId, and optional customInstructions
     * @return structured ApplicationPreparationResult (with stored applicationId)
     */
    @PostMapping("/prepare")
    public ResponseEntity<ApplicationPreparationResult> prepareApplication(
            @RequestBody ApplicationPrepareRequest request) {

        validatePreparationRequest(request);
        log.info("Prepare application request accepted: candidateId={}, jobId={}", request.getCandidateId(), request.getJobId());

        ApplicationPreparationResult result = preparationService.prepareApplication(
                request.getCandidateId(),
                request.getJobId(),
                request.getJobTitle(),
                request.getCompany(),
                request.getLocation(),
                request.getCustomInstructions(),
                isMockJob(request.getJobId()));

        JobApplication application = toEntity(request, result);
        application.setApplicationStatus(ApplicationStatus.GENERATED);
        application.setUpdatedAt(LocalDateTime.now());

        storageService.store(application);

        result.setApplicationId(application.getId());
        result.setStatus("GENERATED");
        log.info("Stored prepared application with id {}", application.getId());

        return ResponseEntity.ok(result);
    }

    private void validatePreparationRequest(ApplicationPrepareRequest request) {
        if (request == null || request.getCandidateId() == null || request.getCandidateId() <= 0) {
            throw new IllegalArgumentException("A valid candidateId is required to prepare an application.");
        }
        if (request.getJobId() == null || request.getJobId().isBlank()) {
            throw new IllegalArgumentException("A valid jobId is required to prepare an application.");
        }
        if (candidateProfiles != null) {
            candidateProfiles.getByIdOrThrow(request.getCandidateId());
        }
        if (jobs != null) {
            var job = jobs.findById(request.getJobId())
                    .orElseThrow(() -> new IllegalArgumentException("The selected job is no longer available."));
            request.setJobTitle(job.title());
            request.setCompany(job.company());
            request.setLocation(job.location());
        }
    }
    /**
     * Bridges an ApplicationPreparationResult into a persistent JobApplication.
     */
    private JobApplication toEntity(ApplicationPrepareRequest request, ApplicationPreparationResult result) {
        JobApplication app = new JobApplication(
                result.getCandidateId(),
                result.getJobId(),
                result.getJobTitle(),
                result.getCompany(),
                request.getLocation());
        app.setGeneratedResumeSummary(result.getTailoredProfessionalSummary());
        app.setCoverLetter(result.getCoverLetter());
        app.setApplicationAnswers(result.getSuggestedAnswers());
        app.setCandidateStrengths(join(result.getCandidateStrengths()));
        app.setMatchingSkills(join(result.getMatchingSkills()));
        app.setMissingSkills(join(result.getMissingSkills()));
        app.setResumeHighlights(join(result.getResumeHighlights()));
        app.setMatchScore(result.getMatchScore());
        app.setRecommendation(result.getRecommendation());
        app.setCreatedAt(result.getCreatedAt() != null ? result.getCreatedAt() : LocalDateTime.now());
        return app;
    }

    private String join(List<String> values) {
        if (values == null) return null;
        return String.join(", ", values);
    }

    /**
     * Retrieves a specific application by ID.
     *
     * @param applicationId the application ID
     * @return the JobApplication or 404 if not found
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<JobApplication> getApplication(@PathVariable Long applicationId) {
        return storageService.findById(applicationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Retrieves all applications prepared for a candidate.
     *
     * @param candidateId the candidate's ID
     * @return list of JobApplication entities
     */
    @GetMapping("/candidate/{candidateId}")
    public ResponseEntity<List<JobApplication>> getApplicationsByCandidate(@PathVariable Long candidateId) {
        List<JobApplication> apps = storageService.findByCandidateId(candidateId);
        return ResponseEntity.ok(apps);
    }

    /**
     * Allows the user to edit AI-generated application content.
     * Professional summary, cover letter, and application answers can be modified.
     *
     * @param applicationId the application ID
     * @param updates     the fields to update (coverLetter, professionalSummary, applicationAnswers)
     * @return updated JobApplication
     */
    @PutMapping("/{applicationId}")
    public ResponseEntity<JobApplication> updateApplication(
            @PathVariable Long applicationId,
            @RequestBody ApplicationUpdates updates) {

        // Convert controller DTO to storage service DTO
        ApplicationStorageService.ApplicationUpdates storageUpdates = new ApplicationStorageService.ApplicationUpdates();
        storageUpdates.setCoverLetter(updates.getCoverLetter());
        storageUpdates.setProfessionalSummary(updates.getProfessionalSummary());
        storageUpdates.setApplicationAnswers(updates.getApplicationAnswers());

        Optional<JobApplication> updated = storageService.update(applicationId, storageUpdates);
        if (updated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(updated.get());
    }

    /**
     * Explicitly approves an application.
     * This must be an explicit API request — AI generation never automatically approves.
     *
     * @param applicationId the application ID
     * @return updated JobApplication with status APPROVED_FOR_APPLICATION
     */
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<JobApplication> approveApplication(@PathVariable Long applicationId) {
        Optional<JobApplication> approved = storageService.approve(applicationId);
        if (approved.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Application {} approved for manual submission", applicationId);
        return ResponseEntity.ok(approved.get());
    }

    /**
     * Rejects a prepared application.
     *
     * @param applicationId the application ID
     * @return updated JobApplication with status REJECTED
     */
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<JobApplication> rejectApplication(@PathVariable Long applicationId) {
        Optional<JobApplication> rejected = storageService.reject(applicationId);
        if (rejected.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Application {} rejected", applicationId);
        return ResponseEntity.ok(rejected.get());
    }

    /**
     * Request payload for preparing an application.
     */
    public static class ApplicationPrepareRequest {
        private Long candidateId;
        private String jobId;
        private String jobTitle;
        private String company;
        private String location;
        private String customInstructions;

        // Default constructor for JSON deserialization
        public ApplicationPrepareRequest() {
        }

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
        }

        public String getJobId() {
            return jobId;
        }

        public void setJobId(String jobId) {
            this.jobId = jobId;
        }

        public String getJobTitle() {
            return jobTitle;
        }

        public void setJobTitle(String jobTitle) {
            this.jobTitle = jobTitle;
        }

        public String getCompany() {
            return company;
        }

        public void setCompany(String company) {
            this.company = company;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getCustomInstructions() {
            return customInstructions;
        }

        public void setCustomInstructions(String customInstructions) {
            this.customInstructions = customInstructions;
        }
    }

    /**
     * Request payload for updating an application.
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

    // Helper method to check if job ID corresponds to a mock job.
    // Mock catalog IDs follow the "mock-<prefix>-<nnn>" scheme (e.g. mock-sw-001).
    private boolean isMockJob(String jobId) {
        if (jobId == null) return false;
        return jobId.toLowerCase().contains("mock");
    }
}