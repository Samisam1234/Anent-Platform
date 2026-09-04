package com.agentplatform.orchestrator.tailoring;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import com.agentplatform.orchestrator.resume.SkillTaxonomy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Generates a safe, deterministic, review-only tailored resume draft (Phase 4.5).
 *
 * <p>Consumes a {@link CandidateProfile}, a {@link Job} and the {@link ResumeTailoringAnalysis}
 * (Phase 4.4) and produces a {@link TailoredResumeDraft} by reordering/rewording/highlighting
 * content that already exists in the candidate profile. It NEVER fabricates: missing job
 * requirements are kept out of the draft and only reported as advisory warnings, and the
 * original candidate/job/analysis objects are never mutated.</p>
 *
 * <p>Fully deterministic — identical inputs always yield identical output. No LLM, no
 * network, no timestamps, no random values, no database. The result is a review draft only;
 * it is not persisted and does not overwrite anything.</p>
 */
@Service
public class TailoredResumeDraftService {

    private static final String WARN_MISSING_NOT_ADDED =
            "Missing job requirements were NOT added to this draft.";
    private static final String WARN_ONLY_EXISTING =
            "Only existing candidate information was used; no skills, experience, projects, "
                    + "internships, or certifications were invented.";
    private static final String WARN_ORIGINAL_UNTOUCHED =
            "The original resume and candidate profile were NOT modified.";

    /**
     * Builds a safe, deterministic tailored resume draft. All inputs may be {@code null}.
     */
    public TailoredResumeDraft generate(CandidateProfile candidate, Job job,
                                        ResumeTailoringAnalysis analysis) {
        CandidateProfile c = candidate == null ? emptyProfile() : candidate;
        ResumeTailoringAnalysis a = analysis == null ? emptyAnalysis() : analysis;

        String jobId = a.jobId() == null ? (job != null ? job.id() : null) : a.jobId();
        Long candidateId = a.candidateId();

        List<String> orderedSkills = orderSkills(c, a);
        List<String> projects = orderEntries(c.projects(), a.relevantProjects(),
                ResumeSection.PROJECTS);
        List<String> experience = orderEntries(c.experience(), a.relevantExperience(),
                ResumeSection.EXPERIENCE);
        List<String> internships = orderEntries(c.internships(), a.relevantInternships(),
                ResumeSection.INTERNSHIPS);
        List<ResumeSection> sectionOrder = applySectionOrder(c, a);

        String summary = buildSummary(c);

        List<String> warnings = new ArrayList<>();
        warnings.add(WARN_MISSING_NOT_ADDED);
        warnings.add(WARN_ONLY_EXISTING);
        warnings.add(WARN_ORIGINAL_UNTOUCHED);
        if (!a.missingRequirements().isEmpty()) {
            warnings.add("Some job requirements are missing from the candidate profile and were "
                    + "not added to this resume draft.");
        }
        warnings = List.copyOf(warnings);

        return new TailoredResumeDraft(jobId, candidateId, summary, orderedSkills,
                projects, experience, internships, sectionOrder,
                DraftOrigin.DETERMINISTIC, warnings);
    }

    // ─── Skill ordering ─────────────────────────────────────────────────────
    //
    // Priority: required-job highlighted skills → preferred-job highlighted skills →
    // remaining existing candidate skills. The analysis already lists highlighted skills in
    // required-then-preferred order, so it forms the priority prefix; everything is
    // canonicalized and deduplicated. Missing requirements never reach the draft.

