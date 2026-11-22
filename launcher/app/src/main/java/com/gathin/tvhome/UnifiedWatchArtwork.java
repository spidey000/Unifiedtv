package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;

import java.text.Normalizer;
import java.util.Locale;

/** Prefers correctly shaped platform art; fills missing aspect ratios from TMDB. */
final class UnifiedWatchArtwork {
    static final class Pair {
        final Bitmap portrait, landscape;
        Pair(Bitmap portrait, Bitmap landscape) {
            this.portrait = portrait;
            this.landscape = landscape;
        }
    }

    static Pair resolve(Context context, UnifiedWatchNext.Entry item) {
        Bitmap poster = UnifiedWatchNext.artwork(context, item.poster);
        Bitmap thumbnail = item.thumbnail.equals(item.poster) ? poster
                : UnifiedWatchNext.artwork(context, item.thumbnail);
        Bitmap portrait = isPortrait(poster) ? poster : isPortrait(thumbnail) ? thumbnail : null;
        Bitmap landscape = isWide(thumbnail) ? thumbnail : isWide(poster) ? poster : null;

        if (portrait == null || landscape == null) {
            try {
                UnifiedCatalogApi.MediaArt match = UnifiedCatalogApi.watchArtwork(context,
                        item.title, item.mediaType, item.releaseDate);
                if (match != null) {
                    if (portrait == null) {
                        Bitmap tmdbPoster = UnifiedCatalogApi.poster(context, match.poster);
                        if (isPortrait(tmdbPoster)) portrait = tmdbPoster;
                    }
                    if (landscape == null) {
                        Bitmap tmdbBackdrop = UnifiedCatalogApi.backdrop(context, match.backdrop);
                        if (isWide(tmdbBackdrop)) landscape = tmdbBackdrop;
                    }
                }
            } catch (Exception ignored) { /* User artwork still works without TMDB. */ }
        }

        // Even when the platform gives only one shape and TMDB is unavailable,
        // 2:3 center-crops it; 16:9 uses FIT_CENTER to display the ENTIRE image.
        if (portrait == null) portrait = poster != null ? poster : thumbnail;
        if (landscape == null) landscape = thumbnail != null ? thumbnail : poster;
        return new Pair(portrait, landscape);
    }

    static boolean isPortrait(Bitmap bitmap) {
        return bitmap != null && isPortrait(bitmap.getWidth(), bitmap.getHeight());
    }

    static boolean isWide(Bitmap bitmap) {
        return bitmap != null && isWide(bitmap.getWidth(), bitmap.getHeight());
    }

    static boolean isPortrait(int width, int height) {
        return width > 0 && height > 0 && width * 100L <= height * 88L;
    }

    static boolean isWide(int width, int height) {
        return width > 0 && height > 0 && width * 100L >= height * 125L;
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
