package org.tinycircl.pandoc4j.ast;

/**
 * Link or image target: a URL and an optional title.
 *
 * <p>In Pandoc JSON this appears as {@code ["https://example.com", "Link Title"]}.
 */
public record Target(String url, String title) {

    public static final Target EMPTY = new Target("", "");
}
