package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.job.Job;
import java.util.List;

public record JobSearchResult(List<Job> jobs, int total, String source, boolean live, String message) {
    public JobSearchResult {
        jobs = jobs != null ? List.copyOf(jobs) : List.of();
    }
}

