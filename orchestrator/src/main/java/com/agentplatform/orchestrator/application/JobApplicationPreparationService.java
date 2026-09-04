package com.agentplatform.orchestrator.application;

import com.agentplatform.core.ai.AiErrorClassifier;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prepares job application materials using the configured AI provider (Ollama by default).
 * If AI generation fails, uses deterministic Java fallback content so the system
 * remains usable.
 */
@Service
public class JobApplicationPreparationService {

    private static final Logger log = LoggerFactory.getLogger(JobApplicationPreparationService.class);

    private final ChatModel chatModel;

    public JobApplicationPreparationService(ChatModel chatModel) {
        this.chatModel = chatModel;
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

        log.info("Preparing application for candidate {} and job {} (title: {}, skipAi={})", candidateId, jobId, jobTitle, skipAi);

        if (skipAi) {
            log.info("AI skipped (mock job) — using deterministic fallback content");
            return generateFallbackApplication(candidateId, jobId, jobTitle, company, location, customInstructions);
        }

        boolean hasApiKey = chatModel != null && isProviderConfigured();

        if (hasApiKey) {
            try {
                return generateWithAi(candidateId, jobId, jobTitle, company, location, customInstructions);
            } catch (Exception e) {
                log.warn("AI generation failed, falling back to deterministic content: {}", e.getMessage());
            }
        } else {
            log.info("No AI provider configured — using deterministic fallback content");
        }

        return generateFallbackApplication(candidateId, jobId, jobTitle, company, location, customInstructions);
    }

    private boolean isProviderConfigured() {
        try {
            String modelName = chatModel.getClass().getSimpleName();
            return !modelName.contains("NoOp") && !modelName.contains("fallback");
        } catch (Exception e) {
            return false;
        }
    }

    private ApplicationPreparationResult generateWithAi(
            Long candidateId,
            String jobId,
            String jobTitle,
            String company,
            String location,
            String customInstructions) {

        String prompt = buildApplicationPrompt(jobTitle, company, location, customInstructions);

        String aiResponse;
        try {
            aiResponse = chatModel.chat(prompt);
        } catch (Exception e) {
            AiErrorClassifier.Failure failure = AiErrorClassifier.classify(e);
            log.warn("AI generation failed: {} (kind: {})", failure.message(), failure.kind());
            throw new RuntimeException(failure.message());
        }

        return parseAiResponse(aiResponse, candidateId, jobId, jobTitle, company);
    }

    private String buildApplicationPrompt(
            String jobTitle,
            String company,
            String location,
            String customInstructions) {

        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional job application assistant. Prepare a complete job application package for the following candidate applying to ")
                .append(jobTitle).append(" at ").append(company).append(" in ").append(location).append(".\n\n");

        if (customInstructions != null && !customInstructions.isBlank()) {
            sb.append("Custom instructions: ").append(customInstructions).append("\n\n");
        }

        sb.append("""
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
              "matchingSkills": ["skill1", "skill2"],
              "missingSkills": ["skill1", "skill2"],
              "resumeHighlights": ["highlight1", "highlight2"],
              "matchScore": 85
            }
            """);

        return sb.toString();
    }

    private ApplicationPreparationResult parseAiResponse(
            String aiResponse,
            Long candidateId,
            String jobId,
            String jobTitle,
            String company) {

        int jsonStart = aiResponse.indexOf('{');
        int jsonEnd = aiResponse.lastIndexOf('}');

        if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
            throw new RuntimeException("AI response did not contain valid JSON");
        }

