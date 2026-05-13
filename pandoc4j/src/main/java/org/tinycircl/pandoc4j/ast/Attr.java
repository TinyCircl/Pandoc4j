package org.tinycircl.pandoc4j.ast;

import java.util.List;
import java.util.Map;

/**
 * Pandoc element attribute triple: identifier, CSS classes, and key-value pairs.
 *
 * <p>In Pandoc JSON this appears as {@code ["id", ["class1","class2"], [["key","val"]]]}.
 */
public record Attr(String id, List<String> classes, Map<String, String> attributes) {

    /** The empty attribute (no id, no classes, no key-values). */
    public static final Attr EMPTY = new Attr("", List.of(), Map.of());

    /** Returns {@code true} if all fields are empty. */
    public boolean isEmpty() {
        return id.isBlank() && classes.isEmpty() && attributes.isEmpty();
    }
}
