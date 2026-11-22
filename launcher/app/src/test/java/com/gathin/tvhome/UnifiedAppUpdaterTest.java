package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Version checks are pure and do not require network, UI, or update permissions. */
public class UnifiedAppUpdaterTest {
    @Test public void detectsNewPatchMinorAndMajorVersions() {
        assertTrue(UnifiedAppUpdater.newer("v0.1.5", "0.1.4"));
        assertTrue(UnifiedAppUpdater.newer("v0.2.0", "0.1.99"));
        assertTrue(UnifiedAppUpdater.newer("v1.0.0", "0.99.99"));
    }

    @Test public void ignoresTheInstalledOrAnOlderVersion() {
        assertFalse(UnifiedAppUpdater.newer("v0.1.4", "0.1.4"));
        assertFalse(UnifiedAppUpdater.newer("v0.1.3", "0.1.4"));
        assertFalse(UnifiedAppUpdater.newer("v0.9.9", "1.0.0"));
    }

    @Test public void rejectsInvalidReleaseTags() {
        assertFalse(UnifiedAppUpdater.newer("latest", "0.1.4"));
        assertFalse(UnifiedAppUpdater.newer("v0.1.5-beta", "0.1.4"));
        assertFalse(UnifiedAppUpdater.newer("", "0.1.4"));
    }

    @Test public void handlesDeveloperBuildsAndDoubleDigitPatches() {
        assertTrue(UnifiedAppUpdater.newer("v0.1.10", "0.1.9"));
        assertTrue(UnifiedAppUpdater.newer("v0.1.5", "0.1.0-dev"));
    }
}
