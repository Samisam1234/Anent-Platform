package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link CareerAgent} responsible for structuring a candidate resume profile.
 *
 * <p>Pure delegation: if a {@code CandidateProfile} is already present in the
 * context it is reused as-is (no re-parsing); otherwise it builds one from the
 * raw resume text via the existing {@link ResumeProfileService}. It never
 * duplicates resume extraction/parsing logic.</p>
 *
 * <p>Optional AI reasoning (via {@link AgentReasoningService}) may enrich the
 * message, but the deterministic profile is always authoritative and reasoning
 * failure never fails the agent.</p>
 */
@Component
public class ResumeAgent implements CareerAgent {

    private static final Logger log = LoggerFactory.getLogger(ResumeAgent.class);

    private final ResumeProfileService resumeProfileService;
    private final AgentReasoningService reasoningService;

    /** Backward-compatible constructor (no reasoning layer wired). */
    public ResumeAgent(ResumeProfileService resumeProfileService) {
        this(resumeProfileService, null);
    }

    /** Full constructor with optional controlled reasoning. */
    @Autowired
    public ResumeAgent(ResumeProfileService resumeProfileService,
                       AgentReasoningService reasoningService) {
        this.resumeProfileService = resumeProfileService;
        this.reasoningService = reasoningService;
    }

    @Override
    public AgentType type() {
        return AgentType.RESUME;
    }

    @Override
    public boolean canExecute(AgentContext context) {
        if (context == null) {
            return false;
        }
        return context.candidateProfile() != null
                || (context.resumeText() != null && !context.resumeText().isBlank());
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        if (request == null || context == null) {
            return AgentResult.failed(AgentType.RESUME,
                    "ResumeAgent requires a request and a context.", "RESUME_INVALID_INPUT");
        }
        try {
            if (context.candidateProfile() != null) {
                CandidateProfile existing = context.candidateProfile();
                return AgentResult.completed(AgentType.RESUME,
                        withReasoning(AgentType.RESUME, context,
                                "Candidate profile already available; reused without re-parsing."),
                        existing);
            }
            CandidateProfile profile = resumeProfileService.buildProfile(context.resumeText());
            context.setCandidateProfile(profile);
            return AgentResult.completed(AgentType.RESUME,
                    withReasoning(AgentType.RESUME, context,
                            "Candidate profile built from resume text."),
                    profile);
        } catch (Exception e) {
            log.warn("ResumeAgent failed: {}", safeMessage(e));
            return AgentResult.failed(AgentType.RESUME,
                    "Could not build a candidate profile from the resume.", "RESUME_PARSE_FAILED");
        }
    }

    /**
     * Enriches the deterministic message with an optional AI reasoning summary, if
     * a reasoning service is wired and the AI call succeeds/validates. Returns the
     * original message on any reasoning failure (never fails the agent).
     */
    private String withReasoning(AgentType type, AgentContext context, String baseMessage) {
        if (reasoningService == null) {
            return baseMessage;
        }
        try {
            AgentReasoningResult result = reasoningService.reason(type, context,
                    "Provide controlled reasoning for the resume profile.");
            if (result == null || !result.aiUsed()) {
                return baseMessage;
            }
            return baseMessage + " Reasoning: " + result.summary();
        } catch (Exception e) {
            log.warn("ResumeAgent reasoning enrichment failed; deterministic result kept: {}",
                    safeMessage(e));
            return baseMessage;
        }
    }

    private static String safeMessage(Object value) {
        String s = value == null ? "unknown" : value.toString();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }
}
