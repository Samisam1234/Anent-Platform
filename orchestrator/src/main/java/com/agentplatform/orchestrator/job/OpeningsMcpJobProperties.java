package com.agentplatform.orchestrator.job;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-sources.openings-mcp")
public class OpeningsMcpJobProperties {

    private boolean enabled = false;
    private String baseUrl = "http://localhost:9000/";
    private int timeoutSeconds = 20;
    private String countryCode = "IND";
    private boolean includeGoogle = true;
    private boolean includeAmazon = true;
    private boolean includeApple = true;
    private boolean includeMeta = true;

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

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public boolean isIncludeGoogle() {
        return includeGoogle;
    }

    public void setIncludeGoogle(boolean includeGoogle) {
        this.includeGoogle = includeGoogle;
    }

    public boolean isIncludeAmazon() {
        return includeAmazon;
    }

    public void setIncludeAmazon(boolean includeAmazon) {
        this.includeAmazon = includeAmazon;
    }

    public boolean isIncludeApple() {
        return includeApple;
    }

    public void setIncludeApple(boolean includeApple) {
        this.includeApple = includeApple;
    }

    public boolean isIncludeMeta() {
        return includeMeta;
    }

    public void setIncludeMeta(boolean includeMeta) {
        this.includeMeta = includeMeta;
    }
}