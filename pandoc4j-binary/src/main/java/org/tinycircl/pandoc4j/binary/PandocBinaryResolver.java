package org.tinycircl.pandoc4j.binary;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class PandocBinaryResolver {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    private final PandocBinaryOptions options;

    PandocBinaryResolver(PandocBinaryOptions options) {
        this.options = options != null ? options : PandocBinaryOptions.builder().build();
    }

    Path resolve() {
        PandocBinaryManifest manifest = PandocBinaryManifest.load();
        Platform platform = Platform.current();
        String version = options.getVersion() != null ? options.getVersion() : manifest.defaultVersion();
        PandocBinaryManifest.Entry entry = manifest.entry(version, platform.key());
        Path cacheRoot = resolveCacheRoot(options);
        Path installDir = cacheRoot.resolve("pandoc").resolve(version).resolve(platform.key());
        debug("resolve version=" + version + ", platform=" + platform.key()
                + ", cache=" + cacheRoot + ", installDir=" + installDir);

        if (!options.isForceDownload()) {
            Path cached = validateCachedInstallation(installDir, entry, platform);
            if (cached != null) {
                debug("cache hit: " + cached);
                return cached;
            }
        }

        Path lockDir = cacheRoot.resolve("locks");
        Path lockFile = lockDir.resolve(version + "-" + platform.key() + ".lock");
        try {
            Files.createDirectories(lockDir);
            debug("acquiring lock: " + lockFile);
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                debug("lock acquired: " + lockFile);
                if (!options.isForceDownload()) {
                    Path cached = validateCachedInstallation(installDir, entry, platform);
                    if (cached != null) {
                        debug("cache hit after lock: " + cached);
                        return cached;
                    }
                }
                if (options.isOffline()) {
                    throw new PandocBinaryException(
                            "Pandoc binary cache miss in offline mode for version=" + version
                                    + ", platform=" + platform.key()
                                    + ", cache=" + cacheRoot);
                }
                Path archive = resolveArchive(cacheRoot, entry);
                return installArchive(cacheRoot, installDir, archive, entry, platform);
            }
        } catch (IOException e) {
            throw new PandocBinaryException("Failed to resolve managed Pandoc binary: " + e.getMessage(), e);
        }
    }

    static Path resolveCacheRoot(PandocBinaryOptions options) {
        Path configured = options != null ? options.getCacheDirectory() : null;
        if (configured != null) {
            return expandHome(configured).toAbsolutePath().normalize();
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData, "pandoc4j", "cache").toAbsolutePath().normalize();
            }
        }

        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            if (os.contains("mac") || os.contains("linux")) {
                return Path.of(home, ".cache", "pandoc4j").toAbsolutePath().normalize();
            }
            return Path.of(home, ".pandoc4j", "cache").toAbsolutePath().normalize();
        }

        return Path.of(System.getProperty("java.io.tmpdir"), "pandoc4j", "cache")
                .toAbsolutePath().normalize();
    }

    private static Path expandHome(Path path) {
        String value = path.toString();
        if ("~".equals(value)) {
            return Path.of(System.getProperty("user.home", ""));
        }
        if (value.startsWith("~/") || value.startsWith("~\\")) {
            return Path.of(System.getProperty("user.home", ""), value.substring(2));
        }
        return path;
    }

    private Path validateCachedInstallation(Path installDir,
                                            PandocBinaryManifest.Entry entry,
                                            Platform platform) {
        Path metadata = installDir.resolve("install.json");
        if (!Files.isRegularFile(metadata)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(metadata)) {
            JsonNode node = MAPPER.readTree(in);
            if (!entry.version().equals(node.path("version").asString())
                    || !entry.platformKey().equals(node.path("platform").asString())
                    || !entry.sha256().equalsIgnoreCase(node.path("sha256").asString())) {
                return null;
            }
            String relativeExecutable = node.path("executable").asString();
            if (relativeExecutable == null || relativeExecutable.isBlank()) {
                return null;
            }
            Path executable = installDir.resolve(relativeExecutable).normalize();
            if (!executable.startsWith(installDir.normalize())) {
                return null;
            }
            if (Files.isRegularFile(executable)
                    && Files.isExecutable(executable)
                    && versionMatches(executable, entry.version())) {
                return executable;
            }
            return null;
        } catch (JacksonException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private Path resolveArchive(Path cacheRoot, PandocBinaryManifest.Entry entry) throws IOException {
        Path downloadDir = cacheRoot.resolve("downloads");
        Files.createDirectories(downloadDir);
        Path archive = downloadDir.resolve(entry.asset());
        if (!options.isForceDownload() && Files.isRegularFile(archive)) {
            verifyArchiveIfNeeded(archive, entry);
            return archive;
        }

        URI downloadUri = downloadUri(entry);
        Path partial = downloadDir.resolve(entry.asset() + ".partial");
        Files.deleteIfExists(partial);
        try {
            HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(30));
            ProxySelector proxySelector = resolveProxySelector();
            if (proxySelector != null) {
                clientBuilder.proxy(proxySelector);
            }
            HttpClient client = clientBuilder.build();
            HttpRequest request = HttpRequest.newBuilder(downloadUri)
                    .timeout(DOWNLOAD_TIMEOUT)
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "pandoc4j-binary")
                    .GET()
                    .build();
            debug("GET " + downloadUri);
            debug("proxy " + proxyDescription());
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            debug("response status=" + response.statusCode()
                    + ", uri=" + response.uri()
                    + ", contentLength=" + response.headers().firstValueAsLong("content-length").orElse(-1));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                Files.deleteIfExists(partial);
                throw new PandocBinaryException(
                        "Failed to download Pandoc binary from " + downloadUri
                                + ": HTTP " + response.statusCode());
            }
            copyResponseBody(response, partial);
            verifyArchiveIfNeeded(partial, entry);
            moveReplacing(partial, archive);
            debug("download complete: " + archive + " (" + Files.size(archive) + " bytes)");
            return archive;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PandocBinaryException("Interrupted while downloading Pandoc binary from " + downloadUri, e);
        }
    }

    private URI downloadUri(PandocBinaryManifest.Entry entry) {
        String base = options.getBaseUrl().toString();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return URI.create(base + entry.version() + "/" + entry.asset());
    }

    private ProxySelector resolveProxySelector() {
        ProxyAddress proxy = resolveProxy();
        if (proxy == null) {
            return ProxySelector.getDefault();
        }
        return ProxySelector.of(InetSocketAddress.createUnresolved(proxy.host(), proxy.port()));
    }

    private String proxyDescription() {
        ProxyAddress proxy = resolveProxy();
        if (proxy == null) {
            return "default";
        }
        return proxy.host() + ":" + proxy.port();
    }

    private ProxyAddress resolveProxy() {
        String host = firstNonBlank(
                options.getProxyHost(),
                System.getProperty("https.proxyHost"),
                System.getProperty("http.proxyHost"));
        if (host == null) {
            return null;
        }
        int port = options.getProxyPort() > 0
                ? options.getProxyPort()
                : intProperty("https.proxyPort", intProperty("http.proxyPort", 80));
        return new ProxyAddress(host, port);
    }

    private void copyResponseBody(HttpResponse<InputStream> response, Path partial) throws IOException {
        OptionalLong contentLength = response.headers().firstValueAsLong("content-length");
        long expected = contentLength.orElse(-1);
        long total = 0;
        long lastLogged = 0;
        boolean firstReadLogged = false;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(partial)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (!firstReadLogged) {
                    debug("first body bytes=" + read);
                    firstReadLogged = true;
                }
                out.write(buffer, 0, read);
                total += read;
                if (isDebugEnabled() && total - lastLogged >= 5L * 1024L * 1024L) {
                    lastLogged = total;
                    debug("downloaded " + total + (expected > 0 ? "/" + expected : "") + " bytes");
                }
            }
        }
        debug("body complete bytes=" + total + (expected > 0 ? ", expected=" + expected : ""));
    }

    private void verifyArchiveIfNeeded(Path archive, PandocBinaryManifest.Entry entry) throws IOException {
        if (!options.isVerifyChecksum()) {
            return;
        }
        debug("verifying sha256: " + archive);
        String actual = sha256(archive);
        if (!entry.sha256().equalsIgnoreCase(actual)) {
            Files.deleteIfExists(archive);
            throw new PandocBinaryException(
                    "Checksum mismatch for " + archive.getFileName()
                            + ": expected " + entry.sha256() + ", actual " + actual);
        }
        debug("sha256 verified: " + actual);
    }

    private Path installArchive(Path cacheRoot,
                                Path installDir,
                                Path archive,
                                PandocBinaryManifest.Entry entry,
                                Platform platform) throws IOException {
        Path extractRoot = cacheRoot.resolve("pandoc");
        Files.createDirectories(extractRoot);
        Path tempDir = Files.createTempDirectory(extractRoot, entry.version() + "-" + platform.key() + "-");
        Path executable = null;
        try {
            debug("extracting " + archive + " to " + tempDir);
            extract(archive, tempDir);
            executable = findExecutable(tempDir, platform);
            if (!platform.windows()) {
                executable.toFile().setExecutable(true, false);
            }
            if (!versionMatches(executable, entry.version())) {
                throw new PandocBinaryException(
                        "Downloaded Pandoc executable does not report expected version "
                                + entry.version() + ": " + executable);
            }

            Path relativeExecutable = tempDir.relativize(executable);
            if (Files.exists(installDir)) {
                deleteRecursively(installDir);
            }
            Files.createDirectories(installDir.getParent());
            moveReplacing(tempDir, installDir);
            Path finalExecutable = installDir.resolve(relativeExecutable).normalize();
            writeInstallMetadata(installDir, relativeExecutable, entry, downloadUri(entry));
            debug("installed pandoc binary: " + finalExecutable);
            return finalExecutable;
        } finally {
            if (Files.exists(tempDir)) {
                deleteRecursively(tempDir);
            }
        }
    }

    private static void extract(Path archive, Path destination) throws IOException {
        String fileName = archive.getFileName().toString();
        if (fileName.endsWith(".zip")) {
            extractZip(archive, destination);
        } else if (fileName.endsWith(".tar.gz")) {
            extractTarGz(archive, destination);
        } else {
            throw new PandocBinaryException("Unsupported Pandoc archive type: " + archive);
        }
    }

    private static void extractZip(Path archive, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = safeResolve(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static void extractTarGz(Path archive, Path destination) throws IOException {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            byte[] header = new byte[512];
            while (readHeader(in, header)) {
                if (isZeroBlock(header)) {
                    break;
                }

                String name = tarName(header);
                long size = parseOctal(header, 124, 12);
                char type = (char) header[156];
                Path target = safeResolve(destination, name);

                if (type == '5') {
                    Files.createDirectories(target);
                    skipTarPayload(in, size);
                } else if (type == '0' || type == '\0') {
                    Files.createDirectories(target.getParent());
                    copyExact(in, target, size);
                    skipPadding(in, size);
                } else {
                    skipTarPayload(in, size);
                }
            }
        }
    }

    private static boolean readHeader(InputStream in, byte[] header) throws IOException {
        int offset = 0;
        while (offset < header.length) {
            int read = in.read(header, offset, header.length - offset);
            if (read < 0) {
                if (offset == 0) {
                    return false;
                }
                throw new IOException("Unexpected EOF while reading tar header");
            }
            offset += read;
        }
        return true;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String tarName(byte[] header) {
        String name = extractString(header, 0, 100);
        String prefix = extractString(header, 345, 155);
        if (!prefix.isBlank()) {
            return prefix + "/" + name;
        }
        return name;
    }

    private static String extractString(byte[] bytes, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] bytes, int offset, int length) {
        String value = extractString(bytes, offset, length).trim();
        if (value.isBlank()) {
            return 0;
        }
        return Long.parseLong(value, 8);
    }

    private static void copyExact(InputStream in, Path target, long size) throws IOException {
        try (var out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("Unexpected EOF while extracting " + target);
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static void skipTarPayload(InputStream in, long size) throws IOException {
        in.skipNBytes(size);
        skipPadding(in, size);
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long padding = (512 - (size % 512)) % 512;
        if (padding > 0) {
            in.skipNBytes(padding);
        }
    }

    private static Path safeResolve(Path destination, String entryName) {
        Path target = destination.resolve(entryName).normalize();
        if (!target.startsWith(destination.normalize())) {
            throw new PandocBinaryException("Archive entry escapes target directory: " + entryName);
        }
        return target;
    }

    private static Path findExecutable(Path root, Platform platform) throws IOException {
        try (var paths = Files.walk(root)) {
            List<Path> candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> platform.executableName().equals(path.getFileName().toString()))
                    .sorted(Comparator.comparingInt(path -> path.toString().contains("bin") ? 0 : 1))
                    .toList();
            if (candidates.isEmpty()) {
                throw new PandocBinaryException("Downloaded archive does not contain " + platform.executableName());
            }
            return candidates.get(0);
        }
    }

    private static boolean versionMatches(Path executable, String expectedVersion) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String firstLine = out.lines().findFirst().orElse("");
            return process.exitValue() == 0 && firstLine.contains(expectedVersion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new PandocBinaryException("SHA-256 digest is not available", e);
        }
    }

    private static void writeInstallMetadata(Path installDir,
                                             Path relativeExecutable,
                                             PandocBinaryManifest.Entry entry,
                                             URI sourceUrl) throws IOException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("version", entry.version());
        node.put("platform", entry.platformKey());
        node.put("asset", entry.asset());
        node.put("sha256", entry.sha256());
        node.put("sourceUrl", sourceUrl.toString());
        node.put("executable", relativeExecutable.toString().replace('\\', '/'));
        node.put("downloadedAt", Instant.now().toString());
        Files.writeString(installDir.resolve("install.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node),
                StandardCharsets.UTF_8);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException e) {
                    throw new PandocBinaryException("Failed to delete " + candidate, e);
                }
            });
        }
    }

    private void debug(String message) {
        if (isDebugEnabled()) {
            System.err.println("[pandoc4j-binary] " + message);
        }
    }

    private boolean isDebugEnabled() {
        return options.isDebug() || Boolean.getBoolean("pandoc.binary.debug");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int intProperty(String property, int defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record ProxyAddress(String host, int port) {}
}
