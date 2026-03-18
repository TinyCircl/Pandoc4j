package com.xiaoyuan.pdrd.ast;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A Pandoc inline element.
 *
 * <p>Use pattern matching (Java 21) to handle specific node types:
 * <pre>{@code
 * switch (inline) {
 *     case Inline.Str s        -> process(s.text());
 *     case Inline.Emph e       -> process(e.inlines());
 *     case Inline.Link l       -> process(l.target().url());
 *     case Inline.Unknown u    -> // forward-compatible fallback
 *     default                  -> {}
 * }
 * }</pre>
 */
public sealed interface Inline permits
        Inline.Str, Inline.Emph, Inline.Strong, Inline.Strikeout,
        Inline.Superscript, Inline.Subscript, Inline.SmallCaps,
        Inline.Quoted, Inline.Code, Inline.Space, Inline.SoftBreak,
        Inline.LineBreak, Inline.Math, Inline.RawInline,
        Inline.Link, Inline.Image, Inline.Note, Inline.Span,
        Inline.Unknown {

    /** Plain text. */
    record Str(String text) implements Inline {}

    /** Inter-word space. */
    record Space() implements Inline {}

    /** Soft line break (rendered as a space in most formats). */
    record SoftBreak() implements Inline {}

    /** Hard line break. */
    record LineBreak() implements Inline {}

    /** Emphasized text. */
    record Emph(List<Inline> inlines) implements Inline {}

    /** Strongly emphasized text. */
    record Strong(List<Inline> inlines) implements Inline {}

    /** Strikethrough text. */
    record Strikeout(List<Inline> inlines) implements Inline {}

    /** Superscript text. */
    record Superscript(List<Inline> inlines) implements Inline {}

    /** Subscript text. */
    record Subscript(List<Inline> inlines) implements Inline {}

    /** Small caps text. */
    record SmallCaps(List<Inline> inlines) implements Inline {}

    /** Quoted text (single or double quotes). */
    record Quoted(String quoteType, List<Inline> inlines) implements Inline {}

    /** Inline code. */
    record Code(Attr attr, String text) implements Inline {}

    /** Inline math formula. */
    record Math(MathType mathType, String text) implements Inline {}

    /** Raw inline content in a specific format (e.g. raw HTML). */
    record RawInline(String format, String text) implements Inline {}

    /** Hyperlink. */
    record Link(Attr attr, List<Inline> inlines, Target target) implements Inline {}

    /** Image. */
    record Image(Attr attr, List<Inline> alt, Target target) implements Inline {}

    /** Footnote / endnote content. */
    record Note(List<Block> blocks) implements Inline {}

    /** Generic inline container with attributes. */
    record Span(Attr attr, List<Inline> inlines) implements Inline {}

    /**
     * Catch-all for node types not explicitly modeled here.
     * Preserves the raw JSON for forward compatibility with future Pandoc versions.
     */
    record Unknown(String type, JsonNode rawContent) implements Inline {}
}
