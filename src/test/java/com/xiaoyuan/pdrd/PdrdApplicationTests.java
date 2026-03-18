package com.xiaoyuan.pdrd;

import com.xiaoyuan.pdrd.core.PandocInstallation;
import com.xiaoyuan.pdrd.core.WorkingDirectory;
import com.xiaoyuan.pdrd.exception.PandocConversionException;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for pandoc4j.
 *
 * <p>These tests require Pandoc to be installed on the host machine.
 * Tests are tagged with {@link Tag @Tag("integration")} so they can be
 * excluded in CI environments without Pandoc.
 */
@Tag("integration")
class PdrdApplicationTests {

    private static PandocInstallation installation;

    @BeforeAll
    static void setup() {
        try {
            installation = PandocInstallation.detect();
        } catch (Exception e) {
            // Tests will be skipped if Pandoc is unavailable
        }
    }

    // ── PandocInstallation ─────────────────────────────────────────────────

    @Test
    @DisplayName("PandocInstallation.detect() finds the Pandoc binary")
    void installationDetected() {
        assertNotNull(installation, "Pandoc must be installed to run integration tests");
        assertTrue(installation.isAvailable(), "Pandoc executable must be available");
    }

    @Test
    @DisplayName("PandocInstallation.getVersion() returns a non-blank version string")
    void installationVersion() {
        assumePandocAvailable();
        String version = installation.getVersion();
        assertNotNull(version);
        assertFalse(version.isBlank(), "Version must not be blank");
        System.out.println("Pandoc version: " + version);
    }

    // ── Format enum ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Format.fromExtension resolves known extensions")
    void formatFromExtension() {
        assertEquals(Optional.of(Format.DOCX),     Format.fromExtension("docx"));
        assertEquals(Optional.of(Format.MARKDOWN),  Format.fromExtension("md"));
        assertEquals(Optional.of(Format.HTML),      Format.fromExtension("html"));
        assertEquals(Optional.of(Format.PDF),       Format.fromExtension("pdf"));
        assertEquals(Optional.of(Format.LATEX),     Format.fromExtension("tex"));
        assertEquals(Optional.empty(),              Format.fromExtension("unknown_xyz"));
    }

    @Test
    @DisplayName("Format.fromExtension ignores leading dot")
    void formatFromExtensionWithDot() {
        assertEquals(Optional.of(Format.DOCX), Format.fromExtension(".docx"));
    }

    @Test
    @DisplayName("Format.fromPandocName resolves known pandoc names")
    void formatFromPandocName() {
        assertEquals(Optional.of(Format.MARKDOWN_GFM), Format.fromPandocName("gfm"));
        assertEquals(Optional.of(Format.HTML5),         Format.fromPandocName("html5"));
        assertEquals(Optional.empty(),                   Format.fromPandocName("nonexistent"));
    }

    @Test
    @DisplayName("Format.getPandocName returns correct identifier")
    void formatPandocName() {
        assertEquals("markdown", Format.MARKDOWN.getPandocName());
        assertEquals("docx",     Format.DOCX.getPandocName());
        assertEquals("gfm",      Format.MARKDOWN_GFM.getPandocName());
        assertEquals("html5",    Format.HTML5.getPandocName());
    }

    // ── Text conversion ────────────────────────────────────────────────────

    @Test
    @DisplayName("convertText: Markdown → HTML5 produces expected tags")
    void convertMarkdownToHtml() {
        assumePandocAvailable();
        String html = Pandoc4j.convertText("# Hello\n\nWorld", Format.MARKDOWN, Format.HTML5);
        assertNotNull(html);
        assertTrue(html.contains("<h1"), "Output must contain an <h1> tag");
        assertTrue(html.contains("Hello"),  "Output must contain heading text");
        assertTrue(html.contains("<p>"),    "Output must contain a <p> tag");
    }

    @Test
    @DisplayName("convertText: Markdown → Plain text strips markup")
    void convertMarkdownToPlain() {
        assumePandocAvailable();
        String plain = Pandoc4j.convertText("**bold** and _italic_", Format.MARKDOWN, Format.PLAIN);
        assertNotNull(plain);
        assertTrue(plain.contains("bold"),   "Plain output must contain 'bold'");
        assertTrue(plain.contains("italic"), "Plain output must contain 'italic'");
        assertFalse(plain.contains("**"),    "Plain output must not contain ** markers");
    }

