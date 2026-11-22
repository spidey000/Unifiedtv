package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnifiedCatalogLayoutTest {
    @Test public void sixPostersFitButSeventhDoesNotEnterViewport() {
        int usable = 960 - 52 - 21 - 12;
        int width = UnifiedCatalogLayout.posterWidthDp(960, 52, 21, 12, 7);
        assertTrue(6 * width + 5 * 7 <= usable);
        assertTrue(6 * width + 6 * 7 > usable);
        assertEquals(Math.round(width * 1.5f), UnifiedCatalogLayout.posterHeightDp(width));
    }

    @Test public void focusPosterHasSafeInsetsAndStillKeepsTwoToThreeRatio() {
        for (int screen : new int[]{320, 720, 960, 1280, 1920}) {
            int slotWidth = UnifiedCatalogLayout.posterWidthDp(screen, 52, 21, 12, 7);
            int slotHeight = UnifiedCatalogLayout.posterHeightDp(slotWidth);
            int posterWidth = UnifiedCatalogLayout.cardWidthDp(slotWidth);
            int posterHeight = UnifiedCatalogLayout.cardHeightDp(slotWidth);
            assertEquals(Math.round(posterWidth * 1.5f), posterHeight);
            assertTrue(posterWidth < slotWidth);
            assertTrue(posterHeight < slotHeight);
            assertTrue(UnifiedCatalogLayout.FOCUS_INSET_TOP_DP > posterHeight * .025f / 2);
            assertTrue(UnifiedCatalogLayout.focusBottomInsetDp(slotWidth)
                    > posterHeight * .025f / 2);
            assertEquals(slotHeight, UnifiedCatalogLayout.FOCUS_INSET_TOP_DP
                    + posterHeight + UnifiedCatalogLayout.focusBottomInsetDp(slotWidth));
        }
    }

    @Test public void sixColumnsWrapOnlyAfterSixItems() {
        assertEquals(0, UnifiedCatalogLayout.row(0));
        assertEquals(0, UnifiedCatalogLayout.row(5));
        assertEquals(1, UnifiedCatalogLayout.row(6));
        assertEquals(5, UnifiedCatalogLayout.column(5));
        assertEquals(0, UnifiedCatalogLayout.column(6));
        assertEquals(9, UnifiedCatalogLayout.row(59));
        assertEquals(5, UnifiedCatalogLayout.column(59));
    }

    @Test public void sixSixteenByNineAppBannersFitWithoutHorizontalScroll() {
        for (int screen : new int[]{320, 720, 960, 1280, 1920}) {
            int usable = screen - 52 - 21 - 12;
            int width = UnifiedCatalogLayout.appBannerWidthDp(screen, 52, 21, 12);
            int height = UnifiedCatalogLayout.appBannerHeightDp(width);
            int cellWidth = width + 2 * UnifiedCatalogLayout.APP_FOCUS_INSET_X_DP
                    + UnifiedCatalogLayout.APP_CELL_GAP_DP;
            assertTrue(6 * cellWidth <= usable);
            assertTrue(7 * cellWidth > usable);
            assertEquals(Math.round(width * 9f / 16f), height);
            assertTrue(UnifiedCatalogLayout.appCardHeightDp(width) > height);
            assertEquals(UnifiedCatalogLayout.appCardHeightDp(width)
                    + UnifiedCatalogLayout.APP_FOCUS_INSET_TOP_DP
                    + UnifiedCatalogLayout.APP_FOCUS_INSET_BOTTOM_DP,
                    UnifiedCatalogLayout.appRowStrideDp(width));
        }
    }

    @Test public void geometryRemainsPositiveOnNarrowScreens() {
        int poster = UnifiedCatalogLayout.posterWidthDp(320, 52, 21, 12, 7);
        assertTrue(poster > 0);
        assertTrue(UnifiedCatalogLayout.posterHeightDp(poster) > poster);
    }
}
