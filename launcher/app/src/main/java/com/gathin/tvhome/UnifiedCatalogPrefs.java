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
                    if (manager.getLaunchIntentForPackage(candidate) != null) return candidate;
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
        List<Provider> result = new ArrayList<>(PROVIDERS);
        String raw = prefs(c).getString(ORDER, "");
        List<String> keys = Arrays.asList(raw.split(","));
        result.sort(Comparator.comparingInt(p -> {
            int i = keys.indexOf(p.key);
            return i < 0 ? 1000 + PROVIDERS.indexOf(p) : i;
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
