# Phase 4 Design: pandoc4j-binary

This document defines the Phase 4 design for `org.tinycircl:pandoc4j-binary`:
a zero-install companion artifact that resolves a managed Pandoc executable for
applications that do not want to install Pandoc at the OS level.

## Goals

- Keep `org.tinycircl:pandoc4j` as the lightweight wrapper-only artifact.
- Let users add `org.tinycircl:pandoc4j-binary` and keep using the existing
  `Pandoc4j`, `ConversionRequest`, and Spring `PandocClient` APIs.
- Download only official Pandoc release archives, cache them outside the
  project, verify checksums, and run the extracted `pandoc` executable.
- Avoid admin privileges, installers, PATH mutation, and platform package
  managers.
- Make downloads deterministic: no "latest at runtime" lookup by default.
- Preserve explicit user configuration: `pandoc.path`, `PANDOC_PATH`, and
  Spring `pandoc.executable-path` must remain hard overrides.

## Non-Goals

- Do not bundle native Pandoc binaries into the Maven jar in the first version.
  The companion artifact provides managed download and cache logic.
- Do not install `.msi`, `.pkg`, `.deb`, Homebrew, apt, winget, or any
  OS-level package.
- Do not support every Pandoc release asset on day one. Unsupported platforms
  should fail with a clear error.
- Do not update Pandoc automatically to the newest upstream release at runtime.

## Current Project Boundary

The current library has one core resolution path:

1. `PandocInstallation.detect()`
2. `PandocExecutor`
3. `ConversionRequest` / `Pandoc4j`
4. `PandocAutoConfiguration` / `PandocClient`

`PandocExecutor` already depends only on a `PandocInstallation`, so Phase 4 does
not need to change conversion execution. The missing extension point is binary
resolution: today `PandocInstallation.detect()` can only inspect local system
locations and PATH.

## Artifact Layout

Move the repository to a Maven reactor while preserving published coordinates:

```text
pom.xml                         # parent, packaging=pom
pandoc4j/
  pom.xml                       # artifactId=pandoc4j, current source tree
pandoc4j-binary/
  pom.xml                       # artifactId=pandoc4j-binary
  src/main/java/...
  src/main/resources/...
```

The existing public artifact remains:

```xml
<dependency>
  <groupId>org.tinycircl</groupId>
  <artifactId>pandoc4j</artifactId>
  <version>...</version>
</dependency>
```

The zero-install artifact is:

```xml
<dependency>
  <groupId>org.tinycircl</groupId>
  <artifactId>pandoc4j-binary</artifactId>
  <version>...</version>
</dependency>
```

`pandoc4j-binary` depends on `pandoc4j`. Users should not need to declare both
unless their build tool does not pull transitive dependencies.

## Core SPI Change

Add a tiny provider SPI to the core artifact:

```java
package org.tinycircl.pandoc4j.core;

import java.util.Optional;

public interface PandocInstallationProvider {
    Optional<PandocInstallation> resolve();
}
```

Update `PandocInstallation.detect()` to use this order:

1. System property `pandoc.path`
2. Environment variable `PANDOC_PATH`
3. `ServiceLoader<PandocInstallationProvider>` providers
4. Common OS installation locations
5. `pandoc` / `pandoc.exe` on PATH

This makes `pandoc4j-binary` drop-in for the static APIs while still letting
explicit paths win. If a user keeps `pandoc4j-binary` on the classpath but wants
system PATH resolution, they can disable the binary provider.

Also add `PandocInstallation.detectLocal()` for framework integrations that
must explicitly skip providers and use only explicit paths, common install
locations, and PATH.

## pandoc4j-binary Public API

Expose one small entry point for users who want direct control:

```java
package org.tinycircl.pandoc4j.binary;

public final class PandocBinary {
    public static PandocInstallation getInstallation();
    public static PandocInstallation getInstallation(PandocBinaryOptions options);
    public static Path getExecutablePath();
    public static Path getExecutablePath(PandocBinaryOptions options);
    public static void clearCache(PandocBinaryOptions options);
}
```

