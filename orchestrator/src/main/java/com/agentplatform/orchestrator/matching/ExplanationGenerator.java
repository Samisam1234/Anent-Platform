package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.ExperienceMatchLevel;
import com.agentplatform.orchestrator.matching.RecommendationLevel;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExplanationGenerator {
    public String generate(CandidateProfile candidate, Job job, int score, RecommendationLevel recommendation, List<String> matchedSkills, List<String> missingSkills, boolean locationMatch, ExperienceMatchLevel experienceMatch, CareerTrack careerTrack) {
        StringBuilder sb = new StringBuilder();
        String recLabel = recommendation.getDisplayName().toLowerCase();
        String trackName = switch (careerTrack) {
            default -> throw new IllegalArgumentException("Unknown career track");
            case CareerTrack.SOFTWARE -> "Software Engineering";
            case CareerTrack.HARDWARE -> "Hardware / ECE / VLSI";
            case CareerTrack.MIXED -> "Cross-disciplinary Software & Hardware";
            case CareerTrack.UNKNOWN -> "Engineering";
        };
        sb.append("This job is a ").append(recLabel).append(" (").append(score).append("%) for your ").append(trackName).append(" track. ");
        if (!matchedSkills.isEmpty()) {
            sb.append("Your profile matches key requirements: ").append(this.formatSkillList(matchedSkills)).append(". ");
        } else {
            sb.append("No direct required technical skills matched your resume. ");
        }
        if (locationMatch) {
            String loc = job.location() != null ? job.location() : "preferred location";
            sb.append("Location (").append(loc).append(") aligns with your preferences. ");
        } else if (job.location() != null && !job.location().isBlank()) {
            sb.append("Location is listed as ").append(job.location()).append(". ");
        }
        if (experienceMatch == ExperienceMatchLevel.STRONG_MATCH) {
            sb.append("Experience level aligns well with early-career qualifications. ");
        } else if (experienceMatch == ExperienceMatchLevel.NO_MATCH) {
            sb.append("Note: The role targets senior-level experience (").append(job.experienceRequirement()).append("). ");
        }
        if (!missingSkills.isEmpty()) {
            sb.append("Key missing requirement: ").append(this.formatSkillList(missingSkills)).append(".");
        } else if (!matchedSkills.isEmpty()) {
            sb.append("All primary required skills are covered in your profile.");
        }
        return sb.toString().trim();
    }

    public List<String> generateStrengths(List<String> matchedSkills, List<String> matchedPreferredSkills, boolean locationMatch, boolean roleMatch, ExperienceMatchLevel experienceMatch, double educationScore) {
        ArrayList<String> strengths = new ArrayList<String>();
        if (matchedSkills != null && !matchedSkills.isEmpty()) {
            int show = Math.min(3, matchedSkills.size());
            strengths.add("Matches key requirements: " + this.formatSkillList(matchedSkills.subList(0, show)));
        }
        if (roleMatch) {
            strengths.add("Role aligns with your preferred positions.");
        }
        if (locationMatch) {
            strengths.add("Location matches your preference.");
        }
        if (educationScore >= 0.9) {
            strengths.add("Educational background lines up with the role's field.");
        }
        if (experienceMatch == ExperienceMatchLevel.STRONG_MATCH) {
            strengths.add("Experience level aligns with early-career qualifications.");
        }
        if (matchedPreferredSkills != null && !matchedPreferredSkills.isEmpty()) {
            strengths.add("Also matches desired add-ons: " + this.formatSkillList(matchedPreferredSkills));
        }
        return strengths.stream().limit(4L).toList();
    }

    public List<String> generateConcerns(List<String> missingSkills, List<String> missingPreferredSkills, boolean locationMatch, boolean roleMatch, Job job, ExperienceMatchLevel experienceMatch) {
        ArrayList<String> concerns = new ArrayList<String>();
        if (missingSkills != null && !missingSkills.isEmpty()) {
            concerns.add("Missing required: " + this.formatSkillList(missingSkills));
        }
        if (experienceMatch == ExperienceMatchLevel.NO_MATCH && job != null && job.experienceRequirement() != null && !job.experienceRequirement().isBlank()) {
            concerns.add("Role targets senior-level experience (" + job.experienceRequirement() + ").");
        }
        if (!(locationMatch || job == null || job.location() == null || job.location().isBlank() || "remote".equalsIgnoreCase(job.location()))) {
            concerns.add("Location listed as " + job.location() + " \u2014 outside your stated preference.");
        }
        if (!roleMatch) {
            concerns.add("Role doesn't match your stated preferred positions.");
        }
        if (missingSkills != null && !missingSkills.isEmpty() && missingPreferredSkills != null && !missingPreferredSkills.isEmpty()) {
            concerns.add("Could not add preferred edge: " + this.formatSkillList(missingPreferredSkills));
        }
        return concerns.stream().limit(3L).toList();
    }

    private String formatSkillList(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        if (skills.size() == 1) {
            return skills.get(0);
        }
        if (skills.size() == 2) {
            return skills.get(0) + " and " + skills.get(1);
        }
        int maxShow = Math.min(4, skills.size());
        List<String> sub = skills.subList(0, maxShow);
        String joined = String.join((CharSequence)", ", sub.subList(0, sub.size() - 1));
        String result = joined + " and " + sub.get(sub.size() - 1);
        if (skills.size() > maxShow) {
            result = result + " (+" + (skills.size() - maxShow) + " more)";
        }
        return result;
    }
}

