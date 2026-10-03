package com.agentplatform.orchestrator.matching;

import com.agentplatform.orchestrator.job.Job;
import com.agentplatform.orchestrator.resume.CandidateProfile;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Classifies jobs and candidate profiles into fine-grained career tracks and turns the
 * relationship between them into a track score.
 *
 * <p>Tracks are no longer collapsed into a single SOFTWARE/HARDWARE pair: embedded and
 * VLSI/FPGA are distinct disciplines with different role vocabularies, and AI/ML is its own
 * track rather than generic software. The coarse family is still available through
 * {@link CareerTrack#family()} for callers that only reason at that level.</p>
 *
 * <p>An unclassifiable job is scored <em>neutrally</em> ({@link #UNKNOWN_TRACK_SCORE}), not
 * favourably. It previously received 0.8, which let a listing with no discernible
 * discipline — a service-desk or product-support role, say — out-earn a genuine partial
 * match on track alone. Neutral keeps such a listing from being rewarded while still
 * letting a legitimately unusual role through on its other merits; zeroing it would
 * discard real jobs whose wording simply does not use these keywords.</p>
 */
@Component
public class CareerTrackEngine {

    /** Neutral score for a job whose discipline cannot be determined. */
    static final double UNKNOWN_TRACK_SCORE = 0.5;
    /** Score when the job and candidate are in the same family but different disciplines. */
    static final double SAME_FAMILY_SCORE = 0.6;
    /** Score when the job and candidate are in different families. */
    static final double CROSS_FAMILY_SCORE = 0.3;
    /** Score when the candidate shows no skill signal at all. */
    static final double NO_SIGNAL_SCORE = 0.5;

    private static final Set<String> SW_KEYWORDS = Set.of("java", "spring boot", "backend", "full stack", "javascript", "typescript", "react", "node", "microservices", "sql", "postgresql", "rest apis", "docker", "cloud", "api", "web", "python");
    private static final Set<String> HW_KEYWORDS = Set.of("vlsi", "rtl", "verilog", "systemverilog", "uvm", "fpga", "embedded", "firmware", "microcontroller", "arm", "rtos", "digital design", "asic", "physical design", "pcb", "hardware", "sta");
    private static final Set<String> EMBEDDED_KEYWORDS = Set.of("embedded", "firmware", "microcontroller", "rtos", "bare metal", "bare-metal", "freertos", "stm32", "arm cortex", "iot firmware", "embedded c", "embedded software");
    private static final Set<String> VLSI_KEYWORDS = Set.of("vlsi", "rtl", "verilog", "systemverilog", "uvm", "fpga", "asic", "physical design", "sta", "static timing", "logic synthesis", "design verification", "soc design", "vhdl", "timing closure", "place and route", "dft");
    private static final Set<String> AI_ML_KEYWORDS = Set.of("machine learning", "deep learning", "neural network", "computer vision", "nlp", "natural language processing", "mlops", "tensorflow", "pytorch", "keras", "scikit-learn", "llm", "data science", "model training", "ai engineer", "reinforcement learning", "generative ai");

    /**
     * Precompiled matchers for every keyword in every vocabulary above, built once.
     *
     * <p>Keyword matching used to be {@code text.contains(kw)} — plain substring matching.
     * That made short acronyms in this taxonomy match inside ordinary English: {@code "sta"}
     * is a VLSI keyword and also a substring of <em>standard</em>, <em>status</em>,
     * <em>start</em>, <em>state</em> and <em>establish</em>, so almost every job description
     * produced a VLSI hit. The effect was two-sided: service-desk, product-support,
     * marketing and sales listings were classified VLSI/FPGA and so survived the
     * career-track filter for a hardware candidate, while a genuine software role that
     * happened to say "standard" could be classified VLSI/FPGA and wrongly rejected for a
     * software candidate.</p>
     *
     * <p>These use the same boundary rule already proven in
     * {@code JobRelevanceScorer.containsWord} and {@code NegativeJobFilter.buildPatterns}:
     * a word boundary is asserted only where the phrase edge is a word character. That keeps
     * symbol-terminated keywords working ({@code C++}, {@code .NET}) while stopping
     * {@code "api"} from matching inside <em>rapid</em> or <em>capital</em>. They are
     * compiled once rather than per call because {@code countHits} runs five vocabularies
     * over the whole listing text for every job in every search.</p>
     */
    private static final Map<String, Pattern> KEYWORD_PATTERNS = buildKeywordPatterns();

    private static Map<String, Pattern> buildKeywordPatterns() {
        Set<String> all = new HashSet<>();
        all.addAll(SW_KEYWORDS);
        all.addAll(HW_KEYWORDS);
        all.addAll(EMBEDDED_KEYWORDS);
        all.addAll(VLSI_KEYWORDS);
        all.addAll(AI_ML_KEYWORDS);
        Map<String, Pattern> patterns = new HashMap<>();
        for (String keyword : all) {
            patterns.put(keyword, compileKeyword(keyword));
        }
        return Map.copyOf(patterns);
    }

    private static Pattern compileKeyword(String keyword) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        String prefix = Character.isLetterOrDigit(lower.charAt(0)) ? "\\b" : "";
        String suffix = Character.isLetterOrDigit(lower.charAt(lower.length() - 1)) ? "\\b" : "";
        return Pattern.compile(prefix + Pattern.quote(lower) + suffix, Pattern.CASE_INSENSITIVE);
    }

    public CareerTrackEvaluation evaluate(CandidateProfile candidate, Job job) {
        CareerTrack jobTrack = this.classifyJob(job);
        if (candidate == null) {
            return new CareerTrackEvaluation(jobTrack, NO_SIGNAL_SCORE);
        }
        Set<CareerTrack> candidateTracks = classifyCandidate(candidate);
        boolean hasSoftwareSkills = candidateTracks.stream().anyMatch(CareerTrack::isSoftwareFamily);
        boolean hasHardwareSkills = candidateTracks.stream().anyMatch(CareerTrack::isHardwareFamily);

        double score;
        switch (jobTrack) {
            case SOFTWARE:
            case EMBEDDED:
            case VLSI_FPGA:
            case AI_ML:
                score = specificTrackScore(jobTrack, candidateTracks, hasSoftwareSkills, hasHardwareSkills);
                break;
            case HARDWARE: {
                score = hasHardwareSkills ? 1.0 : (hasSoftwareSkills ? CROSS_FAMILY_SCORE : NO_SIGNAL_SCORE);
                break;
            }
            case MIXED: {
                if (hasSoftwareSkills && hasHardwareSkills) {
                    score = 1.0;
                }
                else if (hasSoftwareSkills || hasHardwareSkills) {
                    score = 0.85;
                }
                else {
                    score = NO_SIGNAL_SCORE;
                }
                break;
            }
            default:
                // Unclassifiable: neutral, never a bonus.
                score = UNKNOWN_TRACK_SCORE;
        }
        return new CareerTrackEvaluation(jobTrack, score);
    }

    /**
     * Scores a job with a specific discipline against the tracks the candidate shows.
     *
     * <p>Exact discipline match scores highest. A different discipline in the same family
     * (embedded vs VLSI/FPGA, or AI/ML vs software) scores {@link #SAME_FAMILY_SCORE} —
     * related and worth surfacing, but not a direct fit. A different family scores
     * {@link #CROSS_FAMILY_SCORE}, which is at or below the mismatch threshold so the
     * matching service applies its track penalty.</p>
     */
    private double specificTrackScore(CareerTrack jobTrack, Set<CareerTrack> candidateTracks,
                                      boolean hasSoftwareSkills, boolean hasHardwareSkills) {
        if (candidateTracks.contains(jobTrack)) {
            return 1.0;
        }
        boolean sameFamily = candidateTracks.stream()
                .anyMatch(t -> t.family() == jobTrack.family());
        if (sameFamily) {
            return SAME_FAMILY_SCORE;
        }
        boolean jobIsHardware = jobTrack.isHardwareFamily();
        boolean candidateHasOppositeFamily = jobIsHardware ? hasSoftwareSkills : hasHardwareSkills;
        return candidateHasOppositeFamily ? CROSS_FAMILY_SCORE : NO_SIGNAL_SCORE;
    }

    /**
     * Classifies a job into a fine-grained track from its title, description and required
     * skills. Discipline-specific keyword sets take precedence over the generic
     * software/hardware sets, so a VLSI listing is reported as {@code VLSI_FPGA} rather
     * than generic {@code HARDWARE}.
     */
    public CareerTrack classifyJob(Job job) {
        if (job == null) {
            return CareerTrack.UNKNOWN;
        }
        String text = ((job.title() != null ? job.title() : "") + " "
                + (job.description() != null ? job.description() : "") + " "
                + (job.requiredSkills() != null ? String.join(" ", job.requiredSkills()) : ""))
                .toLowerCase(Locale.ROOT);

        int vlsiHits = countHits(VLSI_KEYWORDS, text);
        int embeddedHits = countHits(EMBEDDED_KEYWORDS, text);
        int aiHits = countHits(AI_ML_KEYWORDS, text);
        int swHits = countHits(SW_KEYWORDS, text);
        int hwHits = countHits(HW_KEYWORDS, text);

        int specificHits = vlsiHits + embeddedHits + aiHits;
        if (specificHits > 0) {
            // Pick the strongest discipline signal. A tie between two hardware disciplines
            // (or between AI/ML and software) stays MIXED rather than guessing.
            int best = Math.max(vlsiHits, Math.max(embeddedHits, aiHits));
            int winners = 0;
            CareerTrack winner = null;
            if (vlsiHits == best) { winners++; winner = CareerTrack.VLSI_FPGA; }
            if (embeddedHits == best) { winners++; winner = CareerTrack.EMBEDDED; }
            if (aiHits == best) { winners++; winner = CareerTrack.AI_ML; }
            if (winners == 1) {
                // A clear single discipline wins, but a comparable opposing-family signal
                // makes the role genuinely cross-disciplinary rather than a pure fit.
                boolean opposing = winner.isHardwareFamily() ? swHits >= best
                        : hwHits >= best;
                return opposing ? CareerTrack.MIXED : winner;
            }
            return CareerTrack.MIXED;
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

    /**
     * The fine-grained tracks a candidate's skills indicate. Derived from the same keyword
     * vocabulary used for jobs so both sides of the comparison mean the same thing.
     */
    public Set<CareerTrack> classifyCandidate(CandidateProfile candidate) {
        Set<CareerTrack> tracks = EnumSet.noneOf(CareerTrack.class);
        if (candidate == null) {
            return tracks;
        }
        StringBuilder text = new StringBuilder();
        appendSkills(text, candidate.softwareSkills());
        appendSkills(text, candidate.hardwareSkills());
        appendSkills(text, candidate.skills());
        String lower = text.toString().toLowerCase(Locale.ROOT);
        if (lower.isBlank()) {
            return tracks;
        }
        if (countHits(VLSI_KEYWORDS, lower) > 0) {
            tracks.add(CareerTrack.VLSI_FPGA);
        }
        if (countHits(EMBEDDED_KEYWORDS, lower) > 0) {
            tracks.add(CareerTrack.EMBEDDED);
        }
        if (countHits(AI_ML_KEYWORDS, lower) > 0) {
            tracks.add(CareerTrack.AI_ML);
        }
        if (countHits(SW_KEYWORDS, lower) > 0) {
            tracks.add(CareerTrack.SOFTWARE);
        }
        if (countHits(HW_KEYWORDS, lower) > 0) {
            tracks.add(CareerTrack.HARDWARE);
        }
        return tracks;
    }

    private static void appendSkills(StringBuilder sb, java.util.List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                sb.append(value).append(' ');
            }
        }
    }

    /**
     * Counts how many of {@code keywords} occur in {@code text} as whole words or phrases.
     *
     * <p>Substring matching is deliberately not used — see {@link #KEYWORD_PATTERNS}.</p>
     */
    private static int countHits(Set<String> keywords, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int hits = 0;
        for (String keyword : keywords) {
            Pattern pattern = KEYWORD_PATTERNS.get(keyword);
            if (pattern != null && pattern.matcher(text).find()) {
                hits++;
            }
        }
        return hits;
    }

    public record CareerTrackEvaluation(CareerTrack jobTrack, double trackScore) {
    }
}
