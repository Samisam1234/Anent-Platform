package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class EducationMatchingEngine {
    private static final List<String> HW_ED_KEYWORDS = List.of("ece", "electronics", "electronic and communication", "electrical", "vlsi", "semiconductor", "embedded", "instrumentation");
    private static final List<String> SW_ED_KEYWORDS = List.of("computer science", "software", "information technology", "it ", "computer engineering", "data science", "computer applications", "mca");
    private static final List<String> JOB_HW_INDICATORS = List.of("vlsi", "rtl", "verilog", "systemverilog", "fpga", "asic", "embedded", "firmware", "hardware", "digital design", "physical design", "electronics", "semiconductor", "ece");
    private static final List<String> JOB_SW_INDICATORS = List.of("java", "spring", "software", "backend", "full stack", "developer", "microservices", "web", "cloud", "frontend");

    public EducationEvaluation evaluate(CandidateProfile candidate, Job job) {
        if (candidate == null || job == null) {
            return new EducationEvaluation(0.5);
        }
        List<String> education = candidate.education();
        if (education == null || education.isEmpty()) {
            return new EducationEvaluation(0.6);
        }
        String jobText = ((job.title() != null ? job.title() : "") + " " + (job.description() != null ? job.description() : "") + " " + String.join((CharSequence)" ", job.requiredSkills() != null ? job.requiredSkills() : List.of())).toLowerCase(Locale.ROOT);
        boolean jobIsHardware = JOB_HW_INDICATORS.stream().anyMatch(jobText::contains);
        boolean jobIsSoftware = JOB_SW_INDICATORS.stream().anyMatch(jobText::contains);
        boolean candidateHasHw = false;
        boolean candidateHasSw = false;
        boolean candidateHasEngineering = false;
        for (String entry : education) {
            if (entry == null || entry.isBlank()) continue;
            String lower = entry.toLowerCase(Locale.ROOT);
            if (HW_ED_KEYWORDS.stream().anyMatch(lower::contains)) {
                candidateHasHw = true;
            }
            if (SW_ED_KEYWORDS.stream().anyMatch(lower::contains)) {
                candidateHasSw = true;
            }
            if (!lower.contains("engineering") && !lower.contains("b.tech") && !lower.contains("btech") && !lower.contains("b.e ") && !lower.contains("b.sc") && !lower.contains("bsc") && !lower.contains("master") && !lower.contains("a.m.i.e") && !lower.contains("m.tech") && !lower.contains("m.s") && !lower.contains("b.e.tech")) continue;
            candidateHasEngineering = true;
        }
        if (jobIsHardware && candidateHasHw) {
            return new EducationEvaluation(1.0);
        }
        if (jobIsSoftware && candidateHasSw) {
            return new EducationEvaluation(1.0);
        }
        if (jobIsHardware && candidateHasEngineering) {
            return new EducationEvaluation(0.75);
        }
        if (jobIsSoftware && candidateHasEngineering) {
            return new EducationEvaluation(0.75);
        }
        if (jobIsHardware && candidateHasSw) {
            return new EducationEvaluation(0.6);
        }
        if (jobIsSoftware && candidateHasHw) {
            return new EducationEvaluation(0.6);
        }
        if (candidateHasEngineering) {
            return new EducationEvaluation(0.7);
        }
        return new EducationEvaluation(0.5);
    }

    public record EducationEvaluation(double educationScore) {
    }
}

