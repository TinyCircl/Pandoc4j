package com.xiaoyuan.pdrd.ast;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A Pandoc block element.
 *
 * <p>Use Java 21 pattern matching to handle specific node types:
 * <pre>{@code
 * switch (block) {
 *     case Block.Header h    -> System.out.println("H" + h.level() + ": " + h.inlines());
 *     case Block.Para p      -> System.out.println("Para: " + p.inlines());
 *     case Block.CodeBlock c -> System.out.println("Code: " + c.text());
 *     case Block.Unknown u   -> // forward-compatible fallback
 *     default                -> {}
 * }
 * }</pre>
 */
public sealed interface Block permits
        Block.Plain, Block.Para, Block.LineBlock, Block.CodeBlock,
        Block.RawBlock, Block.BlockQuote, Block.OrderedList, Block.BulletList,
        Block.DefinitionList, Block.Header, Block.HorizontalRule,
        Block.Table, Block.Figure, Block.Div, Block.Unknown {

    /** A paragraph. */
    record Para(List<Inline> inlines) implements Block {}

    /**
     * Plain text, not wrapped in a paragraph tag.
     * Used inside list items and other contexts.
     */
    record Plain(List<Inline> inlines) implements Block {}

    /** Multiple lines treated as a single block. */
    record LineBlock(List<List<Inline>> lines) implements Block {}

    /** A code block with optional language/attributes. */
    record CodeBlock(Attr attr, String text) implements Block {}

    /** Raw content in a specific format (e.g. raw HTML or LaTeX). */
    record RawBlock(String format, String text) implements Block {}

    /** A block quotation. */
    record BlockQuote(List<Block> blocks) implements Block {}

    /** An ordered list. */
    record OrderedList(ListAttributes listAttributes, List<List<Block>> items) implements Block {}

    /** A bullet (unordered) list. */
    record BulletList(List<List<Block>> items) implements Block {}

    /** A definition list: each item has a term and one or more definitions. */
    record DefinitionList(List<DefinitionItem> items) implements Block {}

    /** One item in a {@link DefinitionList}. */
    record DefinitionItem(List<Inline> term, List<List<Block>> definitions) {}

    /** A section heading. */
    record Header(int level, Attr attr, List<Inline> inlines) implements Block {}

    /** A horizontal rule. */
    record HorizontalRule() implements Block {}

    /**
     * A table.
     * {@code caption} holds optional caption inlines.
     * {@code head} and {@code body} contain raw JSON rows (complex structure);
     * use {@link Block.Unknown} semantics or access via {@link PandocDocument#rawJson()}.
     */
    record Table(List<Inline> caption, JsonNode rawTable) implements Block {}

    /** A figure with optional caption and body. */
    record Figure(Attr attr, List<Inline> caption, List<Block> blocks) implements Block {}

    /** A generic block container with attributes. */
    record Div(Attr attr, List<Block> blocks) implements Block {}

    /**
     * Catch-all for node types not explicitly modeled here.
     * Preserves the raw JSON for forward compatibility with future Pandoc versions.
     */
    record Unknown(String type, JsonNode rawContent) implements Block {}
}
