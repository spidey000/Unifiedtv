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

    @Test public void sixColumnsWrapOnlyAfterSixItems() {
        assertEquals(0, UnifiedCatalogLayout.row(0));
        assertEquals(0, UnifiedCatalogLayout.row(5));
        assertEquals(1, UnifiedCatalogLayout.row(6));
        assertEquals(5, UnifiedCatalogLayout.column(5));
        assertEquals(0, UnifiedCatalogLayout.column(6));
        assertEquals(9, UnifiedCatalogLayout.row(59));
        assertEquals(5, UnifiedCatalogLayout.column(59));
    }

    @Test public void geometryRemainsPositiveOnNarrowScreens() {
        int poster = UnifiedCatalogLayout.posterWidthDp(320, 52, 21, 12, 7);
        assertTrue(poster > 0);
        assertTrue(UnifiedCatalogLayout.posterHeightDp(poster) > poster);
    }
}
