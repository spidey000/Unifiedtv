package com.gathin.tvhome;

import android.graphics.PorterDuff;
import android.widget.ImageView;

/** Packaged full-name streaming service logos; distinct from square Android app icons. */
final class UnifiedProviderWordmarks {
    static int forKey(String key) {
        if ("netflix".equals(key)) return R.drawable.uic_provider_netflix;
        if ("prime".equals(key)) return R.drawable.uic_provider_prime;
        if ("disney".equals(key)) return R.drawable.uic_provider_disney;
        return 0;
    }

    static int forPackage(String packageName) {
        if (packageName == null) return 0;
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.PROVIDERS)
            for (String candidate : provider.packages)
                if (packageName.equals(candidate)) return forKey(provider.key);
        return 0;
    }

    static void applyTint(ImageView image, int resourceId) {
        image.clearColorFilter();
        // Source marks are white: Netflix is red, Prime and Disney+ remain white on dark glass.
        if (resourceId == R.drawable.uic_provider_netflix)
            image.setColorFilter(0xFFE50914, PorterDuff.Mode.SRC_IN);
    }

    private UnifiedProviderWordmarks() {}
}
