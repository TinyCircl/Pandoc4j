package org.tinycircl.pandoc4j;

/**
 * Quick smoke-test entry point for pandoc4j.
 *
 * <p>Run this class to verify that Pandoc is correctly installed and reachable,
 * and to see the SDK in action.
 */
public class Pandoc4jDemo {

    public static void main(String[] args) {
        System.out.println("pandoc4j – Pandoc version: " + Pandoc4j.getVersion());

        String html = Pandoc4j.convertText(
                "# Hello from pandoc4j\n\nThis is **bold** and _italic_.",
                Format.MARKDOWN, Format.HTML5);
        System.out.println("\nMarkdown → HTML5:");
        System.out.println(html);

        var doc = Pandoc4j.toAst("# AST Demo\n\nHello **world**.", Format.MARKDOWN);
        System.out.println("AST headings: " + doc.headings());
        System.out.println("Pandoc API version: " + doc.apiVersionString());
    }
}
