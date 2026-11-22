package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** User-owned TMDB credentials and provider priorities. No provider account credentials are stored. */
final class UnifiedCatalogPrefs {
    static final String NAME = "unifiedtv_catalog";
    static final String KEY = "tmdb_v3_api_key";
    static final String REGION = "region";
    static final String ON_HOME = "catalog_on_home";
    static final String INCLUDE_FREE = "include_free";
    static final String ORDER = "provider_priority";

    static final class Provider {
        final String key, name;
        final int[] tmdbIds;
        final String[] packages;
        Provider(String key, String name, int[] ids, String... packages) {
            this.key = key;
            this.name = name;
            this.tmdbIds = ids;
            this.packages = packages;
        }
        boolean supports(int id) {
            for (int accepted : tmdbIds) if (id == accepted) return true;
            return false;
        }
        String installedPackage(Context context) {
            PackageManager manager = context.getPackageManager();
            for (String candidate : packages) {
                try {
                    if (manager.getLeanbackLaunchIntentForPackage(candidate) != null
                            || manager.getLaunchIntentForPackage(candidate) != null) return candidate;
                } catch (RuntimeException ignored) { /* Vendor package manager may be restrictive. */ }
            }
            return null;
        }
    }

    // Platform IDs are TMDB provider identifiers, not package IDs.
    static final List<Provider> PROVIDERS = Collections.unmodifiableList(Arrays.asList(
        new Provider("netflix", "Netflix", new int[]{8, 1796}, "com.netflix.ninja", "com.netflix.mediaclient"),
        new Provider("prime", "Prime Video", new int[]{9, 119}, "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient", "com.amazon.avod"),
        new Provider("max", "Max / HBO Max", new int[]{1899, 118, 1825}, "com.wbd.stream", "com.hbo.hbonow", "com.hbo.hbomax"),
        new Provider("disney", "Disney+", new int[]{337}, "com.disney.disneyplus"),
        new Provider("apple", "Apple TV+", new int[]{350, 2}, "com.apple.atve.androidtv.appletv"),
        new Provider("skyshowtime", "SkyShowtime", new int[]{1773}, "com.skyshowtime.androidtv", "com.skyshowtime.skyshowtime"),
        new Provider("movistar", "Movistar Plus+", new int[]{149, 2241}, "com.movistarplus.androidtv", "es.plus.yomvi"),
        new Provider("filmin", "Filmin", new int[]{63}, "com.filmin.filmin", "com.filmin.android")
    ));

    static final String LAST_TAB = "last_tab";
    static final String CONTINUE_ENABLED = "continue_enabled";
    static final String CONTINUE_HOME = "continue_home";
    static final String CONTINUE_PREVIEWS = "continue_previews";
    static final String CONTINUE_NEXT_EPISODES = "continue_next";
    static final String CONTINUE_HIDE_FINISHED = "continue_hide_finished";
    static final String CONTINUE_MAX_AGE = "continue_max_age_days";
    static final String CONTINUE_OTHER_APPS = "continue_other_apps";
    static final String UPDATE_AUTO_CHECK = "update_auto_check";
    static final String UPDATE_LAST_CHECK = "update_last_check";
    static final String UPDATE_LAST_SHOWN = "update_last_shown";


    // Stremio and Nuvio are catalog/player apps rather than TMDB subscription providers.
    // Their title links and Watch Next entries do not depend on TMDB availability.
    static final List<Provider> CONTINUE_SOURCES;
    static {
        List<Provider> sources = new ArrayList<>(PROVIDERS);
        sources.add(new Provider("stremio", "Stremio", new int[]{},
                "com.stremio.one", "com.stremio.two"));
        sources.add(new Provider("nuvio", "Nuvio", new int[]{},
                "com.nuvio.tv", "com.nuvio.app"));
        CONTINUE_SOURCES = Collections.unmodifiableList(sources);
    }

    /** Automatically look for signed GitHub releases, without downloading them. */
    static boolean autoCheckUpdates(Context c) {
        return prefs(c).getBoolean(UPDATE_AUTO_CHECK, true);
    }

    static boolean continueEnabled(Context c) {
        return prefs(c).getBoolean(CONTINUE_ENABLED, true);
    }

    static boolean continueOnHome(Context c) {
        return prefs(c).getBoolean(CONTINUE_HOME, true);
    }

    static boolean continuePreviews(Context c) {
        return prefs(c).getBoolean(CONTINUE_PREVIEWS, true);
    }

    static boolean continueNext(Context c) {
        return prefs(c).getBoolean(CONTINUE_NEXT_EPISODES, true);
    }

    static boolean continueHideFinished(Context c) {
        return prefs(c).getBoolean(CONTINUE_HIDE_FINISHED, true);
    }

    static int continueMaxAge(Context c) {
        int days = prefs(c).getInt(CONTINUE_MAX_AGE, 90);
        return days < 1 || days > 365 ? 90 : days;
    }

    static boolean continueSourceSelected(Context c, Provider p) {
        return prefs(c).getBoolean("continue_source_" + p.key, true);
    }

    static boolean continueOtherApps(Context c) {
        return prefs(c).getBoolean(CONTINUE_OTHER_APPS, false);
    }

    static Provider sourceForPackage(String pkg) {
        if (pkg == null) return null;
        for (Provider provider : CONTINUE_SOURCES)
            for (String candidate : provider.packages)
                if (candidate.equals(pkg)) return provider;
        return null;
    }

    static boolean allowedContinuePackage(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        Provider provider = sourceForPackage(pkg);
        if (provider == null) {
            if (!continueOtherApps(c)) return false;
            try { return c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null
                    || c.getPackageManager().getLaunchIntentForPackage(pkg) != null; }
            catch (RuntimeException e) { return false; }
        }
        return continueSourceSelected(c, provider)
                && provider.installedPackage(c) != null;
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static String apiKey(Context c) {
        return prefs(c).getString(KEY, "").trim();
    }

    static String region(Context c) {
        String country = prefs(c).getString(REGION, "ES").trim().toUpperCase(Locale.ROOT);
        return country.matches("[A-Z]{2}") ? country : "ES";
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean(ON_HOME, true);
    }

    static boolean allowsFree(Context c) {
        return prefs(c).getBoolean(INCLUDE_FREE, true);
    }

    static boolean selected(Context c, Provider provider) {
        return prefs(c).getBoolean("selected_" + provider.key, true);
    }

    static List<Provider> ordered(Context c) {
        List<Provider> result = new ArrayList<>(CONTINUE_SOURCES);
        String raw = prefs(c).getString(ORDER, "");
        List<String> keys = Arrays.asList(raw.split(","));
        result.sort(Comparator.comparingInt(p -> {
            int i = keys.indexOf(p.key);
            return i < 0 ? 1000 + CONTINUE_SOURCES.indexOf(p) : i;
        }));
        return result;
    }

    static void move(Context c, Provider p, int direction) {
        List<Provider> list = ordered(c);
        int index = list.indexOf(p);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= list.size()) return;
        Collections.swap(list, index, target);
        StringBuilder csv = new StringBuilder();
        for (Provider item : list) {
            if (csv.length() > 0) csv.append(',');
            csv.append(item.key);
        }
        prefs(c).edit().putString(ORDER, csv.toString()).apply();
    }

    private UnifiedCatalogPrefs() {}
}
