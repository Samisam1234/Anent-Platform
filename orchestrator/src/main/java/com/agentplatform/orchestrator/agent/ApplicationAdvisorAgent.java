package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.advisor.ApplicationAdvisorResponse;
import com.agentplatform.orchestrator.advisor.ApplicationAdvisorService;
import com.agentplatform.orchestrator.application.ApplicationEmailDraft;
import com.agentplatform.orchestrator.application.ApplicationPreparationService;
import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysis;
import com.agentplatform.orchestrator.tailoring.ResumeTailoringAnalysisService;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraft;
import com.agentplatform.orchestrator.tailoring.TailoredResumeDraftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link CareerAgent} responsible for application materials — ATS tailoring
 * analysis, a tailored resume draft, an email draft, and deterministic application advice.
 *
 * <p>Delegates to the existing {@link ResumeTailoringAnalysisService},
 * {@link TailoredResumeDraftService}, {@link ApplicationPreparationService},
 * and {@link ApplicationAdvisorService}.
 * It NEVER sends email: producing a review-only draft is a distinct, explicitly
 * user-approved action downstream.</p>
 *
 * <p>Requires a candidate profile and a job. The gap analysis is reused when
 * present (for ATS tailoring); if absent an empty analysis is used safely.</p>
 *
 * <p>Optional AI reasoning (via {@link AgentReasoningService}) may enrich the
 * message, but the deterministic materials are always authoritative and
 * reasoning failure never fails the agent.</p>
 */
@Component
public class ApplicationAdvisorAgent implements CareerAgent {

    private static final Logger log = LoggerFactory.getLogger(ApplicationAdvisorAgent.class);

    private final ResumeTailoringAnalysisService tailoringAnalysisService;
    private final TailoredResumeDraftService tailoredResumeDraftService;
    private final ApplicationPreparationService applicationPreparationService;
    private final AgentReasoningService reasoningService;
    private final ApplicationAdvisorService applicationAdvisorService;

    /** Backward-compatible constructor (no reasoning layer or advisor service wired). */
    public ApplicationAdvisorAgent(ResumeTailoringAnalysisService tailoringAnalysisService,
                                   TailoredResumeDraftService tailoredResumeDraftService,
                                   ApplicationPreparationService applicationPreparationService) {
        this(tailoringAnalysisService, tailoredResumeDraftService,
                applicationPreparationService, null, null);
    }

    /** Constructor with reasoning layer (no advisor service wired). */
    public ApplicationAdvisorAgent(ResumeTailoringAnalysisService tailoringAnalysisService,
                                   TailoredResumeDraftService tailoredResumeDraftService,
                                   ApplicationPreparationService applicationPreparationService,
                                   AgentReasoningService reasoningService) {
        this(tailoringAnalysisService, tailoredResumeDraftService,
                applicationPreparationService, reasoningService, null);
    }

    /** Full constructor with optional controlled reasoning and advisor service. */
    @Autowired
    public ApplicationAdvisorAgent(ResumeTailoringAnalysisService tailoringAnalysisService,
                                   TailoredResumeDraftService tailoredResumeDraftService,
                                   ApplicationPreparationService applicationPreparationService,
                                   @Autowired(required = false) AgentReasoningService reasoningService,
                                   @Autowired(required = false) ApplicationAdvisorService applicationAdvisorService) {
        this.tailoringAnalysisService = tailoringAnalysisService;
        this.tailoredResumeDraftService = tailoredResumeDraftService;
        this.applicationPreparationService = applicationPreparationService;
        this.reasoningService = reasoningService;
        this.applicationAdvisorService = applicationAdvisorService;
    }

    @Override
    public AgentType type() {
        return AgentType.APPLICATION_ADVISOR;
    }

    @Override
    public boolean canExecute(AgentContext context) {
        if (context == null) {
            return false;
        }
        return context.candidateProfile() != null && context.job() != null;
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        if (request == null || context == null) {
            return AgentResult.failed(AgentType.APPLICATION_ADVISOR,
                    "ApplicationAdvisorAgent requires a request and a context.",
                    "APPLICATION_ADVISOR_INVALID_INPUT");
        }
        try {
            CareerGapAnalysis gap = context.careerGapAnalysis();
            ResumeTailoringAnalysis tailoring = tailoringAnalysisService.analyze(
                    context.candidateProfile(), context.job(), gap);
            context.setTailoringAnalysis(tailoring);

            TailoredResumeDraft draft = tailoredResumeDraftService.generate(
                    context.candidateProfile(), context.job(), tailoring);
            context.setTailoredDraft(draft);

            ApplicationEmailDraft emailDraft = applicationPreparationService.prepare(
                    context.candidateProfile(), context.job(), draft);
            context.setApplicationDraft(emailDraft);

            if (applicationAdvisorService != null) {
                Long candidateId = (context.candidateId() != null && context.candidateId() > 0)
                        ? context.candidateId()
                        : 1L;
                ApplicationAdvisorResponse advisorResponse = applicationAdvisorService.adviseFromDomain(
                        candidateId,
                        context.candidateProfile(),
                        context.job());
                context.setApplicationAdvisorResponse(advisorResponse);
            }

            return AgentResult.completed(AgentType.APPLICATION_ADVISOR,
                    withReasoning(AgentType.APPLICATION_ADVISOR, context,
                            "Application materials prepared (review-only; no email sent), ATS readiness: "
                                     + tailoring.atsReadiness().score() + "."),
                    emailDraft);
        } catch (Exception e) {
            log.warn("ApplicationAdvisorAgent failed: {}", safeMessage(e));
            return AgentResult.failed(AgentType.APPLICATION_ADVISOR,
                    "Application materials could not be prepared.", "APPLICATION_ADVISOR_FAILED");
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
                    "Provide controlled reasoning for the ATS tailoring and application materials.");
            if (result == null || !result.aiUsed()) {
                return baseMessage;
            }
            return baseMessage + " Reasoning: " + result.summary();
        } catch (Exception e) {
            log.warn("ApplicationAdvisorAgent reasoning enrichment failed; deterministic result kept: {}",
                    safeMessage(e));
            return baseMessage;
        }
    }

    private static String safeMessage(Object value) {
        String s = value == null ? "unknown" : value.toString();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }
}
