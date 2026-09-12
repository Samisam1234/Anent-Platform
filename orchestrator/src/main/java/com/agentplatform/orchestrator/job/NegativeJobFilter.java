package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.matching.CareerTrack;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Excludes listings that are clearly not engineering roles for the candidate's discipline:
 * service-desk, helpdesk, product-support, sales and marketing positions, plus unpaid
 * placements.
 *
 * <p><strong>Matching is deliberately restricted to the title and to structured fields.</strong>
 * A rejection is only ever triggered by the job title or by {@code employmentType}. Free
 * description text is never used, because a legitimate firmware role routinely says
 * "this is not a support role" or "no sales responsibilities", and rejecting on an
 * incidental sentence would discard good jobs. Titles are short and deliberate, so a
 * phrase match there is meaningful; the same phrase buried in a paragraph is not.</p>
 *
 * <p>Phrases are matched on word boundaries, so {@code sales} rejects "Sales Engineer" but
 * not a role at "Salesforce".</p>
 *
 * <p>Two rule tiers. The global tier — service desk, helpdesk, help desk, sales and the
 * unpaid rules — applies to every search, because those phrases appear in the exclusion
 * list of every discipline and describe roles that are not engineering roles for anyone.
 * The discipline tier adds rules that are only wrong for particular tracks: product support
 * for software and VLSI/FPGA, marketing for the hardware disciplines, SaaS for VLSI/FPGA.
 * Those are applied relative to the candidate's own tracks, and are not imposed on a search
 * with no known discipline, which has no basis to judge them.</p>
 */
@Component
public class NegativeJobFilter {

    /**
     * Rules that apply to every search, whether or not the candidate's discipline is known.
     *
     * <p>These are the phrases that appear in the exclusion list of <em>every</em>
     * discipline — service desk, helpdesk, help desk and sales — together with the unpaid
     * rules. They describe roles that are not engineering roles for anyone, so there is no
     * reason to wait for a candidate track before applying them; making them global is also
     * what stops an anonymous or skill-less search from passing every listing through.</p>
     */
    private static final List<String> GLOBAL_EXCLUSIONS = List.of(
            "service desk", "helpdesk", "help desk", "sales",
            "unpaid", "unpaid internship", "internship without stipend", "no stipend", "volunteer");

    /**
     * Additional rules that only apply to the listed disciplines, exactly as specified:
     * product support is excluded for software and VLSI/FPGA (but not for embedded, where
     * product-facing firmware work is legitimate), marketing for the hardware disciplines,
     * and SaaS for VLSI/FPGA.
     */
    private static final Map<CareerTrack, List<String>> TRACK_EXCLUSIONS = Map.of(
            CareerTrack.SOFTWARE, List.of("product support"),
            CareerTrack.VLSI_FPGA, List.of("product support", "saas"),
            CareerTrack.EMBEDDED, List.of("marketing"),
            CareerTrack.HARDWARE, List.of("marketing"),
            CareerTrack.AI_ML, List.of());

    /** Compiled word-boundary patterns, built once. */
    private static final Map<String, Pattern> PATTERNS = buildPatterns();

    private static Map<String, Pattern> buildPatterns() {
        Set<String> phrases = new LinkedHashSet<>(GLOBAL_EXCLUSIONS);
        TRACK_EXCLUSIONS.values().forEach(phrases::addAll);
        Map<String, Pattern> map = new java.util.HashMap<>();
        for (String phrase : phrases) {
            map.put(phrase, Pattern.compile("\\b" + Pattern.quote(phrase.toLowerCase(Locale.ROOT)) + "\\b"));
        }
        return Map.copyOf(map);
    }

    /** Whether the listing should be excluded for a candidate with these tracks. */
    public boolean isExcluded(Job job, Set<CareerTrack> candidateTracks) {
        return exclusionReason(job, candidateTracks) != null;
    }

    /**
     * The phrase that caused the exclusion, or {@code null} if the listing is acceptable.
     * Returned so the exclusion can be logged and explained rather than silently dropped.
     */
    public String exclusionReason(Job job, Set<CareerTrack> candidateTracks) {
        if (job == null) {
            return null;
        }
        String title = lower(job.title());
        String employmentType = lower(job.employmentType());

        for (String phrase : GLOBAL_EXCLUSIONS) {
            if (contains(title, phrase) || contains(employmentType, phrase)) {
                return phrase;
            }
        }
        if (title.isEmpty()) {
            return null;
        }
        for (String phrase : applicableExclusions(candidateTracks)) {
            if (contains(title, phrase)) {
                return phrase;
            }
        }
        return null;
    }

    /** Union of the discipline rules that apply to this candidate, de-duplicated in order. */
    Set<String> applicableExclusions(Set<CareerTrack> candidateTracks) {
        Set<String> phrases = new LinkedHashSet<>();
        if (candidateTracks == null || candidateTracks.isEmpty()) {
            return phrases;
        }
        for (CareerTrack track : candidateTracks) {
            if (track == null) {
                continue;
            }
            List<String> own = TRACK_EXCLUSIONS.get(track);
            if (own != null) {
                phrases.addAll(own);
                continue;
            }
            // A MIXED candidate spans families, so both families' rules apply.
            if (track == CareerTrack.MIXED) {
                TRACK_EXCLUSIONS.values().forEach(phrases::addAll);
            }
        }
        return phrases;
    }

    private static boolean contains(String haystack, String phrase) {
        if (haystack.isEmpty()) {
            return false;
        }
        Pattern pattern = PATTERNS.get(phrase);
        return pattern != null && pattern.matcher(haystack).find();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
