package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opt-out automatic update discovery; downloads and installs always require user action.
 * Uses only the repository's published, signed GitHub Release assets, never a remote
 * installer service or an unofficial APK mirror. Network and package verification run
 * off the main thread. Android's own installer asks for final confirmation.
 */
final class UnifiedAppUpdater {
    private static final String API =
            "https://api.github.com/repos/spidey000/Unifiedtv/releases/latest";
    private static final String ASSET_PREFIX =
            "https://github.com/spidey000/Unifiedtv/releases/download/";
    private static final String APK_TYPE = "application/vnd.android.package-archive";
    private static final long CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000;
    private static final int APK_LIMIT = 12 * 1024 * 1024;
    private static final Pattern VERSION = Pattern.compile("v?(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CHECKING = new AtomicBoolean();
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean();

    // Set only after the user taps Install and Android requires permission.
    private static File pendingInstall;
    private static boolean awaitingInstallPermission;

    static final class Release {
        final String tag, apkName, apkUrl, checksumUrl;
        final long apkSize;

        Release(String tag, String apkName, String apkUrl, String checksumUrl, long size) {
            this.tag = tag;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.checksumUrl = checksumUrl;
            this.apkSize = size;
        }
    }

    static void checkAutomatically(Activity activity) {
        if (!UnifiedCatalogPrefs.autoCheckUpdates(activity)) return;
        long now = System.currentTimeMillis();
        long last = UnifiedCatalogPrefs.prefs(activity)
                .getLong(UnifiedCatalogPrefs.UPDATE_LAST_CHECK, 0L);
        if (last > 0 && now >= last && now - last < CHECK_INTERVAL_MS) return;
        if (!CHECKING.compareAndSet(false, true)) return;
        // Throttle failed checks as well, so an offline TV does not retry at every Home.
        UnifiedCatalogPrefs.prefs(activity).edit()
                .putLong(UnifiedCatalogPrefs.UPDATE_LAST_CHECK, now).apply();
        check(activity, false);
    }

    static void checkNow(Activity activity) {
        if (!CHECKING.compareAndSet(false, true)) {
            Toast.makeText(activity, "Ya se está comprobando la versión", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, "Buscando una actualización de UnifiedTV…", Toast.LENGTH_SHORT).show();
        check(activity, true);
    }

    private static void check(Activity activity, boolean manual) {
        IO.execute(() -> {
            Release release = null;
            String error = null;
            try {
                release = latestRelease();
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                CHECKING.set(false);
            }
            final Release found = release;
            final String failure = error;
            activity.runOnUiThread(() -> {
                if (!usable(activity)) return;
                if (failure != null) {
                    if (manual) message(activity, "No se pudo comprobar la versión", failure);
                    return;
                }
                String current = installedVersion(activity);
                if (found == null || !newer(found.tag, current)) {
                    if (manual) message(activity, "UnifiedTV está actualizado",
                            "Versión instalada: " + current);
                    return;
                }
                if (!manual) {
                    if (!UnifiedCatalogPrefs.autoCheckUpdates(activity)) return;
                    String shown = UnifiedCatalogPrefs.prefs(activity)
                            .getString(UnifiedCatalogPrefs.UPDATE_LAST_SHOWN, "");
                    if (found.tag.equals(shown)) return;
                    UnifiedCatalogPrefs.prefs(activity).edit()
                            .putString(UnifiedCatalogPrefs.UPDATE_LAST_SHOWN, found.tag).apply();
                }
                StreamUi.dialog(activity)
                        .setTitle("Nueva versión de UnifiedTV")
                        .setMessage("Instalada: " + current + "\nDisponible: " + found.tag.substring(1)
                                + "\n\n¿Quieres descargar la APK oficial y actualizar?")
                        .setPositiveButton("Descargar", (dialog, which) -> download(activity, found))
                        .setNegativeButton("Más tarde", null)
                        .show();
            });
        });
    }

    private static Release latestRelease() throws Exception {
        byte[] data = readSmall(API, 512 * 1024);
        JSONObject release = new JSONObject(new String(data, StandardCharsets.UTF_8));
        String tag = release.optString("tag_name", "");
        if (!VERSION.matcher(tag).matches() || !tag.startsWith("v")
                || release.optBoolean("draft") || release.optBoolean("prerelease"))
            throw new IllegalStateException("La última publicación no es una versión estable válida");
        String apkName = "UnifiedTV-" + tag + ".apk";
        String apkUrl = "", checksumUrl = "";
        long apkSize = -1;
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) throw new IllegalStateException("No hay APK firmada en la release");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            String url = a.optString("browser_download_url", "");
            String expectedUrl = ASSET_PREFIX + tag + "/" + name;
            if (!url.equals(expectedUrl)) continue;
            if (apkName.equals(name)) {
                apkUrl = url;
                apkSize = a.optLong("size", -1);
            } else if ("SHA256SUMS".equals(name)) checksumUrl = url;
        }
        if (apkUrl.isEmpty() || checksumUrl.isEmpty() || apkSize < 100_000
                || apkSize > APK_LIMIT)
            throw new IllegalStateException("La release no contiene una APK verificable");
        return new Release(tag, apkName, apkUrl, checksumUrl, apkSize);
    }

