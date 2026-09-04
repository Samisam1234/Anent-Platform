package com.agentplatform.orchestrator.tailoring;

import com.agentplatform.orchestrator.gap.CareerGapAnalysis;
import com.agentplatform.orchestrator.gap.SkillGap;
import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.matching.CareerTrack;
import com.agentplatform.orchestrator.matching.CareerTrackEngine;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.ResumeEvidence;
import com.agentplatform.orchestrator.resume.SkillTaxonomy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Deterministic, explainable ATS resume tailoring analysis (Phase 4.4).
 *
 * <p>Consumes a {@code CandidateProfile}, a target {@code Job} and the authoritative
 * {@link CareerGapAnalysis} (Steps 4.1–4.2) and explains how existing, verified resume
 * content can be reorganized and highlighted for that job. It reuses the gap results
 * and {@link SkillTaxonomy}/{@link SkillNormalizer via the taxonomy text miner} — it
 * never re-implements skill matching with raw string equality.</p>
 *
 * <p>Truthfulness over score: the system only highlights content that already exists
 * in the candidate profile, never invents skills/experience/projects, and keeps
 * missing required/preferred skills clearly labelled as MISSING and out of the
 * highlighted set. No LLM, no database, no network. Fully deterministic.</p>
 */
@Service
public class ResumeTailoringAnalysisService {

    private final CareerTrackEngine careerTrackEngine;

    public ResumeTailoringAnalysisService(CareerTrackEngine careerTrackEngine) {
        this.careerTrackEngine = careerTrackEngine;
    }

