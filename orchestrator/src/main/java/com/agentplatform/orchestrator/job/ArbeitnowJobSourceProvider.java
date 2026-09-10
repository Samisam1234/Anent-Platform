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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link JobSourceProvider} over the free public
 * <a href="https://www.arbeitnow.com">Arbeitnow</a> job-board API.
 *
 * <p>Endpoint and payload are taken from Arbeitnow's published API documentation:
 * {@code GET https://www.arbeitnow.com/api/job-board-api?page=N} returning
 * {@code { data: [...], links: {...}, meta: {...} }}. No authentication is required.
 * Field mapping is limited to keys the documented response actually contains — nothing is
 * invented, and any unknown key is ignored by Jackson.</p>
 *
 * <h2>Rate limiting</h2>
 * <p>Arbeitnow responds with {@code x-ratelimit-limit: 3} and its {@code meta.terms} asks
 * callers not to abuse the free feed. Pages are therefore fetched <em>sequentially</em>
 * and capped by {@link ArbeitnowJobProperties#getMaxPages()} (default 1 page = 100
 * listings), so one search costs one request. A {@code 429} is treated like any other
 * upstream failure: empty result, warning logged, search continues.</p>
 *
 * <h2>URLs</h2>
 * <p>Arbeitnow supplies only {@code url} — its own listing page. That becomes
 * {@code sourceUrl}. The feed does not expose the employer's application destination, so
 * {@code applicationUrl} stays {@code null} rather than being back-filled; the UI then
 * offers "View Job Listing" and states that no employer application link is available.</p>
 */
@Component
public class ArbeitnowJobSourceProvider implements JobSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowJobSourceProvider.class);

    public static final String SOURCE_NAME = "ARBEITNOW";
    public static final String SOURCE_TYPE = "PUBLIC_API";

    private static final String DEFAULT_BASE_URL = "https://www.arbeitnow.com/api/job-board-api";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ArbeitnowJobProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ArbeitnowJobSourceProvider(ArbeitnowJobProperties properties, RestTemplate restTemplate) {
        this(properties, restTemplate, new ObjectMapper());
    }

    public ArbeitnowJobSourceProvider(ArbeitnowJobProperties properties, RestTemplate restTemplate,
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

        int maxPages = Math.max(1, properties.getMaxPages());
        int limit = request != null && request.limit() != null ? request.limit() : 20;
        List<Job> mapped = new ArrayList<>();

        // Sequential and page-capped on purpose: the feed is free and rate-limited to
        // 3 requests. Stopping as soon as the caller's limit is satisfied keeps the
        // upstream cost at one request in the normal case.
        for (int page = 1; page <= maxPages && mapped.size() < limit; page++) {
            List<ArbeitnowJobProperties.ArbeitnowJob> rawJobs = fetchPage(page);
            if (rawJobs == null || rawJobs.isEmpty()) {
                break;
            }
            for (ArbeitnowJobProperties.ArbeitnowJob raw : rawJobs) {
                Job job = map(raw);
                if (job != null) {
                    mapped.add(job);
                }
            }
        }

        log.debug("Job source '{}' mapped {} job listings", SOURCE_NAME, mapped.size());
        return mapped;
    }

    private List<ArbeitnowJobProperties.ArbeitnowJob> fetchPage(int page) {
        String url = buildUrl(page);
        String body;
        try {
            body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class)
                    .getBody();
        } catch (HttpStatusCodeException e) {
            // 429 is the documented rate-limit response; it is expected, not exceptional.
            log.warn("Job source '{}' returned HTTP {} for page {} — stopping pagination",
                    SOURCE_NAME, e.getStatusCode().value(), page);
            return List.of();
        } catch (ResourceAccessException e) {
            log.warn("Job source '{}' unreachable (timeout/connection): {} — returning no listings",
                    SOURCE_NAME, e.getMessage());
            return List.of();
        } catch (RestClientException e) {
            log.warn("Job source '{}' call failed: {} — returning no listings", SOURCE_NAME, e.getMessage());
            return List.of();
        }

        if (body == null || body.isBlank()) {
            log.warn("Job source '{}' returned an empty body for page {}", SOURCE_NAME, page);
            return List.of();
        }

        try {
            ArbeitnowJobProperties.ArbeitnowResponse response =
                    objectMapper.readValue(body, ArbeitnowJobProperties.ArbeitnowResponse.class);
            return response.data();
        } catch (JsonProcessingException e) {
            log.warn("Job source '{}' returned a malformed payload for page {} — skipping", SOURCE_NAME, page);
            return List.of();
        }
    }

    private String buildUrl(int page) {
        String base = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? DEFAULT_BASE_URL
                : properties.getBaseUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "page=" + page;
    }

    Job map(ArbeitnowJobProperties.ArbeitnowJob raw) {
        if (raw == null || raw.slug() == null || raw.slug().isBlank()) {
            return null;
        }
        String safeUrl = raw.url() != null && JobUrlValidator.isValidExternalUrl(raw.url())
                ? raw.url()
                : null;
        return new Job(
                "arbeitnow-" + raw.slug(),
                raw.title(),
                raw.company_name(),
                resolveLocation(raw),
                JobNormalizer.normalizeDescription(raw.description()),
                // Arbeitnow's `tags` are coarse industry/category labels rather than a
                // curated skill list, but they are the only skill-ish signal the feed
                // gives. They go through the same taxonomy as every other source so
                // implausible tokens are dropped instead of polluting matching.
                SkillTaxonomy.displayNames(raw.tags()),
                List.of(),
                null,
                employmentType(raw),
                postingDate(raw.created_at()),
                SOURCE_NAME,
                safeUrl,
                SOURCE_TYPE,
                Instant.now(),
                // Arbeitnow exposes no employer application destination — only its own
                // listing page (mapped to sourceUrl above). Left null rather than
                // fabricated.
                null);
    }

    private String resolveLocation(ArbeitnowJobProperties.ArbeitnowJob raw) {
        boolean remote = Boolean.TRUE.equals(raw.remote());
        String location = raw.location();
        if (location == null || location.isBlank()) {
            return remote ? "Remote" : null;
        }
        return remote ? location + " (Remote)" : location;
    }

    private String employmentType(ArbeitnowJobProperties.ArbeitnowJob raw) {
        List<String> types = raw.job_types();
        if (types == null || types.isEmpty()) {
            return null;
        }
        List<String> clean = new ArrayList<>();
        for (String type : types) {
            if (type != null && !type.isBlank()) {
                clean.add(type.trim());
            }
        }
        return clean.isEmpty() ? null : String.join(", ", clean);
    }

    /** Arbeitnow timestamps are Unix epoch seconds; the app filters on {@code yyyy-MM-dd}. */
    private String postingDate(Long createdAt) {
        if (createdAt == null || createdAt <= 0) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(createdAt).atZone(ZoneOffset.UTC).toLocalDate().format(DATE);
        } catch (RuntimeException e) {
            log.debug("Job source '{}' had an unparseable created_at: {}", SOURCE_NAME, createdAt);
            return null;
        }
    }
}
