package org.tinycircl.pandoc4j.binary;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

final class PandocBinaryManifest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String RESOURCE = "/org/tinycircl/pandoc4j/binary/pandoc-binaries.json";

    private final JsonNode root;
    private final String defaultVersion;

    private PandocBinaryManifest(JsonNode root, String defaultVersion) {
        this.root = root;
        this.defaultVersion = defaultVersion;
    }

    static PandocBinaryManifest load() {
        try (InputStream in = PandocBinaryManifest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new PandocBinaryException("Missing bundled Pandoc binary manifest: " + RESOURCE);
            }
            JsonNode root = MAPPER.readTree(in);
            String defaultVersion = root.path("defaultVersion").asString();
            if (defaultVersion == null || defaultVersion.isBlank()) {
                throw new PandocBinaryException("Pandoc binary manifest is missing defaultVersion");
            }
            return new PandocBinaryManifest(root, defaultVersion);
        } catch (JacksonException e) {
            throw new PandocBinaryException("Failed to parse Pandoc binary manifest: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new PandocBinaryException("Failed to read Pandoc binary manifest: " + e.getMessage(), e);
        }
    }

    String defaultVersion() {
        return defaultVersion;
    }

    Entry entry(String version, String platformKey) {
        JsonNode node = root.path("versions").path(version).path(platformKey);
        if (node == null || node.isMissingNode()) {
            throw new PandocBinaryException(
                    "Pandoc binary manifest does not include version=" + version
                            + " for platform=" + platformKey);
        }
        String asset = node.path("asset").asString();
        String sha256 = node.path("sha256").asString();
        if (asset == null || asset.isBlank() || sha256 == null || sha256.isBlank()) {
            throw new PandocBinaryException(
                    "Pandoc binary manifest entry is incomplete for version=" + version
                            + ", platform=" + platformKey);
        }
        return new Entry(version, platformKey, asset, sha256);
    }

    record Entry(String version, String platformKey, String asset, String sha256) {}
}