Options:

```java
PandocBinaryOptions options = PandocBinaryOptions.builder()
    .version("3.9.0.2")
    .cacheDirectory(Path.of("/var/cache/pandoc4j"))
    .baseUrl(URI.create("https://github.com/jgm/pandoc/releases/download"))
    .offline(false)
    .forceDownload(false)
    .verifyChecksum(true)
    .build();
```

The default `PandocInstallationProvider` delegates to `PandocBinary` using
system properties and environment variables.

## Spring Boot Integration

Add `PandocBinaryAutoConfiguration` in the binary artifact:

```java
@AutoConfiguration(before = PandocAutoConfiguration.class)
@ConditionalOnClass(Pandoc4j.class)
@ConditionalOnProperty(prefix = "pandoc.binary", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PandocBinaryProperties.class)
public class PandocBinaryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @Conditional(NoExplicitPandocExecutablePathCondition.class)
    PandocInstallation pandocInstallation(PandocBinaryProperties properties,
                                          Environment environment) {
        return PandocBinary.getInstallation(properties.toOptions());
    }
}
```

`NoExplicitPandocExecutablePathCondition` should check `pandoc.executable-path`
and skip the binary bean when that property is present. Then the existing core
auto-configuration owns explicit path validation.

Spring properties:

```yaml
pandoc:
  executable-path: /usr/local/bin/pandoc  # still wins when set
  timeout-seconds: 120
  binary:
    enabled: true
    version: 3.9.0.2
    cache-dir: ~/.cache/pandoc4j
    base-url: https://github.com/jgm/pandoc/releases/download
    offline: false
    force-download: false
    verify-checksum: true
    proxy-host: 127.0.0.1
    proxy-port: 7890
    debug: false
```

If the binary artifact is present and enabled, download/cache failures should be
fail-fast with an actionable `PandocBinaryException`. Users can opt back into
the current optional-startup behavior with `pandoc.binary.enabled=false`.

When `pandoc.binary.enabled=false`, core Spring auto-configuration should call
`PandocInstallation.detectLocal()` so the ServiceLoader provider from
`pandoc4j-binary` does not accidentally download Pandoc anyway.

## Platform Support

Initial support should use portable archives only:

| OS | Arch | Asset |
|---|---|---|
| Windows | x86_64 / amd64 | `pandoc-{version}-windows-x86_64.zip` |
| macOS | aarch64 / arm64 | `pandoc-{version}-arm64-macOS.zip` |
| macOS | x86_64 / amd64 | `pandoc-{version}-x86_64-macOS.zip` |
| Linux | x86_64 / amd64 | `pandoc-{version}-linux-amd64.tar.gz` |
| Linux | aarch64 / arm64 | `pandoc-{version}-linux-arm64.tar.gz` |

MSI, PKG, and DEB assets are intentionally not used because they are installers
or system packages.

The resolver should normalize Java runtime values:

- `os.name`: `Windows`, `Mac OS X`, `Linux`
- `os.arch`: `amd64`, `x86_64`, `aarch64`, `arm64`

Unsupported combinations throw:

```text
Pandoc binary is not available for os=<os.name>, arch=<os.arch>.
Set pandoc.path/PANDOC_PATH, install Pandoc manually, or disable pandoc.binary.
```

## Manifest and Checksums

Ship an embedded manifest in `pandoc4j-binary`:

