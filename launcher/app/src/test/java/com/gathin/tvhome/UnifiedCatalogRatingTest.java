package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Rating data comes directly from TMDB catalog/search results: no extra API call. */
public class UnifiedCatalogRatingTest {
    private UnifiedCatalogApi.Media media(double average, int votes) {
        return new UnifiedCatalogApi.Media(1, "movie", "Título", "/poster.jpg", "2026",
                average, votes);
    }

    @Test public void usesSpanishDecimalSeparatorAndOneDigit() {
        UnifiedCatalogApi.Media item = media(8.76, 1240);
        assertTrue(item.hasRating());
        assertEquals("8,8", item.ratingText());
    }

    @Test public void noVotesMustNotLookLikeAZeroScore() {
        UnifiedCatalogApi.Media item = media(0, 0);
        assertFalse(item.hasRating());
        assertEquals("", item.ratingText());
        assertFalse(media(8.5, 0).hasRating());
    }

    @Test public void rejectsInvalidOrOutOfRangeScores() {
        assertFalse(media(-1, 10).hasRating());
        assertFalse(media(10.1, 10).hasRating());
        assertFalse(media(Double.NaN, 10).hasRating());
        assertFalse(media(Double.POSITIVE_INFINITY, 10).hasRating());
    }

    @Test public void perfectTenIsValid() {
        assertTrue(media(10, 1).hasRating());
        assertEquals("10,0", media(10, 1).ratingText());
    }
}
