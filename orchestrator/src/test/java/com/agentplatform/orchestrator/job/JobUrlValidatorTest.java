package com.agentplatform.orchestrator.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic unit tests for {@link JobUrlValidator}.
 *
 * <p>No network, no database, no AI providers, no external services.
 * All assertions are purely in-memory URL string validation.</p>
 */
class JobUrlValidatorTest {

    // ── Null, blank, and malformed ──

    @ParameterizedTest(name = "rejects null/blank: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Rejects null, blank, and whitespace-only input")
    void rejectsNullOrBlank(String input) {
        assertFalse(JobUrlValidator.isValidExternalUrl(input));
    }

    @Test
    @DisplayName("Rejects malformed URL strings")
    void rejectsMalformed() {
        assertFalse(JobUrlValidator.isValidExternalUrl("not-a-url"));
        assertFalse(JobUrlValidator.isValidExternalUrl("://missing-scheme"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https://"));
    }

    // ── Valid HTTP / HTTPS ──

    @Test
    @DisplayName("Accepts valid HTTPS URLs")
    void acceptsHttps() {
        assertTrue(JobUrlValidator.isValidExternalUrl("https://remotive.com/api/remote-jobs"));
        assertTrue(JobUrlValidator.isValidExternalUrl("https://www.linkedin.com/jobs/view/999"));
        assertTrue(JobUrlValidator.isValidExternalUrl("https://boards.greenhouse.io/embed/job_app?token=abc"));
        assertTrue(JobUrlValidator.isValidExternalUrl("https://www.naukri.com/job-listings/123"));
    }

    @Test
    @DisplayName("Accepts valid HTTP URLs")
    void acceptsHttp() {
        assertTrue(JobUrlValidator.isValidExternalUrl("http://company.org/careers/apply"));
        assertTrue(JobUrlValidator.isValidExternalUrl("http://www.google.com/jobs"));
    }

    // ── Blocked schemes ──

    @Test
    @DisplayName("Rejects javascript: scheme")
    void rejectsJavascriptScheme() {
        assertFalse(JobUrlValidator.isValidExternalUrl("javascript:alert(1)"));
        assertFalse(JobUrlValidator.isValidExternalUrl("JavaScript:void(0)"));
    }

    @Test
    @DisplayName("Rejects data: scheme")
    void rejectsDataScheme() {
        assertFalse(JobUrlValidator.isValidExternalUrl("data:text/html,<h1>hi</h1>"));
    }

    @Test
    @DisplayName("Rejects file: scheme")
    void rejectsFileScheme() {
        assertFalse(JobUrlValidator.isValidExternalUrl("file:///etc/passwd"));
        assertFalse(JobUrlValidator.isValidExternalUrl("file://localhost/etc/passwd"));
    }

    @Test
    @DisplayName("Rejects vbscript: scheme")
    void rejectsVbscriptScheme() {
        assertFalse(JobUrlValidator.isValidExternalUrl("vbscript:MsgBox(1)"));
    }

    // ── Loopback addresses ──

    @Test
    @DisplayName("Rejects localhost")
    void rejectsLocalhost() {
        assertFalse(JobUrlValidator.isValidExternalUrl("http://localhost:8080/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https://localhost/path"));
    }

    @Test
    @DisplayName("Rejects 127.0.0.1")
    void rejectsLoopbackIp() {
        assertFalse(JobUrlValidator.isValidExternalUrl("http://127.0.0.1:3000/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https://127.0.0.1/admin"));
    }

    @Test
    @DisplayName("Rejects IPv6 loopback ::1")
    void rejectsIpv6Loopback() {
        assertFalse(JobUrlValidator.isValidExternalUrl("http://[::1]:8080/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https://[::1]/path"));
    }

    @Test
    @DisplayName("Rejects 0.0.0.0")
    void rejectsZeroAddress() {
        assertFalse(JobUrlValidator.isValidExternalUrl("http://0.0.0.0:8080/jobs"));
    }

    // ── Private / internal IP ranges ──

    @Test
    @DisplayName("Rejects RFC 1918 private IPs")
    void rejectsPrivateIps() {
        assertFalse(JobUrlValidator.isValidExternalUrl("http://10.0.0.1/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://10.255.255.255/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://172.16.0.1/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://172.31.255.255/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://192.168.1.1/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://192.168.0.100:9090/api"));
    }

    // ── Fake / development domains ──

    @Test
    @DisplayName("Rejects mockjobs.local and similar placeholder domains")
    void rejectsFakeDomains() {
        assertFalse(JobUrlValidator.isValidExternalUrl("https://mockjobs.local/jobs/sw-001"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://mockjobs.example.com/apply"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https://example.com/jobs/1"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://test.example.com/apply"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https://myapp.test/jobs"));
        assertFalse(JobUrlValidator.isValidExternalUrl("http://service.internal/api"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https://dev.invalid/jobs"));
    }

    // ── Edge cases ──

    @Test
    @DisplayName("Handles URLs with ports, paths, query strings, and fragments")
    void handlesComplexUrls() {
        assertTrue(JobUrlValidator.isValidExternalUrl("https://boards.greenhouse.io:8443/jobs/123?ref=linkedin#section"));
        assertTrue(JobUrlValidator.isValidExternalUrl("http://company.org/careers?location=remote&type=fulltime"));
    }

    @Test
    @DisplayName("Handles URL with uppercase scheme and host")
    void handlesCaseInsensitive() {
        assertTrue(JobUrlValidator.isValidExternalUrl("HTTPS://REMOTIVE.COM/jobs/1"));
        assertTrue(JobUrlValidator.isValidExternalUrl("HTTP://Company.Org/Careers"));
    }

    @Test
    @DisplayName("Rejects URLs with only scheme and no host")
    void rejectsSchemeOnly() {
        assertFalse(JobUrlValidator.isValidExternalUrl("http:"));
        assertFalse(JobUrlValidator.isValidExternalUrl("https:"));
    }
}
