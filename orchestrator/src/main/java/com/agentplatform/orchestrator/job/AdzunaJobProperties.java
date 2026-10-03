package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for the <a href="https://developer.adzuna.com">Adzuna</a> job search API.
 *
 * <p><strong>Credentials are mandatory.</strong> Adzuna authenticates every call with an
 * {@code app_id} / {@code app_key} pair issued free of charge from the Adzuna developer
 * portal. Both are read from configuration or the environment and are never hard-coded:
 * </p>
 *
 * <pre>
 * ADZUNA_APP_ID=...  ADZUNA_APP_KEY=...
 * </pre>
 *
 * <p>When either credential is absent, {@link AdzunaJobSourceProvider#isAvailable()}
 * returns {@code false}, the provider is skipped before any network call, and the reason
 * is logged. No request is attempted with placeholder credentials and no data is
 * fabricated in their place.</p>
 *
 * <p>Endpoint shape mirrors the documented search API:
 * {@code GET /v1/api/jobs/{country}/search/{page}} returning
 * {@code { count, results: [...] }}.</p>
 */
@Component
@ConfigurationProperties(prefix = "job-sources.adzuna")
public class AdzunaJobProperties {

    /** Disabled by default: without credentials there is nothing to call. */
    private boolean enabled = false;
    private String baseUrl = "https://api.adzuna.com/v1/api/jobs";
    /** ISO country index to search (Adzuna scopes every query by country code). */
    private String country = "gb";
    private String appId = "";
    private String appKey = "";
    /** Listings per page. Adzuna caps this at 50. */
    private int resultsPerPage = 50;
    /**
     * Pages to fetch per search. Kept at 1 by default: Adzuna's free tier is metered per
     * day, so one search should cost one request.
     */
    private int maxPages = 1;

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

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public int getResultsPerPage() {
        return resultsPerPage;
    }

    public void setResultsPerPage(int resultsPerPage) {
        this.resultsPerPage = resultsPerPage;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    /** Whether the mandatory credential pair is present. Never logs the values. */
    public boolean hasCredentials() {
        return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
    }

    /** Adzuna search envelope: {@code count} plus the {@code results} array. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaResponse(Integer count, List<AdzunaJob> results) {
    }

    /** A single Adzuna listing. Field names mirror the documented API. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaJob(
            String id,
            String title,
            String description,
            String redirect_url,
            String created,
            String contract_time,
            String contract_type,
            Double salary_min,
            Double salary_max,
            AdzunaCompany company,
            AdzunaLocation location,
            AdzunaCategory category) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaCompany(String display_name) {
    }

    /**
     * Adzuna location: a parsed {@code area} hierarchy (country → … → city) plus a
     * flattened {@code display_name}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaLocation(List<String> area, String display_name, String country) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdzunaCategory(String label, String tag) {
    }
}
