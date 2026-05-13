package org.tinycircl.pandoc4j.ast;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.tinycircl.pandoc4j.exception.PandocException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between Pandoc JSON AST ({@link JsonNode}) and the pandoc4j Java document model.
 *
 * <h2>Pandoc JSON node shape</h2>
 * Most nodes follow the pattern {@code {"t":"TypeName","c":content}} where
 * {@code content} varies per type. Terminal nodes like {@code Space} have no
 * {@code "c"} field.
 *
 * <h2>Thread safety</h2>
 * This class is stateless; a single instance may be shared across threads.
 */
public class PandocAstMapper {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    // ── Parse: JSON → Java model ──────────────────────────────────────────

    /**
     * Parses a Pandoc JSON string into a {@link PandocDocument}.
     *
     * @throws PandocException if the JSON is malformed or missing required fields
     */
    public PandocDocument parse(String json) {
        try {
            return parse(MAPPER.readTree(json));
        } catch (JacksonException e) {
            throw new PandocException("Failed to parse Pandoc JSON AST: " + e.getMessage(), e);
        }
    }

    /** Parses an already-read {@link JsonNode} into a {@link PandocDocument}. */
    public PandocDocument parse(JsonNode root) {
        List<Integer> apiVersion = new ArrayList<>();
        if (root.has("pandoc-api-version")) {
            root.get("pandoc-api-version").forEach(v -> apiVersion.add(v.asInt()));
        }

        Map<String, JsonNode> meta = new LinkedHashMap<>();
        if (root.has("meta")) {
            root.get("meta").properties().forEach(e -> meta.put(e.getKey(), e.getValue()));
        }

        List<Block> blocks = parseBlocks(root.path("blocks"));

        return new PandocDocument(apiVersion, meta, blocks, root);
    }

    // ── Blocks ────────────────────────────────────────────────────────────

