package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.util.LruCache;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads ONLY the Watch Next and opt-in continuation preview rows exposed by
 * Android TV's public provider. Never reads another app's databases or accounts.
 * Requires an explicit READ_TV_LISTINGS grant on compatible TVs.
 */
final class UnifiedWatchNext {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    static final int PERMISSION_REQUEST = 7341;

    static final class Entry {
        final String providerPackage;
        final String source;
        final String title;
        final String poster;
        final String thumbnail;
        final String mediaType;
        final String releaseDate;
        final String intentUri;
        final long updated;
        final long duration;
        final long position;
        final boolean fromPreview;
        final String episode;
        Entry(String pkg, String source, String title, String poster, String intentUri,
              long updated, long position, long duration, boolean preview, String episode) {
            this(pkg, source, title, poster, "", "", "", intentUri, updated, position,
                    duration, preview, episode);
        }
        Entry(String pkg, String source, String title, String poster, String thumbnail,
              String mediaType, String releaseDate, String intentUri,
              long updated, long position, long duration, boolean preview, String episode) {
            this.providerPackage = pkg;
            this.source = source;
            this.title = title;
            this.poster = poster;
            this.thumbnail = thumbnail;
            this.mediaType = mediaType;
            this.releaseDate = releaseDate;
            this.intentUri = intentUri;
            this.updated = updated;
            this.position = position;
            this.duration = duration;
            this.fromPreview = preview;
            this.episode = episode == null ? "" : episode;
        }
        boolean meetsMinimum(int percent, int minutes) {
            if (percent == 0 && minutes == 0) return true;
            if (position <= 0) return false;
            return (percent > 0 && duration > 0 && position * 100.0 >= duration * (double) percent)
                    || (minutes > 0 && position >= minutes * 60_000L);
        }

        int progress() {
            return duration > 0 ? (int) Math.max(0, Math.min(100, position * 100.0 / duration)) : -1;
        }
    }

    static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT >= 26
                && context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    static List<Entry> load(Context context) {
        if (!hasPermission(context) || !UnifiedCatalogPrefs.continueEnabled(context))
            return Collections.emptyList();

        List<Entry> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        readWatchNext(context, list, seen);
        if (UnifiedCatalogPrefs.continuePreviews(context))
            readContinuePreviews(context, list, seen);

        list.sort((a, b) -> Long.compare(b.updated, a.updated));
        // Showcase the newest item from each platform first; then fill with the
        // remaining recent titles, so one provider cannot crowd out the others.
        List<Entry> balanced = new ArrayList<>(list.size());
        Set<String> firstPackages = new HashSet<>();
        for (Entry item : list) if (firstPackages.add(item.providerPackage)) balanced.add(item);
        for (Entry item : list) if (!balanced.contains(item)) balanced.add(item);
        if (balanced.size() > 60) return new ArrayList<>(balanced.subList(0, 60));
        return balanced;
    }

    private static void readWatchNext(Context context, List<Entry> results, Set<String> seen) {
        String[] columns = {
                "title", "package_name", "poster_art_uri", "thumbnail_uri",
                "type", "release_date", "intent_uri", "last_engagement_time_utc_millis",
                "last_playback_position_millis", "duration_millis",
                "watch_next_type", "browsable", "season_display_number",
                "episode_display_number", "episode_title"
        };
        // Do not pass SQL selection: a non-privileged caller is forbidden to filter
        // inside the TV provider. Filter on the app side instead.
        try (Cursor cursor = context.getContentResolver()
                .query(TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String pkg = string(cursor, "package_name");
                if (!UnifiedCatalogPrefs.allowedContinuePackage(context, pkg)) continue;
                int kind = integer(cursor, "watch_next_type", -1);
                if (kind != TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
                        && !(kind == TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
                                && UnifiedCatalogPrefs.continueNext(context))) continue;
                if (integer(cursor, "browsable", 1) == 0) continue;
                String season = string(cursor, "season_display_number");
                String episode = string(cursor, "episode_display_number");
                String details = !season.isEmpty() && !episode.isEmpty()
                        ? "T" + season + " · E" + episode
                        : string(cursor, "episode_title");
                Entry e = new Entry(pkg, sourceName(pkg), string(cursor, "title"),
                        string(cursor, "poster_art_uri"), string(cursor, "thumbnail_uri"),
                        mediaType(cursor), string(cursor, "release_date"),
                        string(cursor, "intent_uri"),
                        number(cursor, "last_engagement_time_utc_millis"),
                        number(cursor, "last_playback_position_millis"),
                        number(cursor, "duration_millis"), false, details);
                accept(context, e, results, seen);
            }
        } catch (Exception ignored) {
            // No TV provider on this ROM, permission restricted by manufacturer,
            // or missing columns. Leave the rest of UnifiedTV usable.
        }
    }

