package com.agentplatform.orchestrator.job;

import java.util.List;

/**
 * Service-provider interface for a single external job board.
 *
 * <p>This is the extension point for job discovery. Each concrete provider knows exactly
 * one upstream API (Remotive, Adzuna, Arbeitnow, …) and is responsible for translating
 * that API's raw payload into the platform's {@link Job} model, including provenance:
 * {@code source}, {@code sourceUrl} and, where the upstream genuinely supplies one,
 * {@code applicationUrl}.</p>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #fetchJobs(JobSearchRequest)} must <strong>never throw</strong>. A
 *       provider that is unreachable, rate-limited, mis-configured or returning a
 *       malformed payload returns an empty list and logs a warning. This is what lets
 *       {@link JobAggregatorService} fan out to every provider in parallel without one
 *       failure taking down the whole search.</li>
 *   <li>{@link #isAvailable()} must be a cheap, side-effect-free check. It must not
 *       perform I/O — it reports configuration state (enabled, credentials present), so
 *       the aggregator can skip a provider before spending a network call on it.</li>
 *   <li>Providers must not fabricate data. If the upstream does not expose an employer
 *       application destination, {@code applicationUrl} stays {@code null}.</li>
 * </ul>
 *
 * <p>Relationship to {@link JobSource}: {@code JobSource} is the search-pipeline
 * abstraction consumed by {@link JobSearchService} (it adds {@code isLive()} and
 * {@code search()}). {@code JobSourceProvider} is the narrower per-upstream SPI.
 * {@link JobAggregatorService} implements {@code JobSource} and delegates to every
 * available {@code JobSourceProvider}, so the pipeline sees one aggregated source
 * regardless of how many upstreams are configured.</p>
 */
public interface JobSourceProvider {

    /**
     * Fetches listings from this provider's upstream API.
     *
     * @param criteria search criteria; never {@code null}. Providers should apply what
     *                 they can upstream (keywords, location, limit) and may rely on the
     *                 pipeline for the rest.
     * @return the mapped listings, or an empty list on any failure. Never {@code null}.
     */
    List<Job> fetchJobs(JobSearchRequest criteria);

    /**
     * Stable, uppercase machine name for this provider (e.g. {@code "REMOTIVE"}).
     * Stored on every {@link Job#source()} it produces and shown in the UI.
     */
    String getSourceName();

    /**
     * Whether this provider is configured and eligible to be called. Must not perform
     * I/O. A disabled provider, or one missing required credentials, returns
     * {@code false} and is skipped entirely.
     */
    boolean isAvailable();
}
