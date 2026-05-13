package org.tinycircl.pandoc4j.binary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PandocBinaryOptionsTest {

    @Test
    @DisplayName("System properties map to PandocBinaryOptions")
    void fromSystemProperties() {
        String oldVersion = System.getProperty("pandoc.binary.version");
        String oldCache = System.getProperty("pandoc.binary.cacheDir");
        String oldBase = System.getProperty("pandoc.binary.baseUrl");
        String oldOffline = System.getProperty("pandoc.binary.offline");
        String oldVerify = System.getProperty("pandoc.binary.verifyChecksum");
        String oldProxyHost = System.getProperty("pandoc.binary.proxyHost");
        String oldProxyPort = System.getProperty("pandoc.binary.proxyPort");
        String oldDebug = System.getProperty("pandoc.binary.debug");

        try {
            System.setProperty("pandoc.binary.version", "3.9.0.2");
            System.setProperty("pandoc.binary.cacheDir", "target/pandoc-cache");
            System.setProperty("pandoc.binary.baseUrl", "https://example.invalid/pandoc");
            System.setProperty("pandoc.binary.offline", "true");
            System.setProperty("pandoc.binary.verifyChecksum", "false");
            System.setProperty("pandoc.binary.proxyHost", "127.0.0.1");
            System.setProperty("pandoc.binary.proxyPort", "7890");
            System.setProperty("pandoc.binary.debug", "true");

            PandocBinaryOptions options = PandocBinaryOptions.fromSystemProperties();

            assertEquals("3.9.0.2", options.getVersion());
            assertEquals(Path.of("target/pandoc-cache"), options.getCacheDirectory());
            assertEquals(URI.create("https://example.invalid/pandoc"), options.getBaseUrl());
            assertTrue(options.isOffline());
            assertFalse(options.isVerifyChecksum());
            assertEquals("127.0.0.1", options.getProxyHost());
            assertEquals(7890, options.getProxyPort());
            assertTrue(options.isDebug());
        } finally {
            restore("pandoc.binary.version", oldVersion);
            restore("pandoc.binary.cacheDir", oldCache);
            restore("pandoc.binary.baseUrl", oldBase);
            restore("pandoc.binary.offline", oldOffline);
            restore("pandoc.binary.verifyChecksum", oldVerify);
            restore("pandoc.binary.proxyHost", oldProxyHost);
            restore("pandoc.binary.proxyPort", oldProxyPort);
            restore("pandoc.binary.debug", oldDebug);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