    /**
     * Analyzes how the existing candidate resume can be tailored for {@code job}.
     */
    public ResumeTailoringAnalysis analyze(CandidateProfile candidate, Job job,
                                           CareerGapAnalysis gapAnalysis) {
        candidate = candidate == null ? emptyProfile() : candidate;
        gapAnalysis = gapAnalysis == null ? emptyGapAnalysis() : gapAnalysis;

        // ── Authoritative matched / missing skills (from the gap analysis) ────
        List<SkillGap> matchedRequired = gapAnalysis.matchedRequiredSkills();
        List<SkillGap> matchedPreferred = gapAnalysis.matchedPreferredSkills();
        List<SkillGap> missingRequired = gapAnalysis.missingRequiredSkills();
        List<SkillGap> missingPreferred = gapAnalysis.missingPreferredSkills();

        List<String> matchedRequiredNames = canonicalNames(matchedRequired);
        List<String> matchedPreferredNames = canonicalNames(matchedPreferred);
        List<String> missingRequiredNames = canonicalNames(missingRequired);
        List<String> missingPreferredNames = canonicalNames(missingPreferred);

        // ── Highlighted skills: ONLY verified matched skills, with real evidence ──
        List<HighlightedSkill> highlighted = new ArrayList<>();
        for (SkillGap g : matchedRequired) {
            highlighted.add(new HighlightedSkill(g.canonicalSkill(), g.evidenceSources()));
        }
        for (SkillGap g : matchedPreferred) {
            highlighted.add(new HighlightedSkill(g.canonicalSkill(), g.evidenceSources()));
        }

        // ── Job canonical skill set for entry relevance (deterministic) ───────
        Set<String> jobSkills = new LinkedHashSet<>();
        addCanonical(jobSkills, job == null ? List.of() : job.requiredSkills());
        addCanonical(jobSkills, job == null ? List.of() : job.preferredSkills());

        List<RelevantEntry> relevantProjects = relevantEntries(candidate.projects(),
                ResumeSection.PROJECTS, jobSkills);
        List<RelevantEntry> relevantExperience = relevantEntries(candidate.experience(),
                ResumeSection.EXPERIENCE, jobSkills);
        List<RelevantEntry> relevantInternships = relevantEntries(candidate.internships(),
                ResumeSection.INTERNSHIPS, jobSkills);

        // ── Recommended section order (deterministic, evidence + track-based) ──
        List<ResumeSection> sectionOrder = sectionOrder(candidate, job,
                relevantProjects, relevantExperience, relevantInternships);

        // ── Recommendations ───────────────────────────────────────────────────
        List<TailoringRecommendation> recommendations = new ArrayList<>();
        for (SkillGap g : matchedRequired) {
            recommendations.add(highlightSkill(g, true));
        }
        for (SkillGap g : matchedPreferred) {
            recommendations.add(highlightSkill(g, false));
        }
        for (RelevantEntry e : relevantProjects) {
            recommendations.add(highlightEntry(e));
        }
        for (RelevantEntry e : relevantExperience) {
            recommendations.add(highlightEntry(e));
        }
        for (RelevantEntry e : relevantInternships) {
            recommendations.add(highlightEntry(e));
        }
        for (ResumeSection s : sectionOrder) {
            recommendations.add(new TailoringRecommendation(RecommendationType.SECTION_PRIORITY,
                    s.name(),
                    "Recommended placement based on job relevance and candidate evidence.",
                    List.of()));
        }

        // ── Missing requirements (clearly labelled; never highlighted) ────────
        List<TailoringRecommendation> missingRequirements = new ArrayList<>();
        for (SkillGap g : missingRequired) {
            missingRequirements.add(new TailoringRecommendation(
                    RecommendationType.MISSING_REQUIREMENT, g.canonicalSkill(),
                    "Required by the target job but not present in the candidate profile (MISSING).",
                    List.of()));
        }
        for (SkillGap g : missingPreferred) {
            missingRequirements.add(new TailoringRecommendation(
                    RecommendationType.MISSING_REQUIREMENT, g.canonicalSkill(),
                    "Preferred by the target job but not present in the candidate profile (MISSING).",
                    List.of()));
        }

        AtsReadinessAnalysis readiness = atsReadiness(candidate, job,
                matchedRequired.size(), missingRequired.size(),
                matchedPreferred.size(), missingPreferred.size(),
                !relevantProjects.isEmpty(),
                !relevantExperience.isEmpty() || !relevantInternships.isEmpty());

        return new ResumeTailoringAnalysis(
                gapAnalysis.candidateId(), gapAnalysis.jobId(),
                matchedRequiredNames, matchedPreferredNames,
                missingRequiredNames, missingPreferredNames,
                highlighted, relevantProjects, relevantExperience, relevantInternships,
                sectionOrder, recommendations, missingRequirements, readiness);
    }

    // ─── Highlight recommendations ───────────────────────────────────────────

    private static TailoringRecommendation highlightSkill(SkillGap g, boolean required) {
        String reason = required
                ? "Covered by the candidate profile and required by the target job — highlight close to the top."
                : "Covered by the candidate profile and preferred by the target job — worth highlighting.";
        return new TailoringRecommendation(RecommendationType.HIGHLIGHT_SKILL,
                g.canonicalSkill(), reason, g.evidenceSources());
    }

    private static TailoringRecommendation highlightEntry(RelevantEntry e) {
        String reason = "Existing content matched the job's skills ("
                + String.join(", ", e.matchedJobSkills()) + ").";
        return new TailoringRecommendation(
                switch (e.section()) {
                    case PROJECTS -> RecommendationType.HIGHLIGHT_PROJECT;
                    case INTERNSHIPS -> RecommendationType.HIGHLIGHT_INTERNSHIP;
                    default -> RecommendationType.HIGHLIGHT_EXPERIENCE;
                },
                e.content(), reason, List.of(sectionToEvidence(e.section())));
    }

