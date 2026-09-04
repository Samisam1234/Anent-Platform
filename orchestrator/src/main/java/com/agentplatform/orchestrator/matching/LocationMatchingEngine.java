package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class LocationMatchingEngine {
    public LocationEvaluation evaluate(CandidateProfile candidate, Job job) {
        if (candidate == null || job == null) {
            return new LocationEvaluation(false, 0.0);
        }
        String jobLoc = (job.location() != null ? job.location() : "").toLowerCase(Locale.ROOT).trim();
        String candHomeLoc = (candidate.location() != null ? candidate.location() : "").toLowerCase(Locale.ROOT).trim();
        List<String> preferredLocs = candidate.preferredLocations() != null ? candidate.preferredLocations().stream().map(s -> s.toLowerCase(Locale.ROOT).trim()).toList() : List.of();
        boolean jobIsRemote = jobLoc.contains("remote") || jobLoc.contains("work from home") || jobLoc.contains("wfh");
        boolean candPrefersRemote = preferredLocs.stream().anyMatch(p -> p.contains("remote") || p.contains("wfh"));
        if (jobIsRemote) {
            if (candPrefersRemote || preferredLocs.isEmpty()) {
                return new LocationEvaluation(true, 1.0);
            }
            return new LocationEvaluation(true, 0.9);
        }
        for (String pref : preferredLocs) {
            if (!this.isCityMatch(pref, jobLoc)) continue;
            return new LocationEvaluation(true, 1.0);
        }
        if (!candHomeLoc.isEmpty() && this.isCityMatch(candHomeLoc, jobLoc)) {
            return new LocationEvaluation(true, 1.0);
        }
        if (preferredLocs.isEmpty() && candHomeLoc.isEmpty()) {
            return new LocationEvaluation(true, 0.7);
        }
        return new LocationEvaluation(false, 0.0);
    }

    private boolean isCityMatch(String loc1, String loc2) {
        String n2;
        if (loc1.isEmpty() || loc2.isEmpty()) {
            return false;
        }
        String n1 = this.normalizeCity(loc1);
        if (n1.equals(n2 = this.normalizeCity(loc2)) || loc1.contains(n2) || loc2.contains(n1)) {
            return true;
        }
        if (this.isHyderabad(n1) && this.isHyderabad(n2)) {
            return true;
        }
        return this.isBengaluru(n1) && this.isBengaluru(n2);
    }

    private String normalizeCity(String loc) {
        return loc.toLowerCase(Locale.ROOT).replaceAll(",.*$", "").replaceAll("[^a-z0-9]", "").trim();
    }

    private boolean isHyderabad(String city) {
        return city.contains("hyderabad") || city.contains("secunderabad") || city.contains("cyberabad");
    }

    private boolean isBengaluru(String city) {
        return city.contains("bengaluru") || city.contains("bangalore");
    }

    public record LocationEvaluation(boolean locationMatch, double locationScore) {
    }
}

