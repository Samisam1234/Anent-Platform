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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OpeningsMcpJobSourceProvider implements JobSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(OpeningsMcpJobSourceProvider.class);

    public static final String SOURCE_NAME = "OPENINGS_MCP";
    public static final String SOURCE_TYPE = "MCP";

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

        String keyword = criteria != null && criteria.keywords() != null && !criteria.keywords().isEmpty()
        		 ? criteria.keywords().get(0)
                : "";
        if (keyword.isBlank()) {
            return List.of();
        }

        String location = criteria != null && criteria.location() != null && !criteria.location().isBlank()
                ? criteria.location()
                : "India";

        List<CompletableFuture<List<Job>>> futures = new ArrayList<>();

        if (properties.isIncludeGoogle()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("google_search_jobs", Map.of(
                    "keyword", keyword, "location", location
            ))));
        }
        if (properties.isIncludeAmazon()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("amazon_search_jobs", Map.of(
                    "keyword", keyword, "country", properties.getCountryCode()
            ))));
        }
        if (properties.isIncludeApple()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("apple_search_jobs", Map.of(
                    "keyword", keyword, "country_code", properties.getCountryCode()
            ))));
        }
        if (properties.isIncludeMeta()) {
            futures.add(CompletableFuture.supplyAsync(() -> fetchTool("meta_search_jobs", Map.of(
                    "keyword", keyword
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

        return allJobs;
    }

    private List<Job> fetchTool(String toolName, Map<String, Object> arguments) {
        log.info("Openings-MCP ENTER fetchTool: tool={}, url={}", toolName, properties.getBaseUrl());
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

            JsonNode dataArray = parseSseData(responseBody);
            if (dataArray == null || !dataArray.isArray()) {
                return List.of();
            }

            List<Job> jobs = new ArrayList<>();
            for (JsonNode item : dataArray) {
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

    JsonNode parseSseData(String responseBody) {
        try {
            for (String line : responseBody.split("\\r?\\n")) {
                if (line.startsWith("data: ")) {
                    String jsonStr = line.substring(6).trim();
                    if (!jsonStr.isBlank()) {
                        JsonNode root = objectMapper.readTree(jsonStr);
                        if (root.has("error")) {
                            return null;
                        }
                        JsonNode data = root.path("result").path("structuredContent").path("data");
                        if (data.isArray()) {
                            return data;
                        }
                    }
                }
            }
            JsonNode root = objectMapper.readTree(responseBody);
            if (!root.has("error")) {
                JsonNode data = root.path("result").path("structuredContent").path("data");
                if (data.isArray()) return data;
                if (root.isArray()) return root;
            }
        } catch (Exception e) {
            log.debug("Failed to parse SSE / JSON response: {}", e.getMessage());
        }
        return null;
    }

    Job mapItem(String toolName, JsonNode item) {
        if (item == null || item.isMissingNode()) {
            return null;
        }
        String title = item.path("title").asText(null);
        if (title == null || title.isBlank()) {
            return null;
        }

        String rawId = item.path("id").asText("");
        String company = item.path("company").asText(null);
        String location = item.path("location").asText(null);
        String rawUrl = item.path("url").asText(null);
        String sourceUrl = rawUrl != null && JobUrlValidator.isValidExternalUrl(rawUrl) ? rawUrl : null;
        String rawApplyUrl = item.path("apply_url").asText(null);
        String applicationUrl = rawApplyUrl != null && JobUrlValidator.isValidExternalUrl(rawApplyUrl) ? rawApplyUrl : null;

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
        String jobId = "openings-" + toolName + "-" + (rawId.isBlank() ? Math.abs(item.hashCode()) : rawId);

        return new Job(
                jobId, title, company, location, description,
                List.of(), List.of(),
                item.path("experience_level").asText(null),
                null, null,
                SOURCE_NAME, sourceUrl, SOURCE_TYPE, Instant.now(),
                applicationUrl
        );
    }
}
