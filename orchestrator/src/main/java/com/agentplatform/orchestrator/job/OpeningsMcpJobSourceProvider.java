package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OpeningsMcpJobSourceProvider implements JobSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(OpeningsMcpJobSourceProvider.class);

    public static final String SOURCE_NAME = "OPENINGS_MCP";
    public static final String SOURCE_TYPE = "MCP";

    /**
     * Verified employer-owned hosts per search tool, recorded from live responses of
     * openings-mcp 0.16.2. For these tools the server reads the employer's <em>own</em>
     * careers site, so the returned {@code url} is the employer's career posting page —
     * the page that carries the apply form — and may be published as the application
     * destination.
     *
     * <p>Matching is exact-host on purpose. A host that is not listed here is never
     * promoted to an employer destination, and no aggregator tool appears in this map at
     * all: for LinkedIn, Indeed, freehire or any keyword board the {@code url} is the
     * board's own page, so it stays {@code sourceUrl} and {@code applicationUrl} stays
     * null. Nothing is ever inferred from a company homepage or a synthesised path.</p>
     */
    private static final Map<String, Set<String>> FIRST_PARTY_APPLICATION_HOSTS = Map.of(
            "amazon_search_jobs", Set.of("www.amazon.jobs", "account.amazon.jobs"),
            "apple_search_jobs", Set.of("jobs.apple.com"),
            "google_search_jobs", Set.of("www.google.com", "careers.google.com"),
            "meta_search_jobs", Set.of("www.metacareers.com")
    );

    private final OpeningsMcpJobProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicLong idCounter = new AtomicLong(1);

    @Autowired
    public OpeningsMcpJobSourceProvider(OpeningsMcpJobProperties properties, RestTemplate restTemplate) {
        this(properties, restTemplate, new ObjectMapper());
    }

    public OpeningsMcpJobSourceProvider(OpeningsMcpJobProperties properties, RestTemplate restTemplate,
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
        if (properties == null || !properties.isEnabled()) {
            return false;
        }
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(base.trim());
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<Job> fetchJobs(JobSearchRequest criteria) {
        if (!isAvailable()) {
            return List.of();
        }

        List<String> keywords = criteria != null && criteria.keywords() != null
                ? criteria.keywords()
                : List.of();
        if (keywords.isEmpty()) {
            return List.of();
        }

        String location = criteria != null && criteria.location() != null && !criteria.location().isBlank()
                ? criteria.location()
                : "India";

        // Build a single cohesive query string from all keywords for better search results
        String query = String.join(" ", keywords);

        List<CompletableFuture<List<Job>>> futures = new ArrayList<>();

        if (properties.isIncludeGoogle()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("google_search_jobs", Map.of(
                    "keyword", query, "location", location
            ))));
        }
        if (properties.isIncludeAmazon()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("amazon_search_jobs", Map.of(
                    "keyword", query, "country", properties.getCountryCode()
            ))));
        }
        if (properties.isIncludeApple()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("apple_search_jobs", Map.of(
                    "keyword", query, "country_code", properties.getCountryCode()
            ))));
        }
        if (properties.isIncludeMeta()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("meta_search_jobs", Map.of(
                    "keyword", query
            ))));
        }

        long timeoutMs = Math.max(1, properties.getTimeoutSeconds()) * 1000L;
        List<Job> allJobs = new ArrayList<>();

        for (CompletableFuture<List<Job>> future : futures) {
            try {
                List<Job> toolJobs = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
                if (toolJobs != null) {
                    allJobs.addAll(toolJobs);
                }
            } catch (Exception e) {
                log.warn("Openings-MCP tool call failed: {}", e.getMessage());
            }
        }

        enrichApplyUrlsFromDetail(allJobs, timeoutMs);
        return allJobs;
    }

    /**
     * Optional second step: ask the server for a listing's detail record, which is the only
     * place {@code apply_url} is published. It costs one extra round trip per listing, so
     * it is off by default ({@code apply-url-detail-limit=0}) and bounded when enabled: at
     * most that many listings per call, only those still without an application URL.
     * Failure simply leaves the listing as it was — the career-posting URL for a
     * first-party tool, or no destination at all.
     */
    private void enrichApplyUrlsFromDetail(List<Job> jobs, long timeoutMs) {
        int limit = properties.getApplyUrlDetailLimit();
        if (limit <= 0) {
            return;
        }
        int remaining = limit;
        for (int i = 0; i < jobs.size() && remaining > 0; i++) {
            Job job = jobs.get(i);
            if (job.applicationUrl() != null && !job.applicationUrl().isBlank()) {
                continue;
            }
            String[] parts = job.id().split("-", 3);
            if (parts.length < 3) {
                continue;
            }
            String toolName = parts[1];
            String upstreamId = parts[2];
            if (toolName == null || toolName.isBlank() || upstreamId == null || upstreamId.isBlank()) {
                continue;
            }
            remaining--;
            try {
                String applyUrl = CompletableFuture
                        .supplyAsync(() -> fetchDetailApplyUrl(toolName, upstreamId))
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
                if (applyUrl != null) {
                    jobs.set(i, withApplicationUrl(job, applyUrl));
                }
            } catch (Exception e) {
                log.warn("Openings-MCP detail call for '{}' failed: {}", job.id(), e.getMessage());
            }
        }
    }

    /** Re-issues an immutable {@link Job} with a detail-supplied application URL. */
    private static Job withApplicationUrl(Job job, String applicationUrl) {
        return new Job(job.id(), job.title(), job.company(), job.location(), job.description(),
                job.requiredSkills(), job.preferredSkills(), job.experienceRequirement(),
                job.employmentType(), job.postingDate(), job.source(), job.sourceUrl(),
                job.sourceType(), job.discoveredAt(), applicationUrl);
    }

    private List<Job> fetchTool(String toolName, Map<String, Object> arguments) {
        try {
            Map<String, Object> payload = Map.of(
                    "jsonrpc", "2.0",
                    "id", idCounter.incrementAndGet(),
                    "method", "tools/call",
                    "params", Map.of("name", toolName, "arguments", arguments)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream")));

            String responseBody = restTemplate.exchange(
                    properties.getBaseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(payload), headers),
                    String.class
            ).getBody();

            if (responseBody == null || responseBody.isBlank()) {
                return List.of();
            }

            List<JsonNode> entries = entriesOf(parseStructuredContent(responseBody));

            List<Job> jobs = new ArrayList<>();
            for (JsonNode item : entries) {
                Job job = mapItem(toolName, item);
                if (job != null) {
                    jobs.add(job);
                }
            }
            return jobs;
        } catch (Exception e) {
            log.warn("Openings-MCP tool {} failed: {}", toolName, e.getMessage());
            return List.of();
        }
    }

    /**
     * Detail lookup for one listing. The server answers with the posting itself as the
     * structured content — a flat object, not a {@code data} array — so this goes through
     * the shared envelope parser and tolerates either shape.
     *
     * @return the validated {@code apply_url}, or {@code null} when the detail record does
     *         not publish one
     */
    private String fetchDetailApplyUrl(String toolName, String upstreamId) {
        if (toolName == null || toolName.isBlank() || upstreamId == null || upstreamId.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "jsonrpc", "2.0",
                    "id", idCounter.incrementAndGet(),
                    "method", "tools/call",
                    "params", Map.of("name", detailToolName(toolName),
                            "arguments", Map.of("job_id", upstreamId))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream")));

            String responseBody = restTemplate.exchange(
                    properties.getBaseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(payload), headers),
                    String.class
            ).getBody();

            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }
            for (JsonNode entry : entriesOf(parseStructuredContent(responseBody))) {
                String applyUrl = entry.path("apply_url").asText(null);
                if (applyUrl != null && JobUrlValidator.isValidExternalUrl(applyUrl)) {
                    return applyUrl;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Openings-MCP detail tool for '{}' failed: {}", toolName, e.getMessage());
            return null;
        }
    }

    private static String detailToolName(String searchToolName) {
        return searchToolName.endsWith("_search_jobs")
                ? searchToolName.substring(0, searchToolName.length() - "_search_jobs".length()) + "_get_job_detail"
                : searchToolName;
    }

    /**
     * Extracts {@code result.structuredContent} from a JSON or SSE-framed JSON-RPC body.
     * Returns a missing node when the body is not JSON-RPC, carries an error, or has no
     * structured content.
     */
    JsonNode parseStructuredContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            JsonNode root = null;
            for (String line : responseBody.split("\\r?\\n")) {
                if (line.startsWith("data: ")) {
                    String jsonStr = line.substring(6).trim();
                    if (!jsonStr.isBlank()) {
                        root = objectMapper.readTree(jsonStr);
                        break;
                    }
                }
            }
            if (root == null) {
                root = objectMapper.readTree(responseBody);
            }
            if (root == null || root.has("error")) {
                return objectMapper.nullNode();
            }
            JsonNode structured = root.path("result").path("structuredContent");
            return structured.isMissingNode() ? objectMapper.nullNode() : structured;
        } catch (Exception e) {
            log.debug("Failed to parse SSE / JSON response: {}", e.getMessage());
            return objectMapper.nullNode();
        }
    }

    /**
     * Search tools return {@code structuredContent.data} as an array; a detail tool returns
     * the posting as the structured content itself. Both are accepted, so one mapping path
     * serves either response shape.
     */
    private static List<JsonNode> entriesOf(JsonNode structuredContent) {
        if (structuredContent == null || structuredContent.isMissingNode() || structuredContent.isNull()) {
            return List.of();
        }
        JsonNode data = structuredContent.get("data");
        if (data != null && data.isArray()) {
            List<JsonNode> entries = new ArrayList<>();
            data.forEach(entries::add);
            return entries;
        }
        if (data != null && data.isObject()) {
            return List.of(data);
        }
        if (structuredContent.isObject()) {
            return List.of(structuredContent);
        }
        return List.of();
    }

    /**
     * Kept for callers and tests that expect the search-array shape: the
     * {@code structuredContent.data} array, or {@code null} when the payload carries none.
     */
    JsonNode parseSseData(String responseBody) {
        JsonNode structured = parseStructuredContent(responseBody);
        if (structured.isMissingNode() || structured.isNull()) {
            return null;
        }
        JsonNode data = structured.get("data");
        return data != null && data.isArray() ? data : null;
    }

    Job mapItem(String toolName, JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }
        String title = item.path("title").asText(null);
        if (title == null || title.isBlank()) {
            return null;
        }

        // Upstream identifiers are not uniform: the aggregate boards and the amazon/google
        // tools publish "id", while apple/meta publish "job_id". The upstream value is kept
        // verbatim, so the same listing keeps the same id (and the same detail-call target)
        // across searches.
        String rawId = firstNonBlank(item.path("id").asText(null), item.path("job_id").asText(null));
        String company = firstNonBlank(item.path("company").asText(null), item.path("company_name").asText(null));
        String location = item.path("location").asText(null);
        String rawUrl = item.path("url").asText(null);
        String sourceUrl = rawUrl != null && JobUrlValidator.isValidExternalUrl(rawUrl) ? rawUrl : null;

        // An explicit apply_url from the server's own detail record is the strongest
        // signal and needs no allowlist. Otherwise a URL may stand in as the application
        // destination only for a verified first-party employer tool whose host is listed —
        // which is why an aggregator page can never end up here.
        String rawApplyUrl = item.path("apply_url").asText(null);
        String applicationUrl = null;
        if (rawApplyUrl != null && JobUrlValidator.isValidExternalUrl(rawApplyUrl)) {
            applicationUrl = rawApplyUrl;
        } else if (isFirstPartyApplicationUrl(toolName, rawUrl)) {
            applicationUrl = rawUrl;
        }

        StringBuilder descBuilder = new StringBuilder();
        if (item.has("minimum_qualifications") && item.path("minimum_qualifications").isArray()) {
            descBuilder.append("Minimum Qualifications:\n");
            for (JsonNode q : item.path("minimum_qualifications")) {
                descBuilder.append("- ").append(q.asText()).append("\n");
            }
        }
        String desc = item.path("description").asText(null);
        if (desc != null && !desc.isBlank()) {
            if (descBuilder.length() > 0) descBuilder.append("\n");
            descBuilder.append(desc);
        }
        String description = descBuilder.length() > 0 ? descBuilder.toString() : null;
        // Same identity format as before: only when a provider published no identifier at
        // all does a content-derived suffix stand in, so one listing cannot collide with
        // another listing's upstream id.
        String jobId = "openings-" + toolName + "-" + (rawId == null ? Math.abs(item.hashCode()) : rawId);

        return new Job(
                jobId, title, company, location, description,
                List.of(), List.of(),
                item.path("experience_level").asText(null),
                null, null,
                SOURCE_NAME, sourceUrl, SOURCE_TYPE, Instant.now(),
                applicationUrl
        );
    }

    /**
     * Whether a URL returned by a first-party employer career tool is that employer's own
     * posting page. Requires both an explicit host from {@link #FIRST_PARTY_APPLICATION_HOSTS}
     * for this exact tool and the shared {@link JobUrlValidator} check, so a tool/host
     * mismatch, an aggregator host, a private address or a non-http scheme is refused.
     */
    private static boolean isFirstPartyApplicationUrl(String toolName, String url) {
        Set<String> allowedHosts = FIRST_PARTY_APPLICATION_HOSTS.get(toolName);
        if (allowedHosts == null || url == null || !JobUrlValidator.isValidExternalUrl(url)) {
            return false;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && allowedHosts.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
