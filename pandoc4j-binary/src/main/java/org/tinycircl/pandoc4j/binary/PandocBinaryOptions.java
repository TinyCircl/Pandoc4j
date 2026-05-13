package org.tinycircl.pandoc4j.binary;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

public final class PandocBinaryOptions {

    public static final URI DEFAULT_BASE_URL =
            URI.create("https://github.com/jgm/pandoc/releases/download");

    private final String version;
    private final Path cacheDirectory;
    private final URI baseUrl;
    private final boolean offline;
    private final boolean forceDownload;
    private final boolean verifyChecksum;
    private final String proxyHost;
    private final int proxyPort;
    private final boolean debug;

    private PandocBinaryOptions(Builder builder) {
        this.version = blankToNull(builder.version);
        this.cacheDirectory = builder.cacheDirectory;
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl : DEFAULT_BASE_URL;
        this.offline = builder.offline;
        this.forceDownload = builder.forceDownload;
        this.verifyChecksum = builder.verifyChecksum;
        this.proxyHost = blankToNull(builder.proxyHost);
        this.proxyPort = builder.proxyPort;
        this.debug = builder.debug;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PandocBinaryOptions fromSystemProperties() {
        Builder builder = builder();
        String version = firstNonBlank(
                System.getProperty("pandoc.binary.version"),
                System.getenv("PANDOC4J_BINARY_VERSION"));
        if (version != null) {
            builder.version(version);
        }

        String cacheDir = firstNonBlank(
                System.getProperty("pandoc.binary.cacheDir"),
                System.getenv("PANDOC4J_BINARY_CACHE_DIR"));
        if (cacheDir != null) {
            builder.cacheDirectory(Path.of(cacheDir));
        }

        String baseUrl = firstNonBlank(
                System.getProperty("pandoc.binary.baseUrl"),
                System.getenv("PANDOC4J_BINARY_BASE_URL"));
        if (baseUrl != null) {
            builder.baseUrl(URI.create(baseUrl));
        }

        builder.offline(booleanProperty("pandoc.binary.offline", "PANDOC4J_BINARY_OFFLINE", false));
        builder.forceDownload(booleanProperty("pandoc.binary.forceDownload", "PANDOC4J_BINARY_FORCE_DOWNLOAD", false));
        builder.verifyChecksum(booleanProperty("pandoc.binary.verifyChecksum", "PANDOC4J_BINARY_VERIFY_CHECKSUM", true));
        String proxyHost = firstNonBlank(
                System.getProperty("pandoc.binary.proxyHost"),
                System.getenv("PANDOC4J_BINARY_PROXY_HOST"),
                System.getProperty("https.proxyHost"),
                System.getProperty("http.proxyHost"));
        int proxyPort = intProperty(
                "pandoc.binary.proxyPort",
                "PANDOC4J_BINARY_PROXY_PORT",
                intProperty("https.proxyPort", null, intProperty("http.proxyPort", null, -1)));
        if (proxyHost != null) {
            builder.proxy(proxyHost, proxyPort);
        }
        builder.debug(booleanProperty("pandoc.binary.debug", "PANDOC4J_BINARY_DEBUG", false));
        return builder.build();
    }

    public String getVersion() {
        return version;
    }

    public Path getCacheDirectory() {
        return cacheDirectory;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public boolean isOffline() {
        return offline;
    }

    public boolean isForceDownload() {
        return forceDownload;
    }

    public boolean isVerifyChecksum() {
        return verifyChecksum;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public boolean isDebug() {
        return debug;
    }

    static boolean isProviderEnabled() {
        return booleanProperty("pandoc.binary.enabled", "PANDOC4J_BINARY_ENABLED", true);
    }

    private static boolean booleanProperty(String property, String env, boolean defaultValue) {
        String value = firstNonBlank(System.getProperty(property), System.getenv(env));
        if (value == null) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> defaultValue;
        };
    }

    private static int intProperty(String property, String env, int defaultValue) {
        String value = firstNonBlank(
                property != null ? System.getProperty(property) : null,
                env != null ? System.getenv(env) : null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static final class Builder {

        private String version;
        private Path cacheDirectory;
        private URI baseUrl;
        private boolean offline;
        private boolean forceDownload;
        private boolean verifyChecksum = true;
        private String proxyHost;
        private int proxyPort = -1;
        private boolean debug;

        private Builder() {}

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder cacheDirectory(Path cacheDirectory) {
            this.cacheDirectory = cacheDirectory;
            return this;
        }

        public Builder baseUrl(URI baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder offline(boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder forceDownload(boolean forceDownload) {
            this.forceDownload = forceDownload;
            return this;
        }

        public Builder verifyChecksum(boolean verifyChecksum) {
            this.verifyChecksum = verifyChecksum;
            return this;
        }

        public Builder proxy(String host, int port) {
            this.proxyHost = host;
            this.proxyPort = port;
            return this;
        }

        public Builder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public PandocBinaryOptions build() {
            return new PandocBinaryOptions(this);
        }
    }
}
