package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Local apps that can receive a title selected in TMDB. They are NOT TMDB/JustWatch
 * availability providers: installation never means a title is playable there.
 * Additional launchable apps are strictly opt-in, and never require QUERY_ALL_PACKAGES.
 */
final class UnifiedPlaybackApps {
    static final class App {
        final String key;
        final String name;
        final String packageName;
        App(String key, String name, String packageName) {
            this.key = key;
            this.name = name;
            this.packageName = packageName;
        }
    }

    static final class Known {
        final String key;
        final String name;
        final String[] packages;
        Known(String key, String name, String... packages) {
            this.key = key;
            this.name = name;
            this.packages = packages;
        }

        String installedPackage(Context context) {
            PackageManager pm = context.getPackageManager();
            for (String pkg : packages) {
                try {
                    if (pm.getLeanbackLaunchIntentForPackage(pkg) != null
                            || pm.getLaunchIntentForPackage(pkg) != null) return pkg;
                } catch (RuntimeException ignored) { /* OEM package managers can fail. */ }
            }
            return null;
        }
    }

    // Explicitly supported local catalog apps, followed by well-known media players.
    static final List<Known> KNOWN = Collections.unmodifiableList(java.util.Arrays.asList(
            new Known("stremio", "Stremio", "com.stremio.one", "com.stremio.two"),
            new Known("kodi", "Kodi", "org.xbmc.kodi"),
            new Known("plex", "Plex", "com.plexapp.android", "tv.plex"),
            new Known("jellyfin", "Jellyfin", "org.jellyfin.androidtv"),
            new Known("emby", "Emby", "tv.emby.embyatv"),
            new Known("vlc", "VLC", "org.videolan.vlc"),
            new Known("nova", "Nova Video Player", "org.courville.nova")
    ));

    static boolean selected(Context context, Known app) {
        return UnifiedCatalogPrefs.prefs(context).getBoolean("playback_source_" + app.key, true);
    }

    static boolean selectedOther(Context context, String pkg) {
        return UnifiedCatalogPrefs.prefs(context).getBoolean("playback_package_" + pkg, false);
    }

    static List<App> enabled(Context context) {
        List<App> result = new ArrayList<>();
        for (Known app : KNOWN) {
            String pkg = app.installedPackage(context);
            if (pkg != null && selected(context, app)) result.add(new App(app.key, app.name, pkg));
        }
        for (App app : otherInstalledApps(context)) {
            if (selectedOther(context, app.packageName)) result.add(app);
        }
        return result;
    }

    /**
     * Let users explicitly include any other TV app they installed, rather than
     * guessing that arbitrary installed apps support video or a title-search API.
     */
    static List<App> otherInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> excluded = new HashSet<>();
        excluded.add(context.getPackageName());
        Collections.addAll(excluded, UnifiedCatalogPrefs.NUVIO.packages);
        for (Known app : KNOWN) Collections.addAll(excluded, app.packages);
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.PROVIDERS)
            Collections.addAll(excluded, provider.packages);

        LinkedHashMap<String, App> apps = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(category);
            try {
                for (ResolveInfo info : pm.queryIntentActivities(main, 0)) {
                    if (info.activityInfo == null) continue;
                    String pkg = info.activityInfo.packageName;
                    if (pkg == null || excluded.contains(pkg) || apps.containsKey(pkg)) continue;
                    CharSequence label = info.loadLabel(pm);
                    apps.put(pkg, new App("other", label == null ? pkg : label.toString(), pkg));
                }
            } catch (RuntimeException ignored) { /* Some TV ROMs limit launcher visibility. */ }
        }
        List<App> result = new ArrayList<>(apps.values());
        result.sort(Comparator.comparing((App app) -> app.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(app -> app.packageName));
        return result;
    }

    private UnifiedPlaybackApps() {}
}
