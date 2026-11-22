package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small, bounded network/cache layer. Call only from a worker thread. */
final class UnifiedCatalogApi {
    static final class Media {
        final int id;
        final String type, title, poster, year, backdrop, overview;
        final double voteAverage;
        final int voteCount;
        volatile boolean availabilityReady;
        volatile String watchLink = "";
        volatile String availabilityError = "";
        final Set<Integer> providerIds = new HashSet<>();

        Media(int id, String type, String title, String poster, String year,
              double voteAverage, int voteCount) {
            this(id, type, title, poster, year, voteAverage, voteCount, "", "");
        }

        Media(int id, String type, String title, String poster, String year,
              double voteAverage, int voteCount, String backdrop, String overview) {
            this.id = id;
            this.backdrop = backdrop;
            this.overview = overview;
            this.type = type;
            this.title = title;
            this.poster = poster;
            this.year = year;
            this.voteAverage = voteAverage;
            this.voteCount = voteCount;
        }

        /** TMDB ratings use a 0–10 scale. Zero votes means unrated, not 0/10. */
        boolean hasRating() {
            return voteCount > 0 && !Double.isNaN(voteAverage)
                    && !Double.isInfinite(voteAverage)
                    && voteAverage > 0 && voteAverage <= 10;
        }

        String ratingText() {
            return hasRating()
                    ? String.format(Locale.forLanguageTag("es-ES"), "%.1f", voteAverage)
                    : "";
        }
    }