```json
{
  "defaultVersion": "3.9.0.2",
  "versions": {
    "3.9.0.2": {
      "windows-x86_64": {
        "asset": "pandoc-3.9.0.2-windows-x86_64.zip",
        "sha256": "c97542f2800f446e788d9f74237856d995421ad1bb3cc8324286840c5f272d3a"
      },
      "linux-x86_64": {
        "asset": "pandoc-3.9.0.2-linux-amd64.tar.gz",
        "sha256": "a69abfababda8a56969a254b09f9553a7be89ddec00d4e0fe9fd585d71a67508"
      },
      "linux-arm64": {
        "asset": "pandoc-3.9.0.2-linux-arm64.tar.gz",
        "sha256": "b6d21e8f9c3b15744f5a7ab40248019157ed7793875dbe0383d4c82ff572b528"
      },
      "macos-arm64": {
        "asset": "pandoc-3.9.0.2-arm64-macOS.zip",
        "sha256": "6e9eca844076bcbb599bbeebbba78a70f93b5307782b85c2c272872812c88875"
      },
      "macos-x86_64": {
        "asset": "pandoc-3.9.0.2-x86_64-macOS.zip",
        "sha256": "b9fbceabccbc8f34ac021a50483fc32f8160568d0b4b2c22d81bb29e3054fd82"
      }
    }
  }
}
```

Rules:

- Default runtime behavior uses the embedded manifest only.
- If `version` is overridden, the version must exist in the manifest unless the
  user also supplies an explicit URL and checksum.
- `verifyChecksum=true` is the default and should not be silently bypassed.
- A checksum mismatch deletes the partial download and fails.

## Cache Layout

Default cache root:

- Windows: `%LOCALAPPDATA%\pandoc4j\cache`
- macOS/Linux: `${user.home}/.cache/pandoc4j`
- Fallback: `${user.home}/.pandoc4j/cache`

Install layout:

```text
<cache-root>/
  pandoc/
    3.9.0.2/
      windows-x86_64/
        install.json
        bin/pandoc.exe
      linux-x86_64/
        install.json
        bin/pandoc
  downloads/
    pandoc-3.9.0.2-windows-x86_64.zip
    *.partial
  locks/
    pandoc-3.9.0.2-windows-x86_64.lock
```

`install.json` records:

- Pandoc version
- Platform key
- Asset name
- SHA-256
- Source URL
- Executable relative path
- Download timestamp

Cache hit validation:

1. `install.json` exists.
2. Executable exists and is executable.
3. `pandoc --version` first line contains the expected version.

## Download and Extraction Flow

1. Resolve platform and manifest entry.
2. Acquire a cross-process `FileChannel.lock()` for version/platform.
3. Re-check cache after acquiring the lock.
4. If `offline=true` and cache miss, fail without network access.
5. Download to `downloads/<asset>.partial` with `java.net.http.HttpClient`.
6. Verify SHA-256 before extraction.
7. Extract into a unique temp directory under the cache root.
8. Protect against Zip Slip by verifying every extracted path stays inside the
   temp extraction directory.
9. Find the executable under the extracted tree, preferring `bin/pandoc(.exe)`.
10. Set executable permissions on Unix.
11. Run `pandoc --version` and verify the expected version.
12. Atomically move the temp directory to the final install directory.
13. Write `install.json` last.

Do not execute anything from the archive before checksum verification completes.

## System Properties and Environment Variables

Non-Spring runtime knobs:

| Java system property | Environment variable | Meaning |
|---|---|---|
| `pandoc.binary.enabled` | `PANDOC4J_BINARY_ENABLED` | Enable provider, default `true` |
| `pandoc.binary.version` | `PANDOC4J_BINARY_VERSION` | Managed Pandoc version |
| `pandoc.binary.cacheDir` | `PANDOC4J_BINARY_CACHE_DIR` | Cache root |
| `pandoc.binary.baseUrl` | `PANDOC4J_BINARY_BASE_URL` | Release download base URL |
| `pandoc.binary.offline` | `PANDOC4J_BINARY_OFFLINE` | Cache-only mode |
| `pandoc.binary.forceDownload` | `PANDOC4J_BINARY_FORCE_DOWNLOAD` | Ignore existing cache |
| `pandoc.binary.verifyChecksum` | `PANDOC4J_BINARY_VERIFY_CHECKSUM` | Verify archive SHA-256 |
| `pandoc.binary.proxyHost` | `PANDOC4J_BINARY_PROXY_HOST` | Explicit HTTP(S) proxy host |
| `pandoc.binary.proxyPort` | `PANDOC4J_BINARY_PROXY_PORT` | Explicit HTTP(S) proxy port |
| `pandoc.binary.debug` | `PANDOC4J_BINARY_DEBUG` | Print resolver/download phase logs |

