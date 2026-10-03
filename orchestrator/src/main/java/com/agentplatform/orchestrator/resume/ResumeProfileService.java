package com.agentplatform.orchestrator.resume;

import com.agentplatform.core.ai.AiErrorClassifier;
import com.agentplatform.core.config.OllamaChatModelFactory;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResumeProfileService {
    private static final Logger log = LoggerFactory.getLogger(ResumeProfileService.class);
    private static final String PROMPT_TEMPLATE = "You are a professional resume parser.\nExtract structured information from the resume text provided below.\n\nReturn ONLY a valid JSON object \u2014 no explanations, no markdown, no code fences.\nUse empty strings \"\" for missing scalar fields.\nUse empty arrays [] for missing list fields.\n\nRequired JSON structure:\n{\n  \"name\": \"Full Name\",\n  \"email\": \"email@example.com\",\n  \"phone\": \"+1-555-000-0000\",\n  \"location\": \"City, Country\",\n  \"education\": [\"Degree at Institution (Year)\", \"...\"],\n  \"skills\": [\"skill1\", \"skill2\"],\n  \"experience\": [\"Job Title at Company (duration): brief description\", \"...\"],\n  \"internships\": [\"Intern Title at Company (duration): brief description\", \"...\"],\n  \"projects\": [\"Project Name: brief description\", \"...\"],\n  \"certifications\": [\"Certification Name (Issuer, Year)\", \"...\"],\n  \"softwareSkills\": [\"Java\", \"Spring Boot\", \"Docker\", \"...\"],\n  \"hardwareSkills\": [\"Arduino\", \"Raspberry Pi\", \"...\"],\n  \"preferredRoles\": [\"Backend Engineer\", \"...\"],\n  \"preferredLocations\": [\"Remote\", \"New York\", \"...\"]\n}\n\nResume text:\n---\n%s\n---\n\nReturn ONLY the JSON object. Nothing else.\n";
    private final OllamaChatModelFactory ollamaChatModelFactory;
    private final ObjectMapper objectMapper;
    private final DeterministicCandidateProfileBuilder deterministicBuilder;

    public ResumeProfileService(OllamaChatModelFactory ollamaChatModelFactory, ObjectMapper objectMapper) {
        this.ollamaChatModelFactory = ollamaChatModelFactory;
        this.objectMapper = objectMapper;
        this.deterministicBuilder = new DeterministicCandidateProfileBuilder();
    }

    public CandidateProfile buildProfile(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException("Resume text must not be blank");
        }
        log.info("Sending resume ({} chars) to LLM for structured parsing", (Object)resumeText.length());
        try {
            String llmResponse = this.callLlm(resumeText);
            return this.parseProfile(llmResponse);
        }
        catch (ResumeException e) {
            log.warn("LLM resume parsing unavailable ({}); falling back to the deterministic parser", (Object)e.getMessage());
            return this.deterministicBuilder.build(resumeText);
        }
    }

    private String callLlm(String resumeText) {
        String prompt = String.format(PROMPT_TEMPLATE, resumeText);
        try {
            ChatModel ollamaModel = this.ollamaChatModelFactory.chatModel(this.ollamaChatModelFactory.resolveModelName(null));
            String response = ollamaModel.chat(prompt);
            log.debug("Ollama responded with {} characters", (Object)(response != null ? response.length() : 0));
            return response;
        }
        catch (Exception e) {
            AiErrorClassifier.Failure failure = AiErrorClassifier.classify((Throwable)e, (String)"Ollama");
            log.error("Ollama LLM call failed during resume parsing: {}", (Object)failure.message(), (Object)e);
            throw new ResumeException(failure.message(), e);
        }
    }

    private CandidateProfile parseProfile(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            throw new ResumeException("The AI model returned an empty response. Please try again.");
        }
        String json = this.extractJsonBlock(llmResponse);
        try {
            CandidateProfile profile = (CandidateProfile)this.objectMapper.readValue(json, CandidateProfile.class);
            if (profile.name() == null || profile.name().isBlank()) {
                log.warn("LLM profile has no name. Raw LLM response snippet: {}", (Object)(llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse));
                throw new ResumeException("Could not extract a valid candidate profile from the resume. The AI response was incomplete. Please try again.");
            }
            log.info("Successfully parsed CandidateProfile for: {}", (Object)profile.name());
            return profile;
        }
        catch (ResumeException re) {
            throw re;
        }
        catch (Exception e) {
            log.error("Failed to deserialize LLM JSON response: {}. Snippet: {}", (Object)e.getMessage(), (Object)(llmResponse.length() > 300 ? llmResponse.substring(0, 300) : llmResponse));
            throw new ResumeException("The AI model returned an unparseable response. Please try again.", e);
        }
    }

    private String extractJsonBlock(String text) {
        int start = text.indexOf(123);
        int end = text.lastIndexOf(125);
        if (start == -1 || end == -1 || start >= end) {
            log.error("No JSON object found in LLM response. Snippet: {}", (Object)(text.length() > 300 ? text.substring(0, 300) : text));
            throw new ResumeException("The AI model did not return a recognizable JSON response. Please try again.");
        }
        return text.substring(start, end + 1);
    }
}

