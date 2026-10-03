package com.agentplatform.orchestrator.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Deterministic deduplication of job listings across multiple sources.
 *
 * <p>Uses a composite key (normalized title + company + location) as the primary dedup
 * strategy, with URL-based deduplication as a secondary guard. No LLM calls.</p>
 */
@Service
public class JobDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(JobDeduplicationService.class);

    /**
     * Deduplicates a list of jobs using composite key and URL-based strategies.
     *
     * <p>Two listings that describe the same job are <em>merged</em> rather than dropped, so
     * information only one source published is not lost. The first occurrence keeps its
     * position, its id and its source attribution; the later record only fills in fields the
     * first one left empty.</p>
     *
     * @param jobs raw job listings from one or more sources
     * @return deduplicated list preserving first-occurrence order
     */
    public List<Job> deduplicate(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> byCompositeKey = new HashMap<>();
        Map<String, Integer> byUrlKey = new HashMap<>();
        List<Job> unique = new ArrayList<>();

        for (Job job : jobs) {
            if (job == null) continue;

            String compositeKey = JobNormalizer.buildDeduplicationKey(job);
            String urlKey = urlKey(job);

            Integer existing = byCompositeKey.get(compositeKey);
            String reason = "composite-key";
            if (existing == null && !urlKey.isEmpty()) {
                existing = byUrlKey.get(urlKey);
                reason = "url-duplicate";
            }

            if (existing == null) {
                int position = unique.size();
                unique.add(job);
                index(position, job, byCompositeKey, byUrlKey);
            } else {
                Job merged = merge(unique.get(existing), job);
                unique.set(existing, merged);
                // The merge can fill in a company or location that the dedup key is built
                // from, so the merged record is indexed under its own key too. putIfAbsent
                // keeps the first record as the owner of any key already claimed.
                index(existing, merged, byCompositeKey, byUrlKey);
                // The absorbed listing's own URL is still a URL we have now seen, so a later
                // record carrying it must keep resolving to this same listing.
                if (!urlKey.isEmpty()) {
                    byUrlKey.putIfAbsent(urlKey, existing);
                }
                log.debug("Dedup merged job: id={}, title='{}', company='{}', reason={}",
                        job.id(), job.title(), job.company(), reason);
            }
        }

        if (log.isInfoEnabled() && jobs.size() != unique.size()) {
            log.info("Deduplication: {} raw → {} unique ({} merged away)",
                    jobs.size(), unique.size(), jobs.size() - unique.size());
        }

        return unique;
    }

    private static void index(int position, Job job,
                              Map<String, Integer> byCompositeKey,
                              Map<String, Integer> byUrlKey) {
        byCompositeKey.putIfAbsent(JobNormalizer.buildDeduplicationKey(job), position);
        String urlKey = urlKey(job);
        if (!urlKey.isEmpty()) {
            byUrlKey.putIfAbsent(urlKey, position);
        }
    }

    private static String urlKey(Job job) {
        return job.sourceUrl() == null ? "" : job.sourceUrl().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Combines two listings that describe the same job.
     *
     * <p>The first record wins every field it has, and the later record only supplies what
     * the first left null or blank. So when the first source published no
     * {@code applicationUrl} and the second one did, the merged listing carries it — and
     * when the first did publish one, a later record with none cannot erase it. Nothing is
     * ever invented: no URL is derived from any other field, and the id, {@code source} and
     * {@code sourceType} always come from the first record so attribution stays consistent
     * with the existing model.</p>
     *
     * <p>Skill lists are deliberately not unioned. Providers tag skills very differently —
     * one publishes a curated list, another only coarse category labels — so merging them
     * would let one source's vocabulary start scoring another source's listing and silently
     * change matching results.</p>
     */
    static Job merge(Job first, Job later) {
        if (first == null) {
            return later;
        }
        if (later == null) {
            return first;
        }
        return new Job(
                first.id(),
                firstNonBlank(first.title(), later.title()),
                firstNonBlank(first.company(), later.company()),
                firstNonBlank(first.location(), later.location()),
                firstNonBlank(first.description(), later.description()),
                firstNonEmpty(first.requiredSkills(), later.requiredSkills()),
                firstNonEmpty(first.preferredSkills(), later.preferredSkills()),
                firstNonBlank(first.experienceRequirement(), later.experienceRequirement()),
                firstNonBlank(first.employmentType(), later.employmentType()),
                firstNonBlank(first.postingDate(), later.postingDate()),
                first.source(),
                firstNonBlank(first.sourceUrl(), later.sourceUrl()),
                first.sourceType(),
                first.discoveredAt() != null ? first.discoveredAt() : later.discoveredAt(),
                firstNonBlank(first.applicationUrl(), later.applicationUrl()));
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static List<String> firstNonEmpty(List<String> preferred, List<String> fallback) {
        return preferred != null && !preferred.isEmpty() ? preferred : fallback;
    }
}
