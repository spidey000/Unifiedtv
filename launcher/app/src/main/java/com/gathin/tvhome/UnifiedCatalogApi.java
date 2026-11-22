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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        volatile boolean autoTargetReady;
        volatile UnifiedCatalogRoute.Target autoTarget;
        volatile String watchLink = "";
        volatile String availabilityError = "";
        final Set<Integer> providerIds = new HashSet<>();
        final Map<Integer, String> providerLogos = new HashMap<>();

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

    /** Paths are fetched only when Android TV does not provide an image in the right ratio. */
    static final class MediaArt {
        final String poster, backdrop;
        MediaArt(String poster, String backdrop) {
            this.poster = poster;
            this.backdrop = backdrop;
        }
    }

    private static final LruCache<String, MediaArt> WATCH_ART = new LruCache<>(80);
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
        Map<Integer, String> logos = new HashMap<>();
        if (country != null) {
            collect(country.optJSONArray("flatrate"), ids, logos);
            if (UnifiedCatalogPrefs.allowsFree(context)) {
                collect(country.optJSONArray("free"), ids, logos);
                collect(country.optJSONArray("ads"), ids, logos);
            }
            media.watchLink = country.optString("link", "");
        }
        synchronized (media.providerIds) {
            media.providerIds.clear();
            media.providerIds.addAll(ids);
            media.providerLogos.clear();
            media.providerLogos.putAll(logos);
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

    /**
     * Resolve poster and backdrop from TMDB for a Watch Next title. Exact normalized
     * title match only; do not guess if multiple films/shows share the same title.
     * The platform-supplied content type and release year disambiguate where available.
     * Uses the existing bounded JSON cache, and never runs on the UI thread.
     */
    static MediaArt watchArtwork(Context context, String rawTitle, String typeHint,
                                 String releaseDate) throws Exception {
        String title = UnifiedWatchArtwork.searchTitle(rawTitle);
        if (title.isEmpty() || UnifiedCatalogPrefs.apiKey(context).isEmpty()) return null;
        int year = "tv".equals(typeHint) ? 0 : UnifiedWatchArtwork.year(releaseDate);
        if (year == 0) year = UnifiedWatchArtwork.titleYear(rawTitle);
        String cacheKey = typeHint + "|" + year + "|" + UnifiedWatchArtwork.normalizeTitle(title);
        synchronized (WATCH_ART) {
            MediaArt cached = WATCH_ART.get(cacheKey);
            if (cached != null) return cached;
        }
        String endpoint = "/search/multi?language=es-ES&include_adult=false&query="
                + URLEncoder.encode(title, "UTF-8");
        JSONObject result = request(context, endpoint, PROVIDER_TTL, false);
        JSONArray candidates = result.optJSONArray("results");
        if (candidates == null) return null;
        String target = UnifiedWatchArtwork.normalizeTitle(title);
        List<JSONObject> matches = new ArrayList<>();
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject item = candidates.optJSONObject(i);
            if (item == null) continue;
            String type = item.optString("media_type", "");
            if (!"movie".equals(type) && !"tv".equals(type)) continue;
            if (!typeHint.isEmpty() && !typeHint.equals(type)) continue;
            String localized = item.optString("movie".equals(type) ? "title" : "name", "");
            String original = item.optString("movie".equals(type) ? "original_title" : "original_name", "");
            if (!target.equals(UnifiedWatchArtwork.normalizeTitle(localized))
                    && !target.equals(UnifiedWatchArtwork.normalizeTitle(original))) continue;
            String date = item.optString("movie".equals(type) ? "release_date" : "first_air_date", "");
            int candidateYear = UnifiedWatchArtwork.year(date);
            if (year > 0 && candidateYear > 0 && Math.abs(year - candidateYear) > 1) continue;
            matches.add(item);
        }
        if (year > 0 && matches.size() > 1) {
            List<JSONObject> exactYear = new ArrayList<>();
            for (JSONObject item : matches) {
                int candidateYear = UnifiedWatchArtwork.year(item.optString(
                        "movie".equals(item.optString("media_type")) ? "release_date" : "first_air_date", ""));
                if (candidateYear == year) exactYear.add(item);
            }
            if (!exactYear.isEmpty()) matches = exactYear;
        }
        if (matches.size() != 1) return null;
        JSONObject match = matches.get(0);
        MediaArt artwork = new MediaArt(match.optString("poster_path", ""),
                match.optString("backdrop_path", ""));
        synchronized (WATCH_ART) { WATCH_ART.put(cacheKey, artwork); }
        return artwork;
    }

    /** TMDB ids and Netflix ids are distinct. Resolve only authoritative P1874 mappings. */
    static String netflixTitleId(Context context, Media media) {
        if (!"movie".equals(media.type) && !"tv".equals(media.type)) return "";
        File dir = new File(context.getCacheDir(), "unifiedtv_netflix");
        File cache = new File(dir, media.type + "_" + media.id + ".json");
        final long ttl = 14L * 24 * 60 * 60 * 1000;
        if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < ttl) {
            try {
                String id = new JSONObject(readFile(cache)).optString("id", "");
                return UnifiedNetflixLinks.validTitleId(id) ? id : "";
            } catch (Exception ignored) { /* Re-resolve after corrupt cache. */ }
        }

        String id = "";
        boolean validResponse = false;
        try {
            JSONObject external = request(context, "/" + media.type + "/" + media.id
                    + "/external_ids", PROVIDER_TTL, false);
            String qid = external.optString("wikidata_id", "");
            if (!qid.matches("Q[1-9][0-9]{0,11}")) return "";
            URL url = new URL("https://www.wikidata.org/w/api.php?action=wbgetclaims"
                    + "&entity=" + qid + "&property=P1874&format=json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("User-Agent", "UnifiedTV/0.7 (https://github.com/spidey000/Unifiedtv)");
            try {
                if (connection.getResponseCode() != 200) return "";
                JSONObject response = new JSONObject(new String(
                        readLimited(connection.getInputStream(), 128 * 1024), StandardCharsets.UTF_8));
                JSONArray claims = response.optJSONObject("claims") == null ? null
                        : response.optJSONObject("claims").optJSONArray("P1874");
                Set<String> matches = new HashSet<>();
                if (claims != null) for (int i = 0; i < claims.length(); i++) {
                    JSONObject claim = claims.optJSONObject(i);
                    if (claim == null || "deprecated".equals(claim.optString("rank"))) continue;
                    JSONObject snak = claim.optJSONObject("mainsnak");
                    JSONObject data = snak == null ? null : snak.optJSONObject("datavalue");
                    String candidate = data == null ? "" : data.optString("value", "");
                    if (UnifiedNetflixLinks.validTitleId(candidate)) matches.add(candidate);
                }
                if (matches.size() == 1) id = matches.iterator().next();
                validResponse = true; // Ambiguous or absent IDs must not be guessed.
            } finally {
                connection.disconnect();
            }
        } catch (Exception ignored) { /* Network unavailable, retry next time. */ }
        if (validResponse && (dir.exists() || dir.mkdirs())) {
            try (FileOutputStream out = new FileOutputStream(cache)) {
                out.write(new JSONObject().put("id", id).toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { /* In-memory result still works. */ }
        }
        return id;
    }

    private static void collect(JSONArray arr, Set<Integer> ids, Map<Integer, String> logos) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("provider_id", 0);
            if (id <= 0) continue;
            ids.add(id);
            String path = item.optString("logo_path", "");
            if (path.startsWith("/") && !path.contains("..")) logos.put(id, path);
        }
    }

    static String logoPath(Media media, UnifiedCatalogPrefs.Provider provider) {
        synchronized (media.providerIds) {
            for (int id : provider.tmdbIds) {
                String path = media.providerLogos.get(id);
                if (path != null) return path;
            }
        }
        return "";
    }

    static Bitmap logo(Context context, String path) { return artwork(context, path, "w92"); }

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
            connection = openRequest(endpoint, token);
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

    private static HttpURLConnection openRequest(String endpoint, String key) throws IOException {
        String separator = endpoint.contains("?") ? "&" : "?";
        URL url = new URL("https://api.themoviedb.org/3" + endpoint + separator + "api_key="
                + URLEncoder.encode(key, "UTF-8"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "UnifiedTV/0.1");
        return connection;
    }

    static boolean validatesPairingKey(String key) throws IOException {
        HttpURLConnection connection = openRequest("/configuration", key);
        try {
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(4500);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) return false;
            if (status != 200) throw new IOException("TMDB status " + status);
            return true;
        } finally {
            connection.disconnect();
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
