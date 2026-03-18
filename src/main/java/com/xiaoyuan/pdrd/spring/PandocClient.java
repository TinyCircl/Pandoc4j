package com.xiaoyuan.pdrd.spring;

import com.xiaoyuan.pdrd.ConversionRequest;
import com.xiaoyuan.pdrd.Format;
import com.xiaoyuan.pdrd.ast.PandocAstMapper;
import com.xiaoyuan.pdrd.ast.PandocDocument;
import com.xiaoyuan.pdrd.core.PandocExecutor;
import com.xiaoyuan.pdrd.core.PandocInstallation;
import com.xiaoyuan.pdrd.exception.PandocException;

import java.nio.file.Path;
import java.util.List;

/**
 * Spring-managed service that wraps pandoc4j conversion operations.
 *
 * <p>Auto-configured by {@link PandocAutoConfiguration} when Spring Boot is on the
 * classpath. Inject it wherever you need document conversion:
 *
 * <pre>{@code
 * @Service
 * public class DocumentService {
 *
 *     private final PandocClient pandoc;
 *
 *     public DocumentService(PandocClient pandoc) {
 *         this.pandoc = pandoc;
 *     }
 *
 *     public String toHtml(String markdown) {
 *         return pandoc.convertText(markdown, Format.MARKDOWN, Format.HTML5);
 *     }
 *
 *     public String toMarkdown(Path docxFile) {
 *         return pandoc.convertFile(docxFile, Format.MARKDOWN);
 *     }
 * }
 * }</pre>
 *
 * <p>For fine-grained control use {@link #builder()} to access the full
 * {@link ConversionRequest.Builder} API.
 */
public class PandocClient {

    private final PandocInstallation installation;
    private final long timeoutSeconds;

    public PandocClient(PandocInstallation installation, long timeoutSeconds) {
        this.installation = installation;
        this.timeoutSeconds = timeoutSeconds;
    }

    // ── Conversion API ────────────────────────────────────────────────────

    /**
     * Converts a file to the given output format, inferring the input format
     * from the file extension.
     *
     * @param inputFile    path to the source document
     * @param outputFormat desired output format
     * @return converted document as a string
     * @throws PandocException on conversion failure
     */
    public String convertFile(Path inputFile, Format outputFormat) {
        return builder().to(outputFormat).convertFile(inputFile);
    }

    /**
     * Converts a file with an explicitly specified input format.
     *
     * @param inputFile    path to the source document
     * @param inputFormat  source format ({@code null} to infer from extension)
     * @param outputFormat desired output format
     */
    public String convertFile(Path inputFile, Format inputFormat, Format outputFormat) {
        ConversionRequest.Builder b = builder().to(outputFormat);
        if (inputFormat != null) {
            b.from(inputFormat);
        }
        return b.convertFile(inputFile);
    }

    /**
     * Converts an in-memory string from one format to another.
     *
     * @param text         source document content
     * @param inputFormat  format of {@code text}
     * @param outputFormat desired output format
     * @throws PandocException on conversion failure
     */
    public String convertText(String text, Format inputFormat, Format outputFormat) {
        return builder().from(inputFormat).to(outputFormat).convertText(text);
    }

    // ── Builder ───────────────────────────────────────────────────────────

    /**
     * Returns a {@link ConversionRequest.Builder} pre-wired with this client's
     * {@link PandocInstallation} and timeout. Use this for advanced Pandoc options.
     *
     * <pre>{@code
     * String result = pandocClient.builder()
     *     .from(Format.DOCX)
     *     .to(Format.MARKDOWN)
     *     .wrapNone()
     *     .extractMedia("/data/media")
     *     .convertFile(inputPath);
     * }</pre>
     */
    public ConversionRequest.Builder builder() {
        return ConversionRequest.builder().withInstallation(installation);
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    /**
     * Returns the version string of the configured Pandoc installation
     * (e.g. {@code "3.9"}).
     */
    public String getVersion() {
        return installation.getVersion();
    }

    /**
     * Returns a list of formats supported as Pandoc input.
     */
    public List<String> listInputFormats() {
        PandocExecutor executor = new PandocExecutor(installation, timeoutSeconds);
        return executor.execute(List.of("--list-input-formats"))
                .getOutput().lines()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /**
     * Returns a list of formats supported as Pandoc output.
     */
    public List<String> listOutputFormats() {
        PandocExecutor executor = new PandocExecutor(installation, timeoutSeconds);
        return executor.execute(List.of("--list-output-formats"))
                .getOutput().lines()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    // ── AST API ───────────────────────────────────────────────────────────

    /**
     * Parses a file into a {@link PandocDocument} (Pandoc JSON AST).
     *
     * @param inputFile   path to the source document
     * @param inputFormat source format ({@code null} to infer from file extension)
     * @throws PandocException on conversion failure
     */
    public PandocDocument toAst(Path inputFile, Format inputFormat) {
        ConversionRequest.Builder b = builder().to(Format.JSON);
        if (inputFormat != null) b.from(inputFormat);
        String json = b.convertFile(inputFile);
        return PandocAstMapper.getInstance().parse(json);
    }

    /** @see #toAst(Path, Format) */
    public PandocDocument toAst(Path inputFile) {
        return toAst(inputFile, null);
    }

    /**
     * Parses an in-memory string into a {@link PandocDocument}.
     *
     * @param text        source document content
     * @param inputFormat format of {@code text}
     * @throws PandocException on conversion failure
     */
    public PandocDocument toAst(String text, Format inputFormat) {
        String json = builder().from(inputFormat).to(Format.JSON).convertText(text);
        return PandocAstMapper.getInstance().parse(json);
    }

    /**
     * Renders a {@link PandocDocument} to the desired output format.
     *
     * @param document     the document to render
     * @param outputFormat desired output format
     * @throws PandocException on conversion failure
     */
    public String fromAst(PandocDocument document, Format outputFormat) {
        String json = PandocAstMapper.getInstance().serialize(document);
        return builder().from(Format.JSON).to(outputFormat).convertText(json);
    }

    /**
     * Returns the underlying {@link PandocInstallation} for advanced usage.
     */
    public PandocInstallation getInstallation() {
        return installation;
    }
}
