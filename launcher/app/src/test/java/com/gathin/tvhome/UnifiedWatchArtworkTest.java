package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnifiedWatchArtworkTest {
    @Test public void portraitAndLandscapeUseDifferentAspectRatioThresholds() {
        assertTrue(UnifiedWatchArtwork.isPortrait(200, 300));
        assertTrue(UnifiedWatchArtwork.isWide(480, 270));
        assertFalse(UnifiedWatchArtwork.isPortrait(480, 270));
        assertFalse(UnifiedWatchArtwork.isWide(200, 300));
        assertFalse(UnifiedWatchArtwork.isPortrait(0, 300));
        assertFalse(UnifiedWatchArtwork.isWide(480, 0));
        assertFalse(UnifiedWatchArtwork.isPortrait(300, 300));
        assertFalse(UnifiedWatchArtwork.isPortrait(250, 300));
        assertFalse(UnifiedWatchArtwork.isWide(400, 300));
        assertFalse(UnifiedWatchArtwork.isWide(300, 300));
    }

    @Test public void smallOrLandscapePostersNeedSharpPortraitFallback() {
        assertTrue(UnifiedWatchArtwork.needsBetterPoster(120, 180, 300));
        assertTrue(UnifiedWatchArtwork.needsBetterPoster(600, 337, 300));
        assertFalse(UnifiedWatchArtwork.needsBetterPoster(500, 750, 300));
        assertFalse(UnifiedWatchArtwork.needsBetterPoster(780, 1170, 560));
    }

    @Test public void tmdbRatingsAreShownOnlyForActuallyRatedTitles() {
        UnifiedCatalogApi.MediaArt rated = new UnifiedCatalogApi.MediaArt(
                "/poster.jpg", "", 8.24, 100);
        assertTrue(rated.hasRating());
        assertEquals("8,2", rated.ratingText());
        UnifiedCatalogApi.MediaArt unreviewed = new UnifiedCatalogApi.MediaArt(
                "", "", 0, 0);
        assertFalse(unreviewed.hasRating());
        assertEquals("", unreviewed.ratingText());
    }

    @Test public void titleMatchingIgnoresAccentsCaseAndPunctuation() {
        assertEquals("la casa de papel", UnifiedWatchArtwork.normalizeTitle("La Casa de Papel"));
        assertEquals("la casa de papel", UnifiedWatchArtwork.normalizeTitle("la casa de papel!"));
        assertEquals("pocoyo", UnifiedWatchArtwork.normalizeTitle("Pocoyó"));
        assertEquals("el senor de los anillos", UnifiedWatchArtwork.normalizeTitle("El Señor de los Anillos"));
        assertEquals("", UnifiedWatchArtwork.normalizeTitle(""));
    }

    @Test public void titleYearIsExtractedWithoutChangingTheSearchTitle() {
        assertEquals(1999, UnifiedWatchArtwork.titleYear("Matrix (1999)"));
        assertEquals("Matrix", UnifiedWatchArtwork.searchTitle("Matrix (1999)"));
        assertEquals(2024, UnifiedWatchArtwork.year("2024-07-26"));
        assertEquals(0, UnifiedWatchArtwork.year("no-date"));
        assertEquals(0, UnifiedWatchArtwork.titleYear("Matrix"));
        assertEquals("Mi serie", UnifiedWatchArtwork.searchTitle("Mi serie"));
    }
}
