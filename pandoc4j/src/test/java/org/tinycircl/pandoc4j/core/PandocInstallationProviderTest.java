package org.tinycircl.pandoc4j.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class PandocInstallationProviderTest {

    @Test
    @DisplayName("PandocInstallation.detect() consults ServiceLoader providers")
    void detectUsesProvider() {
        Assumptions.assumeTrue(System.getenv(PandocInstallation.ENV_VARIABLE) == null
                        || System.getenv(PandocInstallation.ENV_VARIABLE).isBlank(),
                "PANDOC_PATH overrides provider resolution");

        String oldPath = System.getProperty(PandocInstallation.SYSTEM_PROPERTY);
        String oldProviderPath = System.getProperty(TestPandocInstallationProvider.PROPERTY);
        Path java = javaExecutable();

        try {
            System.clearProperty(PandocInstallation.SYSTEM_PROPERTY);
            System.setProperty(TestPandocInstallationProvider.PROPERTY, java.toString());

            PandocInstallation installation = PandocInstallation.detect();

            assertEquals(java, installation.getExecutablePath());
        } finally {
            restoreProperty(PandocInstallation.SYSTEM_PROPERTY, oldPath);
            restoreProperty(TestPandocInstallationProvider.PROPERTY, oldProviderPath);
        }
    }

    @Test
    @DisplayName("pandoc.path remains higher priority than ServiceLoader providers")
    void explicitSystemPropertyWins() {
        String oldPath = System.getProperty(PandocInstallation.SYSTEM_PROPERTY);
        String oldProviderPath = System.getProperty(TestPandocInstallationProvider.PROPERTY);
        Path java = javaExecutable();

        try {
            System.setProperty(PandocInstallation.SYSTEM_PROPERTY, java.toString());
            System.setProperty(TestPandocInstallationProvider.PROPERTY, Path.of("missing-pandoc").toString());

            PandocInstallation installation = PandocInstallation.detect();

            assertEquals(java, installation.getExecutablePath());
        } finally {
            restoreProperty(PandocInstallation.SYSTEM_PROPERTY, oldPath);
            restoreProperty(TestPandocInstallationProvider.PROPERTY, oldProviderPath);
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe"
                : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Assumptions.assumeTrue(Files.isExecutable(java), "Java executable is not available");
        return java;
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
