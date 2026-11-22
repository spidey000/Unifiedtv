package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UnifiedProviderLinksTest {
    @Test public void acceptsOnlyIncludedOffers() {
        assertTrue(UnifiedJustWatchLinks.included("FLATRATE"));
        assertTrue(UnifiedJustWatchLinks.included("FREE"));
        assertTrue(UnifiedJustWatchLinks.included("ADS"));
        assertFalse(UnifiedJustWatchLinks.included("RENT"));
        assertFalse(UnifiedJustWatchLinks.included("BUY"));
        assertFalse(UnifiedJustWatchLinks.included(""));
    }

    @Test public void providerOwnedLinksMustMatchExpectedHosts() {
        assertTrue(UnifiedJustWatchLinks.safeUrl("netflix",
                "https://www.netflix.com/title/80204890"));
        assertTrue(UnifiedJustWatchLinks.safeUrl("netflix",
                "https://www.netflix.com/watch/80204890"));
        assertFalse(UnifiedJustWatchLinks.safeUrl("netflix",
                "https://www.netflix.com/search?q=example"));
        assertFalse(UnifiedJustWatchLinks.safeUrl("netflix",
                "https://www.netflix.com.evil.test/title/80204890"));
        assertFalse(UnifiedJustWatchLinks.safeUrl("netflix",
                "https://www.justwatch.com/es/pelicula/example"));
        assertFalse(UnifiedJustWatchLinks.safeUrl("netflix",
                "javascript:alert(1)"));
        assertTrue(UnifiedJustWatchLinks.safeUrl("disney",
                "https://www.disneyplus.com/movies/a-film/abc-123"));
        assertFalse(UnifiedJustWatchLinks.safeUrl("disney",
                "https://www.netflix.com/title/80204890"));
    }

    @Test public void neverSubstitutesTmdbIdForNetflixId() {
        assertTrue(UnifiedNetflixLinks.validTitleId("80204890"));
        assertFalse(UnifiedNetflixLinks.validTitleId("tt0133093"));
        assertFalse(UnifiedNetflixLinks.validTitleId("Q1234"));
        assertFalse(UnifiedNetflixLinks.validTitleId("../../etc"));
        assertEquals("https://www.netflix.com/title/80204890",
                UnifiedNetflixLinks.titleUrl("80204890"));
        assertEquals("", UnifiedNetflixLinks.titleUrl("invalid"));
    }
}
