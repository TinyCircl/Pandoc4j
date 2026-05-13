package org.tinycircl.pandoc4j.binary.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.tinycircl.pandoc4j.binary.PandocBinaryOptions;

import java.net.URI;
import java.nio.file.Path;

@ConfigurationProperties(prefix = "pandoc.binary")
public class PandocBinaryProperties {

    private boolean enabled = true;
    private String version;
    private String cacheDir;
    private String baseUrl;
    private boolean offline;
    private boolean forceDownload;
    private boolean verifyChecksum = true;
    private String proxyHost;
    private int proxyPort = -1;
    private boolean debug;

    public PandocBinaryOptions toOptions() {
        PandocBinaryOptions.Builder builder = PandocBinaryOptions.builder()
                .offline(offline)
                .forceDownload(forceDownload)
                .verifyChecksum(verifyChecksum)
                .debug(debug);
        if (version != null && !version.isBlank()) {
            builder.version(version);
        }
        if (cacheDir != null && !cacheDir.isBlank()) {
            builder.cacheDirectory(Path.of(cacheDir));
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(URI.create(baseUrl));
        }
        if (proxyHost != null && !proxyHost.isBlank()) {
            builder.proxy(proxyHost, proxyPort);
        }
        return builder.build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCacheDir() {
        return cacheDir;
    }

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean isForceDownload() {
        return forceDownload;
    }

    public void setForceDownload(boolean forceDownload) {
        this.forceDownload = forceDownload;
    }

    public boolean isVerifyChecksum() {
        return verifyChecksum;
    }

    public void setVerifyChecksum(boolean verifyChecksum) {
        this.verifyChecksum = verifyChecksum;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
