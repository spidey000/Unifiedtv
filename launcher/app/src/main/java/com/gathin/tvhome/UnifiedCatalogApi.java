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

    /** TMDB match shared by Watch Next poster enrichment and its star rating. */
    static final class MediaArt {
        final String poster, backdrop;
        final double voteAverage;
        final int voteCount;
        final int id;
        final String type, overview;

        MediaArt(String poster, String backdrop, double voteAverage, int voteCount) {
            this(poster, backdrop, voteAverage, voteCount, 0, "", "");
        }

        MediaArt(String poster, String backdrop, double voteAverage, int voteCount,
                 int id, String type, String overview) {
            this.poster = poster;
            this.backdrop = backdrop;
            this.voteAverage = voteAverage;
            this.voteCount = voteCount;
            this.id = id;
            this.type = type;
            this.overview = overview;
        }

        boolean hasRating() {
            return voteCount > 0 && Double.isFinite(voteAverage)
                    && voteAverage > 0 && voteAverage <= 10;
        }

        String ratingText() {
            return hasRating() ? String.format(Locale.forLanguageTag("es-ES"),
                    "%.1f", voteAverage) : "";
        }
    }

    private static final LruCache<String, MediaArt> WATCH_ART = new LruCache<>(80);
    private static final long LIST_TTL = 6L * 60 * 60 * 1000;
    private static final long PROVIDER_TTL = 12L * 60 * 60 * 1000;
    // Higher resolution art is sampled for modest TVs and kept in a bounded cache.
    private static final LruCache<String, Bitmap> POSTERS = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
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
                match.optString("backdrop_path", ""),
                match.optDouble("vote_average", 0.0), match.optInt("vote_count", 0),
                match.optInt("id", 0), match.optString("media_type", ""),
                match.optString("overview", ""));
        synchronized (WATCH_ART) { WATCH_ART.put(cacheKey, artwork); }
        return artwork;
    }

    /** Full movie/series metadata for an expanded details dialog; cache is shared with catalog requests. */
    static JSONObject fullDetails(Context context, Media media) throws Exception {
        return request(context, "/" + media.type + "/" + media.id
                + "?language=es-ES&append_to_response=credits", PROVIDER_TTL, false);
    }

    /** Public TMDB details, without inventing fields when the response omits them. */
    static String fullDetailsText(JSONObject info, String type) {
        if (info == null) return "";
        List<String> lines = new ArrayList<>();
        String original = info.optString("movie".equals(type) ? "original_title" : "original_name", "");
        String title = info.optString("movie".equals(type) ? "title" : "name", "");
        if (!original.isEmpty() && !original.equalsIgnoreCase(title))
            lines.add("Título original: " + original);
        String tag = info.optString("tagline", "");
        if (!tag.isEmpty()) lines.add("Lema: " + tag);
        JSONArray genres = info.optJSONArray("genres");
        String genre = names(genres, 10);
        if (!genre.isEmpty()) lines.add("Géneros: " + genre);
        String premiere = info.optString("movie".equals(type) ? "release_date" : "first_air_date", "");
        if (!premiere.isEmpty()) lines.add("Estreno: " + premiere);
        if ("movie".equals(type)) {
            int runtime = info.optInt("runtime", 0);
            if (runtime > 0) lines.add("Duración: " + runtime + " min");
        } else {
            int seasons = info.optInt("number_of_seasons", 0);
            int episodes = info.optInt("number_of_episodes", 0);
            if (seasons > 0) lines.add("Temporadas: " + seasons);
            if (episodes > 0) lines.add("Episodios: " + episodes);
            JSONArray times = info.optJSONArray("episode_run_time");
            if (times != null && times.length() > 0 && times.optInt(0, 0) > 0)
                lines.add("Duración por episodio: " + times.optInt(0) + " min");
            String creators = names(info.optJSONArray("created_by"), 6);
            if (!creators.isEmpty()) lines.add("Creadores: " + creators);
        }
        String state = info.optString("status", "");
        if (!state.isEmpty()) lines.add("Estado: " + state);
        JSONObject credits = info.optJSONObject("credits");
        if (credits != null) {
            JSONArray crew = credits.optJSONArray("crew");
            List<String> directors = new ArrayList<>();
            if (crew != null) for (int i = 0; i < crew.length() && directors.size() < 4; i++) {
                JSONObject member = crew.optJSONObject(i);
                if (member != null && "Director".equals(member.optString("job", ""))) {
                    String name = member.optString("name", "");
                    if (!name.isEmpty() && !directors.contains(name)) directors.add(name);
                }
            }
            if (!directors.isEmpty()) lines.add("Dirección: " + android.text.TextUtils.join(", ", directors));
            String cast = names(credits.optJSONArray("cast"), 9);
            if (!cast.isEmpty()) lines.add("Reparto: " + cast);
        }
        String companies = names(info.optJSONArray("production_companies"), 5);
        if (!companies.isEmpty()) lines.add("Producción: " + companies);
        String countries = names(info.optJSONArray("production_countries"), 6);
        if (!countries.isEmpty()) lines.add("Países: " + countries);
        int votes = info.optInt("vote_count", 0);
        if (votes > 0) lines.add("Votos registrados: " + votes);
        return android.text.TextUtils.join("\n", lines);
    }

    private static String names(JSONArray items, int max) {
        if (items == null) return "";
        List<String> result = new ArrayList<>();
        for (int i = 0; i < items.length() && result.size() < max; i++) {
            JSONObject item = items.optJSONObject(i);
            String name = item == null ? "" : item.optString("name", "");
            if (!name.isEmpty() && !result.contains(name)) result.add(name);
        }
        return android.text.TextUtils.join(", ", result);
    }

    /** The TMDB external IDs endpoint supplies Wikidata QIDs, not service content IDs. */
    static String wikidataId(Context context, Media media) throws Exception {
        JSONObject ids = request(context, "/" + media.type + "/" + media.id
                + "/external_ids", PROVIDER_TTL, false);
        String qid = ids.optString("wikidata_id", "");
        return qid.matches("Q[1-9][0-9]{0,11}") ? qid : "";
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

    /** w780 retains detail on Full HD/4K TVs; old w342 disk cache is not reused. */
    static Bitmap poster(Context c, String path) { return artwork(c, path, "w780"); }

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
                    data = readLimited(in, 2 * 1024 * 1024);
                }
            } else {
                HttpURLConnection connection = (HttpURLConnection)
                        new URL("https://image.tmdb.org/t/p/" + key).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                try {
                    if (connection.getResponseCode() != 200) return null;
                    data = readLimited(connection.getInputStream(), 2 * 1024 * 1024);
                    try (FileOutputStream out = new FileOutputStream(cache)) { out.write(data); }
                } finally { connection.disconnect(); }
            }
            Bitmap poster = decodePoster(c, data);
            if (poster != null) {
                synchronized (POSTERS) { POSTERS.put(key, poster); }
            }
            return poster;
        } catch (Exception ignored) { return null; }
    }

    /**
     * Decode enough pixels for a sharp TV card while avoiding multiple multi-MB
     * full-size bitmaps on inexpensive Android TV hardware.
     */
    private static Bitmap decodePoster(Context c, byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int screenWidth = c.getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.max(320, Math.min(780, Math.round(screenWidth * .28f)));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / (options.inSampleSize * 2) >= targetWidth)
            options.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
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
