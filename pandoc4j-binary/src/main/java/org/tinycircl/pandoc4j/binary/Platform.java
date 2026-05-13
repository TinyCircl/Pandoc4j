package org.tinycircl.pandoc4j.binary;

import java.util.Locale;

record Platform(String key, boolean windows) {

    static Platform current() {
        return from(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static Platform from(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = normalizeArch(osArch);

        if (os.contains("win")) {
            if ("x86_64".equals(arch)) {
                return new Platform("windows-x86_64", true);
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            if ("x86_64".equals(arch)) {
                return new Platform("macos-x86_64", false);
            }
            if ("arm64".equals(arch)) {
                return new Platform("macos-arm64", false);
            }
        } else if (os.contains("linux")) {
            if ("x86_64".equals(arch)) {
                return new Platform("linux-x86_64", false);
            }
            if ("arm64".equals(arch)) {
                return new Platform("linux-arm64", false);
            }
        }

        throw new PandocBinaryException(
                "Pandoc binary is not available for os=" + osName + ", arch=" + osArch
                        + ". Set pandoc.path/PANDOC_PATH, install Pandoc manually, "
                        + "or disable pandoc.binary.");
    }

    String executableName() {
        return windows ? "pandoc.exe" : "pandoc";
    }

    private static String normalizeArch(String osArch) {
        String arch = osArch.toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch;
        };
    }
}
