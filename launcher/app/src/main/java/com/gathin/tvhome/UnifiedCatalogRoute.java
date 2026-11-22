package com.gathin.tvhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the user choose where to open a TMDB title.
 * Subscription services require a matching TMDB/JustWatch availability result;
 * catalog/media apps are separate local launch targets, never availability claims.
 */
final class UnifiedCatalogRoute {
    private static final class Choice {
        final String label;
        final String packageName;
        final String kind;

        Choice(String label, String packageName, String kind) {
            this.label = label;
            this.packageName = packageName;
            this.kind = kind;
        }
    }

    static List<String> localAppNames(Activity activity) {
        List<String> names = new ArrayList<>();
        for (UnifiedPlaybackApps.App app : UnifiedPlaybackApps.enabled(activity)) names.add(app.name);
        return names;
    }

    static void open(Activity activity, UnifiedCatalogApi.Media media) {
        List<Choice> choices = new ArrayList<>();
        Set<String> packages = new HashSet<>();
        if (media.availabilityReady) {
            for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogApi.eligible(activity, media)) {
                String pkg = provider.installedPackage(activity);
                if (pkg != null && packages.add(pkg))
                    choices.add(new Choice(provider.name + "  ·  según TMDB", pkg, "provider"));
            }
        }
        for (UnifiedPlaybackApps.App app : UnifiedPlaybackApps.enabled(activity)) {
            if (!packages.add(app.packageName)) continue;
            String suffix = "nuvio".equals(app.key) ? "  ·  abrir ficha"
                    : "stremio".equals(app.key) ? "  ·  abrir ficha / buscar"
                    : "  ·  abrir app";
            choices.add(new Choice(app.name + suffix, app.packageName, app.key));
        }
        if (choices.isEmpty()) {
            String explanation = media.availabilityReady
                    ? "No hay servicios con disponibilidad TMDB ni reproductores habilitados e instalados."
                    : "No se ha podido consultar la disponibilidad TMDB y no hay reproductores habilitados e instalados.";
            StreamUi.dialog(activity)
                    .setTitle("No hay aplicaciones para abrir el título")
                    .setMessage(explanation + " Puedes activarlos en Ajustes → Plataformas.")
                    .setPositiveButton("Aceptar", null)
                    .setNeutralButton(media.watchLink.isEmpty() ? null : "Ver opciones online", (d, w) -> {
                        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(media.watchLink))); }
                        catch (RuntimeException e) { toast(activity, "No se puede abrir el enlace"); }
                    })
                    .show();
            return;
        }

        CharSequence[] labels = new CharSequence[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).label;
        StreamUi.dialog(activity)
                .setTitle("Abrir «" + media.title + "» con")
                .setMessage("Las apps de catálogo no garantizan que el contenido esté disponible.")
                .setItems(labels, (dialog, which) -> launch(activity, media, choices.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void launch(Activity activity, UnifiedCatalogApi.Media media, Choice choice) {
        if ("nuvio".equals(choice.kind)) {
            String uri = "nuvio://tmdb/" + ("tv".equals(media.type) ? "tv" : "movie") + "/" + media.id;
            if (openUri(activity, choice.packageName, uri)) return;
        } else if ("stremio".equals(choice.kind)) {
            // TMDB media IDs are not Stremio/Cinemeta IDs. Resolve IMDb off the UI thread.
            new Thread(() -> {
                String imdb = "";
                try { imdb = UnifiedCatalogApi.imdbId(activity.getApplicationContext(), media); }
                catch (Exception ignored) { /* Search by title is the safe fallback. */ }
                final String resolved = imdb;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (!resolved.isEmpty()) {
                        String type = "tv".equals(media.type) ? "series" : "movie";
                        String path = "stremio:///detail/" + type + "/" + resolved + "/"
                                + ("movie".equals(type) ? resolved : "");
                        if (openUri(activity, choice.packageName, path)) return;
                    }
                    if (openUri(activity, choice.packageName,
                            "stremio:///search?search=" + Uri.encode(media.title))) return;
                    openSearchOrApp(activity, choice.packageName, choice.label, media.title);
                });
            }, "unifiedtv-stremio-lookup").start();
            return;
        }
        openSearchOrApp(activity, choice.packageName, choice.label, media.title);
    }

    private static boolean openUri(Activity activity, String pkg, String uri) {
        try {
            // Explicitly constrain external deep links to the app chosen by the user.
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    private static void openSearchOrApp(Activity activity, String pkg, String appName, String title) {
        Intent search = new Intent(Intent.ACTION_SEARCH)
                .setPackage(pkg).putExtra(SearchManager.QUERY, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (search.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(search);
                return;
            }
        } catch (RuntimeException ignored) { /* Fall back to opening the selected app. */ }
        try {
            Intent launch = activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                activity.startActivity(launch);
                toast(activity, "Busca «" + title + "» en la aplicación.");
                return;
            }
        } catch (RuntimeException ignored) { /* Show the error below. */ }
        toast(activity, "No se pudo abrir " + appName);
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }

    private UnifiedCatalogRoute() {}
}
