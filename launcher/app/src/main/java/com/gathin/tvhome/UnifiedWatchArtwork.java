package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;

import java.text.Normalizer;
import java.util.Locale;

/** Chooses a single portrait poster; all UnifiedTV catalog cards are 2:3. */
final class UnifiedWatchArtwork {
    /**
     * Show Android TV's own poster if correctly shaped; try its thumbnail only
     * when the poster is not a portrait. A wide source is center-cropped inside
     * the 2:3 card if neither source provides portrait artwork.
     */
    static Bitmap platformPoster(Context context, UnifiedWatchNext.Entry item) {
        Bitmap poster = UnifiedWatchNext.artwork(context, item.poster);
        if (isPortrait(poster)) return poster;
        Bitmap thumbnail = item.thumbnail.equals(item.poster) ? poster
                : UnifiedWatchNext.artwork(context, item.thumbnail);
        if (isPortrait(thumbnail)) return thumbnail;
        return poster != null ? poster : thumbnail;
    }

    /** Resolve only a missing portrait via TMDB; never fetch the 16:9 backdrop. */
    static Bitmap withTmdbPoster(Context context, UnifiedWatchNext.Entry item,
                                 Bitmap existing) {
        if (isPortrait(existing)) return existing;
        try {
            UnifiedCatalogApi.MediaArt match = UnifiedCatalogApi.watchArtwork(context,
                    item.title, item.mediaType, item.releaseDate);
            if (match != null) {
                Bitmap tmdbPoster = UnifiedCatalogApi.poster(context, match.poster);
                if (isPortrait(tmdbPoster)) return tmdbPoster;
            }
        } catch (Exception ignored) { /* Retain the platform's image offline. */ }
        return existing;
    }

    static boolean isPortrait(Bitmap bitmap) {
        return bitmap != null && isPortrait(bitmap.getWidth(), bitmap.getHeight());
    }

    static boolean isWide(Bitmap bitmap) {
        return bitmap != null && isWide(bitmap.getWidth(), bitmap.getHeight());
    }

    static boolean isPortrait(int width, int height) {
        return width > 0 && height > 0 && width * 100L <= height * 82L;
    }

    static boolean isWide(int width, int height) {
        return width > 0 && height > 0 && width * 100L >= height * 150L;
    }

    static String searchTitle(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceFirst("\\s*\\((?:19|20)\\d{2}\\)\\s*$", "").trim();
    }

    static String normalizeTitle(String title) {
        if (title == null) return "";
        return Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    static int titleYear(String title) {
        if (title == null) return 0;
        java.util.regex.Matcher year = java.util.regex.Pattern.compile(
                "\\((19\\d{2}|20\\d{2})\\)\\s*$").matcher(title.trim());
        return year.find() ? Integer.parseInt(year.group(1)) : 0;
    }

    static int year(String date) {
        if (date == null || !date.matches("(?:19|20)\\d{2}(?:-.*)?")) return 0;
        return Integer.parseInt(date.substring(0, 4));
    }

    private UnifiedWatchArtwork() {}
}
