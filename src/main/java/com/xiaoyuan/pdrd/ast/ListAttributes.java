package com.xiaoyuan.pdrd.ast;

/**
 * Attributes for an ordered list: starting number, numbering style, and delimiter.
 *
 * <p>In Pandoc JSON this appears as {@code [startNumber, {"t":"NumberStyle"}, {"t":"Delim"}]}.
 */
public record ListAttributes(int startNumber, String numberStyle, String numberDelim) {

    public static final ListAttributes DEFAULT =
            new ListAttributes(1, "Decimal", "Period");
}
