package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional, best-effort JustWatch per-provider title URLs.
 * The partner API requires a contract; this public GraphQL query is not a
 * guaranteed API and may stop working. Never fabricate provider title IDs.
 */
final class UnifiedJustWatchLinks {
    private static final long CACHE_TTL_MS = 6L * 60 * 60 * 1000;
    private static final String QUERY = "query UnifiedTvLookup($country:Country!,$filter:TitleFilter!){"
            + "popularTitles(country:$country,filter:$filter,first:10,sortBy:POPULAR){"
            + "edges{node{objectType content(country:$country,language:\"es\"){externalIds{tmdbId}}"
            + "... on MovieOrShowOrSeason{offers(country:$country,platform:WEB){"
            + "monetizationType standardWebURL package{clearName}}}}}}}";
    private static final Map<String, Map<String, String>> MEMORY = new HashMap<>();

    /** Returns only provider keys with provider-owned HTTPS content links. */
    static Map<String, String> offers(Context context, UnifiedCatalogApi.Media media) {
        String key = UnifiedCatalogPrefs.region(context) + "_" + media.type + "_" + media.id;
        synchronized (MEMORY) {
            Map<String, String> found = MEMORY.get(key);
            if (found != null) return found;
        }
        File dir = new File(context.getCacheDir(), "unifiedtv_justwatch");
        File file = new File(dir, key + ".json");
        try {
            if (file.isFile() && System.currentTimeMillis() - file.lastModified() < CACHE_TTL_MS) {
                try (FileInputStream in = new FileInputStream(file)) {
                    Map<String, String> cached = decode(new JSONObject(new String(read(in),
                            StandardCharsets.UTF_8)));
                    synchronized (MEMORY) { MEMORY.put(key, cached); }
                    return cached;
                }
            }
        } catch (Exception ignored) { /* Re-query after corrupted cache. */ }
        try {
            JSONObject variables = new JSONObject()
                    .put("country", UnifiedCatalogPrefs.region(context))
                    .put("filter", new JSONObject().put("searchQuery", media.title)
                            .put("includeTitlesWithoutUrl", true));
            byte[] request = new JSONObject()
                    .put("query", QUERY).put("variables", variables)
                    .toString().getBytes(StandardCharsets.UTF_8);
            HttpURLConnection connection = (HttpURLConnection)
                    new URL("https://apis.justwatch.com/graphql").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(6500);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "UnifiedTV/0.7");
            try {
                connection.getOutputStream().write(request);
                if (connection.getResponseCode() != 200) return Collections.emptyMap();
                JSONObject response = new JSONObject(new String(read(connection.getInputStream()),
                        StandardCharsets.UTF_8));
                if (response.optJSONArray("errors") != null) return Collections.emptyMap();
                JSONArray edges = response.optJSONObject("data") == null ? null
                        : response.optJSONObject("data").optJSONObject("popularTitles") == null ? null
                        : response.optJSONObject("data").optJSONObject("popularTitles").optJSONArray("edges");
                Map<String, String> links = new HashMap<>();
                if (edges != null) for (int i = 0; i < edges.length(); i++) {
                    JSONObject edge = edges.optJSONObject(i);
                    JSONObject node = edge == null ? null : edge.optJSONObject("node");
                    if (node == null) continue;
                    String kind = node.optString("objectType", "");
                    if ("movie".equals(media.type) && !"MOVIE".equals(kind)) continue;
                    if ("tv".equals(media.type) && !"SHOW".equals(kind)) continue;
                    JSONObject external = node.optJSONObject("content") == null ? null
                            : node.optJSONObject("content").optJSONObject("externalIds");
                    if (external == null || !String.valueOf(media.id).equals(
                            external.optString("tmdbId", ""))) continue;
                    JSONArray offers = node.optJSONArray("offers");
                    if (offers == null) continue;
                    for (int j = 0; j < offers.length(); j++) {
                        JSONObject offer = offers.optJSONObject(j);
                        if (offer == null || !included(offer.optString("monetizationType", ""))) continue;
                        String link = offer.optString("standardWebURL", "");
                        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.PROVIDERS) {
                            if (safeUrl(provider.key, link)) links.putIfAbsent(provider.key, link);
                        }
                    }
                }
                JSONObject encoded = new JSONObject();
                for (Map.Entry<String, String> e : links.entrySet()) encoded.put(e.getKey(), e.getValue());
                if (dir.isDirectory() || dir.mkdirs()) {
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(encoded.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException ignored) { /* Memory cache is enough. */ }
                }
                Map<String, String> answer = Collections.unmodifiableMap(links);
                synchronized (MEMORY) { MEMORY.put(key, answer); }
                return answer;
            } finally { connection.disconnect(); }
        } catch (Exception ignored) {
            // Public GraphQL is undocumented and may be rate limited.
            return Collections.emptyMap();
        }
    }

    static boolean included(String monetization) {
        return "FLATRATE".equals(monetization) || "FREE".equals(monetization)
                || "ADS".equals(monetization) || "FREE_WITH_ADS".equals(monetization);
    }

    /** Host allowlist prevents JustWatch redirects/search pages opening a wrong app. */
    static boolean safeUrl(String provider, String value) {
        try {
            java.net.URI uri = new java.net.URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            String path = uri.getPath();
            if (path == null || path.length() < 3) return false;
            switch (provider) {
                case "netflix": return domain(host, "netflix.com")
                        && path.matches("/(?:title|watch)/[0-9]{4,14}.*");
                case "prime": return domain(host, "primevideo.com")
                        || domain(host, "amazon.com") && path.contains("/detail/");
                case "max": return domain(host, "max.com") || domain(host, "hbomax.com");
                case "disney": return domain(host, "disneyplus.com");
                case "apple": return host.equals("tv.apple.com");
                case "skyshowtime": return domain(host, "skyshowtime.com");
                case "movistar": return domain(host, "movistarplus.es");
                case "filmin": return domain(host, "filmin.es");
                default: return false;
            }
        } catch (Exception ignored) { return false; }
    }

    private static boolean domain(String host, String root) {
        return host.equals(root) || host.endsWith("." + root);
    }

    private static Map<String, String> decode(JSONObject json) {
        Map<String, String> values = new HashMap<>();
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.PROVIDERS) {
            String link = json.optString(provider.key, "");
            if (safeUrl(provider.key, link)) values.put(provider.key, link);
        }
        return Collections.unmodifiableMap(values);
    }

    private static byte[] read(InputStream in) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int size;
            while ((size = stream.read(buf)) != -1) {
                if (out.size() + size > 512 * 1024) throw new IOException("JustWatch response too large");
                out.write(buf, 0, size);
            }
            return out.toByteArray();
        }
    }

    private UnifiedJustWatchLinks() {}
}
