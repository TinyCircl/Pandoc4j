package org.tinycircl.pandoc4j;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumeration of document formats supported by Pandoc.
 * Each constant holds the exact format name used in Pandoc CLI arguments.
 */
public enum Format {

    // ── Markdown variants ────────────────────────────────────────────────────
    MARKDOWN("markdown", "md", "markdown"),
    MARKDOWN_GFM("gfm", "md", "markdown"),
    MARKDOWN_STRICT("markdown_strict", "md"),
    MARKDOWN_MMD("markdown_mmd", "md"),
    MARKDOWN_PHP_EXTRA("markdown_phpextra", "md"),
    COMMONMARK("commonmark", "md"),
    COMMONMARK_X("commonmark_x", "md"),

    // ── Web ──────────────────────────────────────────────────────────────────
    HTML("html", "html", "htm"),
    HTML5("html5", "html", "htm"),
    HTML4("html4", "html", "htm"),

    // ── Office / OpenDocument ────────────────────────────────────────────────
    DOCX("docx", "docx"),
    PPTX("pptx", "pptx"),
    ODT("odt", "odt"),
    ODP("odp", "odp"),

    // ── PDF ───────────────────────────────────────────────────────────────────
    PDF("pdf", "pdf"),

    // ── LaTeX / TeX ───────────────────────────────────────────────────────────
    LATEX("latex", "tex", "latex"),
    BEAMER("beamer", "tex"),
    CONTEXT("context", "tex"),

    // ── Plain text ────────────────────────────────────────────────────────────
    PLAIN("plain", "txt"),

    // ── Structured markup ─────────────────────────────────────────────────────
    RST("rst", "rst"),
    ORG("org", "org"),
    ASCIIDOC("asciidoc", "adoc", "asciidoc"),
    ASCIIDOCTOR("asciidoctor", "adoc"),
    TEXTILE("textile", "textile"),
    MEDIAWIKI("mediawiki", "wiki"),
    DOKUWIKI("dokuwiki"),
    JIRA("jira"),

    // ── E-book ────────────────────────────────────────────────────────────────
    EPUB("epub", "epub"),
    EPUB2("epub2", "epub"),
    EPUB3("epub3", "epub"),
    FB2("fb2", "fb2"),

    // ── Presentation ─────────────────────────────────────────────────────────
    REVEALJS("revealjs", "html"),
    SLIDY("slidy", "html"),
    SLIDEOUS("slideous", "html"),
    S5("s5", "html"),
    DZSLIDES("dzslides", "html"),

    // ── Data ──────────────────────────────────────────────────────────────────
    CSV("csv", "csv"),
    TSV("tsv", "tsv"),
    JSON("json", "json"),

    // ── DocBook / JATS ────────────────────────────────────────────────────────
    DOCBOOK("docbook", "xml"),
    DOCBOOK4("docbook4", "xml"),
    DOCBOOK5("docbook5", "xml"),
    JATS("jats", "xml"),

    // ── Notebook ──────────────────────────────────────────────────────────────
    IPYNB("ipynb", "ipynb"),

    // ── Man page ──────────────────────────────────────────────────────────────
    MAN("man"),

    // ── RTF ───────────────────────────────────────────────────────────────────
    RTF("rtf", "rtf"),

    // ── Typst ─────────────────────────────────────────────────────────────────
    TYPST("typst", "typ"),

    // ── Native Pandoc AST ─────────────────────────────────────────────────────
    NATIVE("native", "native"),

    // ── OpenXML / OPML ────────────────────────────────────────────────────────
    OPML("opml", "opml");

    private final String pandocName;
    private final String[] extensions;

    Format(String pandocName, String... extensions) {
        this.pandocName = pandocName;
        this.extensions = extensions;
    }

    /**
     * Returns the format identifier used in Pandoc CLI (e.g. {@code --from=markdown}).
     */
    public String getPandocName() {
        return pandocName;
    }

    /**
     * Returns the primary file extension for this format, or an empty string if none.
     */
    public String getPrimaryExtension() {
        return extensions.length > 0 ? extensions[0] : "";
    }

    /**
     * Resolves a {@link Format} from a file extension (without leading dot).
     *
     * <p>Example: {@code Format.fromExtension("docx")} returns {@code Optional.of(Format.DOCX)}.
     */
    public static Optional<Format> fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return Optional.empty();
        }
        String lower = extension.toLowerCase().replaceFirst("^\\.", "");
        return Arrays.stream(values())
                .filter(f -> Arrays.asList(f.extensions).contains(lower))
                .findFirst();
    }

    /**
     * Resolves a {@link Format} from a Pandoc format name string (e.g. {@code "gfm"}).
     */
    public static Optional<Format> fromPandocName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(f -> f.pandocName.equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return pandocName;
    }
}
