package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown=true)
public record JobMatch(Job job, int matchScore, RecommendationLevel recommendation, List<String> matchedSkills, List<String> missingSkills, List<String> matchedPreferredSkills, List<String> missingPreferredSkills, boolean locationMatch, boolean roleMatch, ExperienceMatchLevel experienceMatch, CareerTrack careerTrack, String explanation, List<String> strengths, List<String> concerns, double skillScore, double roleScore, double locationScore, double experienceScore, double trackScore, double educationScore) {
    public JobMatch {
        matchedSkills = matchedSkills != null ? List.copyOf(matchedSkills) : List.of();
        missingSkills = missingSkills != null ? List.copyOf(missingSkills) : List.of();
        matchedPreferredSkills = matchedPreferredSkills != null ? List.copyOf(matchedPreferredSkills) : List.of();
        missingPreferredSkills = missingPreferredSkills != null ? List.copyOf(missingPreferredSkills) : List.of();
        strengths = strengths != null ? List.copyOf(strengths) : List.of();
        concerns = concerns != null ? List.copyOf(concerns) : List.of();
    }
}

