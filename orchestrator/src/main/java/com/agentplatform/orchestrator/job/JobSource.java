package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.job.JobSearchRequest;
import java.util.List;

public interface JobSource {
    public String getSourceName();

    public boolean isLive();

    public boolean isAvailable();

    public List<Job> search(JobSearchRequest var1);
}

