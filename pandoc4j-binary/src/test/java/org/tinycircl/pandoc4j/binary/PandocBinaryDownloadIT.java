package org.tinycircl.pandoc4j.binary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.tinycircl.pandoc4j.ConversionRequest;
import org.tinycircl.pandoc4j.Format;
import org.tinycircl.pandoc4j.core.PandocInstallation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("binary-download")
class PandocBinaryDownloadIT {

    @Test
    @DisplayName("pandoc4j-binary downloads Pandoc and runs a conversion")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void downloadsPandocAndConverts() throws IOException {
        Path cache = Files.createTempDirectory("pandoc4j-binary-it-");
        boolean keepCache = Boolean.getBoolean("pandoc4j.binary.keepCache");
        try {
            PandocInstallation installation = PandocBinary.getInstallation(
                    PandocBinaryOptions.builder()
                            .cacheDirectory(cache)
                            .build());

            String html = ConversionRequest.builder()
                    .withInstallation(installation)
                    .from(Format.MARKDOWN)
                    .to(Format.HTML5)
                    .convertText("# Hello");

            assertTrue(html.contains("<h1"));
        } finally {
            if (keepCache) {
                System.err.println("[pandoc4j-binary] keeping test cache: " + cache);
            } else {
                deleteRecursively(cache);
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path candidate : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(candidate);
            }
        }
    }
}