    private static void readContinuePreviews(Context context, List<Entry> results, Set<String> seen) {
        // A few apps (including some Stremio builds) use a preview channel named
        // Continue Watching rather than Watch Next. Never interpret generic
        // recommendation channels as a viewing history.
        Map<Long, String> allowedChannels = new HashMap<>();
        String[] channelColumns = {"_id", "display_name", "package_name"};
        try (Cursor cursor = context.getContentResolver()
                .query(TvContract.Channels.CONTENT_URI, channelColumns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String pkg = string(cursor, "package_name");
                String name = string(cursor, "display_name").toLowerCase(Locale.ROOT);
                if (!UnifiedCatalogPrefs.allowedContinuePackage(context, pkg)) continue;
                boolean continuation = name.contains("continue") || name.contains("watching")
                        || name.contains("seguir viendo") || name.contains("continuar")
                        || name.contains("resume") || name.contains("en curso");
                if (continuation) allowedChannels.put(number(cursor, "_id"), pkg);
            }
        } catch (Exception ignored) { return; }
        if (allowedChannels.isEmpty()) return;

        String[] columns = {
                "channel_id", "title", "poster_art_uri", "thumbnail_uri",
                "type", "release_date", "intent_uri",
                "last_playback_position_millis", "duration_millis"
        };
        try (Cursor cursor = context.getContentResolver()
                .query(TvContract.PreviewPrograms.CONTENT_URI, columns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String pkg = allowedChannels.get(number(cursor, "channel_id"));
                if (pkg == null) continue;
                Entry item = new Entry(pkg, sourceName(pkg), string(cursor, "title"),
                        string(cursor, "poster_art_uri"), string(cursor, "thumbnail_uri"),
                        mediaType(cursor), string(cursor, "release_date"),
                        string(cursor, "intent_uri"),
                        0, number(cursor, "last_playback_position_millis"),
                        number(cursor, "duration_millis"), true, "");
                accept(context, item, results, seen);
            }
        } catch (Exception ignored) {
            // Some manufacturer providers do not expose preview rows.
        }
    }

    private static void accept(Context context, Entry entry, List<Entry> list, Set<String> seen) {
        if (entry.title.isEmpty() || entry.intentUri.isEmpty()) return;
        if (context.getPackageManager().getLeanbackLaunchIntentForPackage(entry.providerPackage) == null)
            return;
        if (entry.updated > 0) {
            long age = System.currentTimeMillis() - entry.updated;
            if (age > UnifiedCatalogPrefs.continueMaxAge(context) * 86400000L) return;
        }
        if (!entry.meetsMinimum(UnifiedCatalogPrefs.continueMinPercent(context),
                UnifiedCatalogPrefs.continueMinMinutes(context))) return;
        int percent = entry.progress();
        if (UnifiedCatalogPrefs.continueHideFinished(context) && percent >= 95) return;
        String key = entry.providerPackage + "|" + entry.title.toLowerCase(Locale.ROOT);
        if (seen.add(key)) list.add(entry);
    }

