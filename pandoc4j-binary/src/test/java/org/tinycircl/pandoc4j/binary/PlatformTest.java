package org.tinycircl.pandoc4j.binary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PlatformTest {

    @Test
    @DisplayName("Platform normalizes supported OS and architecture aliases")
    void supportedPlatforms() {
        assertEquals("windows-x86_64", Platform.from("Windows 11", "amd64").key());
        assertEquals("windows-x86_64", Platform.from("Windows 11", "x86_64").key());
        assertEquals("macos-arm64", Platform.from("Mac OS X", "aarch64").key());
        assertEquals("macos-x86_64", Platform.from("Mac OS X", "x86_64").key());
        assertEquals("linux-x86_64", Platform.from("Linux", "amd64").key());
        assertEquals("linux-arm64", Platform.from("Linux", "arm64").key());
    }

    @Test
    @DisplayName("Platform rejects unsupported combinations with an actionable error")
    void unsupportedPlatform() {
        PandocBinaryException ex = assertThrows(PandocBinaryException.class,
                () -> Platform.from("Linux", "x86"));

        assertTrue(ex.getMessage().contains("Set pandoc.path/PANDOC_PATH"));
    }
}