Explicit path overrides remain in the core artifact:

- `pandoc.path`
- `PANDOC_PATH`
- Spring `pandoc.executable-path`

Standard JVM proxy settings such as `https.proxyHost` and `https.proxyPort`
are also honored. This matters on Windows systems where browsers and
PowerShell may use the system proxy, but Java test JVMs may otherwise attempt a
slow direct connection to GitHub release assets.

## Security Notes

- Use HTTPS by default.
- Verify SHA-256 before extraction.
- Reject archives with absolute paths or `..` traversal.
- Never add the managed directory to PATH globally.
- Never run platform installers.
- Surface source URL and checksum in exceptions/logs for auditability.
- Keep the manifest in source control so release bumps are reviewable.

## Upstream References

- Official Pandoc releases:
  <https://github.com/jgm/pandoc/releases>
- Latest release metadata API, including asset names and SHA-256 digests:
  <https://api.github.com/repos/jgm/pandoc/releases/latest>
- Pandoc code-signing policy:
  <https://pandoc.org/code-signing-policy.html>

## Test Plan

Unit tests in `pandoc4j`:

- `PandocInstallation.detect()` respects explicit system property/env before SPI.
- ServiceLoader provider is consulted before common locations/PATH.
- Provider exceptions are wrapped with actionable context.

Unit tests in `pandoc4j-binary`:

- Platform normalization for Windows/macOS/Linux and x86_64/arm64 aliases.
- Asset name selection from manifest.
- Unsupported platform failure message.
- Cache hit skips download.
- Offline cache miss fails without opening a network connection.
- Corrupted checksum deletes partial archive and fails.
- Zip Slip entries are rejected.
- Concurrent resolver calls share one lock and one install directory.
- `forceDownload=true` refreshes the cache.

Integration tests:

- Tag networked tests with `@Tag("binary-download")`.
- Use a temporary cache directory.
- Download the manifest default version for the current CI platform.
- Run `Pandoc4j.convertText("# Hi", Format.MARKDOWN, Format.HTML5)` without
  a system Pandoc dependency.
- Add a Spring `ApplicationContextRunner` test proving `PandocClient` is created
  when only `pandoc4j-binary` is present.

## Implementation Milestones

1. [x] Convert repository to a Maven reactor while preserving
   `org.tinycircl:pandoc4j` coordinates.
2. [x] Add the core `PandocInstallationProvider` SPI and tests.
3. [x] Add `pandoc4j-binary` module with manifest parsing, platform resolution,
   cache validation, download, checksum verification, extraction, and locking.
4. [x] Add the ServiceLoader provider for non-Spring usage.
5. [x] Add Spring Boot binary auto-configuration and metadata.
6. [x] Update README install snippets, discovery order, and release docs.
7. [ ] Run `binary-download` integration tests in an
   opt-in CI job or release verification job.

## Open Decisions

- Whether `pandoc4j-binary` version should release as `0.3.0` together with
  `pandoc4j`, or start as `0.1.0` while depending on `pandoc4j:0.3.0`.
  Recommendation: keep versions aligned across the reactor.
- Whether Maven Central publication should deploy both artifacts in one release.
  Recommendation: yes, publish `pandoc4j` and `pandoc4j-binary` together.
- Whether to support Linux musl/Alpine in Phase 4. Recommendation: defer until
  there is a verified upstream asset or documented compatibility path.
