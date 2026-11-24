package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Regression guards for posters, banners and long settings rows at TV sizes. */
public final class TvFocusViewportTest {
    @Test public void keepsVisibleFocusStationary() {
        assertEquals(0, TvFocusViewport.scrollDelta(60, 240, 0, 300, 14));
    }

    @Test public void scrollsDownToRevealBottomOutline() {
        assertEquals(24, TvFocusViewport.scrollDelta(210, 310, 0, 300, 14));
    }

    @Test public void scrollsUpToRevealTopOutline() {
        assertEquals(-19, TvFocusViewport.scrollDelta(110, 150, 115, 300, 14));
    }

    @Test public void handlesScrolledSettingsAndTallPanels() {
        assertEquals(18, TvFocusViewport.scrollDelta(590, 702, 400, 296, 12));
        assertEquals(-12, TvFocusViewport.scrollDelta(0, 400, 0, 300, 12));
    }

    @Test public void visibleContentDoesNotJumpAfterThePageHasScrolled() {
        assertEquals(0, TvFocusViewport.scrollDelta(420, 600, 400, 300, 14));
        assertEquals(24, TvFocusViewport.scrollDelta(610, 710, 400, 300, 14));
    }

    @Test public void doesNotScrollWithoutAViewport() {
        assertEquals(0, TvFocusViewport.scrollDelta(200, 320, 100, 0, 14));
    }
}
