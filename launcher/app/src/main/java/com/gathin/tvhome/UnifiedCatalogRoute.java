package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.List;

/**
 * Tries a provider's public Android search activity first and otherwise opens its TV app.
 * There is no universal provider-specific title URI; never claim that the target
 * playback screen opened unless the provider explicitly supports it.
 */
final class UnifiedCatalogRoute {
    static void open(Activity activity, UnifiedCatalogApi.Media media) {
        List<UnifiedCatalogPrefs.Provider> eligible = UnifiedCatalogApi.eligible(activity, media);
        if (eligible.isEmpty()) {
            new AlertDialog.Builder(activity)
                .setTitle("No disponible en tus plataformas")
                .setMessage("No se ha encontrado «" + media.title + "» en las plataformas seleccionadas e instaladas de "
                        + UnifiedCatalogPrefs.region(activity) + ". Comprueba tus preferencias y tu suscripción.")
                .setPositiveButton("Aceptar", null)
                .setNeutralButton(media.watchLink.isEmpty() ? null : "Ver opciones online", (dialog, which) -> {
                    try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(media.watchLink))); }
                    catch (RuntimeException e) { Toast.makeText(activity, "No se puede abrir el enlace", Toast.LENGTH_SHORT).show(); }
                })
                .show();
            return;
        }
        // A failed Android launch falls back to the next eligible provider.
        for (UnifiedCatalogPrefs.Provider provider : eligible) {
            String pkg = provider.installedPackage(activity);
            if (pkg == null) continue;
            Intent search = new Intent(Intent.ACTION_SEARCH)
                    .setPackage(pkg)
                    .putExtra(SearchManager.QUERY, media.title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (search.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(search);
                    return;
                }
            } catch (RuntimeException ignored) { /* Try package launch instead. */ }
            try {
                Intent launch = activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    activity.startActivity(launch);
                    Toast.makeText(activity, "Abriendo " + provider.name + ". Busca «" + media.title
                            + "» en la app.", Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (RuntimeException ignored) { /* Try the next installed provider. */ }
        }
        new AlertDialog.Builder(activity)
                .setTitle("Error al abrir")
                .setMessage("No se pudo abrir ninguna de las aplicaciones compatibles para «" + media.title + "».")
                .setPositiveButton("Aceptar", null).show();
    }

    private UnifiedCatalogRoute() {}
}
