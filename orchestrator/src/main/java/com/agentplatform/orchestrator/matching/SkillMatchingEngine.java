package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.SkillNormalizer;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SkillMatchingEngine {
    public SkillEvaluation evaluate(CandidateProfile candidate, Job job) {
        if (candidate == null || job == null) {
            return new SkillEvaluation(List.of(), List.of(), List.of(), List.of(), 0.0);
        }
        LinkedHashSet<String> candidateSkillPool = new LinkedHashSet<String>();
        if (candidate.softwareSkills() != null) {
            candidateSkillPool.addAll(candidate.softwareSkills());
        }
        if (candidate.hardwareSkills() != null) {
            candidateSkillPool.addAll(candidate.hardwareSkills());
        }
        if (candidate.skills() != null) {
            candidateSkillPool.addAll(candidate.skills());
        }
        if (candidate.projects() != null) {
            candidateSkillPool.addAll(candidate.projects());
        }
        if (candidate.certifications() != null) {
            candidateSkillPool.addAll(candidate.certifications());
        }
        if (candidate.experience() != null) {
            candidateSkillPool.addAll(candidate.experience());
        }
        List<String> jobRequired = job.requiredSkills() != null ? job.requiredSkills() : List.of();
        List<String> jobPreferred = job.preferredSkills() != null ? job.preferredSkills() : List.of();
        ArrayList<String> matchedReq = new ArrayList<String>();
        ArrayList<String> missingReq = new ArrayList<String>();
        for (String string : jobRequired) {
            if (this.isSkillInPool(string, candidateSkillPool)) {
                matchedReq.add(string);
                continue;
            }
            missingReq.add(string);
        }
        ArrayList<String> matchedPref = new ArrayList<String>();
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string : jobPreferred) {
            if (this.isSkillInPool(string, candidateSkillPool)) {
                matchedPref.add(string);
                continue;
            }
            arrayList.add(string);
        }
        double baseScore = jobRequired.isEmpty() ? 1.0 : (double)matchedReq.size() / (double)jobRequired.size();
        if (!jobPreferred.isEmpty() && !matchedPref.isEmpty()) {
            double prefBonus = 0.1 * ((double)matchedPref.size() / (double)jobPreferred.size());
            baseScore = Math.min(1.0, baseScore + prefBonus);
        }
        return new SkillEvaluation(List.copyOf(matchedReq), List.copyOf(missingReq), List.copyOf(matchedPref), List.copyOf(arrayList), baseScore);
    }

    private boolean isSkillInPool(String skill, Set<String> pool) {
        if (skill == null || skill.isBlank()) {
            return false;
        }
        for (String candidateItem : pool) {
            if (!SkillNormalizer.isMatch(candidateItem, skill)) continue;
            return true;
        }
        return false;
    }

    public record SkillEvaluation(List<String> matchedRequiredSkills, List<String> missingRequiredSkills, List<String> matchedPreferredSkills, List<String> missingPreferredSkills, double skillScore) {
    }
}

