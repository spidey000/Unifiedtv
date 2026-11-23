package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class UnifiedProviderWordmarksTest {
    @Test public void resolvesNamesRatherThanSquareProviderIcons() {
        assertEquals(R.drawable.uic_provider_netflix, UnifiedProviderWordmarks.forKey("netflix"));
        assertEquals(R.drawable.uic_provider_prime, UnifiedProviderWordmarks.forKey("prime"));
        assertEquals(R.drawable.uic_provider_disney, UnifiedProviderWordmarks.forKey("disney"));
        assertEquals(0, UnifiedProviderWordmarks.forKey("nuvio"));
    }

    @Test public void detectsInstalledProvidersWithoutGuessingUnknownPackages() {
        assertEquals(R.drawable.uic_provider_netflix, UnifiedProviderWordmarks.forPackage("com.netflix.ninja"));
        assertEquals(R.drawable.uic_provider_prime, UnifiedProviderWordmarks.forPackage("com.amazon.avod.thirdpartyclient"));
        assertEquals(R.drawable.uic_provider_disney, UnifiedProviderWordmarks.forPackage("com.disney.disneyplus"));
        assertEquals(0, UnifiedProviderWordmarks.forPackage("org.xbmc.kodi"));
        assertEquals(0, UnifiedProviderWordmarks.forPackage(null));
    }
}
