package com.agentplatform.orchestrator.job;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Stateless utility for validating external job/application URLs.
 *
 * <p>This validator ensures that URLs point to real, publicly reachable
 * external sites — not localhost, private networks, or known placeholder
 * domains used during development.</p>
 *
 * <p>No network requests are made; validation is purely syntactic and
 * host-based. Ordinary invalid user data returns {@code false} without
 * throwing.</p>
 *
 * <p>Intended for external job links only. Mock jobs in
 * {@link MockJobSource} use {@code null} sourceUrl, which is correct
 * and should remain unchanged.</p>
 */
public final class JobUrlValidator {

    private JobUrlValidator() {}

    private static final Pattern PRIVATE_IP = Pattern.compile(
            "^(10\\.|172\\.(1[6-9]|2\\d|3[01])\\.|192\\.168\\.)"
    );

    private static final Pattern FAKE_DOMAIN = Pattern.compile(
            "mockjobs|example\\.|\\.test$|\\.internal$|\\.invalid$|0\\.0\\.0\\.0"
    );

    /**
     * Returns {@code true} if the given URL string is a valid, safe external URL.
     *
     * <p>Accepts only {@code http} and {@code https} schemes. Rejects:</p>
     * <ul>
     *   <li>{@code null} or blank input</li>
     *   <li>Malformed URLs</li>
     *   <li>Non-HTTP/HTTPS schemes ({@code javascript:}, {@code data:}, {@code file:}, etc.)</li>
     *   <li>Loopback addresses ({@code localhost}, {@code 127.0.0.1}, {@code ::1})</li>
     *   <li>RFC 1918 private IP ranges ({@code 10.*}, {@code 172.16-31.*}, {@code 192.168.*})</li>
     *   <li>Known development/placeholder domains</li>
     * </ul>
     *
     * @param url the URL string to validate, may be {@code null}
     * @return {@code true} if the URL is valid and safe for external navigation
     */
    public static boolean isValidExternalUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }

        host = host.toLowerCase(java.util.Locale.ROOT);

        // Strip brackets from IPv6 literals (e.g. [::1] → ::1)
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        if (isLoopback(host)) {
            return false;
        }

        if (isPrivateIp(host)) {
            return false;
        }

        if (FAKE_DOMAIN.matcher(host).find()) {
            return false;
        }

        return true;
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "0.0.0.0".equals(host);
    }

    private static boolean isPrivateIp(String host) {
        return PRIVATE_IP.matcher(host).lookingAt();
    }
}
