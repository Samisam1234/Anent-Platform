package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.matching.JobMatch;
import java.util.List;

public record JobMatchResult(Long candidateProfileId, String candidateName, int totalJobs, List<JobMatch> matches, String source, boolean live, String message) {
    public JobMatchResult {
        matches = matches != null ? List.copyOf(matches) : List.of();
    }
}