    private static String mediaType(Cursor cursor) {
        int type = integer(cursor, "type", -1);
        if (type == TvContract.Programs.TYPE_MOVIE) return "movie";
        if (type == TvContract.Programs.TYPE_TV_SERIES
                || type == TvContract.Programs.TYPE_TV_SEASON
                || type == TvContract.Programs.TYPE_TV_EPISODE) return "tv";
        return "";
    }

    private static String sourceName(String pkg) {
        UnifiedCatalogPrefs.Provider p = UnifiedCatalogPrefs.sourceForPackage(pkg);
        return p == null ? pkg : p.name;
    }

    private static String string(Cursor cursor, String name) {
        int idx = cursor.getColumnIndex(name);
        return idx < 0 || cursor.isNull(idx) ? "" : cursor.getString(idx);
    }

    private static long number(Cursor cursor, String name) {
        int idx = cursor.getColumnIndex(name);
        return idx < 0 || cursor.isNull(idx) ? 0L : cursor.getLong(idx);
    }

    private static int integer(Cursor cursor, String name, int fallback) {
        int idx = cursor.getColumnIndex(name);
        return idx < 0 || cursor.isNull(idx) ? fallback : cursor.getInt(idx);
    }

    /** The explicit provider payload is preferred to a title search. */
    static void open(Activity activity, Entry item) {
        if (!UnifiedCatalogPrefs.allowedContinuePackage(activity, item.providerPackage)) return;
        try {
            Intent intent;
            if (item.intentUri.startsWith("intent:")) {
                intent = Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.intentUri));
            }
            // An item belongs to exactly the package that published it.
            // Do not permit a provider row to redirect to an unrelated installed app.
            intent.setPackage(item.providerPackage);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return;
        } catch (Exception ignored) {
            // Some provider links only work after the service's profile chooser.
        }
        try {
            Intent launch = activity.getPackageManager()
                    .getLeanbackLaunchIntentForPackage(item.providerPackage);
            if (launch == null) launch = activity.getPackageManager()
                    .getLaunchIntentForPackage(item.providerPackage);
            if (launch != null) {
                activity.startActivity(launch);
                Toast.makeText(activity, "Abriendo " + item.source
                        + "; busca «" + item.title + "»", Toast.LENGTH_LONG).show();
                return;
            }
        } catch (Exception ignored) { }
        Toast.makeText(activity, "No se puede abrir " + item.source, Toast.LENGTH_LONG).show();
    }

    private static final LruCache<String, Bitmap> ART = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    /** Call from a worker thread; image URIs can be remote or provider-backed. */
    static Bitmap artwork(Context context, String raw) {
        if (raw == null || raw.isEmpty()) return null;
        synchronized (ART) {
            Bitmap found = ART.get(raw);
            if (found != null) return found;
        }
        try {
            Uri uri = Uri.parse(raw);
            String scheme = uri.getScheme();
            if (scheme == null) return null;
            Bitmap bitmap;
            InputStream input;
            HttpURLConnection connection = null;
            if ("https".equals(scheme)) {
                connection = (HttpURLConnection) new URL(raw).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                if (connection.getResponseCode() != 200) { connection.disconnect(); return null; }
                input = connection.getInputStream();
            } else if ("content".equals(scheme) || "android.resource".equals(scheme)) {
                input = context.getContentResolver().openInputStream(uri);
            } else return null;
            if (input == null) return null;
            try (InputStream stream = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stream.read(buf)) != -1) {
                    if (out.size() + n > 512 * 1024) return null;
                    out.write(buf, 0, n);
                }
                byte[] bytes = out.toByteArray();
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            } finally {
                if (connection != null) connection.disconnect();
            }
            if (bitmap != null) synchronized (ART) { ART.put(raw, bitmap); }
            return bitmap;
        } catch (Exception ignored) { return null; }
    }

    private UnifiedWatchNext() {}
}
