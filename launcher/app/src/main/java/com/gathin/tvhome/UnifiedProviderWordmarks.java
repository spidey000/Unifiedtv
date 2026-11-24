package com.gathin.tvhome;

import android.graphics.PorterDuff;
import android.widget.ImageView;

/**
 * Full-name streaming service wordmarks, bundled locally even if the associated app is
 * not installed. Providers are matched by exact key or known Android package.
 */
final class UnifiedProviderWordmarks {
    static int forKey(String key) {
        if (key == null) return 0;
        switch (key) {
            case "netflix": return R.drawable.uic_provider_netflix;
            case "prime": return R.drawable.uic_provider_prime;
            case "disney": return R.drawable.uic_provider_disney;
            case "max": case "hbomax": return R.drawable.uic_provider_hbomax;
            case "apple": case "appletv": return R.drawable.uic_provider_appletv;
            case "skyshowtime": return R.drawable.uic_provider_skyshowtime;
            case "movistar": return R.drawable.uic_provider_movistar;
            case "atresplayer": return R.drawable.uic_provider_atresplayer;
            case "crunchyroll": return R.drawable.uic_provider_crunchyroll;
            case "hulu": return R.drawable.uic_provider_hulu;
            case "paramountplus": return R.drawable.uic_provider_paramountplus;
            case "peacock": return R.drawable.uic_provider_peacock;
            case "plutotv": return R.drawable.uic_provider_plutotv;
            case "mubi": return R.drawable.uic_provider_mubi;
            case "now": return R.drawable.uic_provider_now;
            case "tubi": return R.drawable.uic_provider_tubi;
            case "youtube": return R.drawable.uic_provider_youtube;
            case "channel4": return R.drawable.uic_provider_channel4;
            case "crave": return R.drawable.uic_provider_crave;
            case "rtl": return R.drawable.uic_provider_rtl;
            case "sky": return R.drawable.uic_provider_sky;
            case "starz": return R.drawable.uic_provider_starz;
            case "viaplay": return R.drawable.uic_provider_viaplay;
            default: return 0;
        }
    }

    static int forPackage(String packageName) {
        if (packageName == null) return 0;
        // Exact provider packages already used in availability and Watch Next.
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.CONTINUE_SOURCES)
            for (String candidate : provider.packages)
                if (packageName.equals(candidate)) return forKey(provider.key);
        // Preloaded identities for apps that may be installed in the future.
        switch (packageName) {
            case "com.a3.sgt": case "com.antena3.atresplayer.tv":
                return forKey("atresplayer");
            case "com.google.android.youtube.tv": case "com.google.android.youtube":
                return forKey("youtube");
            case "tv.pluto.android": case "tv.pluto.android.tv":
                return forKey("plutotv");
            case "com.crunchyroll.crunchyroid": case "com.crunchyroll.crunchyroid.tv":
                return forKey("crunchyroll");
            case "com.mubi": return forKey("mubi");
            case "com.paramountplus": case "com.cbs.ott":
                return forKey("paramountplus");
            case "com.hulu.livingroomplus": case "com.hulu.plus": return forKey("hulu");
            case "com.peacocktv.peacockandroid": return forKey("peacock");
            default: return 0;
        }
    }

    /** Plain transparent text for providers without bundled artwork (e.g. Nuvio/Filmin). */
    static android.graphics.drawable.Drawable textLogo(android.content.Context context, String name) {
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(android.graphics.Color.WHITE);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        paint.setTextSize(40);
        int width = Math.max(1, (int) Math.ceil(paint.measureText(name)));
        android.graphics.Paint.FontMetrics metrics = paint.getFontMetrics();
        int height = Math.max(1, (int) Math.ceil(metrics.descent - metrics.ascent));
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                width, height, android.graphics.Bitmap.Config.ARGB_8888);
        new android.graphics.Canvas(bitmap).drawText(name, 0, -metrics.ascent, paint);
        return new android.graphics.drawable.BitmapDrawable(context.getResources(), bitmap);
    }

    static void applyTint(ImageView image, int resourceId) {
        image.clearColorFilter();
        // The pre-rendered Netflix wordmark is white; the brand red contrasts with dark glass.
        if (resourceId == R.drawable.uic_provider_netflix)
            image.setColorFilter(0xFFE50914, PorterDuff.Mode.SRC_IN);
    }

    private UnifiedProviderWordmarks() {}
}
