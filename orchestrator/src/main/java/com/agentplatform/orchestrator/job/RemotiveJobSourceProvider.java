package com.agentplatform.orchestrator.job;

import com.agentplatform.orchestrator.resume.SkillTaxonomy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link JobSourceProvider} over the public <a href="https://remotive.com">Remotive</a>
 * remote-jobs API.
 *
 * <p>This is the original {@code PublicApiJobSource} implementation, moved behind the
 * provider SPI so it sits alongside the other upstreams instead of being the only one.
 * The request shape, field mapping and provenance are unchanged.</p>
 *
 * <p>Disabled by default (see {@link PublicApiJobProperties}): until enabled it never
 * makes a network call and {@link #isAvailable()} returns {@code false}.</p>
 *
 * <p>Design (clean adapter, no scraping):</p>
 * <ul>
 *   <li>Maps each raw item into {@link Job}, filling provenance
 *       ({@code source}={@link #SOURCE_NAME}, {@code sourceType}, {@code sourceUrl},
 *       {@code discoveredAt}).</li>
 *   <li>Reuses {@link JobNormalizer} for HTML stripping so no second normalization
 *       implementation exists.</li>
 *   <li>Passes every external URL through {@link JobUrlValidator}; unsafe or missing URLs
 *       are rejected — fake URLs are never created.</li>
 *   <li>Deduplication stays centralized in {@link JobAggregatorService} /
 *       {@link JobDeduplicationService} across all providers.</li>
 * </ul>
 *
 * <p>Failure handling is provider-local: any timeout, connection failure, HTTP 4xx/5xx
 * or malformed payload yields an empty result plus a log warning, never an exception.</p>
 */
@Component
public class RemotiveJobSourceProvider implements JobSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(RemotiveJobSourceProvider.class);

    public static final String SOURCE_NAME = "REMOTIVE";
    public static final String SOURCE_TYPE = "PUBLIC_API";

    private static final String DEFAULT_BASE_URL = "https://remotive.com/api/remote-jobs";

    private final PublicApiJobProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Primary constructor used by Spring. {@code @Autowired} is explicit because the class
     * also declares a package-private 3-arg constructor used by tests.
     */
    @Autowired
    public RemotiveJobSourceProvider(PublicApiJobProperties properties, RestTemplate restTemplate) {
        this(properties, restTemplate, new ObjectMapper());
    }

    public RemotiveJobSourceProvider(PublicApiJobProperties properties, RestTemplate restTemplate,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public boolean isAvailable() {
        return properties != null && properties.isEnabled();
    }

    @Override
    public List<Job> fetchJobs(JobSearchRequest request) {
        if (!isAvailable()) {
            log.debug("{} disabled — returning no listings (no network call)", SOURCE_NAME);
            return List.of();
        }

        String url = buildUrl(request);
        String body;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
            }
            body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        } catch (HttpStatusCodeException e) {
            log.warn("Job source '{}' returned HTTP {} — returning empty listings",
                    SOURCE_NAME, e.getStatusCode().value());
            return List.of();
        } catch (ResourceAccessException e) {
            log.warn("Job source '{}' unreachable (timeout/connection): {} — returning empty listings",
                    SOURCE_NAME, e.getMessage());
            return List.of();
        } catch (RestClientException e) {
            log.warn("Job source '{}' call failed: {} — returning empty listings", SOURCE_NAME, e.getMessage());
            return List.of();
        }

        if (body == null || body.isBlank()) {
            log.warn("Job source '{}' returned an empty body — returning empty listings", SOURCE_NAME);
            return List.of();
        }

        PublicApiJobProperties.RemotiveResponse response;
        try {
            response = objectMapper.readValue(body, PublicApiJobProperties.RemotiveResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Job source '{}' returned a malformed payload — returning empty listings", SOURCE_NAME);
            return List.of();
        }

        List<PublicApiJobProperties.RemotiveJob> rawJobs = response.jobs();
        if (rawJobs == null || rawJobs.isEmpty()) {
            log.debug("Job source '{}' returned no jobs", SOURCE_NAME);
            return List.of();
        }

        List<Job> mapped = new ArrayList<>();
        for (PublicApiJobProperties.RemotiveJob raw : rawJobs) {
            Job job = map(raw);
            if (job != null) {
                mapped.add(job);
            }
        }
        log.debug("Job source '{}' mapped {} job listings", SOURCE_NAME, mapped.size());
        return mapped;
    }

    private String buildUrl(JobSearchRequest request) {
        String base = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? DEFAULT_BASE_URL
                : properties.getBaseUrl();
        StringBuilder sb = new StringBuilder(base);
        int limit = request != null && request.limit() != null ? request.limit() : 20;

        List<String> params = new ArrayList<>();
        if (request != null && request.keywords() != null && !request.keywords().isEmpty()) {
            String search = request.keywords().stream()
                    .filter(k -> k != null && !k.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining("+"));
            if (!search.isEmpty()) {
                params.add("search=" + search);
            }
        }
        params.add("limit=" + limit);

        sb.append('?').append(String.join("&", params));
        return sb.toString();
    }

    Job map(PublicApiJobProperties.RemotiveJob raw) {
        if (raw == null) {
            return null;
        }
        // External jobs without a verified, safe http(s) URL are preserved but never
        // rendered as navigable links: sourceUrl is nulled so the frontend shows an
        // internal "Job Details" action instead.
        String safeUrl = raw.url() != null && JobUrlValidator.isValidExternalUrl(raw.url())
                ? raw.url()
                : null;
        return new Job(
                idOf(raw),
                raw.title(),
                raw.company_name(),
                raw.candidate_required_location(),
                JobNormalizer.normalizeDescription(raw.description()),
                // Skill tags arrive raw from the feed ("react", "redis", and occasionally
                // malformed English tokens). Normalizing them here — at the source — means
                // every downstream consumer sees the same clean names.
                SkillTaxonomy.displayNames(raw.tags()),
                List.of(),
                null,
                raw.job_type(),
                publishingDate(raw.publication_date()),
                SOURCE_NAME,
                safeUrl,
                SOURCE_TYPE,
                Instant.now(),
                // Employer application destination. Remotive's declared listing schema
                // (see PublicApiJobProperties.RemotiveJob) exposes only `url` — the
                // aggregator's own listing page — and no application/company URL, so none
                // is mapped. `safeUrl` must NOT be reused here: presenting the aggregator
                // page as "the application" would be a fabricated destination.
                null);
    }

    private String idOf(PublicApiJobProperties.RemotiveJob raw) {
        return "remotive-" + raw.id();
    }

    private String publishingDate(String publicationDate) {
        if (publicationDate == null || publicationDate.isBlank()) {
            return null;
        }
        // Remotive uses ISO-8601 like "2026-08-27T14:36:09"; the app filters on "yyyy-MM-dd".
        return publicationDate.length() >= 10 ? publicationDate.substring(0, 10) : publicationDate;
    }
}
