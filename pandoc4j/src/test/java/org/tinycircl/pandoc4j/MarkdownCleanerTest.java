package org.tinycircl.pandoc4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownCleanerTest {

    // ── fixInlineNewlines ─────────────────────────────────────────────────────

    @Test
    void fixInlineNewlines_imageWithNewlineInAlt() {
        String input = "![文本, 信件\n描述已自动生成](ppt/media/image1.png \"内容占位符 4\")";
        String result = MarkdownCleaner.clean(input);
        assertEquals("![文本, 信件 描述已自动生成](ppt/media/image1.png \"内容占位符 4\")", result);
    }

    @Test
    void fixInlineNewlines_imageWithCrLfInAlt() {
        String input = "![line one\r\nline two](img.png)";
        String result = MarkdownCleaner.clean(input);
        assertEquals("![line one line two](img.png)", result);
    }

    @Test
    void fixInlineNewlines_multipleNewlinesCollapsedToOne() {
        String input = "![a\n\nb](img.png)";
        String result = MarkdownCleaner.clean(input);
        assertEquals("![a b](img.png)", result);
    }

    @Test
    void fixInlineNewlines_linkTextWithNewline() {
        String input = "[click\nhere](https://example.com)";
        String result = MarkdownCleaner.clean(input);
        assertEquals("[click here](https://example.com)", result);
    }

    @Test
    void fixInlineNewlines_normalImageUnchanged() {
        String input = "![图示描述已自动生成](ppt/media/image4.png \"图片 16\")";
        String result = MarkdownCleaner.clean(input);
        assertEquals(input, result);
    }

    @Test
    void fixInlineNewlines_emptyAltUnchanged() {
        String input = "![](ppt/media/image4.png)";
        String result = MarkdownCleaner.clean(input);
        assertEquals(input, result);
    }

    @Test
    void fixInlineNewlines_multipleImagesInDocument() {
        String input = "## Slide 1\n\n"
                + "![文本\n描述](ppt/media/image1.png \"占位符\")\n\n"
                + "## Slide 2\n\n"
                + "![正常描述](ppt/media/image2.png)\n\n"
                + "![多行\n内容\n描述](ppt/media/image3.png)";
        String result = MarkdownCleaner.clean(input);
        assertTrue(result.contains("![文本 描述](ppt/media/image1.png \"占位符\")"));
        assertTrue(result.contains("![正常描述](ppt/media/image2.png)"));
        assertTrue(result.contains("![多行 内容 描述](ppt/media/image3.png)"));
    }

    @Test
    void fixInlineNewlines_leadingTrailingSpaceTrimmed() {
        String input = "![\n  alt text  \n](img.png)";
        String result = MarkdownCleaner.clean(input);
        assertEquals("![alt text](img.png)", result);
    }

    // ── collapseBlankLines ────────────────────────────────────────────────────

    @Test
    void collapseBlankLines_threeBlankLinesCollapsedToTwo() {
        String input = "para one\n\n\n\npara two";
        String result = MarkdownCleaner.clean(input);
        // 4 newlines (\n\n\n\n) = 3 blank lines → collapse to 2 blank lines (\n\n\n)
        assertFalse(result.contains("\n\n\n\n"), "should not contain 4 consecutive newlines");
    }

    @Test
    void collapseBlankLines_twoBlankLinesUnchanged() {
        String input = "para one\n\n\npara two";
        String result = MarkdownCleaner.clean(input);
        assertEquals(input, result);
    }

    @Test
    void collapseBlankLines_customMax() {
        String input = "a\n\n\nb";
        String result = MarkdownCleaner.builder()
                .maxConsecutiveBlankLines(1)
                .clean(input);
        assertFalse(result.contains("\n\n\n"), "should not contain 3 consecutive newlines with max=1");
    }

    // ── Builder API ───────────────────────────────────────────────────────────

    @Test
    void builder_disableFixInlineNewlines() {
        String input = "![a\nb](img.png)";
        String result = MarkdownCleaner.builder()
                .fixInlineNewlines(false)
                .clean(input);
        assertEquals(input, result, "rule disabled: input should be unchanged");
    }

    @Test
    void builder_disableCollapseBlankLines() {
        String input = "a\n\n\n\nb";
        String result = MarkdownCleaner.builder()
                .collapseBlankLines(false)
                .clean(input);
        assertEquals(input, result, "rule disabled: input should be unchanged");
    }

    @Test
    void builder_nullInputReturnedAsIs() {
        assertNull(MarkdownCleaner.clean(null));
    }

    @Test
    void builder_emptyInputReturnedAsIs() {
        assertEquals("", MarkdownCleaner.clean(""));
    }

    @Test
    void builder_invalidMaxThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MarkdownCleaner.builder().maxConsecutiveBlankLines(0));
    }

    // ── Real-world PPTX pattern ───────────────────────────────────────────────

    @Test
    void realWorldPptxSlidePattern() {
        String input = "## Slide 5\n\n"
                + "![](ppt/media/image4.png \"图片 16\")\n\n"
                + "## Slide 6\n\n"
                + "![](ppt/media/image5.png \"图片 1\")\n\n"
                + "![文本, 信件\n描述已自动生成](ppt/media/image1.png \"内容占位符 4\")\n\n"
                + "## Slide 7";
        String result = MarkdownCleaner.clean(input);
        // Normal images unchanged
        assertTrue(result.contains("![](ppt/media/image4.png \"图片 16\")"));
        assertTrue(result.contains("![](ppt/media/image5.png \"图片 1\")"));
        // Broken alt text fixed
        assertTrue(result.contains("![文本, 信件 描述已自动生成](ppt/media/image1.png \"内容占位符 4\")"));
        // No stray newlines inside [...]
        assertFalse(result.matches("(?s).*!\\[[^\\]]*\\n[^\\]]*\\].*"));
    }
}