    /** Numeric SemVer comparison; a dev build is considered older than a stable release. */
    static boolean newer(String latest, String installed) {
        int[] available = parts(latest);
        int[] current = parts(installed);
        if (available == null) return false;
        if (current == null) return true;
        for (int i = 0; i < 3; i++) {
            if (available[i] != current[i]) return available[i] > current[i];
        }
        return false;
    }

    private static int[] parts(String value) {
        Matcher match = VERSION.matcher(value == null ? "" : value);
        if (!match.matches()) return null;
        try {
            return new int[]{Integer.parseInt(match.group(1)),
                    Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3))};
        } catch (NumberFormatException e) { return null; }
    }

    private static String installedVersion(Activity activity) {
        try {
            PackageInfo current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return current.versionName == null ? "desconocida" : current.versionName;
        } catch (Exception e) {
            return "desconocida";
        }
    }

    private static void download(Activity activity, Release release) {
        if (!DOWNLOADING.compareAndSet(false, true)) {
            Toast.makeText(activity, "Ya hay una descarga en curso", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(22 * activity.getResources().getDisplayMetrics().density);
        view.setPadding(pad, pad, pad, pad);
        TextView label = new TextView(activity);
        label.setText("Descargando APK verificada…");
        label.setTextSize(16);
        view.addView(label);
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(StreamUi.readableAccent(activity)));
        view.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        AtomicBoolean cancelled = new AtomicBoolean();
        AlertDialog progress = StreamUi.dialog(activity)
                .setTitle("UnifiedTV " + release.tag.substring(1))
                .setView(view)
                .setNegativeButton("Cancelar", (d, which) -> cancelled.set(true))
                .create();
        progress.show();

        IO.execute(() -> {
            File apk = null;
            String error = null;
            try {
                String checksum = expectedChecksum(release);
                apk = downloadApk(activity, release, checksum, cancelled, percent ->
                        activity.runOnUiThread(() -> {
                            if (progress.isShowing()) {
                                bar.setProgress(percent);
                                label.setText("Descargando APK verificada… " + percent + "%");
                            }
                        }));
                if (cancelled.get()) throw new IllegalStateException("Descarga cancelada");
                verifyPackage(activity, apk);
            } catch (Exception e) {
                error = e.getMessage();
                if (apk != null && apk.isFile()) apk.delete();
            } finally {
                DOWNLOADING.set(false);
            }
            final File complete = apk;
            final String failure = error;
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (!usable(activity) || cancelled.get()) return;
                if (failure != null) {
                    message(activity, "No se ha instalado la actualización", failure);
                    return;
                }
                StreamUi.dialog(activity)
                        .setTitle("Actualización descargada")
                        .setMessage("UnifiedTV " + release.tag.substring(1)
                                + "\n\nLa APK coincide con SHA-256 y con la firma de esta aplicación."
                                + "\nAndroid te pedirá confirmar la instalación.")
                        .setPositiveButton("Instalar", (d, w) -> requestInstallation(activity, complete))
                        .setNegativeButton("Después", null)
                        .show();
            });
        });
    }

    private interface Progress { void changed(int percent); }

    private static File downloadApk(Activity activity, Release release,
                                    String checksum, AtomicBoolean cancel, Progress progress)
            throws Exception {
        File directory = new File(activity.getCacheDir(), "unifiedtv-updates");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IllegalStateException("No se puede guardar la actualización");
        File target = new File(directory, release.apkName);
        File temp = new File(directory, release.apkName + ".part");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try {
            HttpURLConnection connection = open(release.apkUrl);
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buf = new byte[8192];
                int count, previousPercent = -1;
                while ((count = in.read(buf)) != -1) {
                    if (cancel.get()) throw new IllegalStateException("Descarga cancelada");
                    total += count;
                    if (total > APK_LIMIT) throw new IllegalStateException("APK demasiado grande");
                    out.write(buf, 0, count);
                    digest.update(buf, 0, count);
                    int percent = (int) Math.min(100, total * 100 / release.apkSize);
                    if (percent != previousPercent) {
                        previousPercent = percent;
                        progress.changed(percent);
                    }
                }
            } finally {
                connection.disconnect();
            }
            if (total != release.apkSize)
                throw new IllegalStateException("La descarga está incompleta");
            String actual = hex(digest.digest());
            if (!actual.equalsIgnoreCase(checksum))
                throw new IllegalStateException("El SHA-256 de la APK no coincide");
            if (target.exists() && !target.delete())
                throw new IllegalStateException("No se puede reemplazar la APK anterior");
            if (!temp.renameTo(target))
                throw new IllegalStateException("No se puede finalizar la descarga");
            return target;
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private static String expectedChecksum(Release release) throws Exception {
        String data = new String(readSmall(release.checksumUrl, 4096), StandardCharsets.UTF_8);
        Pattern line = Pattern.compile("(?m)^([a-fA-F0-9]{64})\\s+\\*?"
                + Pattern.quote(release.apkName) + "\\s*$");
        Matcher match = line.matcher(data);
        if (!match.find()) throw new IllegalStateException("Falta la suma SHA-256 de la APK");
        return match.group(1);
    }

    private static byte[] readSmall(String url, int limit) throws Exception {
        HttpURLConnection conn = open(url);
        try (InputStream stream = conn.getInputStream();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) != -1) {
                if (bytes.size() + n > limit)
                    throw new IllegalStateException("Respuesta demasiado grande");
                bytes.write(buf, 0, n);
            }
            return bytes.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String raw) throws Exception {
        URL url = new URL(raw);
        if (!"https".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalStateException("La actualización debe usar HTTPS");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "UnifiedTV-AndroidTV-Updater");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            throw new IllegalStateException("GitHub ha devuelto HTTP " + code);
        }
        if (!"https".equalsIgnoreCase(connection.getURL().getProtocol())) {
            connection.disconnect();
            throw new IllegalStateException("Redirección sin HTTPS bloqueada");
        }
        return connection;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    /** The platform will also validate the signature before installing; check it first for useful errors. */
    private static void verifyPackage(Activity activity, File apk) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = pm.getPackageInfo(activity.getPackageName(), flags);
        if (archive == null || installed == null
                || !activity.getPackageName().equals(archive.packageName))
            throw new IllegalStateException("La APK no pertenece a UnifiedTV");
        if (versionCode(archive) <= versionCode(installed))
            throw new IllegalStateException("La APK descargada no es una versión más reciente");
        Signature[] archiveSignatures = signatures(archive);
        Signature[] installedSignatures = signatures(installed);
        if (archiveSignatures == null || installedSignatures == null
                || archiveSignatures.length != 1 || installedSignatures.length != 1
                || !Arrays.equals(archiveSignatures[0].toByteArray(),
                                  installedSignatures[0].toByteArray()))
            throw new IllegalStateException("La firma de la APK no coincide con la instalada");
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28) {
            return info.signingInfo == null ? null
                    : info.signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    static boolean canInstall(Activity activity) {
        return Build.VERSION.SDK_INT >= 26
                && activity.getPackageManager().canRequestPackageInstalls();
    }

    static void openInstallPermissionSettings(Activity activity) {
        if (canInstall(activity)) {
            Toast.makeText(activity, "UnifiedTV ya puede solicitar instalaciones", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (Exception ignored) {
                message(activity, "Permiso no disponible",
                        "Abre los ajustes de seguridad de Android TV y permite instalar apps desde UnifiedTV.");
            }
        }
    }

    private static void requestInstallation(Activity activity, File apk) {
        if (!apk.isFile()) {
            message(activity, "APK no disponible", "Vuelve a descargar la versión.");
            return;
        }
        if (!canInstall(activity)) {
            StreamUi.dialog(activity)
                    .setTitle("Permitir instalaciones desde UnifiedTV")
                    .setMessage("Android TV requiere que habilites «Instalar aplicaciones desconocidas» "
                            + "para UnifiedTV. Es un permiso por aplicación y puedes revocarlo cuando quieras. "
                            + "La instalación seguirá necesitando tu confirmación.")
                    .setPositiveButton("Abrir ajustes", (d, w) -> {
                        pendingInstall = apk;
                        awaitingInstallPermission = true;
                        openInstallPermissionSettings(activity);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        launchInstaller(activity, apk);
    }

    /** Called when returning from the unknown-sources permission screen. */
    static void onResume(Activity activity) {
        if (!awaitingInstallPermission) return;
        if (!canInstall(activity)) return;
        File apk = pendingInstall;
        awaitingInstallPermission = false;
        pendingInstall = null;
        if (apk != null && apk.isFile()) launchInstaller(activity, apk);
    }

    private static void launchInstaller(Activity activity, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".unifiedtv-updates", apk);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setDataAndType(uri, APK_TYPE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), "UnifiedTV APK", uri));
            try {
                activity.startActivity(intent);
            } catch (Exception unavailable) {
                intent.setAction(Intent.ACTION_VIEW);
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            message(activity, "Instalador no disponible",
                    "Android TV no ha podido abrir la APK. " + e.getClass().getSimpleName());
        }
    }

    private static boolean usable(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void message(Activity activity, String title, String body) {
        if (usable(activity)) StreamUi.dialog(activity)
                .setTitle(title).setMessage(body).setPositiveButton("Aceptar", null).show();
    }

    private UnifiedAppUpdater() {}
}
