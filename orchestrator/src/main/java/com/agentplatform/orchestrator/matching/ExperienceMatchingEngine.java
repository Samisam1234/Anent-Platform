package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ExperienceMatchingEngine {
    public ExperienceEvaluation evaluate(CandidateProfile candidate, Job job) {
        if (job == null) {
            return new ExperienceEvaluation(ExperienceMatchLevel.STRONG_MATCH, 1.0);
        }
        String expReq = (job.experienceRequirement() != null ? job.experienceRequirement() : "").toLowerCase(Locale.ROOT);
        String jobTitle = (job.title() != null ? job.title() : "").toLowerCase(Locale.ROOT);
        if (expReq.contains("fresh") || expReq.contains("entry") || expReq.contains("0-1") || expReq.contains("intern") || expReq.contains("graduate") || expReq.contains("trainee") || expReq.contains("0 years") || jobTitle.contains("intern") || jobTitle.contains("trainee")) {
            return new ExperienceEvaluation(ExperienceMatchLevel.STRONG_MATCH, 1.0);
        }
        if (expReq.contains("0-2") || expReq.contains("1-2") || expReq.contains("1-3") || expReq.contains("junior") || expReq.contains("associate") || expReq.contains("1 year") || jobTitle.contains("junior") || jobTitle.contains("associate")) {
            return new ExperienceEvaluation(ExperienceMatchLevel.PARTIAL_MATCH, 0.8);
        }
        if (expReq.contains("5+") || expReq.contains("5-8") || expReq.contains("6+") || expReq.contains("7+") || expReq.contains("8+") || jobTitle.contains("senior") || jobTitle.contains("lead") || jobTitle.contains("principal") || jobTitle.contains("architect")) {
            return new ExperienceEvaluation(ExperienceMatchLevel.NO_MATCH, 0.1);
        }
        if (expReq.contains("2-4") || expReq.contains("3-5") || expReq.contains("2-5") || expReq.contains("3+") || expReq.contains("3 years") || expReq.contains("4 years")) {
            return new ExperienceEvaluation(ExperienceMatchLevel.WEAK_MATCH, 0.4);
        }
        return new ExperienceEvaluation(ExperienceMatchLevel.PARTIAL_MATCH, 0.75);
    }

    public record ExperienceEvaluation(ExperienceMatchLevel experienceMatch, double experienceScore) {
    }
}

