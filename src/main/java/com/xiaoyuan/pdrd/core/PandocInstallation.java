package com.xiaoyuan.pdrd.core;

import com.xiaoyuan.pdrd.exception.PandocNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates and validates the Pandoc executable on the current system.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>System property {@code pandoc.path}</li>
 *   <li>Environment variable {@code PANDOC_PATH}</li>
 *   <li>Common installation directories (platform-specific)</li>
 *   <li>{@code pandoc} / {@code pandoc.exe} on {@code PATH} (via {@code which} / {@code where})</li>
 * </ol>
 */
public final class PandocInstallation {

    /** System property key for a custom Pandoc executable path. */
    public static final String SYSTEM_PROPERTY = "pandoc.path";

    /** Environment variable key for a custom Pandoc executable path. */
    public static final String ENV_VARIABLE = "PANDOC_PATH";

    private final Path executablePath;
    private volatile String version;

    private PandocInstallation(Path executablePath) {
        this.executablePath = executablePath;
    }

    // ── Factory methods ────────────────────────────────────────────────────

    /**
     * Auto-detects Pandoc using the resolution order described in the class javadoc.
     *
     * @throws PandocNotFoundException if Pandoc cannot be found
     */
    public static PandocInstallation detect() {
        // 1. System property
        String sysProp = System.getProperty(SYSTEM_PROPERTY);
        if (sysProp != null && !sysProp.isBlank()) {
            return at(Paths.get(sysProp));
        }

        // 2. Environment variable
        String envVar = System.getenv(ENV_VARIABLE);
        if (envVar != null && !envVar.isBlank()) {
            return at(Paths.get(envVar));
        }

        // 3. Common install locations
        for (Path candidate : commonLocations()) {
            if (Files.isExecutable(candidate)) {
                return new PandocInstallation(candidate);
            }
        }

        // 4. Fall back: resolve via PATH using OS shell
        Path fromPath = resolveFromPath();
        if (fromPath != null) {
            return new PandocInstallation(fromPath);
        }

        throw new PandocNotFoundException();
    }

    /**
     * Creates an installation pointing at the supplied explicit path.
     *
     * @throws PandocNotFoundException if the file does not exist or is not executable
     */
    public static PandocInstallation at(Path path) {
        if (!Files.isExecutable(path)) {
            throw new PandocNotFoundException(path.toString());
        }
        return new PandocInstallation(path);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Returns the absolute path to the Pandoc executable. */
    public Path getExecutablePath() {
        return executablePath;
    }

    /**
     * Returns the Pandoc version string (e.g. {@code "3.6.3"}).
     * The result is cached after the first call.
     */
    public String getVersion() {
        if (version == null) {
            synchronized (this) {
                if (version == null) {
                    version = readVersion();
                }
            }
        }
        return version;
    }

    /** Returns {@code true} if the executable still exists and is executable. */
    public boolean isAvailable() {
        return Files.isExecutable(executablePath);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private String readVersion() {
        try {
            Process proc = new ProcessBuilder(executablePath.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(proc.getInputStream().readAllBytes()).trim();
            proc.waitFor();
            // First line: "pandoc 3.6.3"
            String firstLine = out.lines().findFirst().orElse("");
            return firstLine.replace("pandoc", "").trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private static List<Path> commonLocations() {
        List<Path> locations = new ArrayList<>();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (isWindows) {
            locations.add(Paths.get("C:\\Program Files\\Pandoc\\pandoc.exe"));
            locations.add(Paths.get("C:\\Program Files (x86)\\Pandoc\\pandoc.exe"));
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                locations.add(Paths.get(localAppData, "Pandoc", "pandoc.exe"));
            }
        } else {
            locations.add(Paths.get("/usr/local/bin/pandoc"));
            locations.add(Paths.get("/usr/bin/pandoc"));
            locations.add(Paths.get("/opt/homebrew/bin/pandoc"));
            locations.add(Paths.get("/opt/local/bin/pandoc"));
            String home = System.getProperty("user.home", "");
            if (!home.isBlank()) {
                locations.add(Paths.get(home, ".local", "bin", "pandoc"));
            }
        }
        return locations;
    }

    private static Path resolveFromPath() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        try {
            ProcessBuilder pb = isWindows
                    ? new ProcessBuilder("where", "pandoc")
                    : new ProcessBuilder("which", "pandoc");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String result = new String(proc.getInputStream().readAllBytes()).trim();
            int exitCode = proc.waitFor();
            if (exitCode == 0 && !result.isBlank()) {
                Path resolved = Paths.get(result.lines().findFirst().orElse("").trim());
                if (Files.isExecutable(resolved)) {
                    return resolved;
                }
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    @Override
    public String toString() {
        return "PandocInstallation{path=" + executablePath + ", version=" + getVersion() + "}";
    }
}
