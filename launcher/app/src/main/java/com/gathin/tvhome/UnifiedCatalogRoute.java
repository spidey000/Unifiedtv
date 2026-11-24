package com.gathin.tvhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
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
    static final class Target {
        final UnifiedCatalogPrefs.Provider provider;
        final String packageName, url;
        final boolean fromAndroidTv;
        Target(UnifiedCatalogPrefs.Provider provider, String pkg, String url, boolean fromAndroidTv) {
            this.provider = provider;
            this.packageName = pkg;
            this.url = url;
            this.fromAndroidTv = fromAndroidTv;
        }
    }

    /**
     * TMDB is authoritative for regional availability; Wikidata resolves the
     * service-specific title id. An exact on-device Preview/WatchNext intent
     * is preferred inside each platform. Both are resolved in user priority
     * order, never using the TMDB identifier as a provider's own identifier.
     */
    static Target resolve(Activity activity, UnifiedCatalogApi.Media media) {
        synchronized (media) {
            if (media.autoTargetReady) return media.autoTarget;
            if (!media.availabilityReady) return null;
            List<UnifiedCatalogPrefs.Provider> eligible = UnifiedCatalogApi.eligible(activity, media);
            if (eligible.isEmpty()) {
                String pkg = UnifiedCatalogPrefs.NUVIO.installedPackage(activity);
                if (pkg != null && UnifiedCatalogPrefs.nuvioSelected(activity))
                    media.autoTarget = new Target(UnifiedCatalogPrefs.NUVIO, pkg,
                            nuvioUri(media.type, media.id), false);
            } else {
                // Respect provider priority even if its verified content link is missing.
                media.autoTarget = resolveProvider(activity, media, eligible.get(0));
            }
            media.autoTargetReady = media.autoTarget != null;
            return media.autoTarget;
        }
    }

    static UnifiedCatalogPrefs.Provider badgeProvider(Activity activity, UnifiedCatalogApi.Media media) {
        if (!media.availabilityReady) return null;
        List<UnifiedCatalogPrefs.Provider> eligible = UnifiedCatalogApi.eligible(activity, media);
        if (!eligible.isEmpty()) return eligible.get(0);
        return UnifiedCatalogPrefs.nuvioSelected(activity)
                && UnifiedCatalogPrefs.NUVIO.installedPackage(activity) != null
                ? UnifiedCatalogPrefs.NUVIO : null;
    }

    private static Target resolveProvider(Activity activity, UnifiedCatalogApi.Media media,
                                          UnifiedCatalogPrefs.Provider provider) {
        String pkg = provider.installedPackage(activity);
        if (pkg == null) return null;
        String local = UnifiedTvProgramLinks.find(activity, media, pkg);
        if (!local.isEmpty()) return new Target(provider, pkg, local, true);
        String url = UnifiedWikidataLinks.resolve(activity, media).get(provider.key);
        return UnifiedWikidataLinks.safeUrl(provider.key, url)
                ? new Target(provider, pkg, url, false) : null;
    }

    /** A dispatch intent is always scoped to its publisher's installed package. */
    static boolean launchTarget(Activity activity, Target target) {
        if (target == null) return false;
        if ("nuvio".equals(target.provider.key))
            return openUri(activity, target.packageName, target.url);
        try {
            Intent intent;
            if (target.fromAndroidTv) {
                intent = UnifiedTvProgramLinks.parse(target.url, target.packageName);
                if (intent == null) return false;
            } else {
                if (!UnifiedWikidataLinks.safeUrl(target.provider.key, target.url)) return false;
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target.url))
                        .setPackage(target.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            if ("netflix".equals(target.provider.key)) {
                // TV Netflix expects the search source extra. Use its actual launcher
                // component for web title URLs (some builds expose no matching VIEW filter).
                if (!target.fromAndroidTv && "com.netflix.ninja".equals(target.packageName)) {
                    Intent launcher = activity.getPackageManager()
                            .getLeanbackLaunchIntentForPackage(target.packageName);
                    if (launcher != null && launcher.getComponent() != null)
                        intent.setComponent(launcher.getComponent());
                }
                intent.putExtra("source", "30");
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

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
        String nuvio = UnifiedCatalogPrefs.NUVIO.installedPackage(activity);
        if (nuvio != null && UnifiedCatalogPrefs.nuvioSelected(activity) && packages.add(nuvio))
            choices.add(new Choice("Nuvio  ·  proveedor P2P de último recurso", nuvio, "nuvio"));
        for (UnifiedPlaybackApps.App app : UnifiedPlaybackApps.enabled(activity)) {
            if (!packages.add(app.packageName)) continue;
            String suffix = "stremio".equals(app.key) ? "  ·  abrir ficha / buscar"
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
                .setItems(labels, (dialog, which) -> launch(activity, media, choices.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void launch(Activity activity, UnifiedCatalogApi.Media media, Choice choice) {
        if ("provider".equals(choice.kind)) {
            // Each selection resolves its own link, including non-default providers.
            new Thread(() -> {
                Target target = null;
                for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogApi.eligible(activity, media)) {
                    if (choice.packageName.equals(provider.installedPackage(activity))) {
                        target = resolveProvider(activity, media, provider);
                        break;
                    }
                }
                final Target resolved = target;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (launchTarget(activity, resolved)) return;
                    StreamUi.dialog(activity).setTitle("Enlace directo no disponible")
                            .setMessage("La oferta está disponible según TMDB, pero no se pudo abrir"
                                    + " un enlace verificable a «" + media.title + "».")
                            .setPositiveButton("Buscar en la app", (d, w) -> openSearchOrApp(activity,
                                    choice.packageName, choice.label, media.title))
                            .setNegativeButton("Cancelar", null).show();
                });
            }, "unifiedtv-provider-link").start();
            return;
        }
        if ("nuvio".equals(choice.kind)) {
            if (openUri(activity, choice.packageName, nuvioUri(media.type, media.id))) return;
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
                        if (openUri(activity, choice.packageName,
                                stremioUri(media.type, resolved))) return;
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

    static String nuvioUri(String mediaType, int id) {
        return "nuvio://tmdb/" + ("tv".equals(mediaType) ? "tv" : "movie") + "/" + id;
    }

    static String stremioUri(String mediaType, String imdb) {
        String type = "tv".equals(mediaType) ? "series" : "movie";
        return "stremio:///detail/" + type + "/" + imdb + "/"
                + ("movie".equals(type) ? imdb : "");
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
        // Some Android TV players implement MEDIA_SEARCH rather than standard SEARCH.
        // Keep both intents scoped to the app explicitly selected by the viewer.
        for (String action : new String[]{MediaStore.INTENT_ACTION_MEDIA_SEARCH, Intent.ACTION_SEARCH}) {
            Intent search = new Intent(action).setPackage(pkg)
                    .putExtra(SearchManager.QUERY, title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (search.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(search);
                    return;
                }
            } catch (RuntimeException ignored) { /* Try the next search action. */ }
        }
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
