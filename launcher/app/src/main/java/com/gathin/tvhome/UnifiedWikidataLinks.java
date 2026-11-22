package com.gathin.tvhome;

import android.content.Context;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * TMDB external_ids.wikidata_id -> Wikidata claims -> direct title URLs.
 * No unofficial JustWatch endpoint, subscription credentials or scraped page.
 * Never substitutes a TMDB id for an app's own content identifier.
 */
final class UnifiedWikidataLinks {
    private static final long TTL = 14L * 24 * 60 * 60 * 1000;
    private static final LruCache<String, Map<String, String>> MEMORY = new LruCache<>(160);
    private static final String USER_AGENT =
            "UnifiedTV/0.8 (https://github.com/spidey000/Unifiedtv; public movie links)";

    static Map<String, String> resolve(Context context, UnifiedCatalogApi.Media media) {
        String key = media.type + "_" + media.id;
        synchronized (MEMORY) {
            Map<String, String> hit = MEMORY.get(key);
            if (hit != null) return hit;
        }
        File directory = new File(context.getCacheDir(), "unifiedtv_wikidata");
        File file = new File(directory, key + ".json");
        try {
            if (file.isFile() && System.currentTimeMillis() - file.lastModified() < TTL) {
                try (FileInputStream input = new FileInputStream(file)) {
                    Map<String, String> result = decode(new JSONObject(
                            new String(read(input, 64 * 1024), StandardCharsets.UTF_8)));
                    synchronized (MEMORY) { MEMORY.put(key, result); }
                    return result;
                }
            }
        } catch (Exception ignored) { /* Stale or corrupt; query again. */ }

        // Do not treat network failures as a definitive "not available".
        try {
            String qid = UnifiedCatalogApi.wikidataId(context, media);
            if (!qid.matches("Q[1-9][0-9]{0,11}")) {
                qid = wikidataItemFromTmdb(media); // Covers titles without a TMDB wikidata_id.
            }
            if (!qid.matches("Q[1-9][0-9]{0,11}")) return Collections.emptyMap();
            String url = "https://www.wikidata.org/w/api.php?action=wbgetentities&ids="
                    + URLEncoder.encode(qid, "UTF-8") + "&props=claims&format=json";
            JSONObject root = json(url, 768 * 1024);
            JSONObject entities = root.optJSONObject("entities");
            JSONObject entity = entities == null ? null : entities.optJSONObject(qid);
            JSONObject claims = entity == null ? null : entity.optJSONObject("claims");
            if (claims == null) return Collections.emptyMap();
            Map<String, String> links = fromClaims(claims, media.type);
            if (directory.isDirectory() || directory.mkdirs()) {
                JSONObject serial = new JSONObject();
                for (Map.Entry<String, String> entry : links.entrySet())
                    serial.put(entry.getKey(), entry.getValue());
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(serial.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException ignored) { /* Memory cache is enough. */ }
            }
            synchronized (MEMORY) { MEMORY.put(key, links); }
            return links;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    /** SPARQL is a rare fallback, not one query per carrousel item on every launch. */
    private static String wikidataItemFromTmdb(UnifiedCatalogApi.Media media) {
        try {
            String property = "tv".equals(media.type) ? "P4983" : "P4947";
            String query = "SELECT ?item WHERE { ?item <http://www.wikidata.org/prop/direct/"
                    + property + "> \"" + media.id + "\" } LIMIT 2";
            String url = "https://query.wikidata.org/sparql?format=json&query="
                    + URLEncoder.encode(query, "UTF-8");
            JSONObject result = json(url, 64 * 1024);
            JSONArray bindings = result.optJSONObject("results") == null ? null
                    : result.optJSONObject("results").optJSONArray("bindings");
            if (bindings == null || bindings.length() != 1) return "";
            JSONObject item = bindings.getJSONObject(0).optJSONObject("item");
            String value = item == null ? "" : item.optString("value", "");
            int index = value.lastIndexOf('/');
            return index < 0 ? "" : value.substring(index + 1);
        } catch (Exception ignored) {
            return "";
        }
    }

    static Map<String, String> fromClaims(JSONObject claims, String type) {
        Map<String, String> out = new HashMap<>();
        add(out, "netflix", "https://www.netflix.com/title/",
                unique(claims, "P1874", "[0-9]{4,14}"));
        // Newer Prime Video IDs (P14440) are not Amazon ASINs (P8055).
        String prime = unique(claims, "P14440", "[0-9A-Z]{20,32}");
        if (prime.isEmpty()) prime = unique(claims, "P8055", "B[0-9A-Z]{9}");
        add(out, "prime", "https://www.primevideo.com/detail/", prime);
        String disney = unique(claims, "tv".equals(type) ? "P7596" : "P7595",
                "[A-Za-z0-9]{8,32}");
        add(out, "disney", "tv".equals(type)
                ? "https://www.disneyplus.com/series/wp/"
                : "https://www.disneyplus.com/movies/wd/", disney);
        // Disney browse IDs are a useful fallback when the item-specific claim is absent.
        if (!out.containsKey("disney")) add(out, "disney", "https://www.disneyplus.com/browse/",
                unique(claims, "P13902", "[A-Za-z0-9_-]{8,50}"));
        String max = unique(claims, "P8298",
                "(?:movie|show|series)/[A-Za-z0-9_:\\-]{8,110}");
        add(out, "max", "https://play.hbomax.com/", max);
        return Collections.unmodifiableMap(out);
    }

    private static void add(Map<String, String> out, String provider, String prefix, String id) {
        if (!id.isEmpty()) out.put(provider, prefix + id);
    }

    /** Never pick an arbitrary id when active Wikidata statements disagree. */
    static String unique(JSONObject claims, String property, String pattern) {
        JSONArray statements = claims == null ? null : claims.optJSONArray(property);
        Set<String> values = new HashSet<>();
        if (statements != null) for (int i = 0; i < statements.length(); i++) {
            JSONObject statement = statements.optJSONObject(i);
            if (statement == null || "deprecated".equals(statement.optString("rank"))) continue;
            JSONObject snak = statement.optJSONObject("mainsnak");
            JSONObject data = snak == null ? null : snak.optJSONObject("datavalue");
            String id = data == null ? "" : data.optString("value", "");
            if (id.matches(pattern)) values.add(id);
        }
        return values.size() == 1 ? values.iterator().next() : "";
    }

    static boolean safeUrl(String provider, String value) {
        if (value == null) return false;
        try {
            java.net.URI uri = new java.net.URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getPort() != -1) return false;
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            String path = uri.getPath();
            if (path == null) return false;
            switch (provider) {
                case "netflix": return domain(host, "netflix.com")
                        && path.matches("/title/[0-9]{4,14}");
                case "prime": return domain(host, "primevideo.com")
                        && path.matches("/detail/(?:B[0-9A-Z]{9}|[0-9A-Z]{20,32})");
                case "disney": return domain(host, "disneyplus.com")
                        && (path.matches("/(?:movies/wd|series/wp)/[A-Za-z0-9]{8,32}")
                        || path.matches("/browse/[A-Za-z0-9_-]{8,50}"));
                case "max": return domain(host, "hbomax.com")
                        && path.matches("/(?:movie|show|series)/[A-Za-z0-9_:\\-]{8,110}");
                default: return false;
            }
        } catch (Exception ignored) { return false; }
    }

    private static boolean domain(String host, String expected) {
        return host.equals(expected) || host.endsWith("." + expected);
    }

    private static Map<String, String> decode(JSONObject object) {
        Map<String, String> out = new HashMap<>();
        for (String name : new String[]{"netflix", "prime", "disney", "max"}) {
            String value = object.optString(name, "");
            if (safeUrl(name, value)) out.put(name, value);
        }
        return Collections.unmodifiableMap(out);
    }

    private static JSONObject json(String url, int maxBytes) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(4500);
        connection.setReadTimeout(6500);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            if (connection.getResponseCode() != 200)
                throw new IOException("Wikidata returned " + connection.getResponseCode());
            return new JSONObject(new String(read(connection.getInputStream(), maxBytes),
                    StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
    }

    private static byte[] read(InputStream input, int limit) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = stream.read(bytes)) != -1) {
                if (buffer.size() + n > limit) throw new IOException("Wikidata response too large");
                buffer.write(bytes, 0, n);
            }
            return buffer.toByteArray();
        }
    }

    private UnifiedWikidataLinks() {}
}
