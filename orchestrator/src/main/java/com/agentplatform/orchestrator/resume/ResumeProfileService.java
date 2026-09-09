package com.agentplatform.orchestrator.resume;

import com.agentplatform.core.ai.AiErrorClassifier;
import com.agentplatform.core.config.OllamaChatModelFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ResumeProfileService {
    private static final Logger log = LoggerFactory.getLogger(ResumeProfileService.class);
    private static final String PROMPT_TEMPLATE = "You are a professional resume parser.\nExtract structured information from the resume text provided below.\n\nReturn ONLY a valid JSON object \u2014 no explanations, no markdown, no code fences.\nUse empty strings \"\" for missing scalar fields.\nUse empty arrays [] for missing list fields.\n\nRequired JSON structure:\n{\n  \"name\": \"Full Name\",\n  \"email\": \"email@example.com\",\n  \"phone\": \"+1-555-000-0000\",\n  \"location\": \"City, Country\",\n  \"education\": [\"Degree at Institution (Year)\", \"...\"],\n  \"skills\": [\"skill1\", \"skill2\"],\n  \"experience\": [\"Job Title at Company (duration): brief description\", \"...\"],\n  \"internships\": [\"Intern Title at Company (duration): brief description\", \"...\"],\n  \"projects\": [\"Project Name: brief description\", \"...\"],\n  \"certifications\": [\"Certification Name (Issuer, Year)\", \"...\"],\n  \"softwareSkills\": [\"Java\", \"Spring Boot\", \"Docker\", \"...\"],\n  \"hardwareSkills\": [\"Arduino\", \"Raspberry Pi\", \"...\"],\n  \"preferredRoles\": [\"Backend Engineer\", \"...\"],\n  \"preferredLocations\": [\"Remote\", \"New York\", \"...\"]\n}\n\nResume text:\n---\n%s\n---\n\nReturn ONLY the JSON object. Nothing else.\n";
    private final OllamaChatModelFactory ollamaChatModelFactory;
    private final ObjectMapper objectMapper;
    private final DeterministicCandidateProfileBuilder deterministicBuilder;

    /**
     * Daemon pool that carries the LLM call so it can be given a hard wall-clock
     * deadline. Mirrors {@code AiStatusService.PROBE_EXECUTOR}: the model's own
     * HTTP timeout is a socket-idle read timeout and does NOT bound how long a
     * generation may run, so without this the /resume/upload request can stay
     * open indefinitely and the UI never leaves "Parsing resume…".
     */
    private static final ExecutorService AI_EXECUTOR = Executors.newFixedThreadPool(4, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "resume-ai-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    /**
     * Result of a build, including whether the AI model was actually used.
     *
     * @param profile the parsed profile (never null)
     * @param aiUsed  true when the LLM produced it, false when the deterministic
     *                parser did (AI unavailable, timed out, or unparseable)
     * @param notice  user-facing explanation when {@code aiUsed} is false, else null
     */
    public record ProfileOutcome(CandidateProfile profile, boolean aiUsed, String notice) {}

    public ResumeProfileService(OllamaChatModelFactory ollamaChatModelFactory, ObjectMapper objectMapper) {
        this.ollamaChatModelFactory = ollamaChatModelFactory;
        this.objectMapper = objectMapper;
        this.deterministicBuilder = new DeterministicCandidateProfileBuilder();
    }

    public CandidateProfile buildProfile(String resumeText) {
        return buildProfileOutcome(resumeText).profile();
    }

    /**
     * Parses the resume text into a {@link CandidateProfile}, always returning
     * within the configured AI deadline. When the LLM cannot deliver, the
     * deterministic parser produces the profile instead, so the caller never
     * waits indefinitely and the user still gets a usable profile.
     */
    public ProfileOutcome buildProfileOutcome(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException("Resume text must not be blank");
        }
        log.info("Sending resume ({} chars) to LLM for structured parsing", resumeText.length());
        try {
            String llmResponse = callLlm(resumeText);
            return new ProfileOutcome(parseProfile(llmResponse), true, null);
        } catch (ResumeException e) {
            log.warn("LLM resume parsing unavailable ({}); falling back to the deterministic parser", e.getMessage());
            return new ProfileOutcome(deterministicBuilder.build(resumeText), false,
                    "Built-in parser used — " + e.getMessage());
        }
    }

    /**
     * Calls the LLM under a hard wall-clock deadline of
     * {@code ollama.reasoning-timeout}. On timeout the pending call is cancelled
     * and a {@link ResumeException} is raised, which routes to the deterministic
     * fallback in {@link #buildProfileOutcome}.
     */
    private String callLlm(String resumeText) {
        Duration configured = ollamaChatModelFactory.timeout();
        Duration deadline = configured != null ? configured : OllamaChatModelFactory.DEFAULT_TIMEOUT;
        Future<String> future = AI_EXECUTOR.submit(() -> doCallLlm(resumeText));
        try {
            return future.get(deadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new ResumeException("the AI model did not respond within "
                    + deadline.toSeconds() + "s");
        } catch (InterruptedException ie) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResumeException("resume parsing was interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            if (cause instanceof ResumeException re) {
                throw re;
            }
            throw new ResumeException(cause.getMessage() != null
                    ? cause.getMessage() : "the AI model call failed", cause);
        }
    }

    private String doCallLlm(String resumeText) {
        String prompt = String.format(PROMPT_TEMPLATE, resumeText);
        try {
            ChatModel ollamaModel = ollamaChatModelFactory.chatModel(ollamaChatModelFactory.resolveModelName(null));
            String response = ollamaModel.chat(prompt);
            log.debug("Ollama responded with {} characters", response != null ? response.length() : 0);
            return response;
        } catch (Exception e) {
            AiErrorClassifier.Failure failure = AiErrorClassifier.classify(e, "Ollama");
            log.error("Ollama LLM call failed during resume parsing: {}", failure.message(), e);
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

