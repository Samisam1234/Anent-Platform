package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Backwards-compatible {@link JobSource} view of the Remotive provider.
 *
 * <p>The Remotive implementation now lives in {@link RemotiveJobSourceProvider}, behind
 * the {@link JobSourceProvider} SPI, and is aggregated by {@link JobAggregatorService}.
 * This class exists only so pre-existing callers and tests that were written against the
 * {@code JobSource} contract keep working: it delegates every call straight through and
 * adds no behaviour of its own.</p>
 *
 * <p><strong>It is deliberately not a Spring component.</strong> Registering it would make
 * Remotive a second, independent {@link JobSource} alongside the aggregator, so the same
 * board would be fetched twice per search. Production wiring goes
 * {@code RemotiveJobSourceProvider -> JobAggregatorService -> JobSearchService}.</p>
 *
 * @deprecated use {@link RemotiveJobSourceProvider} (or inject
 *             {@link JobAggregatorService}) instead.
 */
@Deprecated
public class PublicApiJobSource extends RemotiveJobSourceProvider implements JobSource {

    public PublicApiJobSource(PublicApiJobProperties properties, RestTemplate restTemplate) {
        super(properties, restTemplate);
    }

    public PublicApiJobSource(PublicApiJobProperties properties, RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        super(properties, restTemplate, objectMapper);
    }

    /**
     * {@link JobSource#isLive()} is the pipeline's "this source contributes real external
     * data" signal. For a single upstream it is the same question as availability.
     */
    @Override
    public boolean isLive() {
        return isAvailable();
    }

    @Override
    public List<Job> search(JobSearchRequest request) {
        return fetchJobs(request);
    }
}
