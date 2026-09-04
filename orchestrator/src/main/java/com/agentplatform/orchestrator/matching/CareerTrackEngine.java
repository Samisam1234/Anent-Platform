package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CareerTrackEngine {
    private static final Set<String> SW_KEYWORDS = Set.of("java", "spring boot", "backend", "full stack", "javascript", "typescript", "react", "node", "microservices", "sql", "postgresql", "rest apis", "docker", "cloud", "api", "web", "python");
    private static final Set<String> HW_KEYWORDS = Set.of("vlsi", "rtl", "verilog", "systemverilog", "uvm", "fpga", "embedded", "firmware", "microcontroller", "arm", "rtos", "digital design", "asic", "physical design", "pcb", "hardware", "sta");

    public CareerTrackEvaluation evaluate(CandidateProfile candidate, Job job) {
        boolean hasHardwareSkills;
        CareerTrack jobTrack = this.classifyJob(job);
        if (candidate == null) {
            return new CareerTrackEvaluation(jobTrack, 0.5);
        }
        boolean hasSoftwareSkills = candidate.softwareSkills() != null && !candidate.softwareSkills().isEmpty();
        boolean bl = hasHardwareSkills = candidate.hardwareSkills() != null && !candidate.hardwareSkills().isEmpty();
        if (!hasSoftwareSkills && candidate.skills() != null) {
            hasSoftwareSkills = candidate.skills().stream().anyMatch(s -> this.isSoftwareMatch(s.toLowerCase(Locale.ROOT)));
        }
        if (!hasHardwareSkills && candidate.skills() != null) {
            hasHardwareSkills = candidate.skills().stream().anyMatch(s -> this.isHardwareMatch(s.toLowerCase(Locale.ROOT)));
        }
        double score = 1.0;
        switch (jobTrack) {
            case SOFTWARE: {
                score = hasSoftwareSkills ? 1.0 : (hasHardwareSkills ? 0.3 : 0.5);
                break;
            }
            case HARDWARE: {
                score = hasHardwareSkills ? 1.0 : (hasSoftwareSkills ? 0.3 : 0.5);
                break;
            }
            case MIXED: {
                if (hasSoftwareSkills && hasHardwareSkills) {
                    score = 1.0;
                    break;
                }
                if (hasSoftwareSkills || hasHardwareSkills) {
                    score = 0.85;
                    break;
                }
                score = 0.5;
                break;
            }
            default: {
                score = 0.8;
            }
        }
        return new CareerTrackEvaluation(jobTrack, score);
    }

    public CareerTrack classifyJob(Job job) {
        if (job == null) {
            return CareerTrack.UNKNOWN;
        }
        String text = ((job.title() != null ? job.title() : "") + " " + (job.description() != null ? job.description() : "") + " " + (job.requiredSkills() != null ? String.join((CharSequence)" ", job.requiredSkills()) : "")).toLowerCase(Locale.ROOT);
        int swHits = 0;
        for (String kw : SW_KEYWORDS) {
            if (!text.contains(kw)) continue;
            ++swHits;
        }
        int hwHits = 0;
        for (String kw : HW_KEYWORDS) {
            if (!text.contains(kw)) continue;
            ++hwHits;
        }
        if (swHits > 0 && hwHits > 0) {
            if (swHits >= 2 * hwHits) {
                return CareerTrack.SOFTWARE;
            }
            if (hwHits >= 2 * swHits) {
                return CareerTrack.HARDWARE;
            }
            return CareerTrack.MIXED;
        }
        if (swHits > 0) {
            return CareerTrack.SOFTWARE;
        }
        if (hwHits > 0) {
            return CareerTrack.HARDWARE;
        }
        return CareerTrack.UNKNOWN;
    }

    private boolean isSoftwareMatch(String s) {
        return SW_KEYWORDS.stream().anyMatch(s::contains);
    }

    private boolean isHardwareMatch(String s) {
        return HW_KEYWORDS.stream().anyMatch(s::contains);
    }

    public record CareerTrackEvaluation(CareerTrack jobTrack, double trackScore) {
    }
}

