package com.agentplatform.orchestrator.gap;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.matching.SkillMatchingEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.CareerTrackEvidence;
import com.agentplatform.orchestrator.resume.ResumeEvidence;
import com.agentplatform.orchestrator.resume.SkillTaxonomy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Deterministic career-gap analysis: compares a candidate's canonical skills and
 * structured experience against a job's requirements to produce explainable,
 * reproducible gaps.
 *
 * <p>Reuses the existing {@link SkillMatchingEngine} (canonical matching is not
 * re-implemented) and {@link CareerTrackEngine#classifyJob(Job)} for the job-side
 * career track. No LLM, no learning content, no fabricated fields.</p>
 *
 * <h2>Severity scoring (transparent)</h2>
 * <pre>
 *   +3  per missing required skill
 *   +1  per missing preferred skill
 *   +2  if there is a knowable experience shortfall (gapYears &gt; 0)
 *   +2  if the job track is deterministically known and mismatches the candidate's
 *   ──
 *   0        → NO_GAP
 *   1–3      → LOW
 *   4–6      → MEDIUM
 *   7–9      → HIGH
 *   ≥10      → CRITICAL
 * </pre>
 * Required gaps outweigh preferred gaps 3:1, matching the intended weighting.
 */
@Service
public class CareerGapAnalysisService {

    private static final int REQUIRED_WEIGHT = 3;
    private static final int PREFERRED_WEIGHT = 1;
    private static final int EXPERIENCE_WEIGHT = 2;
    private static final int TRACK_MISMATCH_WEIGHT = 2;

    private final SkillMatchingEngine skillMatchingEngine;
    private final CareerTrackEngine careerTrackEngine;

    public CareerGapAnalysisService(SkillMatchingEngine skillMatchingEngine,
                                    CareerTrackEngine careerTrackEngine) {
        this.skillMatchingEngine = skillMatchingEngine;
        this.careerTrackEngine = careerTrackEngine;
    }

    /**
     * Analyzes the gap between a candidate and a job.
     *
     * @param candidate candidate profile (may be empty; never structurally null)
     * @param job       job listing (may be empty; never structurally null)
     * @return a deterministic, explainable {@link CareerGapAnalysis}
     */
    public CareerGapAnalysis analyze(CandidateProfile candidate, Job job) {
        if (candidate == null) {
            candidate = emptyProfile();
        }
        if (job == null) {
            job = emptyJob();
        }

        List<SkillGap> matchedRequired = new ArrayList<>();
        List<SkillGap> missingRequired = new ArrayList<>();
        List<SkillGap> matchedPreferred = new ArrayList<>();
        List<SkillGap> missingPreferred = new ArrayList<>();

        SkillMatchingEngine.SkillEvaluation skillEval =
                skillMatchingEngine.evaluate(candidate, job);
        for (String skill : skillEval.matchedRequiredSkills()) {
            matchedRequired.add(gap(skill, candidate));
        }
        for (String skill : skillEval.missingRequiredSkills()) {
            missingRequired.add(gap(skill, candidate));
        }
        for (String skill : skillEval.matchedPreferredSkills()) {
            matchedPreferred.add(gap(skill, candidate));
        }
        for (String skill : skillEval.missingPreferredSkills()) {
            missingPreferred.add(gap(skill, candidate));
        }

        matchedRequired = dedupSkills(matchedRequired);
        missingRequired = dedupSkills(missingRequired);
        matchedPreferred = dedupSkills(matchedPreferred);
        missingPreferred = dedupSkills(missingPreferred);

        ExperienceGap experienceGap = computeExperienceGap(candidate, job);
        CareerTrack candidateTrack = determineCandidateTrack(candidate);
        CareerTrack jobTrack = careerTrackEngine.classifyJob(job);
        boolean trackMismatch = isTrackMismatch(candidateTrack, jobTrack);

        List<ImprovementPriority> priorities = buildPriorities(
                missingRequired, experienceGap, missingPreferred);

        GapSeverity severity = severity(
                missingRequired.size(), missingPreferred.size(),
                experienceGap.hasShortfall(), trackMismatch);

        return new CareerGapAnalysis(
                null, job.id(),
                matchedRequired, missingRequired, matchedPreferred, missingPreferred,
                experienceGap, candidateTrack, jobTrack, trackMismatch,
                severity, priorities);
    }

    private static List<SkillGap> dedupSkills(List<SkillGap> list) {
        if (list == null) {
            return List.of();
        }
        List<SkillGap> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SkillGap g : list) {
            if (g == null || g.canonicalSkill().isEmpty() || !seen.add(g.canonicalSkill())) {
                continue;
            }
            result.add(g);
        }
        return List.copyOf(result);
    }

    private static SkillGap gap(String rawSkill, CandidateProfile candidate) {
        String canonical = SkillTaxonomy.normalize(rawSkill);
        List<ResumeEvidence.SourceSection> sources =
                evidenceFor(candidate, canonical);
        return new SkillGap(canonical, sources);
    }

    /**
     * Maps each canonical matched skill to the resume sections where it was observed.
     * Missing skills have no evidence (never fabricated).
     */
    private static List<ResumeEvidence.SourceSection> evidenceFor(
            CandidateProfile candidate, String canonicalSkill) {
        if (candidate.resumeEvidence() == null || canonicalSkill.isEmpty()) {
            return List.of();
        }
        Set<ResumeEvidence.SourceSection> sections = new LinkedHashSet<>();
        for (ResumeEvidence evidence : candidate.resumeEvidence()) {
            if (evidence == null) {
                continue;
            }
            String evCanonical = SkillTaxonomy.normalize(evidence.canonicalSkill());
            if (!evCanonical.isEmpty() && evCanonical.equalsIgnoreCase(canonicalSkill)) {
                if (evidence.sourceSection() != null
                        && evidence.sourceSection() != ResumeEvidence.SourceSection.UNKNOWN) {
                    sections.add(evidence.sourceSection());
                }
            }
        }
        return List.copyOf(sections);
    }

    // ── Experience gap ──────────────────────────────────────────────────────

    private static final Pattern RANGE = Pattern.compile("(\\d{1,2})\\s*(?:-|to)\\s*(\\d{1,2})\\s*(?:years?|yrs?)");
    private static final Pattern MIN_PLUS = Pattern.compile("(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)");

    /**
     * Computes a numeric experience gap only when both the job requirement and the
     * candidate experience are deterministically parseable; otherwise UNKNOWN.
     */
    private static ExperienceGap computeExperienceGap(CandidateProfile candidate, Job job) {
        Integer required = parseJobRequiredYears(job.experienceRequirement());

        // An entry-level role (0 required years) needs no years — deterministically a gap of
        // zero regardless of candidate experience, so it stays knowable.
        if (required != null && required == 0) {
            return new ExperienceGap(0, candidateYears(candidate), 0, true);
        }

        Integer candidateYears = parseCandidateYears(candidate);
        if (required == null) {
            return new ExperienceGap(null, candidateYears, null, false);
        }
        if (candidateYears == null) {
            return new ExperienceGap(required, null, null, false);
        }
        int gap = Math.max(0, required - candidateYears);
        return new ExperienceGap(required, candidateYears, gap, true);
    }

    private static Integer candidateYears(CandidateProfile candidate) {
        return parseCandidateYears(candidate);
    }

    /**
     * Parses the job's experience requirement into "years required". Handles
     * entry-level markers (→ 0), "X-Y years" (→ the upper bound), and "X+ / X
     * years" (→ X). Unparseable text → null (unknown, not guessed).
     */
    static Integer parseJobRequiredYears(String experienceRequirement) {
        if (experienceRequirement == null) {
            return null;
        }
        String text = experienceRequirement.toLowerCase(Locale.ROOT).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.contains("fresh") || text.contains("entry") || text.contains("intern")
                || text.contains("graduate") || text.contains("trainee")
                || text.contains("0 years") || text.contains("0-1") || text.contains("0-2")) {
            return 0;
        }
        Matcher range = RANGE.matcher(text);
        if (range.find()) {
            return Integer.valueOf(Math.max(
                    Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2))));
        }
        Matcher minPlus = MIN_PLUS.matcher(text);
        if (minPlus.find()) {
            return Integer.valueOf(minPlus.group(1));
        }
        return null;
    }

    /**
     * Parses the candidate's structured experience lines ("X years", "X-Y years",
     * "X+ years") into total years. Only explicit year figures count — project
     * duration, graduation dates and vague prose are never interpreted. Returns the
     * maximum detected, or null when nothing structured is present.
     */
    static Integer parseCandidateYears(CandidateProfile candidate) {
        if (candidate == null || candidate.experience() == null) {
            return null;
        }
        int best = -1;
        boolean found = false;
        for (String line : candidate.experience()) {
            if (line == null) {
                continue;
            }
            String text = line.toLowerCase(Locale.ROOT);
            Matcher range = RANGE.matcher(text);
            if (range.find()) {
                found = true;
                best = Math.max(best, Integer.parseInt(range.group(2)));
                continue;
            }
            Matcher minPlus = MIN_PLUS.matcher(text);
            if (minPlus.find()) {
                found = true;
                best = Math.max(best, Integer.parseInt(minPlus.group(1)));
            }
        }
        return found ? Integer.valueOf(best) : null;
    }

    // ── Career track ────────────────────────────────────────────────────────

    /**
     * Determines the candidate's career track from canonical evidence. Prefers the
     * resume-derived {@code careerTrackEvidence} labels; falls back to the presence
     * of software vs hardware skills when no explicit track evidence exists.
     */
    static CareerTrack determineCandidateTrack(CandidateProfile candidate) {
        if (candidate != null && candidate.careerTrackEvidence() != null
                && !candidate.careerTrackEvidence().isEmpty()) {
            Set<CareerTrack> tracks = new LinkedHashSet<>();
            for (CareerTrackEvidence evidence : candidate.careerTrackEvidence()) {
                if (evidence == null) {
                    continue;
                }
                tracks.add(classifyTrackLabel(evidence.track()));
            }
            tracks.remove(CareerTrack.UNKNOWN);
            if (tracks.size() == 1) {
                return tracks.iterator().next();
            }
            if (tracks.size() > 1) {
                return CareerTrack.MIXED;
            }
        }
        boolean software = candidate != null && candidate.softwareSkills() != null
                && !candidate.softwareSkills().isEmpty();
        boolean hardware = candidate != null && candidate.hardwareSkills() != null
                && !candidate.hardwareSkills().isEmpty();
        if (software && hardware) {
            return CareerTrack.MIXED;
        }
        if (software) {
            return CareerTrack.SOFTWARE;
        }
        if (hardware) {
            return CareerTrack.HARDWARE;
        }
        return CareerTrack.UNKNOWN;
    }

    /**
     * Maps a human-readable track label (as produced by
     * {@link com.agentplatform.orchestrator.resume.CareerTrackEvidence#trackLabelForCategory})
     * onto a track, preserving the fine-grained discipline rather than collapsing it.
     *
     * <p>Discipline-specific labels are tested before the generic software/hardware words,
     * because "Embedded Systems" and "VLSI / FPGA" are hardware labels that must not be
     * reduced to {@code HARDWARE}, and "AI / ML" is its own track rather than generic
     * software. Labels spanning more than one family stay {@code MIXED}.</p>
     */
    private static CareerTrack classifyTrackLabel(String label) {
        if (label == null) {
            return CareerTrack.UNKNOWN;
        }
        String lower = label.toLowerCase(Locale.ROOT);
        boolean vlsi = lower.contains("vlsi") || lower.contains("fpga") || lower.contains("asic");
        boolean embedded = lower.contains("embedded") || lower.contains("firmware");
        boolean aiMl = lower.contains("ai") || lower.contains("ml")
                || lower.contains("machine learning") || lower.contains("data science");
        boolean software = lower.contains("software") || lower.contains("full stack")
                || lower.contains("backend");
        boolean genericHardware = lower.contains("electronics") || lower.contains("ece")
                || lower.contains("communication");

        Set<CareerTrack> matched = new LinkedHashSet<>();
        if (vlsi) {
            matched.add(CareerTrack.VLSI_FPGA);
        }
        if (embedded) {
            matched.add(CareerTrack.EMBEDDED);
        }
        if (aiMl) {
            matched.add(CareerTrack.AI_ML);
        }
        if (software) {
            matched.add(CareerTrack.SOFTWARE);
        }
        if (genericHardware) {
            matched.add(CareerTrack.HARDWARE);
        }
        if (matched.isEmpty()) {
            return CareerTrack.UNKNOWN;
        }
        if (matched.size() > 1 && matched.stream().map(CareerTrack::family).distinct().count() > 1) {
            return CareerTrack.MIXED;
        }
        return matched.iterator().next();
    }

    private static boolean isTrackMismatch(CareerTrack candidate, CareerTrack job) {
        if (candidate == CareerTrack.UNKNOWN || candidate == CareerTrack.MIXED) {
            return false;
        }
        if (job == CareerTrack.UNKNOWN || job == CareerTrack.MIXED) {
            return false;
        }
        // Compared at family level. With fine-grained tracks, an embedded candidate against
        // a VLSI/FPGA role is a different discipline but the same family — related enough
        // to be worth surfacing, and consistent with CareerTrackEngine, which scores a
        // same-family pairing above the mismatch threshold. A software profile against a
        // hardware role remains a genuine mismatch.
        return candidate.family() != job.family();
    }

    // ── Severity ────────────────────────────────────────────────────────────

    /**
     * Applies the documented scoring formula. Visible for deterministic unit tests.
     */
    static GapSeverity severity(int missingRequired, int missingPreferred,
                                boolean experienceShortfall, boolean trackMismatch) {
        int score = missingRequired * REQUIRED_WEIGHT
                + missingPreferred * PREFERRED_WEIGHT
                + (experienceShortfall ? EXPERIENCE_WEIGHT : 0)
                + (trackMismatch ? TRACK_MISMATCH_WEIGHT : 0);
        if (score <= 0) {
            return GapSeverity.NO_GAP;
        }
        if (score <= 3) {
            return GapSeverity.LOW;
        }
        if (score <= 6) {
            return GapSeverity.MEDIUM;
        }
        if (score <= 9) {
            return GapSeverity.HIGH;
        }
        return GapSeverity.CRITICAL;
    }

    // ── Priorities ──────────────────────────────────────────────────────────

    /**
     * Builds 1-based priorities: missing required skills (alphabetical) first, then a
     * knowable experience shortfall, then missing preferred skills. Deterministic and
     * LLM-ready: each priority carries its canonical subject, type, factual reason and
     * a full explanatory description (see {@link ImprovementPriority}). Ranks are
     * consecutive with no duplicates.
     */
    static List<ImprovementPriority> buildPriorities(
            List<SkillGap> missingRequired, ExperienceGap experienceGap,
            List<SkillGap> missingPreferred) {
        List<ImprovementPriority> priorities = new ArrayList<>();
        int rank = 1;

        for (String skill : sortedCanonical(missingRequired)) {
            priorities.add(ImprovementPriority.requiredSkill(rank++, skill));
        }

        if (experienceGap != null && experienceGap.hasShortfall()) {
            Integer required = experienceGap.requiredYears();
            Integer candidate = experienceGap.candidateYears();
            if (required != null && candidate != null) {
                priorities.add(ImprovementPriority.experience(
                        rank++, required, candidate, experienceGap.gapYears()));
            } else {
                priorities.add(ImprovementPriority.experience(rank++, experienceGap.gapYears()));
            }
        }

        for (String skill : sortedCanonical(missingPreferred)) {
            priorities.add(ImprovementPriority.preferredSkill(rank++, skill));
        }

        return List.copyOf(priorities);
    }

    private static List<String> sortedCanonical(List<SkillGap> gaps) {
        if (gaps == null) {
            return List.of();
        }
        return gaps.stream()
                .filter(g -> g != null && !g.canonicalSkill().isEmpty())
                .map(SkillGap::canonicalSkill)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static CandidateProfile emptyProfile() {
        return new CandidateProfile(null, null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private static Job emptyJob() {
        return new Job(null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, null, null);
    }
}