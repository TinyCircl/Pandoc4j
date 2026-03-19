package org.tinycircl.pandoc4j.ast;

import org.tinycircl.pandoc4j.Format;
import org.tinycircl.pandoc4j.Pandoc4j;
import org.tinycircl.pandoc4j.core.PandocInstallation;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Phase 3: Pandoc JSON AST binding.
 */
@Tag("integration")
class PandocAstTest {

    private static PandocInstallation installation;

    @BeforeAll
    static void setup() {
        try {
            installation = PandocInstallation.detect();
        } catch (Exception ignored) {}
    }

    private static void assumePandocAvailable() {
        Assumptions.assumeTrue(installation != null && installation.isAvailable(),
                "Skipping: Pandoc not available");
    }

    // ── PandocAstMapper unit tests (no Pandoc needed) ─────────────────────

    @Test
    @Tag("unit")
    @DisplayName("PandocAstMapper.parse() deserializes a minimal document")
    void parseMinimalDocument() {
        String json = """
                {
                  "pandoc-api-version": [1, 23, 1],
                  "meta": {},
                  "blocks": [
                    {"t": "Para", "c": [
                      {"t": "Str", "c": "Hello"},
                      {"t": "Space"},
                      {"t": "Strong", "c": [{"t": "Str", "c": "World"}]}
                    ]}
                  ]
                }
                """;
        PandocAstMapper mapper = new PandocAstMapper();
        PandocDocument doc = mapper.parse(json);

        assertEquals(List.of(1, 23, 1), doc.apiVersion());
        assertEquals(1, doc.blocks().size());
        assertInstanceOf(Block.Para.class, doc.blocks().get(0));

        Block.Para para = (Block.Para) doc.blocks().get(0);
        assertEquals(3, para.inlines().size());
        assertInstanceOf(Inline.Str.class, para.inlines().get(0));
        assertEquals("Hello", ((Inline.Str) para.inlines().get(0)).text());
        assertInstanceOf(Inline.Space.class, para.inlines().get(1));
        assertInstanceOf(Inline.Strong.class, para.inlines().get(2));
    }

    @Test
    @Tag("unit")
    @DisplayName("PandocAstMapper.parse() deserializes a Header node")
    void parseHeader() {
        String json = """
                {
                  "pandoc-api-version": [1, 23, 1],
                  "meta": {},
                  "blocks": [
                    {"t": "Header", "c": [2, ["my-id", ["cls"], []], [{"t": "Str", "c": "Title"}]]}
                  ]
                }
                """;
        PandocDocument doc = new PandocAstMapper().parse(json);
        assertInstanceOf(Block.Header.class, doc.blocks().get(0));
        Block.Header h = (Block.Header) doc.blocks().get(0);
        assertEquals(2, h.level());
        assertEquals("my-id", h.attr().id());
        assertEquals(List.of("cls"), h.attr().classes());
        assertEquals("Title", ((Inline.Str) h.inlines().get(0)).text());
    }

    @Test
    @Tag("unit")
    @DisplayName("PandocAstMapper.parse() handles CodeBlock")
    void parseCodeBlock() {
        String json = """
                {
                  "pandoc-api-version": [1, 23, 1],
                  "meta": {},
                  "blocks": [
                    {"t": "CodeBlock", "c": [["", ["java"], []], "int x = 42;"]}
                  ]
                }
                """;
        PandocDocument doc = new PandocAstMapper().parse(json);
        assertInstanceOf(Block.CodeBlock.class, doc.blocks().get(0));
        Block.CodeBlock cb = (Block.CodeBlock) doc.blocks().get(0);
        assertEquals(List.of("java"), cb.attr().classes());
        assertEquals("int x = 42;", cb.text());
    }

    @Test
    @Tag("unit")
    @DisplayName("PandocAstMapper.parse() maps Link and Image inlines")
    void parseLinkAndImage() {
        String json = """
                {
                  "pandoc-api-version": [1, 23, 1],
                  "meta": {},
                  "blocks": [
                    {"t": "Para", "c": [
                      {"t": "Link", "c": [["", [], []], [{"t": "Str", "c": "Click"}], ["https://example.com", "My Link"]]},
                      {"t": "Image", "c": [["", [], []], [{"t": "Str", "c": "Alt"}], ["img.png", "My Image"]]}
                    ]}
                  ]
                }
                """;
        PandocDocument doc = new PandocAstMapper().parse(json);
        Block.Para para = (Block.Para) doc.blocks().get(0);

        Inline.Link link = (Inline.Link) para.inlines().get(0);
        assertEquals("https://example.com", link.target().url());
        assertEquals("My Link", link.target().title());
        assertEquals("Click", ((Inline.Str) link.inlines().get(0)).text());

        Inline.Image img = (Inline.Image) para.inlines().get(1);
        assertEquals("img.png", img.target().url());
        assertEquals("Alt", ((Inline.Str) img.alt().get(0)).text());
    }

    @Test
    @Tag("unit")
    @DisplayName("PandocAstMapper.parse() preserves Unknown nodes")
    void parseUnknownNode() {
        String json = """
                {
                  "pandoc-api-version": [1, 23, 1],
                  "meta": {},
                  "blocks": [
                    {"t": "FutureBlockType", "c": "some future content"}
                  ]
                }
                """;
        PandocDocument doc = new PandocAstMapper().parse(json);
        assertInstanceOf(Block.Unknown.class, doc.blocks().get(0));
        Block.Unknown u = (Block.Unknown) doc.blocks().get(0);
        assertEquals("FutureBlockType", u.type());
        assertNotNull(u.rawContent());
    }

