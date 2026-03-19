package org.tinycircl.pandoc4j;

import org.tinycircl.pandoc4j.ast.PandocAstMapper;
import org.tinycircl.pandoc4j.ast.PandocDocument;
import org.tinycircl.pandoc4j.core.PandocExecutor;
import org.tinycircl.pandoc4j.core.PandocInstallation;
import org.tinycircl.pandoc4j.exception.PandocException;

import java.nio.file.Path;
import java.util.List;

/**
 * Main entry point for pandoc4j – a Java SDK wrapper for
 * <a href="https://pandoc.org">Pandoc</a>.
 *
 * <p>Mirrors the convenience API of
 * <a href="https://github.com/bebraw/pypandoc">pypandoc</a> for Python and
 * <a href="https://github.com/tea-node-js/node-pandoc">node-pandoc</a> for Node.js.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Convert a DOCX file to Markdown
 * String md = Pandoc4j.convertFile("report.docx", Format.MARKDOWN);
 *
 * // Convert a Markdown string to HTML
 * String html = Pandoc4j.convertText("# Hello", Format.MARKDOWN, Format.HTML5);
 *
 * // Advanced: use the builder for extra options
 * String result = Pandoc4j.builder()
 *     .from(Format.DOCX)
 *     .to(Format.MARKDOWN)
 *     .wrapNone()
 *     .extractMedia("./media")
 *     .convertFile(Path.of("report.docx"));
 * }</pre>
 *
 * <h2>Pandoc discovery</h2>
 * <p>The library resolves the Pandoc binary in this order:
 * <ol>
 *   <li>System property {@code pandoc.path}</li>
 *   <li>Environment variable {@code PANDOC_PATH}</li>
 *   <li>Common OS installation directories</li>
 *   <li>{@code pandoc} / {@code pandoc.exe} on the system {@code PATH}</li>
 * </ol>
 */
public final class Pandoc4j {

    private Pandoc4j() {}

    // ── File conversion ───────────────────────────────────────────────────

    /**
     * Converts a file to the given output format, inferring the input format
     * from the file extension.
     *
     * @param inputFile    path to the source document
     * @param outputFormat desired output format
     * @return converted document as a string
     * @throws PandocException on conversion failure or if Pandoc is not found
     */
    public static String convertFile(String inputFile, Format outputFormat) {
        return convertFile(Path.of(inputFile), null, outputFormat);
    }

    /** @see #convertFile(String, Format) */
    public static String convertFile(Path inputFile, Format outputFormat) {
        return convertFile(inputFile, null, outputFormat);
    }

    /**
     * Converts a file with an explicitly specified input format.
     *
     * @param inputFile    path to the source document
     * @param inputFormat  source format (pass {@code null} to infer from extension)
     * @param outputFormat desired output format
     */
    public static String convertFile(Path inputFile, Format inputFormat, Format outputFormat) {
        ConversionRequest.Builder builder = ConversionRequest.builder().to(outputFormat);
        if (inputFormat != null) {
            builder.from(inputFormat);
        }
        return builder.convertFile(inputFile);
    }

    // ── Text conversion ───────────────────────────────────────────────────

    /**
     * Converts an in-memory string from one format to another.
     *
     * @param text         source document content
     * @param inputFormat  format of {@code text}
     * @param outputFormat desired output format
     * @return converted document as a string
     * @throws PandocException on conversion failure or if Pandoc is not found
     */
    public static String convertText(String text, Format inputFormat, Format outputFormat) {
        return ConversionRequest.builder()
                .from(inputFormat)
                .to(outputFormat)
                .convertText(text);
    }

    // ── Builder ───────────────────────────────────────────────────────────

    /**
     * Returns a {@link ConversionRequest.Builder} for constructing a conversion
     * with fine-grained Pandoc options.
     */
    public static ConversionRequest.Builder builder() {
        return ConversionRequest.builder();
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    /**
     * Returns the version string of the installed Pandoc (e.g. {@code "3.6.3"}).
     *
     * @throws PandocException if Pandoc is not found
     */
    public static String getVersion() {
        return PandocInstallation.detect().getVersion();
    }

    /**
     * Returns a list of formats supported by the installed Pandoc,
     * parsed from {@code pandoc --list-input-formats} and
     * {@code pandoc --list-output-formats}.
     *
     * @return list of format name strings as reported by Pandoc
     */
    public static List<String> listInputFormats() {
        return queryFormats("--list-input-formats");
    }

    /** @see #listInputFormats() */
    public static List<String> listOutputFormats() {
        return queryFormats("--list-output-formats");
    }

    /**
     * Returns the resolved {@link PandocInstallation} for inspection
     * (e.g. to verify the executable path or version before running conversions).
     */
    public static PandocInstallation getInstallation() {
        return PandocInstallation.detect();
    }

    // ── AST API ───────────────────────────────────────────────────────────

    /**
     * Parses a file into a {@link PandocDocument} (Pandoc JSON AST).
     *
     * <p>Internally runs {@code pandoc --to=json <file>} and maps the JSON
     * output to the pandoc4j Java model.
     *
     * @param inputFile   path to the source document
     * @param inputFormat source format ({@code null} to infer from extension)
     * @throws PandocException on conversion failure or if Pandoc is not found
     */
    public static PandocDocument toAst(Path inputFile, Format inputFormat) {
        ConversionRequest.Builder builder = ConversionRequest.builder().to(Format.JSON);
        if (inputFormat != null) {
            builder.from(inputFormat);
        }
        String json = builder.convertFile(inputFile);
        return PandocAstMapper.getInstance().parse(json);
    }

    /** @see #toAst(Path, Format) */
    public static PandocDocument toAst(Path inputFile) {
        return toAst(inputFile, null);
    }

    /**
     * Parses an in-memory string into a {@link PandocDocument}.
     *
     * @param text        source document content
     * @param inputFormat format of {@code text}
     * @throws PandocException on conversion failure or if Pandoc is not found
     */
    public static PandocDocument toAst(String text, Format inputFormat) {
        String json = ConversionRequest.builder()
                .from(inputFormat)
                .to(Format.JSON)
                .convertText(text);
        return PandocAstMapper.getInstance().parse(json);
    }

    /**
     * Renders a {@link PandocDocument} to the desired output format.
     *
     * <p>Serializes the document back to Pandoc JSON, then runs
     * {@code pandoc --from=json --to=<outputFormat>}.
     *
     * @param document     the document to render
     * @param outputFormat desired output format
     * @throws PandocException on conversion failure or if Pandoc is not found
     */
    public static String fromAst(PandocDocument document, Format outputFormat) {
        String json = PandocAstMapper.getInstance().serialize(document);
        return ConversionRequest.builder()
                .from(Format.JSON)
                .to(outputFormat)
                .convertText(json);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static List<String> queryFormats(String flag) {
        PandocInstallation installation = PandocInstallation.detect();
        PandocExecutor executor = new PandocExecutor(installation);
        ConversionResult result = executor.execute(List.of(flag));
        return result.getOutput().lines()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
