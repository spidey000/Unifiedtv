package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;

/** Launcher entry point for the opt-out unified streaming Home. */
final class UnifiedTvHome {
    private static boolean skipNextReturn;
    private static boolean launchPending;

    static boolean maybeLaunch(Activity launcher) {
        if (skipNextReturn) {
            skipNextReturn = false;
            return false;
        }
        if (!UnifiedCatalogPrefs.enabled(launcher)) return false;
        // HOME may call both onNewIntent and onResume before the catalog is created.
        if (launchPending) return true;
        launchPending = true;
        try {
            launcher.startActivity(new Intent(launcher, UnifiedCatalogActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
        } catch (RuntimeException e) {
            launchPending = false;
            return false; // Keep the original launcher available on malformed OEM firmware.
        }
    }

    static void catalogCreated() { launchPending = false; }

    static void leaveForApps(Activity catalog) {
        // Back reveals the unchanged original CleanYourTV dock and app grid.
        revealClassicOnce();
        catalog.finish();
    }

    static void openInputs(Activity catalog) {
        catalog.startActivity(new Intent(catalog, MainActivity.class)
                .setAction(MainActivity.ACTION_VIEW_INPUTS).putExtra(MainActivity.EXTRA_CATALOG_INPUTS, true));
    }

    static void revealClassicOnce() { skipNextReturn = true; }

    private UnifiedTvHome() {}
}
