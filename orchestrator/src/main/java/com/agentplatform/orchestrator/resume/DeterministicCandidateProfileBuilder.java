package com.agentplatform.orchestrator.resume;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Privacy-preserving, deterministic resume profile builder used when AI is unavailable. */
final class DeterministicCandidateProfileBuilder {

    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d .()-]{7,}\\d)(?!\\d)");

    private static final List<String> CERT_WORDS = List.of("certification", "certified", "certificate", "aws", "oracle", "coursera", "nptel", "udemy");

    enum CareerTrack {
        SOFTWARE,
        EMBEDDED,
        VLSI_FPGA,
        ECE,
        AI_ML
    }

    /**
     * Ordered heading keywords (more specific first) mapped to their resume section.
     * Used both to switch the active section while scanning and to attribute skills
     * found on the same line after the heading's colon.
     */
    private static final List<Map.Entry<String, ResumeEvidence.SourceSection>> HEADINGS = List.of(
            Map.entry("technical skills", ResumeEvidence.SourceSection.SKILLS),
            Map.entry("technical proficiencies", ResumeEvidence.SourceSection.SKILLS),
            Map.entry("core competencies", ResumeEvidence.SourceSection.SKILLS),
            Map.entry("technologies", ResumeEvidence.SourceSection.SKILLS),
            Map.entry("skills", ResumeEvidence.SourceSection.SKILLS),
            Map.entry("work history", ResumeEvidence.SourceSection.EXPERIENCE),
            Map.entry("employment history", ResumeEvidence.SourceSection.EXPERIENCE),
            Map.entry("employment", ResumeEvidence.SourceSection.EXPERIENCE),
            Map.entry("work experience", ResumeEvidence.SourceSection.EXPERIENCE),
            Map.entry("professional experience", ResumeEvidence.SourceSection.EXPERIENCE),
            Map.entry("experience", ResumeEvidence.SourceSection.EXPERIENCE),
            Map.entry("project experience", ResumeEvidence.SourceSection.PROJECT),
            Map.entry("academic projects", ResumeEvidence.SourceSection.PROJECT),
            Map.entry("projects", ResumeEvidence.SourceSection.PROJECT),
            Map.entry("education", ResumeEvidence.SourceSection.EDUCATION),
            Map.entry("academic", ResumeEvidence.SourceSection.EDUCATION),
            Map.entry("certifications", ResumeEvidence.SourceSection.CERTIFICATION),
            Map.entry("certificates", ResumeEvidence.SourceSection.CERTIFICATION),
            Map.entry("licenses", ResumeEvidence.SourceSection.CERTIFICATION),
            Map.entry("certification", ResumeEvidence.SourceSection.CERTIFICATION),
            Map.entry("summary", ResumeEvidence.SourceSection.SUMMARY),
            Map.entry("professional summary", ResumeEvidence.SourceSection.SUMMARY),
            Map.entry("objective", ResumeEvidence.SourceSection.SUMMARY),
            Map.entry("professional profile", ResumeEvidence.SourceSection.SUMMARY),
            Map.entry("profile", ResumeEvidence.SourceSection.SUMMARY)
    );

    CandidateProfile build(String resumeText) {
        String text = resumeText == null ? "" : resumeText.replace('\r', '\n');
        List<String> lines = Arrays.stream(text.split("\\n+"))
                .map(String::trim).filter(s -> !s.isBlank()).toList();

        String name = inferName(lines);
        String email = first(EMAIL, text);
        String phone = first(PHONE, text);

        // ── Skill extraction across all sections of the resume ────────────────
        Map<ResumeEvidence.SourceSection, Set<String>> rawBySection = collectRawSkills(lines);
        Set<String> rawSkills = new LinkedHashSet<>();
        for (Set<String> skills : rawBySection.values()) rawSkills.addAll(skills);
        // Only taxonomy-backed canonical names become skills — prose sentences or
        // unsupported words never leak into the profile (no fabricated skills).
        List<String> canonicalSkills = SkillTaxonomy.normalizeAll(rawSkills).stream()
                .filter(s -> SkillTaxonomy.getCategory(s) != null)
                .toList();

        // ── Build per-skill evidence from each section's raw matches ──────────
        List<ResumeEvidence> resumeEvidence = buildResumeEvidence(rawBySection);

        // ── Split canonical skills into software vs hardware reporting ────────
        List<String> software = splitSoftware(canonicalSkills);
        List<String> hardware = splitHardware(canonicalSkills);

        List<String> education = section(lines, List.of("education", "academic"), List.of("experience", "projects", "skills", "certifications"));
        if (education.isEmpty()) education = linesContaining(lines, List.of("b.tech", "b.e.", "bachelor", "master", "m.tech", "b.sc", "diploma"));
        List<String> experience = section(lines, List.of("experience", "employment", "work history"), List.of("education", "projects", "skills", "certifications"));
        List<String> projects = section(lines, List.of("projects", "project experience"), List.of("education", "experience", "skills", "certifications"));
        List<String> certifications = section(lines, List.of("certifications", "certificates"), List.of("education", "experience", "projects", "skills"));
        if (certifications.isEmpty()) certifications = linesContaining(lines, CERT_WORDS);
        List<String> internships = linesContaining(lines, List.of("intern", "internship"));

        String location = lines.stream().filter(l -> l.matches(".*(?:India|Remote|Hyderabad|Bengaluru|Bangalore|Pune|Chennai|Mumbai|Delhi).*"))
                .findFirst().orElse("");

        List<CareerTrack> tracks = detectTracks(canonicalSkills);
        List<CareerTrackEvidence> trackEvidence = buildTrackEvidence(tracks, canonicalSkills);
        List<String> roles = inferRoles(tracks);

        return new CandidateProfile(name, email, phone, location, education,
                canonicalSkills, experience, internships, projects, certifications,
                software, hardware, roles, location.isBlank() ? List.of() : List.of(location),
                resumeEvidence, trackEvidence);
    }

    // ─── Skill collection across sections ─────────────────────────────────────

    /**
     * Collects raw skill tokens grouped by the resume section they were found in.
     *
     * <p>Taxonomy hits are searched for across every line (both in comma-separated
     * skill tokens and embedded in prose), so a skill mentioned anywhere — a skills
     * list, a project bullet, an experience line, a certification — is captured once
     * per section. The active section is tracked by recognizing section headings,
     * including inline headings such as {@code Skills: Java, Spring Boot}.</p>
     */
    private Map<ResumeEvidence.SourceSection, Set<String>> collectRawSkills(List<String> lines) {
        Map<ResumeEvidence.SourceSection, Set<String>> bySection = new EnumMap<>(ResumeEvidence.SourceSection.class);
        for (ResumeEvidence.SourceSection s : ResumeEvidence.SourceSection.values()) {
            bySection.put(s, new LinkedHashSet<>());
        }
        ResumeEvidence.SourceSection current = ResumeEvidence.SourceSection.UNKNOWN;
        for (String line : lines) {
            Heading heading = detectHeading(line);
            if (heading != null) {
                current = heading.section();
                if (heading.content() != null && !heading.content().isBlank()) {
                    processSkillLine(bySection, current, heading.content());
                }
                continue;
            }
            processSkillLine(bySection, current, line);
        }
        return bySection;
    }

    /**
     * Detects whether a line begins a resume section.
     *
     * @return a {@link Heading}, or {@code null} if the line is not a heading
     */
    private Heading detectHeading(String line) {
        // Collapse internal runs of whitespace so "Technical    Skills" still resolves.
        String stripped = line.trim().replaceFirst("^[\\s\\-•*\\d\\.\\)]+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
        for (Map.Entry<String, ResumeEvidence.SourceSection> entry : HEADINGS) {
            String keyword = entry.getKey();
            if (!stripped.startsWith(keyword)) continue;
            String after = stripped.substring(keyword.length()).trim();
            if (after.isEmpty()) {
                return new Heading(entry.getValue(), null);
            }
            if (after.startsWith(":")) {
                return new Heading(entry.getValue(), after.substring(1).trim());
            }
            // Only treat as heading if keyword is followed by a non-letter (a real boundary).
            char c = after.charAt(0);
            if (!Character.isLetterOrDigit(c)) {
                return new Heading(entry.getValue(), null);
            }
        }
        return null;
    }

    private void processSkillLine(Map<ResumeEvidence.SourceSection, Set<String>> bySection,
                                  ResumeEvidence.SourceSection section, String line) {
        // 1) Explicit comma/slash separated raw tokens — preserves single-char skills
        //    like "C" that {@link SkillTaxonomy#findMatches} deliberately skips.
        bySection.get(section).addAll(splitTokens(line));
        // 2) Taxonomy matches embedded anywhere in the line (project/experience prose).
        bySection.get(section).addAll(SkillTaxonomy.findMatches(line));
    }

    /**
     * Builds a deduplicated, stable list of {@link ResumeEvidence} from the raw
     * per-section skill matches. Skills found in multiple sections are collapsed
     * into a single evidence entry using the most meaningful section.
     */
    private List<ResumeEvidence> buildResumeEvidence(Map<ResumeEvidence.SourceSection, Set<String>> rawBySection) {
        Map<String, ResumeEvidence.SourceSection> bestBySkill = new LinkedHashMap<>();
        Map<String, String> matchedTextBySkill = new LinkedHashMap<>();
        for (Map.Entry<ResumeEvidence.SourceSection, Set<String>> entry : rawBySection.entrySet()) {
            ResumeEvidence.SourceSection section = entry.getKey();
            for (String match : entry.getValue()) {
                String canonical = SkillTaxonomy.normalize(match);
                // Only real taxonomy skills count as evidence — prose tokens and
                // unknown words (which normalize to lowercased noise) are excluded.
                if (canonical.isEmpty() || SkillTaxonomy.getCategory(canonical) == null) continue;
                ResumeEvidence.SourceSection existing = bestBySkill.get(canonical);
                if (existing == null) {
                    bestBySkill.put(canonical, section);
                    matchedTextBySkill.put(canonical, match);
                } else if (sectionPriority(section) > sectionPriority(existing)) {
                    bestBySkill.put(canonical, section);
                    matchedTextBySkill.put(canonical, match);
                }
            }
        }
        List<ResumeEvidence> evidence = new ArrayList<>();
        for (Map.Entry<String, ResumeEvidence.SourceSection> entry : bestBySkill.entrySet()) {
            evidence.add(ResumeEvidence.of(entry.getKey(), entry.getValue(),
                    matchedTextBySkill.get(entry.getKey()), strengthFor(entry.getValue())));
        }
        return evidence;
    }

    /** Precedence used to pick the most meaningful section for a repeated skill. */
    private int sectionPriority(ResumeEvidence.SourceSection section) {
        return switch (section) {
            case SKILLS -> 6;
            case CERTIFICATION -> 5;
            case PROJECT -> 4;
            case EXPERIENCE -> 3;
            case EDUCATION -> 2;
            case SUMMARY -> 1;
            case UNKNOWN -> 0;
        };
    }

    /** Skills listed explicitly or proven by certification are stronger evidence. */
    private ResumeEvidence.EvidenceStrength strengthFor(ResumeEvidence.SourceSection section) {
        return switch (section) {
            case SKILLS, CERTIFICATION -> ResumeEvidence.EvidenceStrength.STRONG;
            case PROJECT, EXPERIENCE -> ResumeEvidence.EvidenceStrength.MEDIUM;
            case EDUCATION, SUMMARY -> ResumeEvidence.EvidenceStrength.WEAK;
            case UNKNOWN -> ResumeEvidence.EvidenceStrength.WEAK;
        };
    }

    /** Splits a line on commas/slashes/pipes and trims each token. */
    private List<String> splitTokens(String line) {
        List<String> tokens = new ArrayList<>();
        for (String part : line.split("[,/|\\n]")) {
            String t = part.trim();
            if (!t.isEmpty()) tokens.add(t);
        }
        return tokens;
    }

    private List<String> splitSoftware(List<String> canonical) {
        return canonical.stream()
                .filter(s -> {
                    SkillTaxonomy.Category c = SkillTaxonomy.getCategory(s);
                    return c == SkillTaxonomy.Category.SOFTWARE || c == SkillTaxonomy.Category.PROGRAMMING_AI;
                })
                .toList();
    }

    private List<String> splitHardware(List<String> canonical) {
        return canonical.stream()
                .filter(s -> {
                    SkillTaxonomy.Category c = SkillTaxonomy.getCategory(s);
                    return c == SkillTaxonomy.Category.EMBEDDED
                            || c == SkillTaxonomy.Category.VLSI_FPGA
                            || c == SkillTaxonomy.Category.COMMUNICATION;
                })
                .toList();
    }

    // ─── Career track detection (weighted evidence, not single keyword) ───────

    private List<CareerTrack> detectTracks(List<String> canonicalSkills) {
        Map<CareerTrack, Double> scores = new EnumMap<>(CareerTrack.class);
        for (CareerTrack track : CareerTrack.values()) scores.put(track, 0.0);

        for (String skill : canonicalSkills) {
            SkillTaxonomy.Category category = SkillTaxonomy.getCategory(skill);
            if (category == null) continue;
            switch (category) {
                case SOFTWARE -> bump(scores, CareerTrack.SOFTWARE, 2.0);
                case PROGRAMMING_AI -> bump(scores, CareerTrack.AI_ML, 2.0);
                case EMBEDDED -> bump(scores, CareerTrack.EMBEDDED, 2.0);
                case VLSI_FPGA -> bump(scores, CareerTrack.VLSI_FPGA, 2.0);
                case COMMUNICATION -> bump(scores, CareerTrack.ECE, 2.0);
            }
        }

        // Cross-category evidence (skills that count toward multiple tracks)
        for (String skill : canonicalSkills) {
            switch (skill) {
                case "Python" -> { bump(scores, CareerTrack.SOFTWARE, 0.5); }
                case "C", "Firmware" -> { bump(scores, CareerTrack.EMBEDDED, 0.5); }
                case "DSP", "Signal Processing", "MATLAB" -> { bump(scores, CareerTrack.ECE, 0.5); }
                default -> { }
            }
        }

        // Threshold: a track is "detected" only if it has enough weight
        List<CareerTrack> detected = new ArrayList<>();
        for (CareerTrack track : CareerTrack.values()) {
            if (scores.get(track) >= 2.0) detected.add(track);
        }
        detected.sort((t1, t2) -> Double.compare(scores.get(t2), scores.get(t1)));
        return detected;
    }

    /**
     * Builds {@link CareerTrackEvidence} — for each detected track, the weighted
     * score and the canonical skills that contributed category evidence, so every
     * track inference is traceable back to the resume. Contributing-skill scores
     * reuse the same cross-category rules as detection.
     */
    private List<CareerTrackEvidence> buildTrackEvidence(List<CareerTrack> tracks, List<String> canonicalSkills) {
        List<CareerTrackEvidence> result = new ArrayList<>();
        for (CareerTrack track : tracks) {
            List<String> contributing = new ArrayList<>();
            for (String skill : canonicalSkills) {
                if (categoryContributes(SkillTaxonomy.getCategory(skill), track)) {
                    contributing.add(skill);
                }
            }
            result.add(new CareerTrackEvidence(CareerTrackEvidence.trackLabelForCategory(categoryForTrack(track)),
                    scoreFor(track, canonicalSkills), contributing));
        }
        return result;
    }

    private boolean categoryContributes(SkillTaxonomy.Category category, CareerTrack track) {
        if (category == null) return false;
        return switch (track) {
            case SOFTWARE -> category == SkillTaxonomy.Category.SOFTWARE || category == SkillTaxonomy.Category.PROGRAMMING_AI;
            case AI_ML -> category == SkillTaxonomy.Category.PROGRAMMING_AI;
            case EMBEDDED -> category == SkillTaxonomy.Category.EMBEDDED;
            case VLSI_FPGA -> category == SkillTaxonomy.Category.VLSI_FPGA;
            case ECE -> category == SkillTaxonomy.Category.COMMUNICATION;
        };
    }

    private double scoreFor(CareerTrack track, List<String> canonicalSkills) {
        double score = 0.0;
        for (String skill : canonicalSkills) {
            if (categoryContributes(SkillTaxonomy.getCategory(skill), track)) score += 2.0;
            switch (skill) {
                case "Python" -> { if (track == CareerTrack.SOFTWARE) score += 0.5; }
                case "C", "Firmware" -> { if (track == CareerTrack.EMBEDDED) score += 0.5; }
                case "DSP", "Signal Processing", "MATLAB" -> { if (track == CareerTrack.ECE) score += 0.5; }
                default -> { }
            }
        }
        return score;
    }

    private SkillTaxonomy.Category categoryForTrack(CareerTrack track) {
        return switch (track) {
            case SOFTWARE -> SkillTaxonomy.Category.SOFTWARE;
            case AI_ML -> SkillTaxonomy.Category.PROGRAMMING_AI;
            case EMBEDDED -> SkillTaxonomy.Category.EMBEDDED;
            case VLSI_FPGA -> SkillTaxonomy.Category.VLSI_FPGA;
            case ECE -> SkillTaxonomy.Category.COMMUNICATION;
        };
    }

    private void bump(Map<CareerTrack, Double> scores, CareerTrack track, double weight) {
        scores.put(track, scores.getOrDefault(track, 0.0) + weight);
    }

    private List<String> inferRoles(List<CareerTrack> tracks) {
        List<String> roles = new ArrayList<>();
        for (CareerTrack track : tracks) {
            switch (track) {
                case SOFTWARE -> roles.add("Software Engineer");
                case AI_ML -> roles.add("AI / ML Engineer");
                case EMBEDDED -> roles.add("Embedded Systems Engineer");
                case VLSI_FPGA -> roles.add("VLSI / FPGA Engineer");
                case ECE -> roles.add("Electronics / ECE Engineer");
            }
        }
        return roles;
    }

    // ─── Existing helper methods (unchanged) ──────────────────────────────────

    private String inferName(List<String> lines) {
        return lines.stream().filter(l -> !EMAIL.matcher(l).find() && !PHONE.matcher(l).find()
                        && l.matches("[A-Za-z][A-Za-z .'-]{2,80}") && l.split("\\s+").length <= 5)
                .findFirst().orElse("Candidate");
    }
    private String first(Pattern p, String text) { Matcher m = p.matcher(text); return m.find() ? m.group().trim() : ""; }
    private List<String> section(List<String> lines, List<String> headings, List<String> stops) {
        List<String> output = new ArrayList<>(); boolean active = false;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (headings.stream().anyMatch(lower::contains)) { active = true; continue; }
            if (active && stops.stream().anyMatch(lower::contains)) break;
            if (active && output.size() < 8) output.add(line);
        }
        return output;
    }
    private List<String> linesContaining(List<String> lines, List<String> words) { return lines.stream().filter(l -> words.stream().anyMatch(w -> l.toLowerCase(Locale.ROOT).contains(w))).limit(8).toList(); }

    /** Immutable result of {@link #detectHeading}: the resolved section plus optional inline content. */
    private record Heading(ResumeEvidence.SourceSection section, String content) {}
}
