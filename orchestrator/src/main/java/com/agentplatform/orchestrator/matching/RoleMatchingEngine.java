package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RoleMatchingEngine {
    private static final Set<String> SW_JAVA_BACKEND_FAMILY = Set.of("java developer", "junior java developer", "backend engineer", "backend developer", "spring boot developer", "java backend developer", "api developer", "microservices developer", "cloud java software engineer", "backend platform engineer", "java full stack developer");
    private static final Set<String> SW_GENERAL_FAMILY = Set.of("software engineer", "software developer", "full stack developer", "web developer", "frontend developer", "application developer", "platform engineer");
    private static final Set<String> HW_VLSI_RTL_FAMILY = Set.of("vlsi design engineer", "rtl design engineer", "rtl design & verification engineer", "asic engineer", "verification engineer", "digital design engineer", "asic verification", "senior asic physical design engineer", "physical design engineer", "graduate hardware engineer");
    private static final Set<String> HW_FPGA_EMBEDDED_FAMILY = Set.of("fpga development engineer", "fpga engineer", "embedded systems & firmware engineer", "embedded engineer", "firmware engineer", "embedded systems developer", "hardware engineer", "embedded hardware & iot intern", "iot engineer");

    public RoleEvaluation evaluate(CandidateProfile candidate, Job job) {
        if (candidate == null || job == null) {
            return new RoleEvaluation(false, 0.0);
        }
        String jobTitle = (job.title() != null ? job.title() : "").toLowerCase(Locale.ROOT).trim();
        String jobDesc = (job.description() != null ? job.description() : "").toLowerCase(Locale.ROOT).trim();
        List<String> preferredRoles = candidate.preferredRoles();
        if (preferredRoles == null || preferredRoles.isEmpty()) {
            return new RoleEvaluation(true, 0.7);
        }
        double maxScore = 0.0;
        boolean isMatch = false;
        for (String role : preferredRoles) {
            if (role == null || role.isBlank()) continue;
            String candRole = role.toLowerCase(Locale.ROOT).trim();
            if (jobTitle.contains(candRole) || candRole.contains(jobTitle)) {
                return new RoleEvaluation(true, 1.0);
            }
            double tokenOverlap = this.calculateTokenOverlap(candRole, jobTitle);
            if (tokenOverlap >= 0.5) {
                isMatch = true;
                maxScore = Math.max(maxScore, 0.9 + tokenOverlap * 0.1);
            }
            if (this.inSameFamily(candRole, jobTitle)) {
                isMatch = true;
                maxScore = Math.max(maxScore, 0.85);
            }
            if (!jobDesc.contains(candRole)) continue;
            isMatch = true;
            maxScore = Math.max(maxScore, 0.65);
        }
        if (maxScore == 0.0) {
            boolean broadHardware;
            boolean broadSoftware = this.isSoftwareRole(jobTitle) && preferredRoles.stream().anyMatch(this::isSoftwareRole);
            boolean bl = broadHardware = this.isHardwareRole(jobTitle) && preferredRoles.stream().anyMatch(this::isHardwareRole);
            if (broadSoftware || broadHardware) {
                isMatch = true;
                maxScore = 0.5;
            }
        }
        return new RoleEvaluation(isMatch, Math.min(1.0, maxScore));
    }

    private boolean inSameFamily(String r1, String r2) {
        if (this.matchesFamily(r1, r2, SW_JAVA_BACKEND_FAMILY)) {
            return true;
        }
        if (this.matchesFamily(r1, r2, SW_GENERAL_FAMILY)) {
            return true;
        }
        if (this.matchesFamily(r1, r2, HW_VLSI_RTL_FAMILY)) {
            return true;
        }
        return this.matchesFamily(r1, r2, HW_FPGA_EMBEDDED_FAMILY);
    }

    private boolean matchesFamily(String r1, String r2, Set<String> family) {
        boolean r1In = family.stream().anyMatch(f -> r1.contains((CharSequence)f) || f.contains(r1));
        boolean r2In = family.stream().anyMatch(f -> r2.contains((CharSequence)f) || f.contains(r2));
        return r1In && r2In;
    }

    private boolean isSoftwareRole(String role) {
        String lower = role.toLowerCase(Locale.ROOT);
        return lower.contains("java") || lower.contains("software") || lower.contains("developer") || lower.contains("backend") || lower.contains("full stack") || lower.contains("engineer") && !this.isHardwareRole(lower);
    }

    private boolean isHardwareRole(String role) {
        String lower = role.toLowerCase(Locale.ROOT);
        return lower.contains("vlsi") || lower.contains("rtl") || lower.contains("fpga") || lower.contains("hardware") || lower.contains("embedded") || lower.contains("asic") || lower.contains("firmware") || lower.contains("ece") || lower.contains("digital");
    }

    private double calculateTokenOverlap(String s1, String s2) {
        HashSet<String> tokens1 = new HashSet<String>(Arrays.asList(s1.split("[\\s,-]+")));
        HashSet<String> tokens2 = new HashSet<String>(Arrays.asList(s2.split("[\\s,-]+")));
        tokens1.removeIf(t -> t.length() <= 2);
        tokens2.removeIf(t -> t.length() <= 2);
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }
        long common = tokens1.stream().filter(tokens2::contains).count();
        return (double)common / (double)Math.min(tokens1.size(), tokens2.size());
    }

    public record RoleEvaluation(boolean roleMatch, double roleScore) {
    }
}

