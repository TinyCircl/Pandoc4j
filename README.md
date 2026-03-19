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
| `pandoc4j-binary` | Includes Pandoc binary out of the box *(coming soon)* | No |

---

## Requirements

- **Java 21+**
- **Pandoc** installed on the machine (for `pandoc4j`; see [pandoc.org/installing](https://pandoc.org/installing.html))

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

Two distribution channels are available. JitPack can be consumed directly from GitHub tags or commits; Maven Central coordinates become available after the official release is published.

---

### Option A – JitPack *(available immediately)*

No registration needed. JitPack builds directly from the GitHub repository.

**Maven**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.TinyCircl</groupId>
    <artifactId>Pandoc4j</artifactId>
    <version>v0.2.0</version>   <!-- or a commit hash -->
</dependency>
```

**Gradle**
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.TinyCircl:Pandoc4j:v0.2.0'
}
```

---

### Option B – Maven Central *(recommended for production)*

After publishing to Maven Central (see [Publishing](#publishing) below), the dependency becomes:

**Maven**
```xml
<dependency>
    <groupId>org.tinycircl</groupId>
    <artifactId>pandoc4j</artifactId>
    <version>0.2.0</version>
</dependency>
```

**Gradle**
```groovy
implementation 'org.tinycircl:pandoc4j:0.2.0'
```

---

## Upgrade Notes

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

// Shift heading levels, add table of contents
String standalone = Pandoc4j.builder()
    .from(Format.MARKDOWN)
    .to(Format.HTML5)
    .standalone()
    .tableOfContents()
    .shiftHeadingLevelBy(1)
    .convertText(markdownContent);

// Raw argument passthrough (forward-compatible with new Pandoc versions)
String out = Pandoc4j.builder()
    .from(Format.MARKDOWN)
    .to(Format.HTML5)
    .arg("--highlight-style=tango", "--number-sections")
    .convertText(content);
```

---

## Spring Boot

Zero-configuration auto-wiring. Add the dependency and inject `PandocClient`:

### Configuration (`application.yml`)

```yaml
pandoc:
  executable-path: /usr/local/bin/pandoc   # optional – auto-detected if omitted
  timeout-seconds: 60                       # optional – default: 120
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
        return pandoc.builder()
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

1. `pandoc.executablePath` Spring Boot property / `pandoc.path` system property
2. `PANDOC_PATH` environment variable
3. Common OS installation directories (`/usr/local/bin`, `%LOCALAPPDATA%\Pandoc\`, etc.)
4. `pandoc` / `pandoc.exe` on the system `PATH`

---

## Project Architecture

```
pandoc4j
├── Pandoc4j.java                    ← Static facade API
├── ConversionRequest.java           ← Fluent builder
├── Format.java                      ← 50+ format enum with extensions
├── ConversionResult.java            ← Process result (exit code, stdout, stderr)
│
├── core/
│   ├── PandocInstallation.java      ← Binary discovery & version detection
│   ├── PandocExecutor.java          ← ProcessBuilder wrapper (timeout, I/O, stderr)
│   └── WorkingDirectory.java        ← Per-request isolated temp directory
│
├── ast/
│   ├── PandocDocument.java          ← Top-level document model
│   ├── Block.java                   ← Sealed interface (Para, Header, CodeBlock, ...)
│   ├── Inline.java                  ← Sealed interface (Str, Emph, Strong, Link, ...)
│   ├── PandocAstMapper.java         ← JsonNode ↔ Java model converter
│   ├── Attr.java                    ← Element attributes (id, classes, key-values)
│   ├── Target.java                  ← Link/image target (url, title)
│   ├── MathType.java                ← Enum (INLINE_MATH, DISPLAY_MATH)
│   └── ListAttributes.java          ← Ordered list attributes
│
├── spring/
│   ├── PandocAutoConfiguration.java ← @AutoConfiguration
│   ├── PandocClient.java            ← Injectable Spring service
│   └── PandocProperties.java        ← @ConfigurationProperties(prefix="pandoc")
│
└── exception/
    ├── PandocException.java          ← Base exception
    ├── PandocNotFoundException.java  ← Pandoc binary not found
    └── PandocConversionException.java ← Conversion failed (exit code + stderr)
```

---

## Publishing

### JitPack（已就绪，无需额外操作）

JitPack 会在首次有人请求该坐标时自动构建。访问：

```
https://jitpack.io/#TinyCircl/Pandoc4j
```

点击 **"Get it"** 即可触发构建。之后所有人可直接添加依赖使用。
如需发布正式版，在 GitHub 上创建一个 Tag（如 `v0.2.0`），JitPack 会自动识别。

---

### Maven Central（一次性配置）

**第一步：注册 Sonatype Central Portal**

1. 前往 [central.sonatype.com](https://central.sonatype.com) 注册账号
2. 申请并验证命名空间 `org.tinycircl`
3. 在账号设置中生成 **User Token**（即 `username` + `password`）

**第二步：配置 `~/.m2/settings.xml`**

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- Sonatype User Token username --></username>
      <password><!-- Sonatype User Token password --></password>
    </server>
  </servers>
</settings>
```

**第三步：生成 GPG 密钥**

```bash
gpg --full-generate-key
# 查看 key ID
gpg --list-secret-keys --keyid-format=long
# 上传公钥到 key server
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

**第四步：发布**

```bash
# 确保 pom.xml 中的版本号不带 -SNAPSHOT（如 0.2.0）

# 发布到 Central Portal（默认会先上传并校验，随后需在网页上手动确认 Publish）
mvn -P release -Dgpg.keyname=<KEY_ID> deploy

# Windows PowerShell 如遇 -Dgpg.keyname 参数解析问题，可改用
cmd /c "mvn -P release -Dgpg.keyname=<KEY_ID> deploy"
```

发布完成后在 [central.sonatype.com](https://central.sonatype.com) 点击 **Publish** 确认，
约 10-30 分钟后同步到 Maven Central 搜索。

---

## Roadmap

- [x] **Phase 1** – Core CLI wrapper (file/text conversion, fluent builder, concurrent safety)
- [x] **Phase 2** – Spring Boot Starter (auto-configuration, `PandocClient` bean)
- [x] **Phase 3** – Pandoc JSON AST (Java sealed-class model, bidirectional mapping)
- [ ] **Phase 4** – `pandoc4j-binary` (auto-download Pandoc binary, zero-install)
- [ ] **Phase 5** – Async/reactive support (`CompletableFuture`, Project Reactor)
- [ ] **Phase 6** – Lua filter / JSON filter pipeline API

---

## License

[Apache License 2.0](LICENSE)
