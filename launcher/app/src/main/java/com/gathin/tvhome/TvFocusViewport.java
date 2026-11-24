package com.gathin.tvhome;

/** Pure vertical focus geometry; no platform dependencies, usable in JVM tests. */
final class TvFocusViewport {
    private TvFocusViewport() {}

    /**
     * @return the signed scroll adjustment that completely exposes a focused
     * row plus its focus outline. Oversize rows are aligned at the top.
     */
    static int scrollDelta(int top, int bottom, int currentScroll, int viewportHeight, int inset) {
        if (viewportHeight <= 0) return 0;
        int visibleTop = currentScroll + inset;
        int visibleBottom = currentScroll + viewportHeight - inset;
        if (bottom - top > viewportHeight - 2 * inset) return top - visibleTop;
        if (bottom > visibleBottom) return bottom - visibleBottom;
        if (top < visibleTop) return top - visibleTop;
        return 0;
    }
}
