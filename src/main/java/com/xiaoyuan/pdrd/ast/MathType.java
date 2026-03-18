package com.xiaoyuan.pdrd.ast;

/**
 * Pandoc math element type.
 */
public enum MathType {
    /** Inline math: rendered within a line of text (e.g. {@code $E=mc^2$}). */
    INLINE_MATH,
    /** Display math: rendered as a block (e.g. {@code $$\int_0^1 f(x)dx$$}). */
    DISPLAY_MATH
}
