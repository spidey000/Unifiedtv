package com.gathin.tvhome;

/** Geometry shared by horizontally scrolling shelves and the full Watch Next grid. */
final class UnifiedCatalogLayout {
    static final int COLUMNS = 6;
    static final int GRID_ROW_GAP_DP = 12;

    /** Width of a 2:3 poster; a seventh tile must not enter the visible shelf. */
    static int posterWidthDp(int screenDp, int railDp, int startDp, int endDp, int gapDp) {
        int usable = Math.max(1, screenDp - railDp - startDp - endDp);
        return Math.max(1, (usable - (COLUMNS - 1) * gapDp) / COLUMNS);
    }

    static int posterHeightDp(int posterWidthDp) {
        return Math.max(1, Math.round(posterWidthDp * 1.5f));
    }

    static int row(int index) { return index / COLUMNS; }
    static int column(int index) { return index % COLUMNS; }

    private UnifiedCatalogLayout() {}
}
