package com.agentplatform.orchestrator.application;

import com.agentplatform.core.ai.AiErrorClassifier;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.matching.SkillMatchingEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.persistence.CandidateProfilePersistenceService;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prepares job application materials using the configured AI provider (Ollama by default).
 * If AI generation fails, uses deterministic Java fallback content so the system
 * remains usable.
 *
 * <p>Both paths are driven by the <strong>already-parsed candidate profile</strong> and the
 * resolved {@link Job}. Skill overlap is delegated to the existing
 * {@link SkillMatchingEngine} (the same engine the matching page uses) so no second
 * skill-comparison implementation exists and no scoring behaviour is changed here.</p>
 *
 * <p>Nothing is invented: every line of generated content is assembled from data that
 * already exists on the {@code CandidateProfile} or the {@code Job}. When no profile is
 * available (e.g. the legacy constructor used by unit tests), clearly generic placeholder
 * content is returned instead of fabricating qualifications.</p>
 *
 * <p>This service never sends email and never submits an application — it only produces a
 * review-ready package that the user must explicitly approve.</p>
 */
@Service
public class JobApplicationPreparationService {

    private static final Logger log = LoggerFactory.getLogger(JobApplicationPreparationService.class);

    private final ChatModel chatModel;
    private final CandidateProfilePersistenceService candidateProfiles;
    private final JobSearchService jobSearchService;
    private final SkillMatchingEngine skillMatchingEngine;

    /**
     * Test/standalone constructor. Candidate and job resolution are unavailable, so
     * generation degrades to the generic placeholder content (previous behaviour).
     */
    public JobApplicationPreparationService(ChatModel chatModel) {
        this(chatModel, null, null, null);
    }

    /**
     * Production constructor. Spring injects the existing candidate/job/skill services so
     * the generated package is derived from the parsed resume and the selected job.
     * {@code @Autowired} is explicit because the class declares more than one constructor.
     */
    @Autowired
    public JobApplicationPreparationService(ChatModel chatModel,
                                            CandidateProfilePersistenceService candidateProfiles,
                                            JobSearchService jobSearchService,
                                            SkillMatchingEngine skillMatchingEngine) {
        this.chatModel = chatModel;
        this.candidateProfiles = candidateProfiles;
        this.jobSearchService = jobSearchService;
        this.skillMatchingEngine = skillMatchingEngine != null ? skillMatchingEngine : new SkillMatchingEngine();
    }

    public ApplicationPreparationResult prepareApplication(
            Long candidateId,
            String jobId,
            String jobTitle,
            String company,
            String location,
            String customInstructions) {

        return prepareApplication(candidateId, jobId, jobTitle, company, location, customInstructions, false);
    }

    public ApplicationPreparationResult prepareApplication(
            Long candidateId,
            String jobId,
            String jobTitle,
            String company,
            String location,
            String customInstructions,
            boolean skipAi) {

        // Resolve the real candidate profile and job once; both may legitimately be absent
        // (no persistence wired, or the job is not currently discoverable).
        CandidateProfile candidate = resolveCandidate(candidateId);
        Job job = resolveJob(jobId);
        SkillMatchingEngine.SkillEvaluation skills = evaluateSkills(candidate, job);

        log.info("Preparing application for candidate {} and job {} (title: {}, skipAi={}, profileLoaded={}, jobLoaded={})",
                candidateId, jobId, jobTitle, skipAi, candidate != null, job != null);

        if (skipAi) {
            log.info("AI skipped (mock job) — using deterministic content derived from the candidate profile");
            return generateFallbackApplication(candidateId, jobId, jobTitle, company, location,
                    customInstructions, candidate, job, skills);
        }

        boolean hasApiKey = chatModel != null && isProviderConfigured();

        if (hasApiKey) {
            try {
                return generateWithAi(candidateId, jobId, jobTitle, company, location,
                        customInstructions, candidate, job, skills);
            } catch (Exception e) {
                log.warn("AI generation failed, falling back to deterministic content: {}", e.getMessage());
            }
        } else {
            log.info("No AI provider configured — using deterministic content derived from the candidate profile");
        }

        return generateFallbackApplication(candidateId, jobId, jobTitle, company, location,
                customInstructions, candidate, job, skills);
    }

    // ─── Candidate / job resolution ──────────────────────────────────────

