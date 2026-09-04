package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for the optional public job source adapter.
 *
 * <p>Reads the {@code job-sources.public-api.*} tree. Disabled by default so the
 * application never touches the network unless explicitly opted in — the
 * {@link MockJobSource} remains the development default.</p>
 */
@Component
@ConfigurationProperties(prefix = "job-sources.public-api")
public class PublicApiJobProperties {

    private boolean enabled = false;
    private String baseUrl = "https://remotive.com/api/remote-jobs";
    private String apiKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Remotive response envelope and item shapes. Matches the public JSON keys;
     * field names mirror the API (snake_case), so no naming strategy is required.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemotiveResponse(List<RemotiveJob> jobs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemotiveJob(
            long id,
            String url,
            String title,
            String company_name,
            String candidate_required_location,
            String description,
            List<String> tags,
            String category,
            String job_type,
            String publication_date) {
    }
}