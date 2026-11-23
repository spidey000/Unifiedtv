package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import java.util.List;

/** Launches UnifiedTv from HOME; never exposes the former launcher UI. */
final class UnifiedTvHome {
    private static boolean launchPending;
    private static final String PAUSE_REDIRECT = "system_home_recovery_no_redirect";

    static boolean isSystemHomeRecovery(Context context) {
        return UnifiedCatalogPrefs.prefs(context).getBoolean(PAUSE_REDIRECT, false);
    }

    /** Stop accessibility and OEM forced-HOME redirects before opening Android's chooser. */
    static void enterSystemHomeRecovery(Context context) {
        UnifiedCatalogPrefs.prefs(context).edit().putBoolean(PAUSE_REDIRECT, true).apply();
    }

    /** Re-enable optional OEM key routing only on explicit user request. */
    static void enableUnifiedHomeRouting(Context context) {
        UnifiedCatalogPrefs.prefs(context).edit().putBoolean(PAUSE_REDIRECT, false).apply();
    }

    static boolean maybeLaunch(Activity launcher) {
        if (launchPending) return true;
        launchPending = true;
        try {
            launcher.startActivity(new Intent(launcher, UnifiedCatalogActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return true;
        } catch (RuntimeException e) {
            launchPending = false;
            Toast.makeText(launcher, "No se pudo iniciar UnifiedTV. Abre los ajustes del televisor.",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    static void catalogCreated() { launchPending = false; }

    static void openInputs(Activity source) {
        Intent request = new Intent("com.android.tv.action.VIEW_INPUTS")
                .addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> handlers = source.getPackageManager().queryIntentActivities(request, 0);
        for (ResolveInfo handler : handlers) {
            if (handler.activityInfo == null
                    || source.getPackageName().equals(handler.activityInfo.packageName)) continue;
            Intent exact = new Intent(request).setClassName(handler.activityInfo.packageName,
                    handler.activityInfo.name);
            try {
                source.startActivity(exact);
                return;
            } catch (RuntimeException ignored) { /* Try another system handler. */ }
        }
        Toast.makeText(source, "Abre Entradas/HDMI desde los ajustes de tu televisor.",
                Toast.LENGTH_LONG).show();
    }

    private UnifiedTvHome() {}
}
