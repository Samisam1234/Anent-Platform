package com.agentplatform.orchestrator.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Weighted relevance scoring between a job listing and a set of search keywords.
 *
 * <p>This replaces a plain "does any keyword appear anywhere" test. That test let a single
 * incidental word in a long description qualify a listing, which is how service-desk and
 * product-support roles ended up in front of engineering candidates.</p>
 *
 * <p>Each keyword contributes the weight of the <em>strongest</em> field it appears in:</p>
 * <ul>
 *   <li>{@value #TITLE_WEIGHT} — the title. A title match is the strongest signal there is.</li>
 *   <li>{@value #SKILL_WEIGHT} — a declared required or preferred skill. Structured, and
 *       authored as a requirement rather than prose.</li>
 *   <li>{@value #DESCRIPTION_WEIGHT} — the description alone. Deliberately the weakest:
 *       one mention in a paragraph cannot carry a listing on its own.</li>
 * </ul>
 *
 * <p>Contributions are summed across distinct keywords and compared against
 * {@value #RELEVANCE_THRESHOLD}. A single description-only hit therefore never qualifies,
 * while a title hit or a declared-skill hit does, and several independent description
 * mentions can accumulate to qualify a listing that genuinely discusses the discipline.
 * Multi-word phrases score a small specificity bonus, since "Spring Boot" says more than a
 * bare token.</p>
 *
 * <p>Matching is on word boundaries, so {@code c} does not match "Scala" and {@code ai}
 * does not match "maintain". Boundaries are only asserted where the phrase actually begins
 * or ends with a word character, so terms such as {@code C++} still match.</p>
 */
@Component
public class JobRelevanceScorer {

    static final double TITLE_WEIGHT = 3.0;
    static final double SKILL_WEIGHT = 2.5;
    static final double DESCRIPTION_WEIGHT = 1.0;
    static final double SPECIFICITY_BONUS = 0.5;
    static final double RELEVANCE_THRESHOLD = 2.5;

    /**
     * The relevance score of a listing against the keywords. Higher is more relevant.
     * Keywords are counted once each, at the weight of their strongest matching field.
     */
    public double score(Job job, List<String> keywords) {
        if (job == null) {
            return 0.0;
        }
        List<String> clean = clean(keywords);
        if (clean.isEmpty()) {
            return 0.0;
        }
        String title = lower(job.title());
        String description = lower(job.description());
        List<String> skills = skillStrings(job);

        double total = 0.0;
        for (String keyword : clean) {
            double best = 0.0;
            if (containsWord(title, keyword)) {
                best = TITLE_WEIGHT;
            }
            else if (skills.stream().anyMatch(skill -> containsWord(skill, keyword)
                    || skill.equals(keyword))) {
                best = SKILL_WEIGHT;
            }
            else if (containsWord(description, keyword)) {
                best = DESCRIPTION_WEIGHT;
            }
            if (best > 0.0 && isSpecificPhrase(keyword)) {
                best += SPECIFICITY_BONUS;
            }
            total += best;
        }
        return total;
    }

    /** Whether the listing is relevant enough to be worth showing. */
    public boolean isRelevant(Job job, List<String> keywords) {
        List<String> clean = clean(keywords);
        if (clean.isEmpty()) {
            // No relevance signal available. This deliberately does not mean "relevant";
            // the caller decides what to do when it cannot judge, and the career-track and
            // negative filters still run.
            return true;
        }
        return score(job, keywords) >= RELEVANCE_THRESHOLD;
    }

    /**
     * The individual keyword contributions, strongest first, for logging and explanation.
     * Each entry is {@code keyword=weight}.
     */
    public List<String> explain(Job job, List<String> keywords) {
        List<String> clean = clean(keywords);
        if (clean.isEmpty() || job == null) {
            return List.of();
        }
        String title = lower(job.title());
        String description = lower(job.description());
        List<String> skills = skillStrings(job);
        List<String> out = new ArrayList<>();
        for (String keyword : clean) {
            double weight = 0.0;
            if (containsWord(title, keyword)) {
                weight = TITLE_WEIGHT;
            }
            else if (skills.stream().anyMatch(s -> containsWord(s, keyword) || s.equals(keyword))) {
                weight = SKILL_WEIGHT;
            }
            else if (containsWord(description, keyword)) {
                weight = DESCRIPTION_WEIGHT;
            }
            if (weight > 0.0 && isSpecificPhrase(keyword)) {
                weight += SPECIFICITY_BONUS;
            }
            if (weight > 0.0) {
                out.add(keyword + "=" + weight);
            }
        }
        return out;
    }

    private static List<String> clean(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static List<String> skillStrings(Job job) {
        List<String> skills = new ArrayList<>();
        addLowered(skills, job.requiredSkills());
        addLowered(skills, job.preferredSkills());
        return skills;
    }

    private static void addLowered(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                target.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    /** A phrase of two or more words is more specific than a bare token. */
    private static boolean isSpecificPhrase(String keyword) {
        return keyword.trim().split("\\s+").length > 1;
    }

    /**
     * Word-boundary containment. Boundaries are asserted only where the phrase edge is a
     * word character, so {@code C++} and {@code .NET} still match while {@code ai} does not
     * match inside "maintain".
     */
    static boolean containsWord(String haystack, String phrase) {
        if (haystack == null || haystack.isEmpty() || phrase == null || phrase.isEmpty()) {
            return false;
        }
        String quoted = Pattern.quote(phrase);
        String prefix = Character.isLetterOrDigit(phrase.charAt(0)) ? "\\b" : "";
        String suffix = Character.isLetterOrDigit(phrase.charAt(phrase.length() - 1)) ? "\\b" : "";
        return Pattern.compile(prefix + quoted + suffix).matcher(haystack).find();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
