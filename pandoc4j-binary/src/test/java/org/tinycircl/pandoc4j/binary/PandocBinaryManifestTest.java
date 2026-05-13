package org.tinycircl.pandoc4j.binary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class PandocBinaryManifestTest {

    @Test
    @DisplayName("Bundled manifest contains the default supported assets")
    void defaultManifestContainsSupportedAssets() {
        PandocBinaryManifest manifest = PandocBinaryManifest.load();

        assertEquals("3.9.0.2", manifest.defaultVersion());
        assertEquals("pandoc-3.9.0.2-windows-x86_64.zip",
                manifest.entry("3.9.0.2", "windows-x86_64").asset());
        assertEquals("pandoc-3.9.0.2-linux-amd64.tar.gz",
                manifest.entry("3.9.0.2", "linux-x86_64").asset());
        assertEquals("pandoc-3.9.0.2-arm64-macOS.zip",
                manifest.entry("3.9.0.2", "macos-arm64").asset());
    }

    @Test
    @DisplayName("Manifest fails clearly for unsupported platform entries")
    void missingManifestEntryFails() {
        PandocBinaryManifest manifest = PandocBinaryManifest.load();

        assertThrows(PandocBinaryException.class,
                () -> manifest.entry("3.9.0.2", "solaris-sparc"));
    }
}
