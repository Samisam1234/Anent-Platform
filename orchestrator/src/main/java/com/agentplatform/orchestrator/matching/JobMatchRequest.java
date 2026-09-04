package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.List;

public record JobMatchRequest(Long candidateProfileId, List<String> keywords, String location, String experience, String employmentType, Integer limit, Integer minScore, CareerTrack trackFilter, CandidateProfile candidateProfile, List<Job> jobs) {
    public JobMatchRequest {
        keywords = keywords != null ? List.copyOf(keywords) : List.of();
        if (limit != null && (limit < 1 || limit > 100)) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        limit = limit == null ? 20 : limit;
        if (minScore != null && (minScore < 0 || minScore > 100)) {
            throw new IllegalArgumentException("Minimum score must be between 0 and 100");
        }
        jobs = jobs != null ? List.copyOf(jobs) : List.of();
    }

    public static JobMatchRequest of(Long candidateProfileId, List<String> keywords, String location, Integer limit) {
        return new JobMatchRequest(candidateProfileId, keywords, location, null, null, limit, null, null, null, null);
    }
}

