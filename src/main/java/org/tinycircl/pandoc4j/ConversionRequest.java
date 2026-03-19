package org.tinycircl.pandoc4j;

import org.tinycircl.pandoc4j.core.PandocExecutor;
import org.tinycircl.pandoc4j.core.PandocInstallation;
import org.tinycircl.pandoc4j.core.WorkingDirectory;
import org.tinycircl.pandoc4j.exception.PandocException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder for a Pandoc conversion operation.
 *
 * <pre>{@code
 * String html = Pandoc4j.builder()
 *     .from(Format.MARKDOWN)
 *     .to(Format.HTML5)
 *     .standalone()
 *     .convertText("# Hello World");
 *
 * String md = Pandoc4j.builder()
 *     .to(Format.MARKDOWN)
 *     .wrapNone()
 *     .extractMedia("./media")
 *     .convertFile(Path.of("report.docx"));
 * }</pre>
 */
public final class ConversionRequest {

    private final Format inputFormat;
    private final Format outputFormat;
    private final List<String> extraArgs;
    private final PandocInstallation installation;
    /** User-specified persistent working dir; {@code null} means use an auto-managed temp dir. */
    private final Path workingDirectory;

    private ConversionRequest(Builder builder) {
        this.inputFormat      = builder.inputFormat;
        this.outputFormat     = builder.outputFormat;
        this.extraArgs        = Collections.unmodifiableList(new ArrayList<>(builder.extraArgs));
        this.installation     = builder.installation;
        this.workingDirectory = builder.workingDirectory;
    }

    // ── Execution ─────────────────────────────────────────────────────────

    /**
     * Converts the given file path and returns the output as a string.
     *
     * <p>Each invocation runs Pandoc inside an isolated temporary working directory
     * to prevent concurrent conversions from interfering with each other.
     * The temp directory is automatically deleted after the conversion completes.
     *
     * <p>The input format is inferred from the file extension when not explicitly set via
     * {@link Builder#from(Format)}.
     *
     * @throws PandocException on conversion failure
     */
    public String convertFile(Path inputFile) {
        try (WorkingDirectory wd = resolvedWorkingDirectory()) {
            List<String> args = buildArgs(inputFile, null);
            PandocExecutor executor = new PandocExecutor(resolvedInstallation());
            return executor.execute(args, null, wd.getPath()).getOutput();
        }
    }

    /** @see #convertFile(Path) */
    public String convertFile(String inputFile) {
        return convertFile(Path.of(inputFile));
    }

    /**
     * Converts the given text string and returns the output as a string.
     *
     * <p>Each invocation runs Pandoc inside an isolated temporary working directory.
     *
     * <p>{@link Builder#from(Format)} must be set when using this method.
     *
     * @throws IllegalStateException if the input format has not been specified
     * @throws PandocException       on conversion failure
     */
    public String convertText(String text) {
        if (inputFormat == null) {
            throw new IllegalStateException(
                    "Input format must be specified via from(Format) when converting text.");
        }
        try (WorkingDirectory wd = resolvedWorkingDirectory()) {
            List<String> args = buildArgs(null, text);
            PandocExecutor executor = new PandocExecutor(resolvedInstallation());
            return executor.execute(args, text, wd.getPath()).getOutput();
        }
    }

    // ── Arg construction ──────────────────────────────────────────────────

    private List<String> buildArgs(Path inputFile, String inputText) {
        List<String> args = new ArrayList<>();

        if (inputFormat != null) {
            args.add("--from=" + inputFormat.getPandocName());
        }
        if (outputFormat != null) {
            args.add("--to=" + outputFormat.getPandocName());
        }

        args.addAll(extraArgs);

        // Output to stdout
        args.add("--output=-");

        if (inputFile != null) {
            args.add(inputFile.toAbsolutePath().toString());
        }
        // When reading from stdin, no file argument is added (Pandoc reads stdin)

        return args;
    }

    private PandocInstallation resolvedInstallation() {
        return installation != null ? installation : PandocInstallation.detect();
    }