    private static List<String> orderSkills(CandidateProfile c, ResumeTailoringAnalysis a) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (HighlightedSkill h : a.highlightedSkills()) {
            addCanonical(result, h.canonicalSkill());
        }
        // Remaining existing candidate skills (software → hardware → general), deduped.
        addCandidateSkills(result, c.softwareSkills());
        addCandidateSkills(result, c.hardwareSkills());
        addCandidateSkills(result, c.skills());
        return List.copyOf(result);
    }

    private static void addCandidateSkills(Set<String> set, List<String> raw) {
        if (raw == null) {
            return;
        }
        for (String s : raw) {
            addCanonical(set, s);
        }
    }

    private static void addCanonical(Set<String> set, String raw) {
        String n = SkillTaxonomy.normalize(raw);
        if (!n.isEmpty()) {
            set.add(n);
        }
    }

    // ─── Entry ordering (projects / experience / internships) ──────────────

    /**
     * Relevant (analysis-flagged) entries first, then remaining existing candidate entries,
     * preserving original text and dropping duplicates. Only candidate content is used.
     */
    private static List<String> orderEntries(List<String> candidateEntries,
                                             List<RelevantEntry> relevant,
                                             ResumeSection section) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (relevant != null) {
            for (RelevantEntry e : relevant) {
                if (e != null && e.section() == section && notBlank(e.content())) {
                    result.add(e.content().trim());
                }
            }
        }
        if (candidateEntries != null) {
            for (String line : candidateEntries) {
                if (notBlank(line)) {
                    result.add(line.trim());
                }
            }
        }
        return List.copyOf(result);
    }

    // ─── Section ordering ──────────────────────────────────────────────────

    /**
     * Uses the analysis' recommended section order but keeps only sections that actually have
     * candidate content — never inventing or force-carrying empty sections.
     */
    private static List<ResumeSection> applySectionOrder(CandidateProfile c,
                                                         ResumeTailoringAnalysis a) {
        LinkedHashSet<ResumeSection> result = new LinkedHashSet<>();
        for (ResumeSection s : a.recommendedSectionOrder()) {
            if (hasContent(c, s)) {
                result.add(s);
            }
        }
        return List.copyOf(result);
    }

    private static boolean hasContent(CandidateProfile c, ResumeSection section) {
        return switch (section) {
            case EXPERIENCE -> notEmpty(c.experience());
            case INTERNSHIPS -> notEmpty(c.internships());
            case PROJECTS -> notEmpty(c.projects());
            case CERTIFICATIONS -> notEmpty(c.certifications());
            case EDUCATION -> notEmpty(c.education());
            case SKILLS -> hasSkills(c);
            case SUMMARY -> true; // SUMMARY always anchors the top of the draft
        };
    }

    private static boolean hasSkills(CandidateProfile c) {
        return notEmpty(c.softwareSkills()) || notEmpty(c.hardwareSkills()) || notEmpty(c.skills());
    }

    private static boolean notEmpty(List<String> list) {
        if (list == null) {
            return false;
        }
        for (String s : list) {
            if (notBlank(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ─── Deterministic professional summary ────────────────────────────────
    //
    // Uses ONLY existing candidate content with conservative wording: no "expert",
    // "professional", or "experienced" proficiency claims and no invented achievements.

    private static String buildSummary(CandidateProfile c) {
        List<String> clauses = new ArrayList<>();

        String topSkills = topSkills(c, 6);
        if (!topSkills.isEmpty()) {
            String lead = notEmpty(c.education()) ? "Graduate" : "Candidate";
            clauses.add(lead + " with knowledge of " + topSkills + ".");
        }
        if (notEmpty(c.projects())) {
            clauses.add("Hands-on experience through projects involving relevant technologies.");
        }
        if (notEmpty(c.experience())) {
            clauses.add("Background includes prior work experience.");
        }
        if (notEmpty(c.internships())) {
            clauses.add("Completed internship experience.");
        }
        if (notEmpty(c.education())) {
            clauses.add("Education background includes " + topEducation(c, 2) + ".");
        }

        if (clauses.isEmpty()) {
            return "No candidate profile content available to summarize.";
        }
        return String.join(" ", clauses).trim();
    }

    private static String topSkills(CandidateProfile c, int max) {
        List<String> pool = new ArrayList<>();
        addNonNulls(pool, c.softwareSkills());
        addNonNulls(pool, c.hardwareSkills());
        addNonNulls(pool, c.skills());
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (String s : pool) {
            String n = SkillTaxonomy.normalize(s);
            if (!n.isEmpty()) {
                canonical.add(n);
            }
        }
        List<String> result = new ArrayList<>(canonical);
        if (result.size() > max) {
            result = new ArrayList<>(result.subList(0, max));
        }
        return String.join(", ", result);
    }

    private static String topEducation(CandidateProfile c, int max) {
        List<String> education = c.education() == null ? List.of() : c.education();
        List<String> present = new ArrayList<>();
        for (String e : education) {
            if (notBlank(e)) {
                present.add(e.trim());
            }
        }
        if (present.size() > max) {
            present = new ArrayList<>(present.subList(0, max));
        }
        return String.join("; ", present);
    }

    private static void addNonNulls(List<String> into, List<String> from) {
        if (from == null) {
            return;
        }
        for (String s : from) {
            if (s != null) {
                into.add(s);
            }
        }
    }

    // ─── Empty fallbacks ───────────────────────────────────────────────────

    private CandidateProfile emptyProfile() {
        return new CandidateProfile(null, null, null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }

    private ResumeTailoringAnalysis emptyAnalysis() {
        return new ResumeTailoringAnalysis(null, null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new AtsReadinessAnalysis(0, "", 0, 0, 0, 0, false, false, ""));
    }
}