    private CandidateProfile resolveCandidate(Long candidateId) {
        if (candidateProfiles == null || candidateId == null) {
            return null;
        }
        try {
            return candidateProfiles.findById(candidateId)
                    .map(entity -> entity.toDomain())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not load candidate profile {} for application preparation: {}",
                    candidateId, e.getMessage());
            return null;
        }
    }

    private Job resolveJob(String jobId) {
        if (jobSearchService == null || jobId == null || jobId.isBlank()) {
            return null;
        }
        try {
            return jobSearchService.findById(jobId).orElse(null);
        } catch (Exception e) {
            log.warn("Could not resolve job '{}' for application preparation: {}", jobId, e.getMessage());
            return null;
        }
    }

    /**
     * Reuses the existing matching engine — the same skill-overlap rules the Matches page
     * already applies. Returns an empty evaluation when either side is unknown.
     */
    private SkillMatchingEngine.SkillEvaluation evaluateSkills(CandidateProfile candidate, Job job) {
        if (candidate == null || job == null) {
            return new SkillMatchingEngine.SkillEvaluation(List.of(), List.of(), List.of(), List.of(), 0.0);
        }
        return skillMatchingEngine.evaluate(candidate, job);
    }

    private boolean isProviderConfigured() {
        try {
            String modelName = chatModel.getClass().getSimpleName();
            return !modelName.contains("NoOp") && !modelName.contains("fallback");
        } catch (Exception e) {
            return false;
        }
    }

    // ─── AI generation ───────────────────────────────────────────────────

    private ApplicationPreparationResult generateWithAi(
            Long candidateId,
            String jobId,
            String jobTitle,
            String company,
            String location,
            String customInstructions,
            CandidateProfile candidate,
            Job job,
            SkillMatchingEngine.SkillEvaluation skills) {

        String prompt = buildApplicationPrompt(jobTitle, company, location, customInstructions, candidate, job, skills);

        String aiResponse;
        try {
            aiResponse = chatModel.chat(prompt);
        } catch (Exception e) {
            AiErrorClassifier.Failure failure = AiErrorClassifier.classify(e);
            log.warn("AI generation failed: {} (kind: {})", failure.message(), failure.kind());
            throw new RuntimeException(failure.message());
        }

        return parseAiResponse(aiResponse, candidateId, jobId, jobTitle, company, candidate, job, skills);
    }

    private String buildApplicationPrompt(
            String jobTitle,
            String company,
            String location,
            String customInstructions,
            CandidateProfile candidate,
            Job job,
            SkillMatchingEngine.SkillEvaluation skills) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional job application assistant. Prepare a complete job application package for the following candidate applying to ")
                .append(jobTitle).append(" at ").append(company).append(" in ").append(location).append(".\n\n");

        appendCandidateContext(sb, candidate, job, skills);

        if (customInstructions != null && !customInstructions.isBlank()) {
            sb.append("Custom instructions: ").append(customInstructions).append("\n\n");
        }

        sb.append("""
            Use ONLY the candidate information above. Never invent experience, skills, degrees,
            employers or achievements that are not listed. Reorder, reword and emphasise what is
            genuinely there; do not add anything that is not.

            Do NOT return matchingSkills, missingSkills or matchScore. Those are computed
            deterministically from the parsed resume and are never taken from the model.

            Generate a structured JSON response with the following fields:
            {
              "tailoredProfessionalSummary": "A concise 3-4 sentence professional summary tailored to this role and company",
              "coverLetter": "A complete cover letter addressing the hiring manager, expressing interest in this specific role, and highlighting relevant experience",
              "suggestedAnswers": [
                "Answer to: 'Why are you interested in this role?'",
                "Answer to: 'What makes you a good fit for this position?'",
                "Answer to: 'Describe your relevant experience for this role'"
              ],
              "candidateStrengths": ["strength1", "strength2", "strength3"],
              "resumeHighlights": ["highlight1", "highlight2"]
            }
            """);

        return sb.toString();
    }

    /**
     * Adds the parsed resume facts to the prompt. Only real profile/job data is included —
     * no PII beyond what the user themselves submitted to this platform.
     */
    private void appendCandidateContext(StringBuilder sb,
                                        CandidateProfile candidate,
                                        Job job,
                                        SkillMatchingEngine.SkillEvaluation skills) {
        if (candidate != null) {
            sb.append("CANDIDATE (from the parsed resume):\n");
            appendLine(sb, "Name", candidate.name());
            appendListLine(sb, "Skills", candidate.skills());
            appendListLine(sb, "Software skills", candidate.softwareSkills());
            appendListLine(sb, "Hardware skills", candidate.hardwareSkills());
            appendListLine(sb, "Education", candidate.education());
            appendListLine(sb, "Experience", candidate.experience());
            appendListLine(sb, "Internships", candidate.internships());
            appendListLine(sb, "Projects", candidate.projects());
            appendListLine(sb, "Certifications", candidate.certifications());
            sb.append("\n");
        } else {
            sb.append("CANDIDATE: no parsed resume profile is available. Do not invent candidate details.\n\n");
        }

        if (job != null) {
            sb.append("JOB REQUIREMENTS:\n");
            appendListLine(sb, "Required skills", job.requiredSkills());
            appendListLine(sb, "Preferred skills", job.preferredSkills());
            appendLine(sb, "Experience requirement", job.experienceRequirement());
            sb.append("\n");
        }

        if (candidate != null && job != null) {
            appendListLine(sb, "Confirmed skill overlap (verified)", skills.matchedRequiredSkills());
            appendListLine(sb, "Missing required skills", skills.missingRequiredSkills());
            sb.append("\n");
        }
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private void appendListLine(StringBuilder sb, String label, List<String> values) {
        List<String> clean = clean(values);
        if (!clean.isEmpty()) {
            sb.append(label).append(": ").append(String.join("; ", clean)).append("\n");
        }
    }

    private ApplicationPreparationResult parseAiResponse(
            String aiResponse,
            Long candidateId,
            String jobId,
            String jobTitle,
            String company,
            CandidateProfile candidate,
            Job job,
            SkillMatchingEngine.SkillEvaluation skills) {

        int jsonStart = aiResponse.indexOf('{');
        int jsonEnd = aiResponse.lastIndexOf('}');

        if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
            throw new RuntimeException("AI response did not contain valid JSON");
        }

        String json = aiResponse.substring(jsonStart, jsonEnd + 1);
        return extractFieldsFromJson(json, candidateId, jobId, jobTitle, company, candidate, job, skills);
    }

    private ApplicationPreparationResult extractFieldsFromJson(
            String json, Long candidateId, String jobId, String jobTitle, String company,
            CandidateProfile candidate, Job job, SkillMatchingEngine.SkillEvaluation skills) {

        ApplicationPreparationResult result = new ApplicationPreparationResult();

        result.setCandidateId(candidateId);
        result.setJobId(jobId);
        result.setJobTitle(jobTitle);
        result.setCompany(company);
        result.setStatus("GENERATED");

        // Extract tailoredProfessionalSummary
        String summary = extractJsonField(json, "tailoredProfessionalSummary");
        result.setTailoredProfessionalSummary(summary != null ? summary : professionalSummary(jobTitle, company, candidate, skills));

        // Extract coverLetter
        String coverLetter = extractJsonField(json, "coverLetter");
        result.setCoverLetter(coverLetter != null ? coverLetter : coverLetter(jobTitle, company, candidate, skills));

        // Extract suggestedAnswers (as comma-separated string)
        List<String> suggestedAnswers = extractJsonArray(json, "suggestedAnswers");
        result.setSuggestedAnswers(suggestedAnswers != null ? String.join(", ", suggestedAnswers) : suggestedAnswers(jobTitle, company, candidate));

        // Extract candidateStrengths as List<String>
        List<String> strengths = extractJsonArray(json, "candidateStrengths");
        result.setCandidateStrengths(strengths != null ? strengths : candidateStrengths(candidate, skills));

        // Matching / missing skills and the coverage score are NEVER taken from the model:
        // they are the deterministic SkillMatchingEngine result, so the package can only
        // claim skills the candidate actually has, and missing requirements stay separate.
        result.setMatchingSkills(matchingSkills(skills, candidate));
        result.setMissingSkills(missingSkills(skills));

        // Extract resumeHighlights as List<String>
        List<String> highlights = extractJsonArray(json, "resumeHighlights");
        result.setResumeHighlights(highlights != null ? highlights : resumeHighlights(candidate));

        result.setMatchScore(skillCoverageScore(candidate, job, skills));

        // Extract recommendation
        String recommendation = extractJsonField(json, "recommendation");
        result.setRecommendation(recommendation != null ? recommendation : generateDefaultRecommendation());

        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf('"', start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private List<String> extractJsonArray(String json, String field) {
        String pattern = "\"" + field + "\":[";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf(']', start);
        if (end == -1) return null;
        String arrayContent = json.substring(start, end);
        return Arrays.stream(arrayContent.split(","))
                .map(s -> s.replaceAll("\"", "").trim())
                .collect(Collectors.toList());
    }

    // ─── Deterministic content (candidate-driven) ────────────────────────

    private ApplicationPreparationResult generateFallbackApplication(
            Long candidateId,
            String jobId,
            String jobTitle,
            String company,
            String location,
            String customInstructions,
            CandidateProfile candidate,
            Job job,
            SkillMatchingEngine.SkillEvaluation skills) {

        ApplicationPreparationResult result = new ApplicationPreparationResult();
        result.setCandidateId(candidateId);
        result.setJobId(jobId);
        result.setJobTitle(jobTitle);
        result.setCompany(company);
        result.setStatus("GENERATED");

        result.setTailoredProfessionalSummary(professionalSummary(jobTitle, company, candidate, skills));
        result.setCoverLetter(coverLetter(jobTitle, company, candidate, skills));
        result.setSuggestedAnswers(suggestedAnswers(jobTitle, company, candidate));
        result.setCandidateStrengths(candidateStrengths(candidate, skills));
        result.setMatchingSkills(matchingSkills(skills, candidate));
        result.setMissingSkills(missingSkills(skills));
        result.setResumeHighlights(resumeHighlights(candidate));
        result.setMatchScore(skillCoverageScore(candidate, job, skills));
        result.setRecommendation(generateDefaultRecommendation());
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    /** Verified skill overlap: matched required skills, then matched preferred skills. */
    private List<String> matchingSkills(SkillMatchingEngine.SkillEvaluation skills, CandidateProfile candidate) {
        if (candidate != null) {
            LinkedHashSet<String> matched = new LinkedHashSet<>(skills.matchedRequiredSkills());
            matched.addAll(skills.matchedPreferredSkills());
            if (!matched.isEmpty()) {
                return List.copyOf(matched);
            }
        }
        return generateDefaultMatchingSkillsList();
    }

    /** Required skills the parsed resume does not evidence. */
    private List<String> missingSkills(SkillMatchingEngine.SkillEvaluation skills) {
        if (!skills.missingRequiredSkills().isEmpty()) {
            return List.copyOf(skills.missingRequiredSkills());
        }
        return generateDefaultMissingSkillsList();
    }

    /** Strengths are stated only from skills the resume actually evidences. */
    private List<String> candidateStrengths(CandidateProfile candidate, SkillMatchingEngine.SkillEvaluation skills) {
        if (candidate == null) {
            return generateDefaultStrengthsList();
        }
        List<String> strengths = new ArrayList<>();
        List<String> matched = new ArrayList<>(skills.matchedRequiredSkills());
        matched.addAll(skills.matchedPreferredSkills());
        for (String skill : matched) {
            strengths.add("Demonstrated " + skill + " experience");
        }
        if (!clean(candidate.education()).isEmpty()) {
            strengths.add("Formal education: " + String.join(", ", clean(candidate.education())));
        }
        if (!clean(candidate.certifications()).isEmpty()) {
            strengths.add("Certifications: " + String.join(", ", clean(candidate.certifications())));
        }
        return strengths.isEmpty() ? generateDefaultStrengthsList() : List.copyOf(strengths);
    }

    /** Highlights are taken verbatim from the parsed resume sections. */
    private List<String> resumeHighlights(CandidateProfile candidate) {
        if (candidate == null) {
            return generateDefaultResumeHighlightsList();
        }
        List<String> highlights = new ArrayList<>();
        highlights.addAll(clean(candidate.experience()));
        highlights.addAll(clean(candidate.projects()));
        highlights.addAll(clean(candidate.internships()));
        highlights.addAll(clean(candidate.certifications()));
        highlights.addAll(clean(candidate.education()));
        return highlights.isEmpty() ? generateDefaultResumeHighlightsList() : List.copyOf(highlights);
    }

    private String professionalSummary(String jobTitle, String company,
                                       CandidateProfile candidate,
                                       SkillMatchingEngine.SkillEvaluation skills) {
        String name = candidate != null && candidate.name() != null && !candidate.name().isBlank()
                ? candidate.name() : "The candidate";
        List<String> matched = new ArrayList<>(skills.matchedRequiredSkills());
        matched.addAll(skills.matchedPreferredSkills());

        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" is applying for the ").append(jobTitle).append(" role at ").append(company).append(". ");
        if (!matched.isEmpty()) {
            sb.append("The parsed resume evidences ").append(String.join(", ", matched)).append(". ");
        }
        List<String> education = candidate != null ? clean(candidate.education()) : List.of();
        if (!education.isEmpty()) {
            sb.append("Educational background: ").append(String.join("; ", education)).append(". ");
        }
        List<String> missing = skills.missingRequiredSkills();
        if (!missing.isEmpty()) {
            sb.append("Areas still to develop: ").append(String.join(", ", missing)).append(".");
        }
        return sb.toString().trim();
    }

    private String coverLetter(String jobTitle, String company,
                               CandidateProfile candidate,
                               SkillMatchingEngine.SkillEvaluation skills) {
        String name = candidate != null && candidate.name() != null && !candidate.name().isBlank()
                ? candidate.name() : null;
        List<String> matched = new ArrayList<>(skills.matchedRequiredSkills());
        matched.addAll(skills.matchedPreferredSkills());
        List<String> experience = candidate != null ? clean(candidate.experience()) : List.of();
        List<String> projects = candidate != null ? clean(candidate.projects()) : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("Dear Hiring Manager,\n\n");
        sb.append("I am writing to express my interest in the ").append(jobTitle)
                .append(" position at ").append(company).append(".\n\n");

        if (!matched.isEmpty()) {
            sb.append("My resume demonstrates experience with ")
                    .append(String.join(", ", matched))
                    .append(", which aligns directly with this role's requirements.\n\n");
        } else {
            sb.append("I am keen to bring my background to this role and to grow into its requirements.\n\n");
        }
        if (!experience.isEmpty()) {
            sb.append("Relevant experience: ").append(String.join("; ", experience)).append("\n\n");
        }
        if (!projects.isEmpty()) {
            sb.append("Relevant projects: ").append(String.join("; ", projects)).append("\n\n");
        }
        sb.append("Thank you for considering my application. I would welcome the opportunity to discuss ")
                .append("how I can contribute to ").append(company).append(".\n\n");
        sb.append("Sincerely,\n");
        if (name != null) {
            sb.append(name);
        }
        return sb.toString();
    }

    private String suggestedAnswers(String jobTitle, String company, CandidateProfile candidate) {
        List<String> matched = candidate != null ? clean(candidate.skills()) : List.of();
        String skillText = matched.isEmpty() ? "my background" : String.join(", ", matched);
        return String.join(" || ",
                "I am interested in this role because it combines my interest in " + jobTitle
                        + " with the opportunity to work on projects at " + company + ".",
                "I am a good fit for this position because my resume evidences " + skillText + ".",
                "My relevant experience includes the work and projects listed in my resume, which map to the "
                        + jobTitle + " requirements."
        );
    }

    // ─── Generic placeholders (only when no parsed profile is available) ─

    private List<String> generateDefaultResumeHighlightsList() {
        return List.of("No resume highlights could be extracted from the uploaded resume.");
    }

    /**
     * Deterministic required-skill coverage for this candidate/job pair, 0–100, or
     * {@code null} when either side was unavailable and nothing could be assessed.
     *
     * <p>Replaces the previous hard-coded 85 and the model-supplied "matchScore": the
     * platform must not report a score it did not calculate. The weighted match score
     * shown on the Matches page is produced by {@code JobMatchingService}; this figure
     * is skill coverage only, and the UI labels it as such.</p>
     */
    private Integer skillCoverageScore(CandidateProfile candidate,
                                       Job job,
                                       SkillMatchingEngine.SkillEvaluation skills) {
        if (candidate == null || job == null || skills == null) {
            return null;
        }
        double coverage = Math.max(0.0, Math.min(1.0, skills.skillScore()));
        return Integer.valueOf((int) Math.round(coverage * 100.0));
    }

    private List<String> generateDefaultStrengthsList() {
        return List.of("No candidate strengths could be derived from the uploaded resume.");
    }

    private List<String> generateDefaultMatchingSkillsList() {
        return List.of("No matching skills could be derived from the uploaded resume.");
    }

    private List<String> generateDefaultMissingSkillsList() {
        return List.of("No skill gaps could be determined for this job.");
    }

    private String generateDefaultRecommendation() {
        return "POSSIBLE_MATCH";
    }

    /** Removes null/blank entries without mutating the source list. */
    private static List<String> clean(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList();
    }
}