    /**
     * Returns a {@link WorkingDirectory} for this conversion.
     * If the user specified a directory via {@link Builder#workingDirectory(Path)},
     * an unmanaged instance is returned (no cleanup on close).
     * Otherwise a fresh managed temp directory is created.
     */
    private WorkingDirectory resolvedWorkingDirectory() {
        if (workingDirectory != null) {
            return WorkingDirectory.of(workingDirectory);
        }
        return WorkingDirectory.createTemp();
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Format inputFormat;
        private Format outputFormat;
        private final List<String> extraArgs = new ArrayList<>();
        private PandocInstallation installation;
        private Path workingDirectory;
        private boolean cleanMarkdownOutput = false;

        private Builder() {}

        /** Sets the input format explicitly (required for {@link #convertText(String)}). */
        public Builder from(Format format) {
            this.inputFormat = format;
            return this;
        }

        /** Sets the output format. */
        public Builder to(Format format) {
            this.outputFormat = format;
            return this;
        }

        /** Uses a specific {@link PandocInstallation} instead of auto-detection. */
        public Builder withInstallation(PandocInstallation pandocInstallation) {
            this.installation = pandocInstallation;
            return this;
        }

        /**
         * Sets a persistent working directory for the Pandoc process.
         *
         * <p>By default each conversion runs in an auto-managed temporary directory
         * that is deleted after the call completes. Use this method when you need
         * Pandoc to resolve resources relative to a specific path, or when you
         * want side-effect files (e.g. from {@code --extract-media}) to land in a
         * known location that outlives the conversion.
         *
         * <p>When a persistent directory is supplied it is <em>not</em> cleaned up
         * by pandoc4j; the caller is responsible for its lifecycle.
         */
        public Builder workingDirectory(Path dir) {
            this.workingDirectory = dir;
            return this;
        }

        // ── Common Pandoc options ─────────────────────────────────────────

        /** Adds {@code --standalone} – produces a complete document, not a fragment. */
        public Builder standalone() {
            return arg("--standalone");
        }

        /** Adds {@code --table-of-contents}. */
        public Builder tableOfContents() {
            return arg("--table-of-contents");
        }

        /**
         * Sets line-wrapping mode.
         * Common values: {@code "none"}, {@code "auto"}, {@code "preserve"}.
         */
        public Builder wrap(String mode) {
            return arg("--wrap=" + mode);
        }

        /** Convenience for {@code wrap("none")}. */
        public Builder wrapNone() {
            return wrap("none");
        }

        /**
         * Extracts media files (images, etc.) to the specified directory.
         * Equivalent to {@code --extract-media=<dir>}.
         */
        public Builder extractMedia(String directory) {
            return arg("--extract-media=" + directory);
        }

        /**
         * Applies a custom Pandoc template.
         * Equivalent to {@code --template=<path>}.
         */
        public Builder template(String templatePath) {
            return arg("--template=" + templatePath);
        }

        /**
         * Sets the number of columns for line wrapping.
         * Equivalent to {@code --columns=<n>}.
         */
        public Builder columns(int n) {
            return arg("--columns=" + n);
        }

        /**
         * Enables or disables a Pandoc extension.
         * Example: {@code extension("+smart")} or {@code extension("-raw_html")}.
         */
        public Builder extension(String extension) {
            if (outputFormat == null) {
                throw new IllegalStateException("Call to(Format) before adding extensions.");
            }
            return arg("--to=" + outputFormat.getPandocName() + extension);
        }

        /**
         * Applies a Lua or JSON filter.
         * Equivalent to {@code --filter=<filter>} or {@code --lua-filter=<filter>}.
         */
        public Builder filter(String filter) {
            return arg("--filter=" + filter);
        }

        /** Adds {@code --shift-heading-level-by=<n>}. */
        public Builder shiftHeadingLevelBy(int n) {
            return arg("--shift-heading-level-by=" + n);
        }

        /** Adds {@code --reference-doc=<path>} (Word/PowerPoint reference document). */
        public Builder referenceDoc(String path) {
            return arg("--reference-doc=" + path);
        }

        /** Adds {@code --highlight-style=<style>}. */
        public Builder highlightStyle(String style) {
            return arg("--highlight-style=" + style);
        }

        /**
         * Applies {@link MarkdownCleaner#clean(String)} to the conversion output
         * before returning it.
         *
         * <p>Useful when converting formats such as PPTX whose source files may carry
         * multi-line accessibility alt-text that Pandoc preserves verbatim, causing
         * broken {@code ![alt](url)} syntax in the resulting Markdown.
         *
         * <p>Has no effect when the output format is not Markdown.
         * Default: {@code false}.
         */
        public Builder cleanMarkdown() {
            this.cleanMarkdownOutput = true;
            return this;
        }

        /** Adds any raw Pandoc CLI argument(s). */
        public Builder arg(String... rawArgs) {
            this.extraArgs.addAll(Arrays.asList(rawArgs));
            return this;
        }

        /** Builds the request without executing it. */
        public ConversionRequest build() {
            return new ConversionRequest(this);
        }

        // ── Terminal shortcuts ────────────────────────────────────────────

        /** Builds and immediately converts the given file. */
        public String convertFile(Path inputFile) {
            String result = build().convertFile(inputFile);
            return cleanMarkdownOutput ? MarkdownCleaner.clean(result) : result;
        }

        /** Builds and immediately converts the given file path string. */
        public String convertFile(String inputFile) {
            return convertFile(Path.of(inputFile));
        }

        /** Builds and immediately converts the given text. */
        public String convertText(String text) {
            String result = build().convertText(text);
            return cleanMarkdownOutput ? MarkdownCleaner.clean(result) : result;
        }
    }
}
