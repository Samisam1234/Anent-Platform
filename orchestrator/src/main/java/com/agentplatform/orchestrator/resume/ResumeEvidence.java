package com.agentplatform.orchestrator.resume;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A single piece of traceable evidence explaining where a canonical skill was
 * observed in a resume.
 *
 * <p>Records only the minimum useful information: the canonical skill, the section
 * of the resume it came from, a short matched snippet, and a deterministic
 * evidence strength. No unnecessary resume text or sensitive data is stored.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeEvidence(
        String canonicalSkill,
        SourceSection sourceSection,
        String matchedText,
        EvidenceStrength evidenceStrength
) {

    /** The resume section within which the skill was observed. */
    public enum SourceSection {
        SKILLS,
        EXPERIENCE,
        PROJECT,
        EDUCATION,
        CERTIFICATION,
        SUMMARY,
        UNKNOWN
    }

    /** Relative strength of the evidence for a given skill. */
    public enum EvidenceStrength {
        STRONG,
        MEDIUM,
        WEAK
    }

    public ResumeEvidence {
        canonicalSkill = canonicalSkill == null ? "" : canonicalSkill.trim();
        matchedText = matchedText == null ? "" : matchedText.trim();
        sourceSection = sourceSection == null ? SourceSection.UNKNOWN : sourceSection;
        evidenceStrength = evidenceStrength == null ? EvidenceStrength.WEAK : evidenceStrength;
    }

    /**
     * Convenience factory that normalizes the skill name via {@link SkillTaxonomy}.
     */
    public static ResumeEvidence of(String canonicalSkill, SourceSection section, String matchedText, EvidenceStrength strength) {
        return new ResumeEvidence(canonicalSkill, section, matchedText, strength);
    }
}
