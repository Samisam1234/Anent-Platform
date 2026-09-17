package com.agentplatform.orchestrator.service;

import com.agentplatform.orchestrator.agent.AgentContext;
import com.agentplatform.orchestrator.agent.EvaluationResult;
import com.agentplatform.orchestrator.agent.OrchestrationRun;
import org.springframework.stereotype.Service;

/**
 * Stateless evaluation aggregator.
 *
 * <p>Observes the deterministic results already produced by the orchestration
 * pipeline and extracts a coherent {@link EvaluationResult}. No external AI
 * calls, no persistence, no configuration, no clocks, no randomness.</p>
 */
@Service
public class EvaluationService {

    /**
     * Builds an evaluation snapshot from the completed orchestration run and
     * its run-local context.
     *
     * @param run the immutable orchestration run snapshot (may be {@code null})
     * @param context the run-local agent context holding domain results (may be {@code null})
     * @return an immutable {@link EvaluationResult}; never {@code null}
     */
    public EvaluationResult evaluate(OrchestrationRun run, AgentContext context) {
        return EvaluationResult.from(run, context);
    }
}