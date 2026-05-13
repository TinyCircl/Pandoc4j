package org.tinycircl.pandoc4j.ast;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * The top-level Pandoc document, corresponding to the JSON structure:
 * <pre>{@code
 * {
 *   "pandoc-api-version": [1, 23, 1],
 *   "meta": { ... },
 *   "blocks": [ ... ]
 * }
 * }</pre>
 *
 * <p>Metadata values are kept as raw {@link JsonNode} objects because the meta
 * structure is recursive and highly variable. Use {@link #rawJson()} for full
 * access to the original JSON when you need to work with meta or table internals.
 */
public record PandocDocument(
        List<Integer> apiVersion,
        Map<String, JsonNode> meta,
        List<Block> blocks,
        JsonNode rawJson
) {

    /**
     * Returns the Pandoc API version as a dotted string, e.g. {@code "1.23.1"}.
     */
    public String apiVersionString() {
        if (apiVersion == null || apiVersion.isEmpty()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apiVersion.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(apiVersion.get(i));
        }
        return sb.toString();
    }

    /**
     * Convenience: collects all top-level headings (level 1 and 2) as plain text.
     */
    public List<String> headings() {
        return blocks.stream()
                .filter(b -> b instanceof Block.Header h && h.level() <= 2)
                .map(b -> ((Block.Header) b).inlines().stream()
                        .filter(i -> i instanceof Inline.Str)
                        .map(i -> ((Inline.Str) i).text())
                        .reduce("", (a, x) -> a + x))
                .toList();
    }
}
