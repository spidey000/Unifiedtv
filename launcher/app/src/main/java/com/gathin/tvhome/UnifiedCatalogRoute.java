package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/** Opens a title in the app the viewer selects. */
final class UnifiedCatalogRoute {
    private static final class Target {
        final String name, pkg, key, label;

        Target(String name, String pkg, String key, String label) {
            this.name = name;
            this.pkg = pkg;
            this.key = key;
            this.label = label;
        }
    }

    static void open(Activity activity, UnifiedCatalogApi.Media media, Executor data) {
        List<Target> targets = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        List<UnifiedCatalogPrefs.Provider> available = UnifiedCatalogApi.eligible(activity, media);
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.ordered(activity)) {
            boolean independent = provider.tmdbIds.length == 0;
            if (!available.contains(provider) &&
                    !(independent && UnifiedCatalogPrefs.selected(activity, provider))) continue;
            String pkg = provider.installedPackage(activity);
            if (pkg == null) continue;
            targets.add(new Target(provider.name, pkg, provider.key,
                    provider.name + (independent ? "  ·  Abrir ficha" : "  ·  Disponible según TMDB")));
            for (String variant : provider.packages) listed.add(variant);
        }

        PackageManager manager = activity.getPackageManager();
        Set<String> mediaPackages = new HashSet<>();
        for (Intent intent : new Intent[]{
                new Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH),
                new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://unifiedtv/video"), "video/*")
        }) {
            for (ResolveInfo app : manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                if (app.activityInfo.exported) mediaPackages.add(app.activityInfo.packageName);
            }
        }

        List<Target> others = new ArrayList<>();
        for (ResolveInfo app : installedApps(activity)) {
            String pkg = app.activityInfo.packageName;
            if (!listed.add(pkg)) continue;
            UnifiedCatalogPrefs.Provider known = UnifiedCatalogPrefs.sourceForPackage(pkg);
            if (known != null) {
                if (UnifiedCatalogPrefs.selected(activity, known) && pkg.equals(known.installedPackage(activity))) {
                    others.add(new Target(known.name, pkg, known.key, known.name));
                }
                continue;
            }
            String name = app.loadLabel(manager).toString();
            if (mediaPackages.contains(pkg) || app.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_VIDEO) {
                targets.add(new Target(name, pkg, "", name + "  ·  Buscar o abrir app"));
            } else {
                others.add(new Target(name, pkg, "", name));
            }
        }

        List<String> labels = new ArrayList<>();
        for (Target target : targets) labels.add(target.label);
        if (!others.isEmpty()) labels.add("Otras aplicaciones instaladas");
        AlertDialog.Builder dialog = StreamUi.dialog(activity).setTitle("Ver en · " + media.title)
                .setNegativeButton("Cancelar", null);
        if (!media.watchLink.isEmpty()) {
            dialog.setNeutralButton("Ver opciones online", (d, which) -> {
                if (!start(activity, new Intent(Intent.ACTION_VIEW, Uri.parse(media.watchLink)))) {
                    toast(activity, "No se puede abrir el enlace");
                }
            });
        }
        if (labels.isEmpty()) {
            dialog.setMessage("No hay aplicaciones instaladas para abrir este título.");
        } else {
            dialog.setItems(labels.toArray(new String[0]), (d, which) -> {
                if (which < targets.size()) {
                    launch(activity, media, targets.get(which), data);
                } else {
                    String[] names = new String[others.size()];
                    for (int i = 0; i < names.length; i++) names[i] = others.get(i).label;
                    StreamUi.dialog(activity).setTitle("Otras apps · " + media.title)
                            .setItems(names, (apps, index) -> launch(activity, media, others.get(index), data))
                            .setNegativeButton("Volver", (apps, index) -> open(activity, media, data))
                            .show();
                }
            });
        }
        dialog.show();
    }

    static List<ResolveInfo> installedApps(Context context) {
        PackageManager manager = context.getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>();
        Set<String> packages = new HashSet<>();
        packages.add(context.getPackageName());
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo app : manager.queryIntentActivities(launch, 0)) {
                if (app.activityInfo.exported && packages.add(app.activityInfo.packageName)) apps.add(app);
            }
        }
        apps.sort(new ResolveInfo.DisplayNameComparator(manager));
        return apps;
    }

    private static void launch(Activity activity, UnifiedCatalogApi.Media media, Target target, Executor data) {
        if ("stremio".equals(target.key)) {
            AlertDialog pending = StreamUi.dialog(activity).setTitle("Abriendo Stremio")
                    .setMessage("Buscando la ficha de «" + media.title + "»…")
                    .setNegativeButton("Cancelar", null).show();
            data.execute(() -> {
                String imdbId = "";
                try {
                    imdbId = UnifiedCatalogApi.imdbId(activity, media);
                } catch (Exception ignored) { /* Fall back to the installed app. */ }
                String uri = stremioLink(media.type, imdbId);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed() || !pending.isShowing()) return;
                    pending.dismiss();
                    openTarget(activity, media, target, uri);
                });
            });
        } else {
            String uri = "nuvio".equals(target.key) ? nuvioLink(media.type, media.id) : "";
            openTarget(activity, media, target, uri);
        }
    }

    static String nuvioLink(String type, int tmdbId) {
        return "nuvio://tmdb/" + type + "/" + tmdbId;
    }

    static String stremioLink(String type, String imdbId) {
        if (!imdbId.matches("tt[0-9]+")) return "";
        if ("tv".equals(type)) return "stremio:///detail/series/" + imdbId + "/";
        return "stremio:///detail/movie/" + imdbId + "/" + imdbId;
    }

    private static void openTarget(Activity activity, UnifiedCatalogApi.Media media, Target target, String uri) {
        if (!uri.isEmpty() && start(activity, new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(target.pkg))) return;

        String[] actions = target.key.isEmpty()
                ? new String[]{MediaStore.INTENT_ACTION_MEDIA_SEARCH, Intent.ACTION_SEARCH}
                : new String[]{Intent.ACTION_SEARCH, MediaStore.INTENT_ACTION_MEDIA_SEARCH};
        for (String action : actions) {
            Intent search = new Intent(action).setPackage(target.pkg).putExtra(SearchManager.QUERY, media.title);
            if (start(activity, search)) return;
        }
        PackageManager manager = activity.getPackageManager();
        Intent intent = manager.getLeanbackLaunchIntentForPackage(target.pkg);
        if (intent == null) intent = manager.getLaunchIntentForPackage(target.pkg);
        if (intent != null && start(activity, intent)) {
            toast(activity, "Busca «" + media.title + "» en " + target.name + ".");
        } else {
            toast(activity, "No se pudo abrir " + target.name + ".");
        }
    }

    private static boolean start(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private static void toast(Activity activity, String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private UnifiedCatalogRoute() {}
}
