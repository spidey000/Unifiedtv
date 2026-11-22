package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;

import java.text.Normalizer;
import java.util.Locale;

/** Chooses a single portrait poster; all UnifiedTV catalog cards are 2:3. */
final class UnifiedWatchArtwork {
    /** Keep any sufficiently detailed portrait provided by the original app. */
    static Bitmap platformPoster(Context context, UnifiedWatchNext.Entry item) {
        Bitmap poster = UnifiedWatchNext.artwork(context, item.poster);
        Bitmap thumbnail = item.thumbnail.equals(item.poster) ? poster
                : UnifiedWatchNext.artwork(context, item.thumbnail);
        int minWidth = targetPosterWidthPixels(context);
        if (isPortrait(poster) && poster.getWidth() >= minWidth) return poster;
        if (isPortrait(thumbnail) && thumbnail.getWidth() >= minWidth) return thumbnail;
        if (isPortrait(poster)) return poster;
        if (isPortrait(thumbnail)) return thumbnail;
        return poster != null ? poster : thumbnail;
    }

    /**
     * Prefer TMDB's 780px portrait when Android published only a tiny source;
     * don't discard a sharper original poster or fetch a landscape backdrop.
     */
    static Bitmap withTmdbPoster(Context context, Bitmap existing,
                                 UnifiedCatalogApi.MediaArt match) {
        int minWidth = targetPosterWidthPixels(context);
        if (!needsBetterPoster(existing, minWidth) || match == null) return existing;
        Bitmap tmdb = UnifiedCatalogApi.poster(context, match.poster);
        if (isPortrait(tmdb) && (existing == null || !isPortrait(existing)
                || tmdb.getWidth() > existing.getWidth())) return tmdb;
        return existing;
    }

    static int targetPosterWidthPixels(Context context) {
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        return Math.max(280, Math.min(780, Math.round(screen * .155f)));
    }

    static boolean needsBetterPoster(Bitmap bitmap, int minWidth) {
        return bitmap == null || needsBetterPoster(bitmap.getWidth(),
                bitmap.getHeight(), minWidth);
    }

    static boolean needsBetterPoster(int width, int height, int minWidth) {
        return !isPortrait(width, height) || width < minWidth;
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
