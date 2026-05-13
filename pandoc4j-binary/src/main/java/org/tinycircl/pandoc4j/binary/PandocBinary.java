package org.tinycircl.pandoc4j.binary;

import org.tinycircl.pandoc4j.core.PandocInstallation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class PandocBinary {

    private PandocBinary() {}

    public static PandocInstallation getInstallation() {
        return getInstallation(PandocBinaryOptions.builder().build());
    }

    public static PandocInstallation getInstallation(PandocBinaryOptions options) {
        return PandocInstallation.at(getExecutablePath(options));
    }

    public static Path getExecutablePath() {
        return getExecutablePath(PandocBinaryOptions.builder().build());
    }

    public static Path getExecutablePath(PandocBinaryOptions options) {
        return new PandocBinaryResolver(options).resolve();
    }

    public static void clearCache(PandocBinaryOptions options) {
        Path cacheRoot = PandocBinaryResolver.resolveCacheRoot(options);
        if (!Files.exists(cacheRoot)) {
            return;
        }
        try (var paths = Files.walk(cacheRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new PandocBinaryException("Failed to delete cache path: " + path, e);
                }
            });
        } catch (IOException e) {
            throw new PandocBinaryException("Failed to clear Pandoc binary cache: " + cacheRoot, e);
        }
    }
}
