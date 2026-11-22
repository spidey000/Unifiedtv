package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;

/** Launcher entry point for the opt-out unified streaming Home. */
final class UnifiedTvHome {
    private static boolean skipNextReturn;

    static boolean maybeLaunch(Activity launcher) {
        if (skipNextReturn) {
            skipNextReturn = false;
            return false;
        }
        if (!UnifiedCatalogPrefs.enabled(launcher)) return false;
        launcher.startActivity(new Intent(launcher, UnifiedCatalogActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP));
        return true;
    }

    static void leaveForApps(Activity catalog) {
        // Back reveals the unchanged original CleanYourTV dock and app grid.
        skipNextReturn = true;
        catalog.finish();
    }

    private UnifiedTvHome() {}
}
