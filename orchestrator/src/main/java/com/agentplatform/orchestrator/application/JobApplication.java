package com.agentplatform.orchestrator.application;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity representing a job application prepared for a candidate.
 * Stores AI-generated content alongside user-editable fields and approval status.
 */
@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "company", nullable = false)
    private String company;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false)
    private ApplicationStatus applicationStatus;

    @Column(name = "generated_resume_summary")
    private String generatedResumeSummary;

    @Column(name = "cover_letter")
    private String coverLetter;

    // TEXT (not VARCHAR 255): generated answers regularly exceed 255 chars.
    @Column(name = "application_answers", columnDefinition = "TEXT")
    private String applicationAnswers;

    @Column(name = "candidate_strengths")
    private String candidateStrengths;

    @Column(name = "matching_skills")
    private String matchingSkills;

    @Column(name = "missing_skills")
    private String missingSkills;

    @Column(name = "resume_highlights")
    private String resumeHighlights;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "recommendation")
    private String recommendation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // Default constructor for JPA
    public JobApplication() {
        this.applicationStatus = ApplicationStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
    }

    public JobApplication(Long candidateId, String jobId, String jobTitle, String company, String location) {
        this();
        this.candidateId = candidateId;
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.location = location;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public ApplicationStatus getApplicationStatus() {
        return applicationStatus;
    }

    public void setApplicationStatus(ApplicationStatus applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    public String getGeneratedResumeSummary() {
        return generatedResumeSummary;
    }

    public void setGeneratedResumeSummary(String generatedResumeSummary) {
        this.generatedResumeSummary = generatedResumeSummary;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public void setCoverLetter(String coverLetter) {
        this.coverLetter = coverLetter;
    }

    public String getApplicationAnswers() {
        return applicationAnswers;
    }

    public void setApplicationAnswers(String applicationAnswers) {
        this.applicationAnswers = applicationAnswers;
    }

    public String getCandidateStrengths() {
        return candidateStrengths;
    }

    public void setCandidateStrengths(String candidateStrengths) {
        this.candidateStrengths = candidateStrengths;
    }

    public String getMatchingSkills() {
        return matchingSkills;
    }

    public void setMatchingSkills(String matchingSkills) {
        this.matchingSkills = matchingSkills;
    }

    public String getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(String missingSkills) {
        this.missingSkills = missingSkills;
    }

    public String getResumeHighlights() {
        return resumeHighlights;
    }

    public void setResumeHighlights(String resumeHighlights) {
        this.resumeHighlights = resumeHighlights;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }
}