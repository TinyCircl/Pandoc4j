package org.tinycircl.pandoc4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-processing utility that sanitises Markdown output produced by Pandoc.
 *
 * <p>Pandoc faithfully preserves source-document metadata — for example,
 * accessibility alt-text from PowerPoint slides — which can occasionally
 * produce syntactically broken Markdown. {@code MarkdownCleaner} applies a set
 * of targeted, safe transformations to restore well-formed Markdown without
 * altering document content.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Apply all default rules in one call
 * String clean = MarkdownCleaner.clean(pandocOutput);
 *
 * // Or integrate directly with the conversion builder
 * String clean = Pandoc4j.builder()
 *         .from(Format.PPTX)
 *         .to(Format.MARKDOWN_GFM)
 *         .wrapNone()
 *         .cleanMarkdown()
 *         .convertFile(path);
 *
 * // Fine-grained control via the cleaner's own builder
 * String clean = MarkdownCleaner.builder()
 *         .fixInlineNewlines(true)
 *         .collapseBlankLines(true)
 *         .maxConsecutiveBlankLines(1)
 *         .clean(pandocOutput);
 * }</pre>
 *
 * <h2>Built-in rules</h2>
 * <ul>
 *   <li><b>fixInlineNewlines</b> — Collapses embedded newlines in image alt-text or
 *       hyperlink text into a single space. Pandoc preserves multi-line accessibility
 *       descriptions from PPTX slides verbatim, breaking the
 *       {@code ![alt](url)} syntax.</li>
 *   <li><b>collapseBlankLines</b> — Reduces runs of more than
 *       {@code maxConsecutiveBlankLines} (default&nbsp;2) consecutive blank lines to
 *       exactly that many. Pandoc sometimes inserts extra blank lines between
 *       PPTX slide sections.</li>
 * </ul>
 */
public final class MarkdownCleaner {

    private MarkdownCleaner() {}

    /**
     * Matches an inline image {@code ![alt](dest)} or hyperlink {@code [text](dest)},
     * capturing:
     * <ol>
     *   <li>The opening bracket (with optional {@code !}): {@code ![ } or {@code [}</li>
     *   <li>The alt / link text — may span multiple lines</li>
     *   <li>The closing {@code ](dest "title")} part</li>
     * </ol>
     *
     * <p>Notes:
     * <ul>
     *   <li>Java character classes ({@code [^\[\]]}) match {@code \n} by default, so
     *       DOTALL is not needed for group&nbsp;2.</li>
     *   <li>Nested brackets inside alt-text are intentionally excluded; Pandoc does not
     *       produce them in practice.</li>
     *   <li>The destination group uses {@code [^)]*} which is sufficient for standard
     *       Pandoc output. URLs containing unescaped {@code )} are not handled.</li>
     * </ul>
     */
    private static final Pattern INLINE_LINK_OR_IMAGE = Pattern.compile(
            "(!?\\[)([^\\[\\]]+)(\\]\\s*\\([^)]*\\))"
    );

    /**
     * Applies all default cleanup rules and returns the sanitised Markdown.
     *
     * <p>Equivalent to {@code MarkdownCleaner.builder().clean(markdown)}.
     *
     * @param markdown raw Pandoc output; {@code null} is returned as-is
     * @return sanitised Markdown string
     */
    public static String clean(String markdown) {
        return builder().clean(markdown);
    }

    /**
     * Returns a {@link Builder} to selectively enable or disable individual rules.
     */
    public static Builder builder() {
        return new Builder();
    }

    // ── Package-private rule implementations (testable in isolation) ─────────

