package com.agentplatform.orchestrator.job;

import java.util.List;

public record JobSearchRequest(
        List<String> keywords,
        String location,
        String experience,
        String employmentType,
        String datePosted,
        Integer limit,
        String source,
        /**
         * Optional stored candidate profile id. When present and no explicit
         * {@code keywords} were supplied, the search derives its relevance keywords from
         * that profile's parsed skills — so job discovery follows the resume instead of
         * requiring the user to type keywords.
         */
        Long candidateProfileId) {

    public JobSearchRequest {
        keywords = keywords != null ? List.copyOf(keywords) : List.of();
        if (limit != null && (limit < 1 || limit > 100)) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        limit = limit == null ? 20 : limit;
    }

    /**
     * Backwards-compatible constructor that leaves the {@code candidateProfileId} unset
     * (no profile-driven keyword derivation).
     */
    public JobSearchRequest(List<String> keywords, String location, String experience,
                            String employmentType, String datePosted, Integer limit, String source) {
        this(keywords, location, experience, employmentType, datePosted, limit, source, null);
    }

    /**
     * Backwards-compatible convenience constructor that leaves the {@code source}
     * filter unset (no source restriction on results).
     */
    public JobSearchRequest(List<String> keywords, String location, String experience,
                            String employmentType, String datePosted, Integer limit) {
        this(keywords, location, experience, employmentType, datePosted, limit, null, null);
    }

    public static JobSearchRequest of(List<String> keywords, String location, String experience,
                                      String employmentType, String datePosted, Integer limit) {
        return new JobSearchRequest(keywords, location, experience, employmentType, datePosted, limit);
    }

    public static JobSearchRequest of(List<String> keywords, String location, String experience,
                                      String employmentType, String datePosted, Integer limit, String source) {
        return new JobSearchRequest(keywords, location, experience, employmentType, datePosted, limit, source);
    }

    public static JobSearchRequest of(List<String> keywords, String location, String experience,
                                      String employmentType, String datePosted, Integer limit,
                                      String source, Long candidateProfileId) {
        return new JobSearchRequest(keywords, location, experience, employmentType, datePosted,
                limit, source, candidateProfileId);
    }
}
