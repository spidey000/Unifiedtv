package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** Pure JVM regression checks: no Android TV or TMDB credentials required. */
public class UnifiedCatalogRouteTest {
    @Test public void nuvioAcceptsTmdbMovieAndTvIds() {
        assertEquals("nuvio://tmdb/movie/603", UnifiedCatalogRoute.nuvioUri("movie", 603));
        assertEquals("nuvio://tmdb/tv/1396", UnifiedCatalogRoute.nuvioUri("tv", 1396));
    }

    @Test public void stremioUsesImdbNotTmdbAndCorrectMediaType() {
        assertEquals("stremio:///detail/movie/tt0133093/tt0133093",
                UnifiedCatalogRoute.stremioUri("movie", "tt0133093"));
        assertEquals("stremio:///detail/series/tt0903747/",
                UnifiedCatalogRoute.stremioUri("tv", "tt0903747"));
    }

    @Test public void knownPlaybackTargetsHaveUniqueIdsAndPackages() {
        Set<String> ids = new HashSet<>();
        Set<String> packages = new HashSet<>();
        for (UnifiedPlaybackApps.Known app : UnifiedPlaybackApps.KNOWN) {
            assertTrue(ids.add(app.key));
            for (String pkg : app.packages) assertTrue(packages.add(pkg));
        }
        assertTrue(ids.contains("nuvio"));
        assertTrue(ids.contains("stremio"));
        assertFalse(UnifiedCatalogPrefs.PROVIDERS.stream().anyMatch(p -> "nuvio".equals(p.key)
                || "stremio".equals(p.key)));
    }
}
