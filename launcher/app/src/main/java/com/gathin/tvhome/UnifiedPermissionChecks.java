package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.accessibility.AccessibilityManager;

import androidx.core.app.NotificationManagerCompat;

import java.util.List;

/**
 * Permission and readiness checks for Android TV settings.
 *
 * Checks reflect the device's current state, never a cached user preference.
 * No helper grants permissions or silently changes system configuration.
 */
final class UnifiedPermissionChecks {
    private UnifiedPermissionChecks() {}

    /** Detects a network that advertises Internet capability (not a live HTTP test). */
    static boolean networkPresent(Context context) {
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) return false;
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Only the user's selected HOME handler counts; an installed launcher does not. */
    static boolean isDefaultHome(Context context) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo handler = context.getPackageManager()
                    .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return handler != null && handler.activityInfo != null
                    && context.getPackageName().equals(handler.activityInfo.packageName);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Enabled accessibility services must match the exact class and package. */
    static boolean accessibilityEnabled(Context context, Class<? extends AccessibilityService> service) {
        try {
            AccessibilityManager manager = (AccessibilityManager)
                    context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager == null) return false;
            ComponentName expected = new ComponentName(context, service);
            List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : enabled) {
                ResolveInfo resolved = info.getResolveInfo();
                if (resolved == null || resolved.serviceInfo == null) continue;
                ComponentName actual = new ComponentName(resolved.serviceInfo.packageName,
                        resolved.serviceInfo.name);
                if (expected.equals(actual)) return true;
            }
        } catch (RuntimeException unsupported) {
            // Some restricted firmware implementations do not expose the list.
        }
        return false;
    }

    /** Service-level grant; the app neither reads nor stores received notifications. */
    static boolean notificationListenerEnabled(Context context) {
        try {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.getPackageName());
        } catch (RuntimeException unsupported) {
            return false;
        }
    }

    /** Optional development-level permission; not obtainable from a runtime dialog. */
    static boolean hasSecureSettingsPermission(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }
}
