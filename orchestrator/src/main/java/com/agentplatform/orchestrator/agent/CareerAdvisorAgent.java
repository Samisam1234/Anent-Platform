package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.CareerGapAnalysisService;
import com.agentplatform.orchestrator.gap.CareerImprovementPlan;
import com.agentplatform.orchestrator.gap.CareerImprovementPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link CareerAgent} responsible for career-gap analysis and improvement guidance.
 *
 * <p>Delegates to the existing {@link CareerGapAnalysisService} (authoritative gap
 * determination) and {@link CareerImprovementPlanService}. The gap analysis is the
 * required, blocking stage — without a candidate and a job it must not run. The AI
 * improvement-plan enrichment is optional: if it fails the service returns a purely
 * deterministic plan, so the advisor still completes.</p>
 *
 * <p>Optional AI reasoning (via {@link AgentReasoningService}) may enrich the
 * message, but the deterministic gap analysis is always authoritative and
 * reasoning failure never fails the agent.</p>
 */
@Component
public class CareerAdvisorAgent implements CareerAgent {

    private static final Logger log = LoggerFactory.getLogger(CareerAdvisorAgent.class);

    private final CareerGapAnalysisService gapAnalysisService;
    private final CareerImprovementPlanService improvementPlanService;
    private final AgentReasoningService reasoningService;

    /** Backward-compatible constructor (no reasoning layer wired). */
    public CareerAdvisorAgent(CareerGapAnalysisService gapAnalysisService,
                              CareerImprovementPlanService improvementPlanService) {
        this(gapAnalysisService, improvementPlanService, null);
    }

    /** Full constructor with optional controlled reasoning. */
    @Autowired
    public CareerAdvisorAgent(CareerGapAnalysisService gapAnalysisService,
                              CareerImprovementPlanService improvementPlanService,
                              AgentReasoningService reasoningService) {
        this.gapAnalysisService = gapAnalysisService;
        this.improvementPlanService = improvementPlanService;
        this.reasoningService = reasoningService;
    }

    @Override
    public AgentType type() {
        return AgentType.CAREER_ADVISOR;
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
            return AgentResult.failed(AgentType.CAREER_ADVISOR,
                    "CareerAdvisorAgent requires a request and a context.", "CAREER_ADVISOR_INVALID_INPUT");
        }
        try {
            CareerGapAnalysis gap = gapAnalysisService.analyze(
                    context.candidateProfile(), context.job());
            context.setCareerGapAnalysis(gap);

            CareerImprovementPlan plan;
            try {
                plan = improvementPlanService.generatePlan(gap);
            } catch (Exception e) {
                log.warn("Career improvement plan enrichment failed; using deterministic gap result: {}",
                        safeMessage(e));
                plan = null;
            }
            context.setImprovementPlan(plan);

            return AgentResult.completed(AgentType.CAREER_ADVISOR,
                    withReasoning(AgentType.CAREER_ADVISOR, context,
                            "Career gap analysis completed. Severity: " + gap.overallGapSeverity() + "."),
                    gap);
        } catch (Exception e) {
            log.warn("CareerAdvisorAgent failed: {}", safeMessage(e));
            return AgentResult.failed(AgentType.CAREER_ADVISOR,
                    "Career gap analysis could not complete.", "CAREER_ADVISOR_FAILED");
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
                    "Provide controlled reasoning for the career-gap analysis and priorities.");
            if (result == null || !result.aiUsed()) {
                return baseMessage;
            }
            return baseMessage + " Reasoning: " + result.summary();
        } catch (Exception e) {
            log.warn("CareerAdvisorAgent reasoning enrichment failed; deterministic result kept: {}",
                    safeMessage(e));
            return baseMessage;
        }
    }

    private static String safeMessage(Object value) {
        String s = value == null ? "unknown" : value.toString();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }
}