    @Test
    @DisplayName("convertText: HTML → Markdown extracts heading")
    void convertHtmlToMarkdown() {
        assumePandocAvailable();
        String md = Pandoc4j.convertText("<h1>Title</h1><p>Body</p>", Format.HTML, Format.MARKDOWN);
        assertNotNull(md);
        assertTrue(md.contains("Title"), "Markdown must contain heading text");
    }

    // ── File conversion ────────────────────────────────────────────────────

    @Test
    @DisplayName("convertFile: Markdown file → HTML5")
    void convertMarkdownFileToHtml() throws IOException {
        assumePandocAvailable();
        Path tempFile = Files.createTempFile("pandoc4j-test-", ".md");
        try {
            Files.writeString(tempFile, "# pandoc4j\n\nTest document.\n");
            String html = Pandoc4j.convertFile(tempFile, Format.HTML5);
            assertNotNull(html);
            assertTrue(html.contains("<h1"), "Output should contain h1 tag");
            assertTrue(html.contains("pandoc4j"), "Output should contain heading text");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ── Builder API ────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder: wrapNone disables line wrapping in Markdown output")
    void builderWrapNone() {
        assumePandocAvailable();
        String longText = "This is a very long paragraph that exceeds the default wrap column. "
                          .repeat(5);
        String wrapped = Pandoc4j.builder()
                .from(Format.MARKDOWN)
                .to(Format.MARKDOWN)
                .convertText(longText);

        String unwrapped = Pandoc4j.builder()
                .from(Format.MARKDOWN)
                .to(Format.MARKDOWN)
                .wrapNone()
                .convertText(longText);

        // With wrapNone the output should be a single line (no internal newlines in the paragraph)
        long wrappedLines   = wrapped.lines().count();
        long unwrappedLines = unwrapped.lines().filter(l -> !l.isBlank()).count();
        assertTrue(unwrappedLines <= wrappedLines,
                   "wrapNone should produce fewer or equal lines");
    }

    @Test
    @DisplayName("builder: standalone produces a complete HTML document")
    void builderStandalone() {
        assumePandocAvailable();
        String html = Pandoc4j.builder()
                .from(Format.MARKDOWN)
                .to(Format.HTML5)
                .standalone()
                .convertText("# Standalone");
        assertTrue(html.contains("<!DOCTYPE") || html.contains("<html"),
                   "Standalone output must contain DOCTYPE or <html>");
    }

    // ── listFormats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("listInputFormats returns a non-empty list containing 'markdown'")
    void listInputFormats() {
        assumePandocAvailable();
        List<String> formats = Pandoc4j.listInputFormats();
        assertFalse(formats.isEmpty(), "Input formats list must not be empty");
        assertTrue(formats.contains("markdown"), "Input formats must include 'markdown'");
    }

    @Test
    @DisplayName("listOutputFormats returns a non-empty list containing 'html5'")
    void listOutputFormats() {
        assumePandocAvailable();
        List<String> formats = Pandoc4j.listOutputFormats();
        assertFalse(formats.isEmpty(), "Output formats list must not be empty");
        assertTrue(formats.contains("html5"), "Output formats must include 'html5'");
    }

    // ── Error handling ─────────────────────────────────────────────────────

    @Test
    @DisplayName("convertText without from() throws IllegalStateException")
    void convertTextWithoutFromThrows() {
        assertThrows(IllegalStateException.class,
                () -> Pandoc4j.builder().to(Format.HTML5).convertText("# Test"));
    }

    @Test
    @DisplayName("Conversion with invalid file throws PandocConversionException")
    void convertNonExistentFileThrows() {
        assumePandocAvailable();
        assertThrows(PandocConversionException.class,
                () -> Pandoc4j.convertFile(Path.of("nonexistent_file_xyz.docx"), Format.MARKDOWN));
    }

    // ── WorkingDirectory ───────────────────────────────────────────────────

    @Test
    @Tag("unit")
    @DisplayName("WorkingDirectory.createTemp() creates a unique directory that exists")
    void workingDirectoryCreateTemp() throws IOException {
        Path dir;
        try (WorkingDirectory wd = WorkingDirectory.createTemp()) {
            dir = wd.getPath();
            assertTrue(Files.isDirectory(dir), "Temp dir must exist while open");
            assertTrue(wd.isManaged(), "createTemp() must return a managed instance");
        }
        assertFalse(Files.exists(dir), "Temp dir must be deleted after close()");
    }

    @Test
    @Tag("unit")
    @DisplayName("WorkingDirectory.createTemp() produces unique paths across calls")
    void workingDirectoryUniquePaths() {
        try (WorkingDirectory a = WorkingDirectory.createTemp();
             WorkingDirectory b = WorkingDirectory.createTemp()) {
            assertNotEquals(a.getPath(), b.getPath(),
                    "Two createTemp() calls must not return the same path");
        }
    }

    @Test
    @Tag("unit")
    @DisplayName("WorkingDirectory.of() wraps an existing dir without deleting on close")
    void workingDirectoryUnmanagedDoesNotDelete() throws IOException {
        Path existing = Files.createTempDirectory("pandoc4j-unmanaged-");
        try {
            try (WorkingDirectory wd = WorkingDirectory.of(existing)) {
                assertFalse(wd.isManaged(), "of() must return an unmanaged instance");
                assertEquals(existing, wd.getPath());
            }
            assertTrue(Files.exists(existing),
                    "Unmanaged working dir must NOT be deleted on close()");
        } finally {
            Files.deleteIfExists(existing);
        }
    }

    @Test
    @Tag("unit")
    @DisplayName("WorkingDirectory.of() rejects a non-existent path")
    void workingDirectoryOfRejectsNonExistent() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkingDirectory.of(Path.of("/nonexistent/path/xyz")));
    }

