package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class UnifiedProviderWordmarksTest {
    @Test public void bundledBrandsIncludeFutureApps() {
        assertEquals(R.drawable.uic_provider_netflix, UnifiedProviderWordmarks.forKey("netflix"));
        assertEquals(R.drawable.uic_provider_prime, UnifiedProviderWordmarks.forKey("prime"));
        assertEquals(R.drawable.uic_provider_disney, UnifiedProviderWordmarks.forKey("disney"));
        assertEquals(R.drawable.uic_provider_hbomax, UnifiedProviderWordmarks.forKey("max"));
        assertEquals(R.drawable.uic_provider_atresplayer, UnifiedProviderWordmarks.forKey("atresplayer"));
        assertEquals(R.drawable.uic_provider_skyshowtime, UnifiedProviderWordmarks.forKey("skyshowtime"));
        assertTrue(UnifiedProviderWordmarks.forKey("paramountplus") != 0);
        assertEquals(0, UnifiedProviderWordmarks.forKey("nuvio"));
    }

    @Test public void exactPackageMappingDoesNotMatchUnknownApps() {
        assertEquals(R.drawable.uic_provider_netflix,
                UnifiedProviderWordmarks.forPackage("com.netflix.ninja"));
        assertEquals(R.drawable.uic_provider_atresplayer,
                UnifiedProviderWordmarks.forPackage("com.antena3.atresplayer.tv"));
        assertEquals(R.drawable.uic_provider_youtube,
                UnifiedProviderWordmarks.forPackage("com.google.android.youtube.tv"));
        assertEquals(R.drawable.uic_provider_hbomax,
                UnifiedProviderWordmarks.forPackage("com.wbd.stream"));
        assertEquals(0, UnifiedProviderWordmarks.forPackage("org.xbmc.kodi"));
        assertEquals(0, UnifiedProviderWordmarks.forPackage(null));
    }
}
