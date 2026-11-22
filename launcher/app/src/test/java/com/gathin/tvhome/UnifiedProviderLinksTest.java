package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class UnifiedProviderLinksTest {
    @Test public void providerOwnedLinksAreStrictlyValidated() {
        assertTrue(UnifiedWikidataLinks.safeUrl("netflix",
                "https://www.netflix.com/title/80204890"));
        assertTrue(UnifiedWikidataLinks.safeUrl("disney",
                "https://www.disneyplus.com/movies/wd/6aM2a8mZATiu"));
        assertTrue(UnifiedWikidataLinks.safeUrl("prime",
                "https://www.primevideo.com/detail/0PKD430VQU4ALLISBLIAGREZ99"));
        assertTrue(UnifiedWikidataLinks.safeUrl("max",
                "https://play.hbomax.com/movie/dfa50804-e6f6-4fa2-a732-693dbc50527b"));
        assertFalse(UnifiedWikidataLinks.safeUrl("netflix",
                "https://www.netflix.com/search?q=example"));
        assertFalse(UnifiedWikidataLinks.safeUrl("netflix",
                "https://www.netflix.com.evil.test/title/80204890"));
        assertFalse(UnifiedWikidataLinks.safeUrl("netflix",
                "https://www.justwatch.com/es/pelicula/example"));
        assertFalse(UnifiedWikidataLinks.safeUrl("netflix",
                "javascript:alert(1)"));
        assertFalse(UnifiedWikidataLinks.safeUrl("disney",
                "https://www.netflix.com/title/80204890"));
        assertFalse(UnifiedWikidataLinks.safeUrl("netflix",
                "https://attacker@www.netflix.com/title/80204890"));
    }

    @Test public void wikidataClaimsResolveEachServiceAndContentType() throws Exception {
        JSONObject claims = new JSONObject()
                .put("P1874", claims("80204890"))
                .put("P14440", claims("0PKD430VQU4ALLISBLIAGREZ99"))
                .put("P7595", claims("6aM2a8mZATiu"))
                .put("P7596", claims("3ZoBZ52QHb4x"))
                .put("P8298", claims("movie/dfa50804-e6f6-4fa2-a732-693dbc50527b"));
        assertEquals("https://www.netflix.com/title/80204890",
                UnifiedWikidataLinks.fromClaims(claims, "movie").get("netflix"));
        assertEquals("https://www.primevideo.com/detail/0PKD430VQU4ALLISBLIAGREZ99",
                UnifiedWikidataLinks.fromClaims(claims, "movie").get("prime"));
        assertEquals("https://www.disneyplus.com/movies/wd/6aM2a8mZATiu",
                UnifiedWikidataLinks.fromClaims(claims, "movie").get("disney"));
        assertEquals("https://www.disneyplus.com/series/wp/3ZoBZ52QHb4x",
                UnifiedWikidataLinks.fromClaims(claims, "tv").get("disney"));
        assertEquals("https://play.hbomax.com/movie/dfa50804-e6f6-4fa2-a732-693dbc50527b",
                UnifiedWikidataLinks.fromClaims(claims, "movie").get("max"));
    }

    @Test public void invalidOrAmbiguousProviderIdsNeverCreateLinks() throws Exception {
        JSONObject claims = new JSONObject()
                .put("P1874", new JSONArray()
                        .put(claim("80204890")).put(claim("81234567")))
                .put("P7595", claims("../evil"))
                .put("P14440", claims("tt1234567"));
        assertFalse(UnifiedWikidataLinks.fromClaims(claims, "movie").containsKey("netflix"));
        assertFalse(UnifiedWikidataLinks.fromClaims(claims, "movie").containsKey("disney"));
        assertFalse(UnifiedWikidataLinks.fromClaims(claims, "movie").containsKey("prime"));
    }

    @Test public void previewsRequireSameTitleAndCompatibleMetadata() {
        assertTrue(UnifiedTvProgramLinks.matches("El Señor de los Anillos", "2001", "movie",
                "El Senor de los Anillos", "2001", "movie", ""));
        assertFalse(UnifiedTvProgramLinks.matches("El Señor de los Anillos", "2001", "movie",
                "El Senor de los Anillos", "2002", "movie", ""));
        assertFalse(UnifiedTvProgramLinks.matches("El Señor de los Anillos", "2001", "movie",
                "El Senor de los Anillos", "2001", "tv", ""));
        assertFalse(UnifiedTvProgramLinks.matches("Mi serie", "2024", "tv",
                "Mi serie", "2024", "tv", "Capítulo 4"));
        assertFalse(UnifiedTvProgramLinks.matches("Mi serie", "2024", "tv",
                "Otra serie", "", "", ""));
    }

    private static JSONArray claims(String value) throws Exception {
        return new JSONArray().put(claim(value));
    }

    private static JSONObject claim(String value) throws Exception {
        return new JSONObject().put("rank", "normal")
                .put("mainsnak", new JSONObject()
                        .put("datavalue", new JSONObject().put("value", value)));
    }
}
