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
     * @param jobs raw job listings from one or more sources
     * @return deduplicated list preserving first-occurrence order
     */
    public List<Job> deduplicate(List<Job> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }

        Set<String> compositeKeys = new HashSet<>();
        Set<String> seenUrls = new HashSet<>();
        List<Job> unique = new ArrayList<>();

        for (Job job : jobs) {
            if (job == null) continue;

            String compositeKey = JobNormalizer.buildDeduplicationKey(job);
            String urlKey = job.sourceUrl() != null ? job.sourceUrl().trim().toLowerCase() : "";

            boolean dupComposite = !compositeKeys.add(compositeKey);
            boolean dupUrl = !urlKey.isEmpty() && !seenUrls.add(urlKey);

            if (!dupComposite && !dupUrl) {
                unique.add(job);
            } else {
                log.debug("Dedup removed job: id={}, title='{}', company='{}', reason={}",
                        job.id(), job.title(), job.company(),
                        dupComposite ? "composite-key" : "url-duplicate");
            }
        }

        if (log.isInfoEnabled() && jobs.size() != unique.size()) {
            log.info("Deduplication: {} raw → {} unique ({} removed)",
                    jobs.size(), unique.size(), jobs.size() - unique.size());
        }

        return unique;
    }
}