        String json = aiResponse.substring(jsonStart, jsonEnd + 1);
        return extractFieldsFromJson(json, candidateId, jobId, jobTitle, company);
    }

    private ApplicationPreparationResult extractFieldsFromJson(
            String json, Long candidateId, String jobId, String jobTitle, String company) {

        ApplicationPreparationResult result = new ApplicationPreparationResult();

        result.setCandidateId(candidateId);
        result.setJobId(jobId);
        result.setJobTitle(jobTitle);
        result.setCompany(company);
        result.setStatus("GENERATED");

        // Extract tailoredProfessionalSummary
        String summary = extractJsonField(json, "tailoredProfessionalSummary");
        result.setTailoredProfessionalSummary(summary != null ? summary : generateDefaultProfessionalSummary(jobTitle, company));

        // Extract coverLetter
        String coverLetter = extractJsonField(json, "coverLetter");
        result.setCoverLetter(coverLetter != null ? coverLetter : generateDefaultCoverLetter(jobTitle, company));

        // Extract suggestedAnswers (as comma-separated string)
        List<String> suggestedAnswers = extractJsonArray(json, "suggestedAnswers");
        result.setSuggestedAnswers(suggestedAnswers != null ? String.join(", ", suggestedAnswers) : generateDefaultSuggestedAnswersAsString(jobTitle, company));

        // Extract candidateStrengths as List<String>
        List<String> strengths = extractJsonArray(json, "candidateStrengths");
        result.setCandidateStrengths(strengths != null ? strengths : generateDefaultStrengthsList());

        // Extract matchingSkills as List<String>
        List<String> matching = extractJsonArray(json, "matchingSkills");
        result.setMatchingSkills(matching != null ? matching : generateDefaultMatchingSkillsList());

        // Extract missingSkills as List<String>
        List<String> missing = extractJsonArray(json, "missingSkills");
        result.setMissingSkills(missing != null ? missing : generateDefaultMissingSkillsList());

        // Extract resumeHighlights as List<String>
        List<String> highlights = extractJsonArray(json, "resumeHighlights");
        result.setResumeHighlights(highlights != null ? highlights : generateDefaultResumeHighlightsList());

        // Extract matchScore as Integer
        result.setMatchScore(extractJsonInt(json, "matchScore"));

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

    private Integer extractJsonInt(String json, String field) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf(',', start);
        if (end == -1) end = json.indexOf('}', start);
        if (end == -1 || start >= end) return null;
        String raw = json.substring(start, end).trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> generateDefaultResumeHighlightsList() {
        return Arrays.asList(
                "Java 8/21 and Spring Boot expertise",
                "REST API design and microservices",
                "H2/PostgreSQL database experience",
                "Agile delivery and CI/CD familiarity");
    }

    private int generateDefaultMatchScore() {
        return 85;
    }

    // ─── Deterministic Java Fallback Content ─────────────────────────────

    private ApplicationPreparationResult generateFallbackApplication(
            Long candidateId,
            String jobId,
            String jobTitle,
            String company,
            String location,
            String customInstructions) {

        ApplicationPreparationResult result = new ApplicationPreparationResult();
        result.setCandidateId(candidateId);
        result.setJobId(jobId);
        result.setJobTitle(jobTitle);
        result.setCompany(company);
        result.setStatus("GENERATED");

        result.setTailoredProfessionalSummary(generateDefaultProfessionalSummary(jobTitle, company));
        result.setCoverLetter(generateDefaultCoverLetter(jobTitle, company));
        result.setSuggestedAnswers(generateDefaultSuggestedAnswersAsString(jobTitle, company));
        result.setCandidateStrengths(generateDefaultStrengthsList());
        result.setMatchingSkills(generateDefaultMatchingSkillsList());
        result.setMissingSkills(generateDefaultMissingSkillsList());
        result.setResumeHighlights(generateDefaultResumeHighlightsList());
        result.setMatchScore(generateDefaultMatchScore());
        result.setRecommendation(generateDefaultRecommendation());
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private String generateDefaultProfessionalSummary(String jobTitle, String company) {
        return "Results-oriented professional with a strong background in software engineering and system design. "
                + "Proven track record delivering scalable applications using Java and Spring Boot. "
                + "Committed to writing clean, maintainable code and solving complex technical challenges. "
                + "Eager to contribute expertise in " + jobTitle + " role at " + company + ".";
    }

    private String generateDefaultCoverLetter(String jobTitle, String company) {
        return "Dear Hiring Manager,\n\n"
                + "I am writing to express my strong interest in the " + jobTitle + " position at " + company + ". "
                + "With my background in Java development and software architecture, I am confident that my skills "
                + "align well with your team's requirements. My experience includes building scalable web applications, "
                + "implementing microservices architecture, and collaborating with cross-functional teams to deliver "
                + "high-quality software solutions.\n\n"
                + "I am particularly drawn to " + company + "'s reputation for innovation and commitment to excellence. "
                + "The opportunity to work on challenging projects using modern technologies such as Spring Boot, "
                + "REST APIs, and database optimization aligns perfectly with my career goals.\n\n"
                + "My key strengths include problem-solving abilities, attention to detail, and the ability to "
                + "translate business requirements into technical specifications. I thrive in agile environments "
                + "and am committed to continuous learning and improvement.\n\n"
                + "Thank you for considering my application. I look forward to the opportunity to discuss how "
                + "my experience and passion for software engineering can contribute to " + company + "'s success.\n\n"
                + "Sincerely,\n";
    }

    private String generateDefaultSuggestedAnswersAsString(String jobTitle, String company) {
        return String.join(" || ",
                "I am interested in this role because it combines my passion for " + jobTitle + " with the opportunity to work on innovative projects at " + company + ".",
                "I make a good fit for this position because I have extensive experience with Java and Spring Boot.",
                "My relevant experience includes " + jobTitle + " work involving Java development and Spring Boot microservices."
        );
    }

    private List<String> generateDefaultStrengthsList() {
        return Arrays.asList(
                "Strong Java and Spring Boot expertise",
                "Experience with microservices architecture",
                "Problem-solving and analytical skills",
                "Ability to work in agile teams"
        );
    }

    private List<String> generateDefaultMatchingSkillsList() {
        return Arrays.asList("Java", "Spring Boot", "REST APIs", "Database design");
    }

    private List<String> generateDefaultMissingSkillsList() {
        return Arrays.asList("Container orchestration", "Cloud platforms (AWS/Azure)");
    }

    private String generateDefaultRecommendation() {
        return "POSSIBLE_MATCH";
    }
}