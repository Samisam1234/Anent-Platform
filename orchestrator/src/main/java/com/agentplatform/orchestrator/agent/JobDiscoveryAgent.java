package com.agentplatform.orchestrator.agent;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchRequest;
import com.agentplatform.orchestrator.job.JobSearchResult;
import com.agentplatform.orchestrator.job.JobSearchService;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link CareerAgent} responsible for discovering jobs.
 *
 * <p>Delegates to the existing {@link JobSearchService}, which owns keyword
 * filtering, normalization, and deduplication. This agent does NOT re-implement
 * any of that logic. When a job is already selected in the context it is reused
 * directly, so downstream stages can run against a single known job.</p>
 */
@Component
public class JobDiscoveryAgent implements CareerAgent {

    private static final Logger log = LoggerFactory.getLogger(JobDiscoveryAgent.class);

    private final JobSearchService jobSearchService;

    public JobDiscoveryAgent(JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    @Override
    public AgentType type() {
        return AgentType.JOB_DISCOVERY;
    }

    @Override
    public boolean canExecute(AgentContext context) {
        // Can run without dependencies: it either searches or reuses a supplied job.
        return context != null;
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentContext context) {
        if (request == null || context == null) {
            return AgentResult.failed(AgentType.JOB_DISCOVERY,
                    "JobDiscoveryAgent requires a request and a context.", "JOB_DISCOVERY_INVALID_INPUT");
        }
        try {
            if (context.job() != null) {
                Job job = context.job();
                return AgentResult.completed(AgentType.JOB_DISCOVERY,
                        "Selected job reused for downstream analysis.", job);
            }
            JobSearchRequest searchRequest = buildSearchRequest(context);
            JobSearchResult result = jobSearchService.search(searchRequest);
            context.setJobSearchResult(result);
            return AgentResult.completed(AgentType.JOB_DISCOVERY,
                    "Job discovery completed. Jobs: " + result.jobs().size() + ".",
                    result);
        } catch (Exception e) {
            log.warn("JobDiscoveryAgent failed: {}", safeMessage(e));
            return AgentResult.failed(AgentType.JOB_DISCOVERY,
                    "Job discovery could not complete.", "JOB_DISCOVERY_FAILED");
        }
    }

    private static JobSearchRequest buildSearchRequest(AgentContext context) {
        CandidateProfile profile = context.candidateProfile();
        List<String> keywords = profile != null ? profile.preferredRoles() : List.of();
        String location = profile != null ? firstNonBlank(profile.preferredLocations()) : null;
        return JobSearchRequest.of(keywords, location, null, null, null, 100);
    }

    private static String firstNonBlank(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String safeMessage(Object value) {
        String s = value == null ? "unknown" : value.toString();
        return s.length() > 220 ? s.substring(0, 220) + "…" : s;
    }
}
