package com.agentplatform.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 8.3 logging configuration.
 *
 * <p>Binds the {@code logging.app.enabled} property (default {@code true}) as a
 * single master switch for the application's centralized correlated logging.
 * The switch is deliberately independent of the {@code logging.level.*}
 * verbosity settings — it toggles the Phase 8.3 logging support itself rather
 * than the level thresholds.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "logging.app")
public class LoggingConfig {

    private boolean enabled = true;

    /** Whether centralized app logging is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}