    /** Maps a {@link ResumeSection} to the matching resume-evidence source section. */
    private static ResumeEvidence.SourceSection sectionToEvidence(ResumeSection section) {
        return switch (section) {
            case PROJECTS -> ResumeEvidence.SourceSection.PROJECT;
            case CERTIFICATIONS -> ResumeEvidence.SourceSection.CERTIFICATION;
            case EDUCATION -> ResumeEvidence.SourceSection.EDUCATION;
            case SKILLS -> ResumeEvidence.SourceSection.SKILLS;
            case SUMMARY -> ResumeEvidence.SourceSection.SUMMARY;
            default -> ResumeEvidence.SourceSection.EXPERIENCE; // EXPERIENCE / INTERNSHIPS
        };
    }

    // ─── Entry relevance (deterministic text mining, no invention) ───────────

    private static List<RelevantEntry> relevantEntries(List<String> lines,
                                                       ResumeSection section,
                                                       Set<String> jobSkills) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<RelevantEntry> result = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> found = SkillTaxonomy.findMatches(line);
            List<String> matched = new ArrayList<>();
            for (String skill : found) {
                if (jobSkills.contains(skill)) {
                    matched.add(skill);
                }
            }
            if (!matched.isEmpty()) {
                result.add(new RelevantEntry(line, section, found, matched));
            }
        }
        return List.copyOf(result);
    }

    // ─── Deterministic section order ─────────────────────────────────────────

    private List<ResumeSection> sectionOrder(CandidateProfile candidate, Job job,
                                                    List<RelevantEntry> projects,
                                                    List<RelevantEntry> experience,
                                                    List<RelevantEntry> internships) {
        List<ResumeSection> order = new ArrayList<>();
        order.add(ResumeSection.SUMMARY);
        if (hasSkills(candidate)) {
            order.add(ResumeSection.SKILLS);
        }

        Map<ResumeSection, Integer> relevance = new EnumMap<>(ResumeSection.class);
        relevance.put(ResumeSection.PROJECTS, projects.size());
        relevance.put(ResumeSection.EXPERIENCE, experience.size());
        relevance.put(ResumeSection.INTERNSHIPS, internships.size());
        relevance.put(ResumeSection.CERTIFICATIONS, 0);
        relevance.put(ResumeSection.EDUCATION, 0);

        CareerTrack track = job == null ? CareerTrack.UNKNOWN : careerTrackEngine.classifyJob(job);
        List<ResumeSection> preference = trackPreference(track);

        List<ResumeSection> present = new ArrayList<>();
        if (hasContent(candidate, ResumeSection.PROJECTS)) present.add(ResumeSection.PROJECTS);
        if (hasContent(candidate, ResumeSection.EXPERIENCE)) present.add(ResumeSection.EXPERIENCE);
        if (hasContent(candidate, ResumeSection.INTERNSHIPS)) present.add(ResumeSection.INTERNSHIPS);
        if (hasContent(candidate, ResumeSection.CERTIFICATIONS)) present.add(ResumeSection.CERTIFICATIONS);
        if (hasContent(candidate, ResumeSection.EDUCATION)) present.add(ResumeSection.EDUCATION);

        present.sort((a, b) -> {
            int byRelevance = Integer.compare(relevance.get(b), relevance.get(a));
            if (byRelevance != 0) {
                return byRelevance;
            }
            return Integer.compare(indexOf(preference, a), indexOf(preference, b));
        });

        order.addAll(present);
        return List.copyOf(order);
    }

    private static List<ResumeSection> trackPreference(CareerTrack track) {
        return switch (track) {
            case SOFTWARE -> List.of(ResumeSection.PROJECTS, ResumeSection.EXPERIENCE,
                    ResumeSection.INTERNSHIPS, ResumeSection.CERTIFICATIONS, ResumeSection.EDUCATION);
            case HARDWARE -> List.of(ResumeSection.PROJECTS, ResumeSection.INTERNSHIPS,
                    ResumeSection.EXPERIENCE, ResumeSection.EDUCATION, ResumeSection.CERTIFICATIONS);
            default -> List.of(ResumeSection.PROJECTS, ResumeSection.EXPERIENCE,
                    ResumeSection.INTERNSHIPS, ResumeSection.EDUCATION, ResumeSection.CERTIFICATIONS);
        };
    }

    private static int indexOf(List<ResumeSection> list, ResumeSection s) {
        return list.indexOf(s); // -1 never happens because preference covers all present sections
    }

    // ─── ATS readiness (director formula, advisory only) ─────────────────────

    private static AtsReadinessAnalysis atsReadiness(CandidateProfile candidate, Job job,
                                                     int matchedRequired, int missingRequired,
                                                     int matchedPreferred, int missingPreferred,
                                                     boolean relevantProject, boolean relevantExperience) {
        int requiredTotal = matchedRequired + missingRequired;
        int preferredTotal = matchedPreferred + missingPreferred;
        double requiredCoverage = requiredTotal == 0 ? 1.0
                : (double) matchedRequired / requiredTotal;
        double preferredCoverage = preferredTotal == 0 ? 1.0
                : (double) matchedPreferred / preferredTotal;

        double score = 60 * requiredCoverage
                + 20 * preferredCoverage
                + (relevantProject ? 10 : 0)
                + (relevantExperience ? 10 : 0);
        int rounded = (int) Math.round(score);
        rounded = Math.max(0, Math.min(100, rounded));

        String explanation = "Required skill coverage: " + matchedRequired + "/" + requiredTotal
                + "; Preferred skill coverage: " + matchedPreferred + "/" + preferredTotal
                + "; Relevant project evidence: " + yesNo(relevantProject)
                + "; Relevant experience evidence: " + yesNo(relevantExperience);

        return new AtsReadinessAnalysis(rounded,
                "Job-specific resume readiness estimate (advisory — not a guarantee of ATS success)",
                matchedRequired, missingRequired, matchedPreferred, missingPreferred,
                relevantProject, relevantExperience, explanation);
    }

    private static String yesNo(boolean b) {
        return b ? "YES" : "NO";
    }

    // ─── Content detection ───────────────────────────────────────────────────

    private static boolean hasContent(CandidateProfile c, ResumeSection section) {
        return switch (section) {
            case EXPERIENCE -> notEmpty(c.experience());
            case INTERNSHIPS -> notEmpty(c.internships());
            case PROJECTS -> notEmpty(c.projects());
            case CERTIFICATIONS -> notEmpty(c.certifications());
            case EDUCATION -> notEmpty(c.education());
            default -> false;
        };
    }

    private static boolean hasSkills(CandidateProfile c) {
        return notEmpty(c.softwareSkills())
                || notEmpty(c.hardwareSkills())
                || notEmpty(c.skills());
    }

    private static boolean notEmpty(List<String> list) {
        if (list == null) {
            return false;
        }
        for (String s : list) {
            if (s != null && !s.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static void addCanonical(Set<String> set, List<String> raw) {
        if (raw == null) {
            return;
        }
        for (String s : raw) {
            String n = SkillTaxonomy.normalize(s);
            if (!n.isEmpty()) {
                set.add(n);
            }
        }
    }

    private static List<String> canonicalNames(List<SkillGap> gaps) {
        if (gaps == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (SkillGap g : gaps) {
            if (g != null && !g.canonicalSkill().isEmpty()) {
                out.add(g.canonicalSkill());
            }
        }
        return List.copyOf(out);
    }

    private CandidateProfile emptyProfile() {
        return new CandidateProfile(null, null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private static CareerGapAnalysis emptyGapAnalysis() {
        return new CareerGapAnalysis(null, null, List.of(), List.of(), List.of(), List.of(),
                new com.agentplatform.orchestrator.gap.ExperienceGap(null, null, null, false),
                com.agentplatform.orchestrator.matching.CareerTrack.UNKNOWN,
                com.agentplatform.orchestrator.matching.CareerTrack.UNKNOWN,
                false, com.agentplatform.orchestrator.gap.GapSeverity.NO_GAP, List.of());
    }
}