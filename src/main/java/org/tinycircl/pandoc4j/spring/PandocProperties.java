package org.tinycircl.pandoc4j.spring;

import org.tinycircl.pandoc4j.core.PandocExecutor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for pandoc4j Spring Boot integration.
 *
 * <p>Example {@code application.yml}:
 * <pre>{@code
 * pandoc:
 *   executable-path: /usr/local/bin/pandoc   # optional; auto-detected if omitted
 *   timeout-seconds: 60                       # default: 120
 * }</pre>
 */
@ConfigurationProperties(prefix = "pandoc")
public class PandocProperties {

    /**
     * Absolute path to the Pandoc executable.
     * When omitted, pandoc4j resolves Pandoc automatically via the
     * {@code PANDOC_PATH} environment variable, common OS locations, and {@code PATH}.
     */
    private String executablePath;

    /**
     * Maximum seconds to wait for a single Pandoc conversion before the process
     * is forcibly terminated.
     * Defaults to {@value PandocExecutor#DEFAULT_TIMEOUT_SECONDS}.
     */
    private long timeoutSeconds = PandocExecutor.DEFAULT_TIMEOUT_SECONDS;

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getExecutablePath() {
        return executablePath;
    }

    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