    /**
     * Normalises embedded newlines in inline image alt-text and hyperlink text.
     *
     * <p>Each run of {@code \r\n}, {@code \r}, or {@code \n} characters inside
     * a {@code [...]} block is collapsed into a single ASCII space, and leading /
     * trailing whitespace is trimmed from the resulting text.
     *
     * <p>Input example (broken):
     * <pre>
     * ![文本, 信件
     * 描述已自动生成](ppt/media/image1.png "内容占位符 4")
     * </pre>
     * Output (fixed):
     * <pre>
     * ![文本, 信件 描述已自动生成](ppt/media/image1.png "内容占位符 4")
     * </pre>
     */
    static String applyFixInlineNewlines(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        Matcher m = INLINE_LINK_OR_IMAGE.matcher(markdown);
        boolean found = false;
        while (m.find()) {
            if (containsNewline(m.group(2))) {
                found = true;
                break;
            }
        }
        if (!found) {
            return markdown;
        }

        m.reset();
        StringBuilder sb = new StringBuilder(markdown.length());
        while (m.find()) {
            String altText = m.group(2);
            if (containsNewline(altText)) {
                String cleaned = altText
                        .replaceAll("[\r\n]+", " ")
                        .replaceAll("[ \\t]{2,}", " ")
                        .trim();
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(m.group(1) + cleaned + m.group(3)));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Collapses runs of more than {@code maxConsecutive} consecutive blank lines
     * to exactly that many.
     *
     * <p>A "blank line" is a line that contains only whitespace (or nothing).
     * Pandoc may emit extra blank lines between PPTX slide sections.
     *
     * @param maxConsecutive maximum number of consecutive blank lines to keep (≥ 1)
     */
    static String applyCollapseBlankLines(String markdown, int maxConsecutive) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        // Build a regex that matches (maxConsecutive+1) or more blank lines.
        // A blank line is \r?\n followed by optional spaces/tabs and another \r?\n.
        String excess = "(\r?\n[ \t]*){" + (maxConsecutive + 2) + ",}";
        String replacement = "\n".repeat(maxConsecutive + 1);
        return markdown.replaceAll(excess, replacement);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean containsNewline(String s) {
        return s != null && (s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Fluent builder for configuring {@link MarkdownCleaner} rules.
     *
     * <p>All rules are enabled by default. Use the setter methods to opt out of
     * specific transformations.
     */
    public static final class Builder {

        private boolean fixInlineNewlines      = true;
        private boolean collapseBlankLines     = true;
        private int     maxConsecutiveBlankLines = 2;

        private Builder() {}

        /**
         * Controls whether embedded newlines in image alt-text and hyperlink text
         * are collapsed to a single space.
         *
         * <p>Default: {@code true}.
         */
        public Builder fixInlineNewlines(boolean enabled) {
            this.fixInlineNewlines = enabled;
            return this;
        }

        /**
         * Controls whether runs of excessive blank lines are collapsed.
         *
         * <p>Default: {@code true}.
         *
         * @see #maxConsecutiveBlankLines(int)
         */
        public Builder collapseBlankLines(boolean enabled) {
            this.collapseBlankLines = enabled;
            return this;
        }

        /**
         * Sets the maximum number of consecutive blank lines to keep when
         * {@link #collapseBlankLines(boolean)} is active.
         *
         * <p>Default: {@code 2}.
         *
         * @param max must be ≥ 1
         * @throws IllegalArgumentException if {@code max < 1}
         */
        public Builder maxConsecutiveBlankLines(int max) {
            if (max < 1) {
                throw new IllegalArgumentException("maxConsecutiveBlankLines must be >= 1, got: " + max);
            }
            this.maxConsecutiveBlankLines = max;
            return this;
        }

        /**
         * Applies the configured rules in order and returns the sanitised Markdown.
         *
         * @param markdown raw Pandoc output; {@code null} or empty strings are
         *                 returned unchanged
         * @return sanitised Markdown string
         */
        public String clean(String markdown) {
            if (markdown == null || markdown.isEmpty()) {
                return markdown;
            }
            String result = markdown;
            if (fixInlineNewlines) {
                result = applyFixInlineNewlines(result);
            }
            if (collapseBlankLines) {
                result = applyCollapseBlankLines(result, maxConsecutiveBlankLines);
            }
            return result;
        }
    }
}
