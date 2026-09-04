package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.JobMatchRequest;
import com.agentplatform.orchestrator.matching.JobMatchResult;
import com.agentplatform.orchestrator.matching.JobMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link CareerAgent} responsible for matching a candidate against discovered jobs.
 *
 * <p>Delegates to the existing {@link JobMatchingService}, which is the sole
 * authority on the scoring algorithm. This agent does NOT implement any scoring,
 * ranking, or filtering logic of its own.</p>
 */
@Component
public class MatchingAgent implements CareerAgent {

    private static final Logger log = LoggerFactory.getLogger(MatchingAgent.class);

    private final JobMatchingService jobMatchingService;

    public MatchingAgent(JobMatchingService jobMatchingService) {
        this.jobMatchingService = jobMatchingService;
    }

    @Override
    public AgentType type() {
        return AgentType.MATCHING;
    }

    @Override
    public boolean canExecute(AgentContext context) {
        if (context == null) {
            return false;
        }
        if (context.candidateProfile() == null) {
            return false;
        }
        return context.job() != null
                || (context.jobSearchResult() != null && !context.jobSearchResult().jobs().isEmpty());
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        if (request == null || context == null) {
            return AgentResult.failed(AgentType.MATCHING,
                    "MatchingAgent requires a request and a context.", "MATCHING_INVALID_INPUT");
        }
        try {
            List<Job> jobs;
            if (context.job() != null) {
                jobs = List.of(context.job());
            } else {
                jobs = context.jobSearchResult().jobs();
            }
            JobMatchRequest matchRequest = new JobMatchRequest(
                    context.candidateId(), null, null, null, null, null, null, null,
                    context.candidateProfile(), jobs);
            JobMatchResult result = jobMatchingService.matchJobs(matchRequest);
            context.setJobMatchResult(result);
            return AgentResult.completed(AgentType.MATCHING,
                    "Matching completed. Total jobs: " + result.totalJobs() + ".",
                    result);
        } catch (Exception e) {
            log.warn("MatchingAgent failed: {}", safeMessage(e));
            return AgentResult.failed(AgentType.MATCHING,
                    "Job matching could not complete.", "MATCHING_FAILED");
        }
    }

    private static String safeMessage(Object value) {
        String s = value == null ? "unknown" : value.toString();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }
}
