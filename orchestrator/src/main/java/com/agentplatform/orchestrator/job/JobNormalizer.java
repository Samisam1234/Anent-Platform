package com.agentplatform.orchestrator.job;

import java.util.Locale;

/**
 * Stateless utility that normalizes job listing fields for consistent deduplication,
 * filtering, and display.
 *
 * <p>Operations are purely textual — no external calls or LLM dependencies.</p>
 */
public final class JobNormalizer {

    private JobNormalizer() {}

    /**
     * Normalizes a job title for deduplication and comparison.
     *
     * <p>Lowercases, strips all characters except letters, digits, {@code +}, and {@code #}
     * (preserving C++ and C#), replaces them with a single space, then collapses and trims.</p>
     *
     * @param title raw title from a job listing; may be {@code null}
     * @return normalized title; empty string for null/blank input
     */
    public static String normalizeTitle(String title) {
        if (title == null) return "";
        return title.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Normalizes a company name for deduplication.
     *
     * <p>Lowercases, collapses whitespace, and trims. Does NOT strip punctuation
     * (e.g. "Dell Technologies, Inc." keeps its comma and period for display).</p>
     *
     * @param company raw company name; may be {@code null}
     * @return normalized company name; empty string for null/blank input
     */
    public static String normalizeCompany(String company) {
        if (company == null) return "";
        return company.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Normalizes a location string for deduplication.
     *
     * <p>Lowercases, collapses whitespace, and trims.</p>
     *
     * @param location raw location; may be {@code null}
     * @return normalized location; empty string for null/blank input
     */
    public static String normalizeLocation(String location) {
        if (location == null) return "";
        return location.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Strips HTML tags and collapses whitespace in a job description.
     *
     * <p>Input is truncated to 2000 characters before processing to guard against
     * extremely large payloads from external job sources.</p>
     *
     * @param description raw description; may be {@code null}
     * @return clean plaintext description; empty string for null/blank input
     */
    public static String normalizeDescription(String description) {
        if (description == null) return "";
        String truncated = description.length() > 2000
                ? description.substring(0, 2000)
                : description;
        return truncated
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Builds a composite deduplication key from a job's normalized title, company, and location.
     *
     * <p>Two jobs with the same key are considered duplicates.</p>
     *
     * @param job the job to build a key for; may be {@code null}
     * @return composite key in the form {@code "title|company|location"}; empty string for null job
     */
    public static String buildDeduplicationKey(Job job) {
        if (job == null) return "";
        return buildDeduplicationKey(job.title(), job.company(), job.location());
    }

    /**
     * Builds a composite deduplication key from raw fields.
     *
     * @param title    raw job title; may be {@code null}
     * @param company  raw company name; may be {@code null}
     * @param location raw location; may be {@code null}
     * @return composite key in the form {@code "title|company|location"}
     */
    public static String buildDeduplicationKey(String title, String company, String location) {
        String t = normalizeTitle(title);
        String c = normalizeCompany(company);
        String l = normalizeLocation(location);
        return t + "|" + c + "|" + l;
    }
}
