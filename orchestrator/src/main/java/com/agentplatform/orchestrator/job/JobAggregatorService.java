package com.agentplatform.orchestrator.job;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Fans a job search out to every configured {@link JobSourceProvider} in parallel and
 * merges the results into one listing set for the matching pipeline.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><strong>Parallel dispatch</strong> — each available provider is queried on the
 *       shared pool via {@link CompletableFuture}, so total latency is the slowest
 *       provider rather than the sum of all of them.</li>
 *   <li><strong>Failure isolation</strong> — a provider that throws, hangs or is
 *       rate-limited is recorded and skipped. It can never abort the search or discard
 *       another provider's results. Only when <em>every</em> provider fails does the
 *       aggregation come back empty.</li>
 *   <li><strong>Normalization</strong> — listings missing an id or a title are dropped so
 *       malformed upstream data cannot reach the frontend or the matcher.</li>
 *   <li><strong>Deduplication</strong> — delegated to {@link JobDeduplicationService},
 *       which keys on normalized title + company + location (with URL as a secondary
 *       guard). The same listing syndicated to two boards is kept once.</li>
 *   <li><strong>Provenance</strong> — every {@link Job} keeps its own {@code source},
 *       {@code sourceUrl} and {@code applicationUrl}. Merging never rewrites them, so
 *       "View Source Listing" always opens the originating board's page for that specific
 *       job and nothing is ever attributed to the wrong provider.</li>
 * </ul>
 *
 * <p>The class implements {@link JobSource} so it plugs into the existing
 * {@link JobSearchService} pipeline unchanged: the service injects a
 * {@code List<JobSource>} and now receives this aggregator (plus the optional development
 * catalog) instead of a single hard-wired upstream.</p>
 */
@Service
public class JobAggregatorService implements JobSource {

    private static final Logger log = LoggerFactory.getLogger(JobAggregatorService.class);

    /** Returned when no provider is configured, so callers can tell "none" from a name. */
    public static final String NO_PROVIDERS = "NONE";

    /**
     * Upper bound on a single provider call. Providers already carry HTTP timeouts; this
     * is the outer safety net so a provider whose client ignores them cannot stall the
     * whole search.
     */
    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(20);

    private static final int POOL_SIZE = 4;

    private final List<JobSourceProvider> providers;
    private final JobDeduplicationService deduplicationService;
    private final ExecutorService executor;

    @Autowired
    public JobAggregatorService(List<JobSourceProvider> providers,
                                JobDeduplicationService deduplicationService) {
        this.providers = providers != null ? List.copyOf(providers) : List.of();
        this.deduplicationService = deduplicationService != null
                ? deduplicationService
                : new JobDeduplicationService();
        this.executor = Executors.newFixedThreadPool(POOL_SIZE, runnable -> {
            Thread thread = new Thread(runnable, "job-provider");
            thread.setDaemon(true);
            return thread;
        });
        if (this.providers.isEmpty()) {
            log.warn("No JobSourceProvider beans are configured — job search will return no listings");
        } else {
            log.info("Job aggregation configured with {} provider(s): {}",
                    this.providers.size(),
                    this.providers.stream().map(JobSourceProvider::getSourceName)
                            .collect(Collectors.joining(", ")));
        }
    }

    /** Constructor for tests and standalone use: no provider list is supplied. */
    public JobAggregatorService(JobDeduplicationService deduplicationService) {
        this(List.of(), deduplicationService);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Reports the providers that actually participated, not a fixed label — the UI shows
     * this string, and it must name the real upstreams (for example
     * {@code "REMOTIVE, ADZUNA, ARBEITNOW"}) rather than hiding them behind "AGGREGATED".
     */
    @Override
    public String getSourceName() {
        List<String> available = providers.stream()
                .filter(this::isProviderAvailable)
                .map(JobSourceProvider::getSourceName)
                .toList();
        return available.isEmpty() ? NO_PROVIDERS : String.join(", ", available);
    }

    @Override
    public boolean isLive() {
        return providers.stream().anyMatch(this::isProviderAvailable);
    }

    @Override
    public boolean isAvailable() {
        return providers.stream().anyMatch(this::isProviderAvailable);
    }

    @Override
    public List<Job> search(JobSearchRequest request) {
        List<JobSourceProvider> active = providers.stream()
                .filter(this::isProviderAvailable)
                .toList();
        if (active.isEmpty()) {
            log.info("No job source provider is available — returning no listings");
            return List.of();
        }

        long started = System.currentTimeMillis();
        List<CompletableFuture<ProviderResult>> futures = active.stream()
                .map(provider -> CompletableFuture
                        .supplyAsync(() -> fetchFrom(provider, request), executor)
                        .orTimeout(PROVIDER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                        .handle((result, error) -> recover(provider, result, error)))
                .toList();

        List<Job> merged = new ArrayList<>();
        List<String> succeeded = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (CompletableFuture<ProviderResult> future : futures) {
            ProviderResult result = future.join();
            merged.addAll(result.jobs());
            if (result.failed()) {
                failed.add(result.providerName());
            } else {
                succeeded.add(result.providerName() + "(" + result.jobs().size() + ")");
            }
        }

        log.info("Job aggregation finished in {} ms — succeeded=[{}], failed=[{}], raw={}",
                System.currentTimeMillis() - started,
                String.join(", ", succeeded),
                String.join(", ", failed),
                merged.size());

        List<Job> normalized = merged.stream().filter(JobAggregatorService::isUsable).toList();
        // Provenance is preserved through deduplication: the surviving Job keeps its own
        // source, sourceUrl and applicationUrl untouched.
        return deduplicationService.deduplicate(normalized);
    }

    private ProviderResult fetchFrom(JobSourceProvider provider, JobSearchRequest request) {
        List<Job> jobs = provider.fetchJobs(request);
        return new ProviderResult(provider.getSourceName(), jobs != null ? jobs : List.of(), false);
    }

    /**
     * Converts any provider failure into an empty, non-fatal result. The exception is
     * logged here — with its stack trace — so the technical cause is never lost, while the
     * search itself continues with whatever the other providers returned.
     */
    private ProviderResult recover(JobSourceProvider provider, ProviderResult result, Throwable error) {
        if (error == null) {
            return result;
        }
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        String name = provider != null ? provider.getSourceName() : "UNKNOWN";
        if (cause instanceof TimeoutException) {
            log.warn("Job source '{}' did not respond within {}s — continuing without it",
                    name, PROVIDER_TIMEOUT.toSeconds());
        } else {
            log.error("Job source '{}' failed during aggregation — continuing without it", name, cause);
        }
        return new ProviderResult(name, List.of(), true);
    }

    private boolean isProviderAvailable(JobSourceProvider provider) {
        if (provider == null) {
            return false;
        }
        try {
            return provider.isAvailable();
        } catch (RuntimeException e) {
            log.warn("Job source '{}' threw while reporting availability — skipping it: {}",
                    provider.getSourceName(), e.getMessage());
            return false;
        }
    }

    /**
     * Rejects listings that cannot be usefully surfaced: a null/blank id or title. Malformed
     * external data is dropped here so it never reaches the matcher or the frontend.
     */
    private static boolean isUsable(Job job) {
        if (job == null) {
            return false;
        }
        if (job.id() == null || job.id().isBlank()) {
            return false;
        }
        return job.title() != null && !job.title().isBlank();
    }

    /** Outcome of one provider call: its listings, or an empty set plus a failure flag. */
    private record ProviderResult(String providerName, List<Job> jobs, boolean failed) {
    }
}