    @Test
    @Tag("unit")
    @DisplayName("PandocAstMapper serialize/parse round-trip preserves structure")
    void roundTripMapper() {
        String original = """
                {
                  "pandoc-api-version": [1, 23, 1],
                  "meta": {},
                  "blocks": [
                    {"t": "Header", "c": [1, ["intro", [], []], [{"t": "Str", "c": "Introduction"}]]},
                    {"t": "Para", "c": [{"t": "Str", "c": "Some text."}]},
                    {"t": "BulletList", "c": [
                      [{"t": "Plain", "c": [{"t": "Str", "c": "Item one"}]}],
                      [{"t": "Plain", "c": [{"t": "Str", "c": "Item two"}]}]
                    ]}
                  ]
                }
                """;
        PandocAstMapper mapper = new PandocAstMapper();
        PandocDocument doc = mapper.parse(original);

        // Re-serialize and re-parse
        String reserialized = mapper.serialize(doc);
        PandocDocument doc2 = mapper.parse(reserialized);

        assertEquals(doc.blocks().size(), doc2.blocks().size());
        assertInstanceOf(Block.Header.class, doc2.blocks().get(0));
        assertInstanceOf(Block.Para.class,   doc2.blocks().get(1));
        assertInstanceOf(Block.BulletList.class, doc2.blocks().get(2));
    }

    // ── Integration tests (Pandoc required) ───────────────────────────────

    @Test
    @DisplayName("Pandoc4j.toAst(text) parses Markdown headings and paragraphs")
    void toAstFromText() {
        assumePandocAvailable();
        String md = "# Title\n\nHello **world**.\n\n## Section\n\nMore text.";
        PandocDocument doc = Pandoc4j.toAst(md, Format.MARKDOWN);

        assertNotNull(doc);
        assertFalse(doc.apiVersion().isEmpty(), "API version must be present");
        assertFalse(doc.blocks().isEmpty(), "Blocks must not be empty");

        long headings = doc.blocks().stream()
                .filter(b -> b instanceof Block.Header).count();
        assertEquals(2, headings, "Should have 2 headers");

        List<String> titles = doc.headings();
        assertTrue(titles.stream().anyMatch(t -> t.contains("Title")));
    }

    @Test
    @DisplayName("Pandoc4j.toAst(file) parses a Markdown file")
    void toAstFromFile() throws IOException {
        assumePandocAvailable();
        Path tmp = Files.createTempFile("pandoc4j-ast-", ".md");
        try {
            Files.writeString(tmp, "# AST File Test\n\nParagraph content.\n");
            PandocDocument doc = Pandoc4j.toAst(tmp);
            assertFalse(doc.blocks().isEmpty());
            assertInstanceOf(Block.Header.class, doc.blocks().get(0));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("Pandoc4j.fromAst() renders PandocDocument to HTML5")
    void fromAstToHtml() {
        assumePandocAvailable();
        PandocDocument doc = Pandoc4j.toAst("# Hello\n\nWorld.", Format.MARKDOWN);
        String html = Pandoc4j.fromAst(doc, Format.HTML5);
        assertNotNull(html);
        assertTrue(html.contains("<h1"), "Should contain h1 tag");
        assertTrue(html.contains("Hello"), "Should contain heading text");
    }

    @Test
    @DisplayName("AST round-trip: MD → AST → MD preserves content")
    void fullRoundTrip() {
        assumePandocAvailable();
        String input = "# Heading\n\nA paragraph with **bold** text.\n\n- Item 1\n- Item 2\n";
        PandocDocument doc = Pandoc4j.toAst(input, Format.MARKDOWN);
        String output = Pandoc4j.fromAst(doc, Format.MARKDOWN_GFM);

        assertNotNull(output);
        assertTrue(output.contains("Heading"),  "Round-trip must preserve heading");
        assertTrue(output.contains("bold"),     "Round-trip must preserve bold text");
        assertTrue(output.contains("Item 1"),   "Round-trip must preserve list item 1");
        assertTrue(output.contains("Item 2"),   "Round-trip must preserve list item 2");
    }

    @Test
    @DisplayName("AST Java manipulation: inject a new paragraph before rendering")
    void astManipulation() {
        assumePandocAvailable();
        PandocDocument doc = Pandoc4j.toAst("# Original\n\nOriginal content.", Format.MARKDOWN);

        // Programmatically add a new paragraph to the beginning
        List<Block> modified = new java.util.ArrayList<>(doc.blocks());
        modified.add(0, new Block.Para(List.of(
                new Inline.Strong(List.of(new Inline.Str("Injected paragraph")))
        )));

        PandocDocument modifiedDoc = new PandocDocument(
                doc.apiVersion(), doc.meta(), modified, null);

        String html = Pandoc4j.fromAst(modifiedDoc, Format.HTML5);
        assertTrue(html.contains("Injected paragraph"), "Injected paragraph must appear in output");
        assertTrue(html.contains("Original"),            "Original content must still be present");
    }

    @Test
    @DisplayName("PandocDocument.apiVersionString() returns dotted version")
    void apiVersionString() {
        String json = """
                {"pandoc-api-version": [1, 23, 1], "meta": {}, "blocks": []}
                """;
        PandocDocument doc = new PandocAstMapper().parse(json);
        assertEquals("1.23.1", doc.apiVersionString());
    }
}