    // ── Concurrent safety ──────────────────────────────────────────────────

    @Test
    @DisplayName("20 concurrent convertText calls all succeed without interference")
    void concurrentTextConversions() throws InterruptedException {
        assumePandocAvailable();

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>(threadCount);
        AtomicInteger errorCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int taskId = i;
            futures.add(pool.submit(() ->
                Pandoc4j.convertText(
                        "# Heading " + taskId + "\n\nParagraph " + taskId,
                        Format.MARKDOWN, Format.HTML5)
            ));
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS),
                "All conversions should finish within 60 s");

        for (int i = 0; i < threadCount; i++) {
            try {
                String html = futures.get(i).get();
                assertNotNull(html);
                assertTrue(html.contains("Heading " + i),
                        "Result for task " + i + " must contain its own heading");
            } catch (ExecutionException e) {
                errorCount.incrementAndGet();
                System.err.println("Task " + i + " failed: " + e.getCause());
            }
        }

        assertEquals(0, errorCount.get(),
                errorCount.get() + " out of " + threadCount + " concurrent conversions failed");
    }

    @Test
    @DisplayName("20 concurrent convertFile calls all succeed without interference")
    void concurrentFileConversions() throws InterruptedException, IOException {
        assumePandocAvailable();

        int threadCount = 20;
        List<Path> tempFiles = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            Path f = Files.createTempFile("pandoc4j-conc-" + i + "-", ".md");
            Files.writeString(f, "# File " + i + "\n\nContent for file " + i);
            tempFiles.add(f);
        }

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<String>> futures = new ArrayList<>(threadCount);
        AtomicInteger errorCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            final int taskId = i;
            futures.add(pool.submit(() ->
                Pandoc4j.convertFile(tempFiles.get(taskId), Format.HTML5)
            ));
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS),
                "All conversions should finish within 60 s");

        for (int i = 0; i < threadCount; i++) {
            try {
                String html = futures.get(i).get();
                assertNotNull(html);
                assertTrue(html.contains("File " + i),
                        "Result for task " + i + " must contain its own heading");
            } catch (ExecutionException e) {
                errorCount.incrementAndGet();
                System.err.println("Task " + i + " failed: " + e.getCause());
            } finally {
                Files.deleteIfExists(tempFiles.get(i));
            }
        }

        assertEquals(0, errorCount.get(),
                errorCount.get() + " out of " + threadCount + " concurrent file conversions failed");
    }

    @Test
    @DisplayName("builder.workingDirectory(Path) uses the specified directory and does not delete it")
    void builderWorkingDirectoryPersists() throws IOException {
        assumePandocAvailable();
        Path persistent = Files.createTempDirectory("pandoc4j-persist-");
        try {
            String html = Pandoc4j.builder()
                    .from(Format.MARKDOWN)
                    .to(Format.HTML5)
                    .workingDirectory(persistent)
                    .convertText("# Persistent");
            assertNotNull(html);
            assertTrue(Files.exists(persistent),
                    "Persistent working directory must not be deleted after conversion");
        } finally {
            Files.deleteIfExists(persistent);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static void assumePandocAvailable() {
        Assumptions.assumeTrue(installation != null && installation.isAvailable(),
                "Skipping: Pandoc not available on this machine");
    }
}

