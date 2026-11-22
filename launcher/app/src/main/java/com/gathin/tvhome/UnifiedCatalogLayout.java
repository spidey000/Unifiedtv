package com.gathin.tvhome;

/** Geometry shared by horizontally scrolling shelves and the full Watch Next grid. */
final class UnifiedCatalogLayout {
    static final int COLUMNS = 6;
    static final int GRID_ROW_GAP_DP = 12;

    /** Separate landscape app banners from 2:3 content posters. */
    static final int APP_CELL_GAP_DP = 8;
    static final int APP_FOCUS_INSET_X_DP = 5;
    static final int APP_FOCUS_INSET_TOP_DP = 7;
    static final int APP_FOCUS_INSET_BOTTOM_DP = 9;
    static final int APP_LABEL_HEIGHT_DP = 27;

    /** Exactly six 16:9 app banners per row, reserving a focus-safe margin. */
    static int appBannerWidthDp(int screenDp, int railDp, int startDp, int endDp) {
        int usable = Math.max(1, screenDp - railDp - startDp - endDp);
        int spacing = COLUMNS * (APP_CELL_GAP_DP + 2 * APP_FOCUS_INSET_X_DP);
        return Math.max(1, (usable - spacing) / COLUMNS);
    }

    static int appBannerHeightDp(int widthDp) {
        return Math.max(1, Math.round(widthDp * 9f / 16f));
    }

    static int appCardHeightDp(int widthDp) {
        return appBannerHeightDp(widthDp) + APP_LABEL_HEIGHT_DP;
    }

    static int appRowStrideDp(int widthDp) {
        return appCardHeightDp(widthDp) + APP_FOCUS_INSET_TOP_DP
                + APP_FOCUS_INSET_BOTTOM_DP;
    }

    /** Width of a 2:3 poster; a seventh tile must not enter the visible shelf. */
    static int posterWidthDp(int screenDp, int railDp, int startDp, int endDp, int gapDp) {
        int usable = Math.max(1, screenDp - railDp - startDp - endDp);
        return Math.max(1, (usable - (COLUMNS - 1) * gapDp) / COLUMNS);
    }

    static int posterHeightDp(int posterWidthDp) {
        return Math.max(1, Math.round(posterWidthDp * 1.5f));
    }

    /** Reserve this fraction of the cell for the focus outline and 2.5% zoom. */
    static final int FOCUS_INSET_X_DP = 6;
    static final int FOCUS_INSET_TOP_DP = 9;

    /** Each carousel/grid cell remains fixed; its actual 2:3 poster sits inside it. */
    static int cardWidthDp(int slotWidthDp) {
        return Math.max(1, slotWidthDp - 2 * FOCUS_INSET_X_DP);
    }

    static int cardHeightDp(int slotWidthDp) {
        return posterHeightDp(cardWidthDp(slotWidthDp));
    }

    /** Bottom breathing space is derived to keep the overall cell height constant. */
    static int focusBottomInsetDp(int slotWidthDp) {
        return Math.max(0, posterHeightDp(slotWidthDp)
                - cardHeightDp(slotWidthDp) - FOCUS_INSET_TOP_DP);
    }

    static int row(int index) { return index / COLUMNS; }
    static int column(int index) { return index % COLUMNS; }

    private UnifiedCatalogLayout() {}
}
