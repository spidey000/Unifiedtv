package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.content.Intent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional on-device content-link index for every TMDB discovery shelf.
 *
 * PreviewPrograms = published recommendations, NOT watched titles.
 * WatchNextPrograms = device's published Continue Watching/Next programs,
 * used only for an exact title match and only when user already granted
 * READ_TV_LISTINGS. No other app's internal database is read.
 */
final class UnifiedTvProgramLinks {
    private static final long CACHE_TTL = 2L * 60 * 1000;
    private static final int MAX_ROWS = 1500;
    private static final Object LOCK = new Object();
    private static Snapshot snapshot;

    static final class Row {
        final String pkg, title, year, type, episode, rawIntent;
        Row(String pkg, String title, String year, String type, String episode, String rawIntent) {
            this.pkg = pkg;
            this.title = title;
            this.year = year;
            this.type = type;
            this.episode = episode;
            this.rawIntent = rawIntent;
        }
    }

    private static final class Snapshot {
        final long time;
        final List<Row> rows;
        Snapshot(long time, List<Row> rows) {
            this.time = time;
            this.rows = rows;
        }
    }

    static String find(Context context, UnifiedCatalogApi.Media media, String pkg) {
        if (!UnifiedWatchNext.hasPermission(context) || pkg == null || pkg.isEmpty()) return "";
        Snapshot current;
        synchronized (LOCK) {
            if (snapshot == null || System.currentTimeMillis() - snapshot.time > CACHE_TTL)
                snapshot = new Snapshot(System.currentTimeMillis(), scan(context));
            current = snapshot;
        }
        String found = "";
        for (Row row : current.rows) {
            if (!pkg.equals(row.pkg) || !matches(media.title, media.year, media.type,
                    row.title, row.year, row.type, row.episode)) continue;
            if (!safeIntent(row.rawIntent, pkg)) continue;
            // Two distinct links for an identically named title are ambiguous.
            if (!found.isEmpty() && !found.equals(row.rawIntent)) return "";
            found = row.rawIntent;
        }
        return found;
    }

    static boolean matches(String title, String year, String type, String rowTitle,
                           String rowYear, String rowType, String episode) {
        String expected = UnifiedWatchArtwork.normalizeTitle(title);
        if (expected.isEmpty() || !expected.equals(UnifiedWatchArtwork.normalizeTitle(rowTitle))) return false;
        if (!rowType.isEmpty() && !type.equals(rowType)) return false;
        if (!year.isEmpty() && !rowYear.isEmpty() && !year.equals(rowYear)) return false;
        // The launch intent for an episode often resumes it instead of showing the show.
        return episode.isEmpty();
    }

    private static List<Row> scan(Context context) {
        List<Row> result = new ArrayList<>();
        Map<Long, String> channels = readChannels(context);
        query(context, TvContract.PreviewPrograms.CONTENT_URI, new String[]{
                "channel_id", "package_name", "title", "intent_uri", "release_date",
                "type", "episode_title", "browsable"
        }, new String[]{"channel_id", "package_name", "title", "intent_uri"}, channels, result);
        query(context, TvContract.WatchNextPrograms.CONTENT_URI, new String[]{
                "package_name", "title", "intent_uri", "release_date",
                "type", "episode_title", "browsable"
        }, new String[]{"package_name", "title", "intent_uri"}, channels, result);
        return result;
    }

    private static Map<Long, String> readChannels(Context context) {
        Map<Long, String> channels = new HashMap<>();
        try (Cursor cursor = context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{"_id", "package_name"}, null, null, null)) {
            if (cursor == null) return channels;
            while (cursor.moveToNext() && channels.size() < 500) {
                String pkg = str(cursor, "package_name");
                if (!pkg.isEmpty()) channels.put(num(cursor, "_id"), pkg);
            }
        } catch (Exception ignored) { /* Restricted OEM provider. */ }
        return channels;
    }

    private static void query(Context context, Uri uri, String[] full, String[] basic,
                              Map<Long, String> channels, List<Row> rows) {
        try {
            if (!read(context, uri, full, channels, rows))
                read(context, uri, basic, channels, rows);
        } catch (Exception ignored) { /* Unsupported or restricted Android TV provider. */ }
    }

    private static boolean read(Context context, Uri uri, String[] columns,
                                Map<Long, String> channels, List<Row> result) {
        try (Cursor cursor = context.getContentResolver().query(uri, columns, null, null, null)) {
            if (cursor == null) return false;
            int count = 0;
            while (cursor.moveToNext() && ++count <= MAX_ROWS) {
                if (intVal(cursor, "browsable", 1) == 0) continue;
                String pkg = str(cursor, "package_name");
                if (pkg.isEmpty()) pkg = channels.get(num(cursor, "channel_id"));
                if (pkg == null || !isSupportedPackage(pkg)) continue;
                String title = str(cursor, "title");
                String intent = str(cursor, "intent_uri");
                if (title.isEmpty() || intent.isEmpty() || !safeIntent(intent, pkg)) continue;
                String release = str(cursor, "release_date");
                String year = release.matches("(?:19|20)[0-9]{2}.*")
                        ? release.substring(0, 4) : "";
                String type = str(cursor, "type").toLowerCase(java.util.Locale.ROOT);
                if (type.contains("movie") || type.contains("film")) type = "movie";
                else if (type.contains("tv") || type.contains("series")
                        || type.contains("episode")) type = "tv";
                else type = "";
                result.add(new Row(pkg, title, year, type, str(cursor, "episode_title"), intent));
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean isSupportedPackage(String packageName) {
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.PROVIDERS) {
            for (String known : provider.packages) if (known.equals(packageName)) return true;
        }
        return false;
    }

    /**
     * Never trust a raw intent blindly. The owning package must match any
     * explicit component. MAIN intents, empty data and foreign selectors
     * must not become a false title-level launch destination.
     */
    static boolean safeIntent(String raw, String pkg) {
        try {
            if (raw == null || raw.isEmpty() || pkg == null || pkg.isEmpty()) return false;
            Intent intent = parse(raw, pkg);
            return intent != null;
        } catch (Exception ignored) { return false; }
    }

    static Intent parse(String raw, String pkg) {
        try {
            if (raw == null || raw.isEmpty() || pkg == null || pkg.isEmpty()) return null;
            Intent intent = raw.startsWith("intent:")
                    ? Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(raw));
            if (intent.getComponent() != null
                    && !pkg.equals(intent.getComponent().getPackageName())) return null;
            if (intent.getPackage() != null && !pkg.equals(intent.getPackage())) return null;
            if (intent.getSelector() != null) return null;
            if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
            Uri data = intent.getData();
            if (data == null || data.getScheme() == null) return null;
            String scheme = data.getScheme().toLowerCase(java.util.Locale.ROOT);
            if ("file".equals(scheme) || "content".equals(scheme) || "javascript".equals(scheme)
                    || "android.resource".equals(scheme)) return null;
            intent.setPackage(pkg);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        } catch (Exception ignored) { return null; }
    }

    private static String str(Cursor cursor, String key) {
        int i = cursor.getColumnIndex(key);
        return i < 0 || cursor.isNull(i) ? "" : cursor.getString(i);
    }

    private static long num(Cursor cursor, String key) {
        int i = cursor.getColumnIndex(key);
        return i < 0 || cursor.isNull(i) ? 0L : cursor.getLong(i);
    }

    private static int intVal(Cursor cursor, String key, int fallback) {
        int i = cursor.getColumnIndex(key);
        return i < 0 || cursor.isNull(i) ? fallback : cursor.getInt(i);
    }

    private UnifiedTvProgramLinks() {}
}
