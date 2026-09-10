package com.agentplatform.orchestrator.job;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration for the <a href="https://www.arbeitnow.com">Arbeitnow</a> job board
 * provider.
 *
 * <p>Arbeitnow publishes a free, unauthenticated JSON job-board API, so this tree carries
 * no credentials — only the enable flag, the endpoint and the page budget.</p>
 *
 * <p><strong>Rate limit:</strong> Arbeitnow answers with
 * {@code x-ratelimit-limit: 3} and its own {@code meta.terms} asks callers not to abuse
 * the free feed. {@link #maxPages} therefore defaults to 1 page (100 listings per page)
 * so a single search costs exactly one request.</p>
 *
 * <p>Response shape is mirrored from the documented API
 * (<a href="https://documenter.getpostman.com/view/18545278/UVJbJdKh">Arbeitnow API docs</a>):
 * a {@code data} array of listings plus {@code links} and {@code meta} envelopes.</p>
 */
@Component
@ConfigurationProperties(prefix = "job-sources.arbeitnow")
public class ArbeitnowJobProperties {

    /** Disabled by default: the app must not touch the network unless opted in. */
    private boolean enabled = false;
    private String baseUrl = "https://www.arbeitnow.com/api/job-board-api";
    /**
     * Pages to fetch per search. Arbeitnow returns {@code per_page: 100} and rate-limits
     * to 3 requests, so one page is the polite default.
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

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    /** Arbeitnow response envelope: {@code data}, {@code links} and {@code meta}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArbeitnowResponse(List<ArbeitnowJob> data, Links links, Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Links(String first, String last, String prev, String next) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(Integer current_page, Integer per_page) {
    }

    /**
     * A single Arbeitnow listing. Field names mirror the API (snake_case), so no naming
     * strategy is required.
     *
     * <p>Note what is deliberately absent: Arbeitnow exposes only {@code url}, which is
     * the Arbeitnow listing page ({@code https://arbeitnow.com/view/<slug>}). It does not
     * expose the employer's own application destination, so no application URL can be
     * mapped from this feed.</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArbeitnowJob(
            String slug,
            String company_name,
            String title,
            String description,
            Boolean remote,
            String url,
            List<String> tags,
            List<String> job_types,
            String location,
            Long created_at) {
    }
}