    private List<Block> parseBlocks(JsonNode array) {
        List<Block> result = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> result.add(parseBlock(n)));
        }
        return result;
    }

    private Block parseBlock(JsonNode node) {
        String type = node.path("t").asString();
        JsonNode c = node.path("c");

        return switch (type) {
            case "Para"      -> new Block.Para(parseInlines(c));
            case "Plain"     -> new Block.Plain(parseInlines(c));
            case "LineBlock" -> {
                List<List<Inline>> lines = new ArrayList<>();
                c.forEach(line -> lines.add(parseInlines(line)));
                yield new Block.LineBlock(lines);
            }
            case "CodeBlock"     -> new Block.CodeBlock(parseAttr(c.get(0)), c.get(1).asString());
            case "RawBlock"      -> new Block.RawBlock(c.get(0).asString(), c.get(1).asString());
            case "BlockQuote"    -> new Block.BlockQuote(parseBlocks(c));
            case "BulletList"    -> {
                List<List<Block>> items = new ArrayList<>();
                c.forEach(item -> items.add(parseBlocks(item)));
                yield new Block.BulletList(items);
            }
            case "OrderedList"   -> {
                ListAttributes la = parseListAttributes(c.get(0));
                List<List<Block>> items = new ArrayList<>();
                c.get(1).forEach(item -> items.add(parseBlocks(item)));
                yield new Block.OrderedList(la, items);
            }
            case "DefinitionList" -> {
                List<Block.DefinitionItem> defs = new ArrayList<>();
                c.forEach(item -> {
                    List<Inline> term = parseInlines(item.get(0));
                    List<List<Block>> definitions = new ArrayList<>();
                    item.get(1).forEach(d -> definitions.add(parseBlocks(d)));
                    defs.add(new Block.DefinitionItem(term, definitions));
                });
                yield new Block.DefinitionList(defs);
            }
            case "Header"        -> new Block.Header(c.get(0).asInt(), parseAttr(c.get(1)), parseInlines(c.get(2)));
            case "HorizontalRule" -> new Block.HorizontalRule();
            case "Table"         -> {
                // Tables are complex; store raw JSON but try to extract caption inlines
                List<Inline> caption = new ArrayList<>();
                JsonNode captionNode = c.path(1).path("c").path(1);
                if (captionNode.isArray()) {
                    caption.addAll(parseInlines(captionNode));
                }
                yield new Block.Table(caption, node);
            }
            case "Figure"        -> {
                Attr attr = parseAttr(c.get(0));
                List<Inline> caption = parseInlines(c.path(1).path("c").path(1));
                List<Block> body = parseBlocks(c.get(2));
                yield new Block.Figure(attr, caption, body);
            }
            case "Div"           -> new Block.Div(parseAttr(c.get(0)), parseBlocks(c.get(1)));
            default              -> new Block.Unknown(type, node);
        };
    }

    // ── Inlines ───────────────────────────────────────────────────────────

    private List<Inline> parseInlines(JsonNode array) {
        List<Inline> result = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> result.add(parseInline(n)));
        }
        return result;
    }

    private Inline parseInline(JsonNode node) {
        String type = node.path("t").asString();
        JsonNode c = node.path("c");

        return switch (type) {
            case "Str"         -> new Inline.Str(c.asString());
            case "Space"       -> new Inline.Space();
            case "SoftBreak"   -> new Inline.SoftBreak();
            case "LineBreak"   -> new Inline.LineBreak();
            case "Emph"        -> new Inline.Emph(parseInlines(c));
            case "Strong"      -> new Inline.Strong(parseInlines(c));
            case "Strikeout"   -> new Inline.Strikeout(parseInlines(c));
            case "Superscript" -> new Inline.Superscript(parseInlines(c));
            case "Subscript"   -> new Inline.Subscript(parseInlines(c));
            case "SmallCaps"   -> new Inline.SmallCaps(parseInlines(c));
            case "Quoted"      -> new Inline.Quoted(c.get(0).path("t").asString(), parseInlines(c.get(1)));
            case "Code"        -> new Inline.Code(parseAttr(c.get(0)), c.get(1).asString());
            case "Math"        -> {
                String mathTypeStr = c.get(0).path("t").asString();
                MathType mt = "DisplayMath".equals(mathTypeStr) ? MathType.DISPLAY_MATH : MathType.INLINE_MATH;
                yield new Inline.Math(mt, c.get(1).asString());
            }
            case "RawInline"   -> new Inline.RawInline(c.get(0).asString(), c.get(1).asString());
            case "Link"        -> new Inline.Link(parseAttr(c.get(0)), parseInlines(c.get(1)), parseTarget(c.get(2)));
            case "Image"       -> new Inline.Image(parseAttr(c.get(0)), parseInlines(c.get(1)), parseTarget(c.get(2)));
            case "Note"        -> new Inline.Note(parseBlocks(c));
            case "Span"        -> new Inline.Span(parseAttr(c.get(0)), parseInlines(c.get(1)));
            default            -> new Inline.Unknown(type, node);
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Parses {@code ["id", ["classes"], [["key","val"]]]} into {@link Attr}. */
    private Attr parseAttr(JsonNode node) {
        if (node == null || !node.isArray()) return Attr.EMPTY;
        String id = node.path(0).asString("");
        List<String> classes = new ArrayList<>();
        node.path(1).forEach(c -> classes.add(c.asString()));
        Map<String, String> kvs = new LinkedHashMap<>();
        node.path(2).forEach(kv -> kvs.put(kv.path(0).asString(), kv.path(1).asString()));
        return new Attr(id, classes, kvs);
    }

    /** Parses {@code ["url", "title"]} into {@link Target}. */
    private Target parseTarget(JsonNode node) {
        if (node == null || !node.isArray()) return Target.EMPTY;
        return new Target(node.path(0).asString(""), node.path(1).asString(""));
    }

    /** Parses {@code [startNum, {t:style}, {t:delim}]} into {@link ListAttributes}. */
    private ListAttributes parseListAttributes(JsonNode node) {
        if (node == null || !node.isArray()) return ListAttributes.DEFAULT;
        int start = node.path(0).asInt(1);
        String style = node.path(1).path("t").asString("DefaultStyle");
        String delim = node.path(2).path("t").asString("DefaultDelim");
        return new ListAttributes(start, style, delim);
    }

    // ── Serialize: Java model → JSON ──────────────────────────────────────

    /**
     * Serializes a {@link PandocDocument} back to Pandoc JSON string.
     * This JSON can be piped into {@code pandoc --from=json} for rendering.
     *
     * @throws PandocException if serialization fails
     */
    public String serialize(PandocDocument doc) {
        try {
            return MAPPER.writeValueAsString(serializeDocument(doc));
        } catch (JacksonException e) {
            throw new PandocException("Failed to serialize PandocDocument to JSON: " + e.getMessage(), e);
        }
    }

    private JsonNode serializeDocument(PandocDocument doc) {
        // If rawJson is available, use it as the base and only re-serialize blocks
        if (doc.rawJson() != null && !doc.rawJson().isMissingNode()) {
            ObjectNode root = (ObjectNode) doc.rawJson().deepCopy();
            root.set("blocks", serializeBlocks(doc.blocks()));
            return root;
        }

        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode version = root.putArray("pandoc-api-version");
        if (doc.apiVersion() != null) {
            doc.apiVersion().forEach(version::add);
        }
        ObjectNode meta = root.putObject("meta");
        if (doc.meta() != null) {
            doc.meta().forEach(meta::set);
        }
        root.set("blocks", serializeBlocks(doc.blocks()));
        return root;
    }

    private ArrayNode serializeBlocks(List<Block> blocks) {
        ArrayNode arr = MAPPER.createArrayNode();
        if (blocks != null) {
            blocks.stream().map(this::serializeBlock).forEach(arr::add);
        }
        return arr;
    }

    private JsonNode serializeBlock(Block block) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (block) {
            case Block.Para p -> {
                node.put("t", "Para");
                node.set("c", serializeInlines(p.inlines()));
            }
            case Block.Plain p -> {
                node.put("t", "Plain");
                node.set("c", serializeInlines(p.inlines()));
            }
            case Block.Header h -> {
                node.put("t", "Header");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(h.level());
                c.add(serializeAttr(h.attr()));
                c.add(serializeInlines(h.inlines()));
                node.set("c", c);
            }
            case Block.CodeBlock cb -> {
                node.put("t", "CodeBlock");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeAttr(cb.attr()));
                c.add(cb.text());
                node.set("c", c);
            }
            case Block.RawBlock rb -> {
                node.put("t", "RawBlock");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(rb.format());
                c.add(rb.text());
                node.set("c", c);
            }
            case Block.BlockQuote bq -> {
                node.put("t", "BlockQuote");
                node.set("c", serializeBlocks(bq.blocks()));
            }
            case Block.BulletList bl -> {
                node.put("t", "BulletList");
                ArrayNode c = MAPPER.createArrayNode();
                bl.items().forEach(item -> c.add(serializeBlocks(item)));
                node.set("c", c);
            }
            case Block.OrderedList ol -> {
                node.put("t", "OrderedList");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeListAttributes(ol.listAttributes()));
                ArrayNode items = MAPPER.createArrayNode();
                ol.items().forEach(item -> items.add(serializeBlocks(item)));
                c.add(items);
                node.set("c", c);
            }
            case Block.HorizontalRule ignored -> node.put("t", "HorizontalRule");
            case Block.Div d -> {
                node.put("t", "Div");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeAttr(d.attr()));
                c.add(serializeBlocks(d.blocks()));
                node.set("c", c);
            }
            case Block.Table t -> {
                // Return the raw JSON if available
                if (t.rawTable() != null && !t.rawTable().isMissingNode()) {
                    return t.rawTable().deepCopy();
                }
                node.put("t", "Table");
            }
            case Block.Figure f -> {
                node.put("t", "Figure");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeAttr(f.attr()));
                c.add(serializeInlines(f.caption()));
                c.add(serializeBlocks(f.blocks()));
                node.set("c", c);
            }
            case Block.LineBlock lb -> {
                node.put("t", "LineBlock");
                ArrayNode c = MAPPER.createArrayNode();
                lb.lines().forEach(line -> c.add(serializeInlines(line)));
                node.set("c", c);
            }
            case Block.DefinitionList dl -> {
                node.put("t", "DefinitionList");
                ArrayNode c = MAPPER.createArrayNode();
                dl.items().forEach(item -> {
                    ArrayNode pair = MAPPER.createArrayNode();
                    pair.add(serializeInlines(item.term()));
                    ArrayNode defs = MAPPER.createArrayNode();
                    item.definitions().forEach(def -> defs.add(serializeBlocks(def)));
                    pair.add(defs);
                    c.add(pair);
                });
                node.set("c", c);
            }
            case Block.Unknown u -> {
                // Return raw JSON unchanged
                return u.rawContent().deepCopy();
            }
        }
        return node;
    }

    private ArrayNode serializeInlines(List<Inline> inlines) {
        ArrayNode arr = MAPPER.createArrayNode();
        if (inlines != null) {
            inlines.stream().map(this::serializeInline).forEach(arr::add);
        }
        return arr;
    }

    private JsonNode serializeInline(Inline inline) {
        ObjectNode node = MAPPER.createObjectNode();
        switch (inline) {
            case Inline.Str s      -> { node.put("t", "Str"); node.put("c", s.text()); }
            case Inline.Space ignored      -> node.put("t", "Space");
            case Inline.SoftBreak ignored  -> node.put("t", "SoftBreak");
            case Inline.LineBreak ignored  -> node.put("t", "LineBreak");
            case Inline.Emph e     -> { node.put("t", "Emph"); node.set("c", serializeInlines(e.inlines())); }
            case Inline.Strong s   -> { node.put("t", "Strong"); node.set("c", serializeInlines(s.inlines())); }
            case Inline.Strikeout s -> { node.put("t", "Strikeout"); node.set("c", serializeInlines(s.inlines())); }
            case Inline.Superscript s -> { node.put("t", "Superscript"); node.set("c", serializeInlines(s.inlines())); }
            case Inline.Subscript s -> { node.put("t", "Subscript"); node.set("c", serializeInlines(s.inlines())); }
            case Inline.SmallCaps s -> { node.put("t", "SmallCaps"); node.set("c", serializeInlines(s.inlines())); }
            case Inline.Quoted q   -> {
                node.put("t", "Quoted");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(MAPPER.createObjectNode().put("t", q.quoteType()));
                c.add(serializeInlines(q.inlines()));
                node.set("c", c);
            }
            case Inline.Code co    -> {
                node.put("t", "Code");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeAttr(co.attr()));
                c.add(co.text());
                node.set("c", c);
            }
            case Inline.Math m     -> {
                node.put("t", "Math");
                ArrayNode c = MAPPER.createArrayNode();
                String mtStr = m.mathType() == MathType.DISPLAY_MATH ? "DisplayMath" : "InlineMath";
                c.add(MAPPER.createObjectNode().put("t", mtStr));
                c.add(m.text());
                node.set("c", c);
            }
            case Inline.RawInline r -> {
                node.put("t", "RawInline");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(r.format()); c.add(r.text());
                node.set("c", c);
            }
            case Inline.Link l     -> {
                node.put("t", "Link");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeAttr(l.attr()));
                c.add(serializeInlines(l.inlines()));
                c.add(serializeTarget(l.target()));
                node.set("c", c);
            }
            case Inline.Image i    -> {
                node.put("t", "Image");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeAttr(i.attr()));
                c.add(serializeInlines(i.alt()));
                c.add(serializeTarget(i.target()));
                node.set("c", c);
            }
            case Inline.Note n     -> { node.put("t", "Note"); node.set("c", serializeBlocks(n.blocks())); }
            case Inline.Span s     -> {
                node.put("t", "Span");
                ArrayNode c = MAPPER.createArrayNode();
                c.add(serializeAttr(s.attr()));
                c.add(serializeInlines(s.inlines()));
                node.set("c", c);
            }
            case Inline.Unknown u  -> { return u.rawContent().deepCopy(); }
        }
        return node;
    }

    /** Serializes {@link Attr} as {@code ["id", ["classes"], [["key","val"]]]}. */
    private JsonNode serializeAttr(Attr attr) {
        ArrayNode node = MAPPER.createArrayNode();
        node.add(attr == null ? "" : attr.id());
        ArrayNode classes = MAPPER.createArrayNode();
        if (attr != null) attr.classes().forEach(classes::add);
        node.add(classes);
        ArrayNode kvs = MAPPER.createArrayNode();
        if (attr != null) {
            attr.attributes().forEach((k, v) -> {
                ArrayNode kv = MAPPER.createArrayNode();
                kv.add(k); kv.add(v);
                kvs.add(kv);
            });
        }
        node.add(kvs);
        return node;
    }

    /** Serializes {@link Target} as {@code ["url", "title"]}. */
    private JsonNode serializeTarget(Target target) {
        ArrayNode node = MAPPER.createArrayNode();
        node.add(target == null ? "" : target.url());
        node.add(target == null ? "" : target.title());
        return node;
    }

    /** Serializes {@link ListAttributes} as {@code [start, {t:style}, {t:delim}]}. */
    private JsonNode serializeListAttributes(ListAttributes la) {
        ArrayNode node = MAPPER.createArrayNode();
        node.add(la.startNumber());
        node.add(MAPPER.createObjectNode().put("t", la.numberStyle()));
        node.add(MAPPER.createObjectNode().put("t", la.numberDelim()));
        return node;
    }

    // ── Shared singleton ──────────────────────────────────────────────────

    private static final PandocAstMapper INSTANCE = new PandocAstMapper();

    /** Returns a shared, thread-safe {@link PandocAstMapper} instance. */
    public static PandocAstMapper getInstance() {
        return INSTANCE;
    }
}
