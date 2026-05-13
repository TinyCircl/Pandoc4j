# pandoc4j

> A modern JVM/Java wrapper for [Pandoc](https://pandoc.org) – the universal document converter.
> Convert between 40+ document formats with type-safe Java APIs, Spring Boot auto-configuration,
> and programmatic AST manipulation.

[![Java](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![License](https://img.shields.io/badge/License-Apache_2.0-green)](LICENSE)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-org.tinycircl%3Apandoc4j-blue)](https://central.sonatype.com/artifact/org.tinycircl/pandoc4j)

---

## Packages

pandoc4j follows the same two-package strategy as [pypandoc](https://github.com/JessicaTegner/pypandoc):

| Package | Description | Pandoc Required |
|---|---|---|
| `pandoc4j` | Wrapper only – use your own Pandoc installation | Yes |
| `pandoc4j-binary` | Auto-downloads a managed Pandoc binary on first use | No |

---

## Requirements

- **Java 21+**
- **Pandoc** installed on the machine when using wrapper-only `pandoc4j`
- No OS-level Pandoc installation is required when using `pandoc4j-binary`

### Installing Pandoc

| Platform | Command |
|---|---|
| Windows | `winget install JohnMacFarlane.Pandoc` |
| macOS | `brew install pandoc` |
| Ubuntu/Debian | `apt install pandoc` |
| Any platform | Download from [github.com/jgm/pandoc/releases](https://github.com/jgm/pandoc/releases) |

### Using with Docker

```dockerfile
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y pandoc && rm -rf /var/lib/apt/lists/*
COPY target/myapp.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## Installation

Official releases are published to Maven Central.

**Maven**
```xml
<dependency>
    <groupId>org.tinycircl</groupId>
    <artifactId>pandoc4j</artifactId>
    <version>0.3.0</version>
</dependency>
```

**Gradle**
```groovy
implementation 'org.tinycircl:pandoc4j:0.3.0'
```

**Zero-install Maven**
```xml
<dependency>
    <groupId>org.tinycircl</groupId>
    <artifactId>pandoc4j-binary</artifactId>
    <version>0.3.0</version>
</dependency>
```

**Zero-install Gradle**
```groovy
implementation 'org.tinycircl:pandoc4j-binary:0.3.0'
```

`pandoc4j-binary` depends on `pandoc4j`, so application code can keep using
the same `Pandoc4j` and `PandocClient` APIs.

When Java does not automatically use your local system proxy, pass standard JVM
proxy properties or configure `pandoc.binary.proxy-host` / `proxy-port`:

```powershell
.\mvnw.cmd -pl pandoc4j-binary -am `
  "-Dtest=PandocBinaryDownloadIT" `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-Dhttps.proxyHost=127.0.0.1" `
  "-Dhttps.proxyPort=7890" `
  "-Dpandoc.binary.debug=true" `
  test
```

---

## Upgrade Notes

### Upgrading to 0.3.0

`pandoc4j` remains wrapper-only and backwards-compatible with `0.2.2`.

- Added the optional `pandoc4j-binary` artifact for zero-install Pandoc resolution
- Converted the repository into a Maven reactor with separate `pandoc4j` and `pandoc4j-binary` modules
- Added `PandocInstallationProvider` extension points for managed binary providers
- Added proxy and debug controls for GitHub release asset downloads

### Upgrading to 0.2.2

Patch release – fully backwards-compatible with 0.2.1.

- Added `ConversionRequest.Builder.withTimeoutSeconds(long)` for per-conversion timeout overrides
- `PandocClient.builder()` now inherits Spring Boot's `pandoc.timeout-seconds` setting
- Default timeout remains `120` seconds; non-positive timeout values fall back to the default

### Upgrading to 0.2.1

Patch release – fully backwards-compatible with 0.2.0.

- Added `MarkdownCleaner` utility class for post-processing Pandoc Markdown output
- Added `ConversionRequest.Builder.cleanMarkdown()` for inline post-processing in the conversion chain
- Bug: Pandoc preserves multi-line accessibility alt-text from PPTX slides verbatim, breaking `![alt](url)` syntax → fixed by `MarkdownCleaner`

### Upgrading to 0.2.0

This release includes a small but important compatibility update for consumers who use the AST API directly:

- The public Java package is now `org.tinycircl.pandoc4j`
- The AST layer now uses Jackson 3.1.0 types from `tools.jackson.*`
- `spring-boot-configuration-processor` is no longer published as a regular dependency; it now runs only as a compile-time annotation processor

If you are upgrading from `0.1.0`, update your imports accordingly:

```java
import org.tinycircl.pandoc4j.Pandoc4j;
import org.tinycircl.pandoc4j.ast.PandocDocument;
import tools.jackson.databind.JsonNode;
```

For a concise release summary, see [`CHANGELOG.md`](CHANGELOG.md).

---

## Quick Start

### Text Conversion

```java
// Markdown → HTML5
String html = Pandoc4j.convertText("# Hello", Format.MARKDOWN, Format.HTML5);

// Markdown → PDF (requires LaTeX)
String latex = Pandoc4j.convertText("# Title\n\nContent.", Format.MARKDOWN, Format.LATEX);
```

### File Conversion

```java
// Infer input format from file extension
String md = Pandoc4j.convertFile("report.docx", Format.MARKDOWN);

// Explicit input format
String html = Pandoc4j.convertFile(Path.of("slides.pptx"), Format.PPTX, Format.HTML5);
```

### Builder API (Advanced Options)

```java
String result = Pandoc4j.builder()
    .from(Format.DOCX)
    .to(Format.MARKDOWN_GFM)
    .wrapNone()
    .extractMedia("/output/media")
    .convertFile(Path.of("report.docx"));

// PPTX → Markdown with post-processing (fix broken alt-text, collapse extra blank lines)
String slides = Pandoc4j.builder()
    .from(Format.PPTX)
    .to(Format.MARKDOWN_GFM)
    .wrapNone()
    .extractMedia("/output/media")
    .cleanMarkdown()                      // applies MarkdownCleaner defaults
    .convertFile(Path.of("slides.pptx"));

// Shift heading levels, add table of contents
String standalone = Pandoc4j.builder()
    .from(Format.MARKDOWN)
    .to(Format.HTML5)
    .standalone()
    .tableOfContents()
    .shiftHeadingLevelBy(1)
    .convertText(markdownContent);

// Override timeout for one slow conversion
String html = Pandoc4j.builder()
    .from(Format.MARKDOWN)
    .to(Format.HTML5)
    .withTimeoutSeconds(30)
    .convertText(largeMarkdown);

// Raw argument passthrough (forward-compatible with new Pandoc versions)
String out = Pandoc4j.builder()
    .from(Format.MARKDOWN)
    .to(Format.HTML5)
    .arg("--highlight-style=tango", "--number-sections")
    .convertText(content);
```

---

## Markdown Post-Processing

`MarkdownCleaner` sanitises Markdown output from Pandoc. Use it standalone or chain it directly
into the builder with `.cleanMarkdown()`.

### Problem this solves

Pandoc preserves accessibility metadata from source documents (e.g. PowerPoint alt-text) verbatim.
When that metadata contains newlines the resulting Markdown is syntactically broken:

```markdown
<!-- broken (alt-text has embedded newline) -->
![文本, 信件
描述已自动生成](ppt/media/image1.png "内容占位符 4")

<!-- fixed -->
![文本, 信件 描述已自动生成](ppt/media/image1.png "内容占位符 4")
```

### Usage

```java
// Option 1 – standalone, all defaults
String clean = MarkdownCleaner.clean(pandocOutput);

// Option 2 – inline with the conversion builder
String clean = Pandoc4j.builder()
        .from(Format.PPTX)
        .to(Format.MARKDOWN_GFM)
        .wrapNone()
        .cleanMarkdown()
        .convertFile(path);

// Option 3 – fine-grained control
String clean = MarkdownCleaner.builder()
        .fixInlineNewlines(true)          // collapse \n inside ![alt](url) and [text](url)
        .collapseBlankLines(true)         // reduce excessive blank lines
        .maxConsecutiveBlankLines(1)      // default: 2
        .clean(pandocOutput);
```

### Built-in rules

| Rule | Default | Description |
|---|---|---|
| `fixInlineNewlines` | on | Collapses embedded `\r`/`\n` in image alt-text and hyperlink text into a single space |
| `collapseBlankLines` | on | Reduces runs of more than N consecutive blank lines (default N=2) |

---

## Spring Boot

Zero-configuration auto-wiring. Add the dependency and inject `PandocClient`:

### Configuration (`application.yml`)

```yaml
pandoc:
  executable-path: /usr/local/bin/pandoc   # optional – auto-detected if omitted
  timeout-seconds: 60                       # optional – default: 120
  binary:
    enabled: true                           # only active when pandoc4j-binary is on the classpath
    version: 3.9.0.2                         # optional – bundled manifest default
    cache-dir: ~/.cache/pandoc4j             # optional
    offline: false                           # optional – use cache only
    verify-checksum: true                    # optional – default: true
    proxy-host: 127.0.0.1                     # optional – useful when Java does not use system proxy
    proxy-port: 7890                          # optional
    debug: false                              # optional – log download/cache phases
```

### Usage

```java
@Service
public class DocumentService {

    private final PandocClient pandoc;

    public DocumentService(PandocClient pandoc) {
        this.pandoc = pandoc;
    }

    public String toHtml(String markdown) {
        return pandoc.convertText(markdown, Format.MARKDOWN, Format.HTML5);
    }

    public String toMarkdown(Path docxFile) {
        return pandoc.convertFile(docxFile, Format.MARKDOWN);
    }

    public String advancedConvert(Path input) {
        return pandoc.builder()           // inherits pandoc.timeout-seconds by default
            .to(Format.MARKDOWN_GFM)
            .wrapNone()
            .convertFile(input);
    }
}
```

### Override Beans

```java
@Bean
public PandocInstallation pandocInstallation() {
    return PandocInstallation.at(Path.of("/custom/path/to/pandoc"));
}
```

---

## Pandoc JSON AST

Convert documents into a type-safe Java object model, manipulate programmatically, then render to any output format.

```
Input File/Text
    ↓  pandoc --to=json
Pandoc JSON
    ↓  PandocAstMapper.parse()
PandocDocument  ← Java objects you can inspect and modify
    ↓  PandocAstMapper.serialize()
Pandoc JSON
    ↓  pandoc --from=json --to=<format>
HTML / Markdown / DOCX / ...
```

### Parse to AST

```java
PandocDocument doc = Pandoc4j.toAst("# Title\n\nParagraph with **bold**.", Format.MARKDOWN);

System.out.println(doc.apiVersionString()); // "1.23.1"
System.out.println(doc.headings());         // ["Title"]
```

### Traverse with Java 21 Pattern Matching

```java
for (Block block : doc.blocks()) {
    switch (block) {
        case Block.Header h    -> System.out.println("H" + h.level() + ": " + inlineText(h.inlines()));
        case Block.Para p      -> System.out.println("Para: " + inlineText(p.inlines()));
        case Block.CodeBlock c -> System.out.println("[" + c.attr().classes() + "] " + c.text());
        case Block.BulletList l -> System.out.println("List with " + l.items().size() + " items");
        case Block.Unknown u   -> System.out.println("Unknown node: " + u.type()); // forward-compatible
        default -> {}
    }
}
```

### Programmatic AST Manipulation

```java
PandocDocument doc = Pandoc4j.toAst(inputMarkdown, Format.MARKDOWN);

// Inject a new paragraph at the beginning
List<Block> modified = new ArrayList<>(doc.blocks());
modified.add(0, new Block.Para(List.of(
    new Inline.Strong(List.of(new Inline.Str("Auto-generated content"))),
    new Inline.Str(" – generated at " + LocalDate.now())
)));

PandocDocument newDoc = new PandocDocument(
    doc.apiVersion(), doc.meta(), modified, null);

// Render to HTML
String html = Pandoc4j.fromAst(newDoc, Format.HTML5);
```

---

## Concurrent Safety

Every conversion runs in an isolated temporary working directory that is automatically deleted after the call. This prevents concurrent requests from interfering with each other (e.g. via `--extract-media` side-effects).

```java
// Thread-safe – safe to call from multiple request threads simultaneously
ExecutorService pool = Executors.newFixedThreadPool(20);
List<Future<String>> futures = tasks.stream()
    .map(task -> pool.submit(() -> Pandoc4j.convertFile(task.file(), Format.HTML5)))
    .toList();
```

---

## Pandoc Discovery

The library resolves the Pandoc binary in this order:

1. `pandoc.executable-path` Spring Boot property / `pandoc.path` system property
2. `PANDOC_PATH` environment variable
3. `PandocInstallationProvider` implementations on the classpath, such as `pandoc4j-binary`
4. Common OS installation directories (`/usr/local/bin`, `%LOCALAPPDATA%\Pandoc\`, etc.)
5. `pandoc` / `pandoc.exe` on the system `PATH`

---

## Project Architecture

```
pandoc4j-parent
├── pandoc4j/
│   ├── Pandoc4j.java                         ← Static facade API
│   ├── ConversionRequest.java                ← Fluent builder
│   ├── core/
│   │   ├── PandocInstallation.java           ← Local discovery + provider SPI
│   │   ├── PandocInstallationProvider.java   ← Optional resolver extension point
│   │   ├── PandocExecutor.java               ← ProcessBuilder wrapper
│   │   └── WorkingDirectory.java             ← Per-request isolated temp directory
│   ├── ast/                                  ← Pandoc JSON AST model
│   ├── spring/                               ← Spring Boot auto-configuration
│   └── exception/                            ← Base and conversion exceptions
│
└── pandoc4j-binary/
    ├── PandocBinary.java                     ← Managed binary entry point
    ├── PandocBinaryOptions.java              ← Version/cache/download options
    ├── PandocBinaryProvider.java             ← ServiceLoader provider
    ├── PandocBinaryResolver.java             ← Download, cache, checksum, extraction
    ├── pandoc-binaries.json                  ← Bundled asset manifest
    └── spring/                               ← Binary Spring Boot auto-configuration
```

---

## Maintainer Docs

Release workflow and Central publishing steps for project maintainers are documented in [`docs/deploy.md`](docs/deploy.md).

Phase 4 package design is documented in [`docs/phase-4-pandoc4j-binary.md`](docs/phase-4-pandoc4j-binary.md).

---

## Roadmap

- [x] **Phase 1** – Core CLI wrapper (file/text conversion, fluent builder, concurrent safety)
- [x] **Phase 2** – Spring Boot Starter (auto-configuration, `PandocClient` bean)
- [x] **Phase 3** – Pandoc JSON AST (Java sealed-class model, bidirectional mapping)
- [x] **Phase 3.1** – Markdown post-processing (`MarkdownCleaner`, builder `.cleanMarkdown()`)
- [x] **Phase 4** – [`pandoc4j-binary`](docs/phase-4-pandoc4j-binary.md) (released in 0.3.0)
- [ ] **Phase 5** – Async/reactive support (`CompletableFuture`, Project Reactor)
- [ ] **Phase 6** – Lua filter / JSON filter pipeline API

---

## License

[Apache License 2.0](LICENSE)
