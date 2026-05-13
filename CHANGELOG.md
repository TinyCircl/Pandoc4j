# Changelog

## 0.3.0 - 2026-05-13

### Highlights

- Converted the project into a Maven reactor with separate `pandoc4j` and `pandoc4j-binary` modules
- Added `PandocInstallationProvider` so optional companion artifacts can provide managed Pandoc installations without making the core wrapper download anything
- Added initial `pandoc4j-binary` support for official Pandoc archive download, SHA-256 verification, cache locking, extraction, ServiceLoader integration, and Spring Boot auto-configuration
- Added explicit `pandoc.binary.proxyHost` / `pandoc.binary.proxyPort` and `pandoc.binary.debug` controls for managed binary downloads

### Compatibility

- `org.tinycircl:pandoc4j` remains the wrapper-only artifact and keeps the existing public API
- Explicit `pandoc.path`, `PANDOC_PATH`, and Spring `pandoc.executable-path` settings remain higher priority than managed binary resolution

## 0.2.2 - 2026-03-30

### Highlights

- Added `ConversionRequest.Builder.withTimeoutSeconds(long)` for per-request Pandoc timeout overrides
- Propagated Spring Boot's `pandoc.timeout-seconds` setting into `PandocClient.builder()`
- Added regression coverage for timeout propagation in Spring auto-configuration

### Upgrade Notes

- Patch release – fully backwards-compatible with `0.2.1`
- Existing `Pandoc4j` and `PandocClient` call sites continue to work unchanged
- Use `.withTimeoutSeconds(...)` only when a specific conversion needs a timeout different from the default/client-level configuration

### Compatibility

- Java baseline remains `21`
- Maven coordinates remain `org.tinycircl:pandoc4j`
- Default timeout remains `120` seconds unless overridden

## 0.2.0 - 2026-03-19

### Highlights

- Upgraded the AST layer from Jackson 2 to Jackson 3.1.0
- Standardized the public Java package to `org.tinycircl.pandoc4j`
- Moved `spring-boot-configuration-processor` out of published dependencies and into compile-time annotation processor configuration

### Upgrade Notes

- Update Java imports from legacy package names to `org.tinycircl.pandoc4j.*`
- If your code interacts with `PandocDocument`, `Block`, `Inline`, or `PandocAstMapper`, update Jackson imports from `com.fasterxml.jackson.*` to `tools.jackson.*`
- Maven coordinates remain the same apart from the version bump:
  `org.tinycircl:pandoc4j:0.2.0`

### Compatibility

- Java baseline remains `21`
- Spring Boot auto-configuration support remains available
- Existing consumers of the high-level conversion APIs should need minimal code changes unless they depend on legacy package names or Jackson 2 AST types
