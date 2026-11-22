package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnifiedCatalogRouteTest {
    @Test public void nuvioUsesTmdbIdsForMovieAndTv() {
        assertEquals("nuvio://tmdb/movie/603", UnifiedCatalogRoute.nuvioLink("movie", 603));
        assertEquals("nuvio://tmdb/tv/1399", UnifiedCatalogRoute.nuvioLink("tv", 1399));
    }

    @Test public void stremioUsesImdbIdAndOpensTheEpisodeListForTv() {
        assertEquals("stremio:///detail/movie/tt0133093/tt0133093",
                UnifiedCatalogRoute.stremioLink("movie", "tt0133093"));
        assertEquals("stremio:///detail/series/tt0944947/",
                UnifiedCatalogRoute.stremioLink("tv", "tt0944947"));
    }

    @Test public void stremioFallsBackWithoutAnImdbId() {
        assertEquals("", UnifiedCatalogRoute.stremioLink("movie", ""));
        assertEquals("", UnifiedCatalogRoute.stremioLink("tv", "null"));
        assertEquals("", UnifiedCatalogRoute.stremioLink("movie", "603"));
    }
}