    private static final long LIST_TTL = 6L * 60 * 60 * 1000;
    private static final long PROVIDER_TTL = 12L * 60 * 60 * 1000;
    private static final LruCache<String, Bitmap> POSTERS = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    /**
     * Browsing categories are independent of streaming services, which TMDB resolves
     * per title/region via watch/providers. "Most watched" is a weekly trending
     * proxy, not a provider's actual playback count.
     */
    static List<Media> list(Context context, String mediaType, String category,
                            String query, boolean refresh) throws Exception {
        String region = UnifiedCatalogPrefs.region(context);
        String type = "tv".equals(mediaType) ? "tv" : "movie";
        String endpoint;
        if ("search".equals(category)) {
            endpoint = "/search/" + type + "?language=es-ES&include_adult=false&query="
                    + URLEncoder.encode(query == null ? "" : query, "UTF-8");
        } else {
            switch (category) {
                case "watched":
                    endpoint = "/trending/" + type + "/week?language=es-ES";
                    break;
                case "new":
                    endpoint = type.equals("movie")
                            ? "/movie/now_playing?language=es-ES&region=" + region
                            : "/tv/on_the_air?language=es-ES";
                    break;
                case "popular":
                    endpoint = "/" + type + "/popular?language=es-ES";
                    break;
                case "rated":
                    endpoint = "/" + type + "/top_rated?language=es-ES";
                    break;
                default:
                    endpoint = "/trending/" + type + "/day?language=es-ES";
                    break;
            }
        }
        JSONObject response = request(context, endpoint, LIST_TTL, refresh);
        JSONArray results = response.optJSONArray("results");
        List<Media> media = new ArrayList<>();
        if (results == null) return media;
        for (int i = 0; i < results.length() && media.size() < 24; i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null) continue;
            String resultType = o.optString("media_type", type);
            if (!type.equals(resultType)) continue;
            String name = o.optString(type.equals("tv") ? "name" : "title", "");
            String poster = o.optString("poster_path", "");
            if (name.isEmpty() || poster.isEmpty() || poster.equals("null")) continue;
            String date = o.optString(type.equals("tv") ? "first_air_date" : "release_date", "");
            String year = date.length() >= 4 ? date.substring(0, 4) : "";
            int id = o.optInt("id", 0);
            double voteAverage = o.optDouble("vote_average", 0.0);
            int voteCount = o.optInt("vote_count", 0);
            if (id > 0) media.add(new Media(id, type, name, poster, year,
                    voteAverage, voteCount, o.optString("backdrop_path", ""), o.optString("overview", "")));
        }
        return media;
    }

    static void availability(Context context, Media media) throws Exception {
        if (media.availabilityReady) return;
        String endpoint = "/" + media.type + "/" + media.id + "/watch/providers";
        JSONObject response = request(context, endpoint, PROVIDER_TTL, false);
        JSONObject countries = response.optJSONObject("results");
        JSONObject country = countries == null ? null : countries.optJSONObject(UnifiedCatalogPrefs.region(context));
        Set<Integer> ids = new HashSet<>();
        if (country != null) {
            collect(country.optJSONArray("flatrate"), ids);
            if (UnifiedCatalogPrefs.allowsFree(context)) {
                collect(country.optJSONArray("free"), ids);
                collect(country.optJSONArray("ads"), ids);
            }
            media.watchLink = country.optString("link", "");
        }
        synchronized (media.providerIds) {
            media.providerIds.clear();
            media.providerIds.addAll(ids);
        }
        media.availabilityReady = true;
    }

    static List<UnifiedCatalogPrefs.Provider> eligible(Context context, Media media) {
        List<UnifiedCatalogPrefs.Provider> result = new ArrayList<>();
        synchronized (media.providerIds) {
            for (UnifiedCatalogPrefs.Provider p : UnifiedCatalogPrefs.ordered(context)) {
                if (!UnifiedCatalogPrefs.selected(context, p) || p.installedPackage(context) == null) continue;
                for (int id : media.providerIds) {
                    if (p.supports(id)) {
                        result.add(p);
                        break;
                    }
                }
            }
        }
        return result;
    }

    /** Resolve an IMDb/Cinemeta ID for a Stremio detail link; TMDB IDs are not interchangeable. */
    static String imdbId(Context context, Media media) throws Exception {
        JSONObject ids = request(context, "/" + media.type + "/" + media.id + "/external_ids",
                PROVIDER_TTL, false);
        String id = ids.optString("imdb_id", "");
        return id.matches("tt[0-9]{5,12}") ? id : "";
    }

    private static void collect(JSONArray arr, Set<Integer> ids) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item != null) ids.add(item.optInt("provider_id"));
        }
    }

    private static JSONObject request(Context c, String endpoint, long ttl, boolean refresh) throws Exception {
        File dir = new File(c.getCacheDir(), "unifiedtv_json");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se puede crear la caché");
        File cache = new File(dir, "v2_" + UnifiedCatalogPrefs.region(c) + "_"
                + Integer.toHexString(endpoint.hashCode()) + ".json");
        if (!refresh && cache.isFile() && System.currentTimeMillis() - cache.lastModified() < ttl)
            return new JSONObject(readFile(cache));

        String token = UnifiedCatalogPrefs.apiKey(c);
        if (token.isEmpty()) {
            if (cache.isFile()) return new JSONObject(readFile(cache));
            throw new IOException("Configura una clave TMDB en Ajustes");
        }
        HttpURLConnection connection = null;
        try {
            String separator = endpoint.contains("?") ? "&" : "?";
            URL url = new URL("https://api.themoviedb.org/3" + endpoint + separator + "api_key="
                    + URLEncoder.encode(token, "UTF-8"));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "UnifiedTV/0.1");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300)
                throw new IOException("TMDB HTTP " + status + (status == 401 ? ": clave incorrecta" : ""));
            byte[] data = readLimited(connection.getInputStream(), 1024 * 1024);
            JSONObject object = new JSONObject(new String(data, StandardCharsets.UTF_8));
            try (FileOutputStream out = new FileOutputStream(cache)) { out.write(data); }
            return object;
        } catch (Exception e) {
            if (cache.isFile()) return new JSONObject(readFile(cache)); // offline last-known-good
            throw e;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static Bitmap poster(Context c, String path) { return artwork(c, path, "w342"); }

    static Bitmap shelfArtwork(Context c, String path) { return artwork(c, path, "w300"); }

    static Bitmap backdrop(Context c, String path) { return artwork(c, path, "w780"); }

    private static Bitmap artwork(Context c, String path, String size) {
        if (path == null || !path.startsWith("/") || path.contains("..")) return null;
        String key = size + path;
        synchronized (POSTERS) {
            Bitmap cached = POSTERS.get(key);
            if (cached != null) return cached;
        }
        File dir = new File(c.getCacheDir(), "unifiedtv_posters");
        if (!dir.exists()) dir.mkdirs();
        File cache = new File(dir, Integer.toHexString(key.hashCode()) + ".jpg");
        byte[] data = null;
        try {
            if (cache.isFile()) {
                try (FileInputStream in = new FileInputStream(cache)) {
                    data = readLimited(in, 350 * 1024);
                }
            } else {
                HttpURLConnection connection = (HttpURLConnection)
                        new URL("https://image.tmdb.org/t/p/" + key).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                try {
                    if (connection.getResponseCode() != 200) return null;
                    data = readLimited(connection.getInputStream(), 350 * 1024);
                    try (FileOutputStream out = new FileOutputStream(cache)) { out.write(data); }
                } finally { connection.disconnect(); }
            }
            Bitmap poster = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (poster != null) {
                synchronized (POSTERS) { POSTERS.put(key, poster); }
            }
            return poster;
        } catch (Exception ignored) { return null; }
    }

    private static String readFile(File path) throws IOException {
        try (FileInputStream in = new FileInputStream(path)) {
            return new String(readLimited(in, 1024 * 1024), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        try (InputStream src = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = src.read(buf)) != -1) {
                if (out.size() + n > limit) throw new IOException("Respuesta demasiado grande");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private UnifiedCatalogApi() {}
}
