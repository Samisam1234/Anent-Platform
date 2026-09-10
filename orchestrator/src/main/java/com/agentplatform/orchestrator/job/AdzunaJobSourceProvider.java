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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link JobSourceProvider} over the <a href="https://developer.adzuna.com">Adzuna</a>
 * job search API.
 *
 * <p>Request and response shapes follow Adzuna's documented search endpoint:
 * {@code GET {baseUrl}/{country}/search/{page}?app_id=…&app_key=…&results_per_page=…&what=…&where=…},
 * returning {@code { count, results: [ … ] }} where each result carries
 * {@code id, title, description, redirect_url, created, contract_time, contract_type,
 * company.display_name, location.display_name, location.area, category.label}.</p>
 *
 * <h2>Credentials</h2>
 * <p>{@code app_id} and {@code app_key} are mandatory and come from configuration or the
 * environment. If either is missing the provider reports itself unavailable and is
 * skipped — it never calls the API with blank credentials and never substitutes invented
 * data for the results it cannot fetch.</p>
 *
 * <h2>URLs</h2>
 * <p>{@code redirect_url} is Adzuna's own forwarding link to the originating listing, so
 * it maps to {@code sourceUrl}. Adzuna does not expose the employer's application
 * destination as a distinct field, so {@code applicationUrl} stays {@code null}; the UI
 * then offers "View Job Listing" rather than claiming an employer application link.</p>
 *
 * <h2>Skills</h2>
 * <p>Adzuna returns no skill tags — only a coarse {@code category}. Rather than treating
 * the category as a skill, skills are extracted from the title and description with
 * {@link SkillTaxonomy#findMatches(String)}, which matches canonical taxonomy names at
 * word boundaries. Only skills the listing genuinely mentions are ever produced.</p>
 */
@Component
public class AdzunaJobSourceProvider implements JobSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(AdzunaJobSourceProvider.class);

    public static final String SOURCE_NAME = "ADZUNA";
    public static final String SOURCE_TYPE = "PUBLIC_API";

    private static final String DEFAULT_BASE_URL = "https://api.adzuna.com/v1/api/jobs";
    /** Adzuna caps results_per_page at 50. */
    private static final int MAX_RESULTS_PER_PAGE = 50;

    private final AdzunaJobProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public AdzunaJobSourceProvider(AdzunaJobProperties properties, RestTemplate restTemplate) {
        this(properties, restTemplate, new ObjectMapper());
    }

    public AdzunaJobSourceProvider(AdzunaJobProperties properties, RestTemplate restTemplate,
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
        return properties != null && properties.isEnabled() && properties.hasCredentials();
    }

    @Override
    public List<Job> fetchJobs(JobSearchRequest request) {
        if (properties == null || !properties.isEnabled()) {
            log.debug("{} disabled — returning no listings (no network call)", SOURCE_NAME);
            return List.of();
        }
        if (!properties.hasCredentials()) {
            // Expected configuration state, not an error: say exactly what is missing so
            // an operator can fix it, without ever printing a credential value.
            log.info("Job source '{}' is enabled but ADZUNA_APP_ID / ADZUNA_APP_KEY are not set — "
                    + "skipping this provider. Register at developer.adzuna.com for a free key pair.",
                    SOURCE_NAME);
            return List.of();
        }

        int limit = request != null && request.limit() != null ? request.limit() : 20;
        int maxPages = Math.max(1, properties.getMaxPages());
        List<Job> mapped = new ArrayList<>();

        for (int page = 1; page <= maxPages && mapped.size() < limit; page++) {
            List<AdzunaJobProperties.AdzunaJob> rawJobs = fetchPage(request, page);
            if (rawJobs == null || rawJobs.isEmpty()) {
                break;
            }
            for (AdzunaJobProperties.AdzunaJob raw : rawJobs) {
                Job job = map(raw);
                if (job != null) {
                    mapped.add(job);
                }
            }
        }

        log.debug("Job source '{}' mapped {} job listings", SOURCE_NAME, mapped.size());
        return mapped;
    }

    private List<AdzunaJobProperties.AdzunaJob> fetchPage(JobSearchRequest request, int page) {
        String url = buildUrl(request, page);
        String body;
        try {
            body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class)
                    .getBody();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                log.warn("Job source '{}' rejected the configured credentials (HTTP {}) — "
                        + "check ADZUNA_APP_ID / ADZUNA_APP_KEY", SOURCE_NAME, status);
            } else if (status == 429) {
                log.warn("Job source '{}' rate limit reached (HTTP 429) — stopping pagination", SOURCE_NAME);
            } else {
                log.warn("Job source '{}' returned HTTP {} — returning no listings", SOURCE_NAME, status);
            }
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
            log.warn("Job source '{}' returned an empty body — returning no listings", SOURCE_NAME);
            return List.of();
        }

        try {
            AdzunaJobProperties.AdzunaResponse response =
                    objectMapper.readValue(body, AdzunaJobProperties.AdzunaResponse.class);
            return response.results();
        } catch (JsonProcessingException e) {
            log.warn("Job source '{}' returned a malformed payload — returning no listings", SOURCE_NAME);
            return List.of();
        }
    }

    private String buildUrl(JobSearchRequest request, int page) {
        String base = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? DEFAULT_BASE_URL
                : properties.getBaseUrl();
        String country = properties.getCountry() == null || properties.getCountry().isBlank()
                ? "gb"
                : properties.getCountry().trim().toLowerCase();
        int perPage = Math.min(Math.max(1, properties.getResultsPerPage()), MAX_RESULTS_PER_PAGE);

        StringBuilder sb = new StringBuilder(base)
                .append('/').append(encode(country))
                .append("/search/").append(page)
                .append("?app_id=").append(encode(properties.getAppId()))
                .append("&app_key=").append(encode(properties.getAppKey()))
                .append("&results_per_page=").append(perPage)
                .append("&content-type=application/json");

        if (request != null && request.keywords() != null && !request.keywords().isEmpty()) {
            String what = request.keywords().stream()
                    .filter(k -> k != null && !k.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(" "));
            if (!what.isEmpty()) {
                sb.append("&what=").append(encode(what));
            }
        }
        if (request != null && isMeaningful(request.location())) {
            sb.append("&where=").append(encode(request.location().trim()));
        }
        return sb.toString();
    }

    private static boolean isMeaningful(String location) {
        if (location == null || location.isBlank()) {
            return false;
        }
        String v = location.trim();
        return !"all".equalsIgnoreCase(v) && !"any".equalsIgnoreCase(v);
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is always available on a compliant JVM; fall back to the raw value.
            return value;
        }
    }

    Job map(AdzunaJobProperties.AdzunaJob raw) {
        if (raw == null || raw.id() == null || raw.id().isBlank()) {
            return null;
        }
        String description = JobNormalizer.normalizeDescription(raw.description());
        String safeUrl = raw.redirect_url() != null && JobUrlValidator.isValidExternalUrl(raw.redirect_url())
                ? raw.redirect_url()
                : null;
        return new Job(
                "adzuna-" + raw.id(),
                raw.title(),
                raw.company() != null ? raw.company().display_name() : null,
                resolveLocation(raw),
                description,
                deriveSkills(raw, description),
                List.of(),
                null,
                employmentType(raw),
                postingDate(raw.created()),
                SOURCE_NAME,
                safeUrl,
                SOURCE_TYPE,
                Instant.now(),
                // Adzuna exposes only its own redirect link (mapped to sourceUrl). It does
                // not publish the employer's application destination as a separate field,
                // so nothing is invented here.
                null);
    }

    /**
     * Extracts the skills a listing actually mentions. Adzuna provides no skill tags, and
     * {@code category.label} is an industry label rather than a skill, so the taxonomy is
     * applied to the title and description instead.
     */
    private List<String> deriveSkills(AdzunaJobProperties.AdzunaJob raw, String description) {
        String haystack = ((raw.title() != null ? raw.title() : "") + " "
                + (description != null ? description : "")).trim();
        return SkillTaxonomy.findMatches(haystack);
    }

    private String resolveLocation(AdzunaJobProperties.AdzunaJob raw) {
        if (raw.location() == null) {
            return null;
        }
        if (raw.location().display_name() != null && !raw.location().display_name().isBlank()) {
            return raw.location().display_name();
        }
        List<String> area = raw.location().area();
        if (area == null || area.isEmpty()) {
            return null;
        }
        List<String> clean = area.stream()
                .filter(a -> a != null && !a.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());
        return clean.isEmpty() ? null : String.join(", ", clean);
    }

    private String employmentType(AdzunaJobProperties.AdzunaJob raw) {
        List<String> parts = new ArrayList<>();
        if (raw.contract_time() != null && !raw.contract_time().isBlank()) {
            parts.add(raw.contract_time().trim().replace('_', ' '));
        }
        if (raw.contract_type() != null && !raw.contract_type().isBlank()) {
            parts.add(raw.contract_type().trim());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** Adzuna timestamps are ISO-8601 (e.g. {@code 2013-11-08T18:07:39Z}). */
    private String postingDate(String created) {
        if (created == null || created.isBlank()) {
            return null;
        }
        return created.length() >= 10 ? created.substring(0, 10) : created;
    }
}
