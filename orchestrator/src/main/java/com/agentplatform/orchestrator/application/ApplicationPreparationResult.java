package com.agentplatform.orchestrator.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured result model returned from application preparation.
 * Contains AI-generated content plus status and metadata.
 */
public class ApplicationPreparationResult {

    private Long applicationId;
    private Long candidateId;
    private String jobId;
    private String jobTitle;
    private String company;
    private String tailoredProfessionalSummary;
    private String coverLetter;
    private String suggestedAnswers;
    private List<String> matchingSkills;
    private List<String> missingSkills;
    private List<String> candidateStrengths;
    private List<String> resumeHighlights;
    private Integer matchScore;
    private String recommendation;
    private String status;
    private LocalDateTime createdAt;

    // Default constructor
    public ApplicationPreparationResult() {
    }

    public ApplicationPreparationResult(Long applicationId, Long candidateId, String jobId, String jobTitle,
                                        String company, String tailoredProfessionalSummary, String coverLetter,
                                        String suggestedAnswers, List<String> matchingSkills,
                                        List<String> missingSkills, List<String> candidateStrengths,
                                        List<String> resumeHighlights, Integer matchScore,
                                        String recommendation, String status, LocalDateTime createdAt) {
        this.applicationId = applicationId;
        this.candidateId = candidateId;
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.tailoredProfessionalSummary = tailoredProfessionalSummary;
        this.coverLetter = coverLetter;
        this.suggestedAnswers = suggestedAnswers;
        this.matchingSkills = matchingSkills;
        this.missingSkills = missingSkills;
        this.candidateStrengths = candidateStrengths;
        this.resumeHighlights = resumeHighlights;
        this.matchScore = matchScore;
        this.recommendation = recommendation;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getTailoredProfessionalSummary() { return tailoredProfessionalSummary; }
    public void setTailoredProfessionalSummary(String tailoredProfessionalSummary) { this.tailoredProfessionalSummary = tailoredProfessionalSummary; }

    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }

    public String getSuggestedAnswers() { return suggestedAnswers; }
    public void setSuggestedAnswers(String suggestedAnswers) { this.suggestedAnswers = suggestedAnswers; }

    public List<String> getMatchingSkills() { return matchingSkills; }
    public void setMatchingSkills(List<String> matchingSkills) { this.matchingSkills = matchingSkills; }

    public List<String> getMissingSkills() { return missingSkills; }
    public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills; }

    public List<String> getCandidateStrengths() { return candidateStrengths; }
    public void setCandidateStrengths(List<String> candidateStrengths) { this.candidateStrengths = candidateStrengths; }

    public List<String> getResumeHighlights() { return resumeHighlights; }
    public void setResumeHighlights(List<String> resumeHighlights) { this.resumeHighlights = resumeHighlights; }

    public Integer getMatchScore() { return matchScore; }
    public void setMatchScore(Integer matchScore) { this.matchScore = matchScore; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}