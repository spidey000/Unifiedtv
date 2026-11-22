package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.widget.ProgressBar;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.Intent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified streaming shelf, shown on Home if enabled. The original dock stays one
 * Back press away; network operations and poster decoding never block the UI.
 */
public final class UnifiedCatalogActivity extends Activity {
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private LinearLayout shelves;
    private TextView status;
    private LinearLayout tabBar;
    private LinearLayout categoryBar;
    private String tab = "movie";
    private String category = "featured";
    private boolean created;
    private boolean returnFromProvider;
    private int generation;
    private String lastSettings;
    private final Set<UnifiedCatalogApi.Media> availabilityPending =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UnifiedCatalogApi.Media, List<Runnable>> callbacks = new IdentityHashMap<>();

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(2), stroke);
        return bg;
    }

    private Button action(String title, View.OnClickListener click) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextAllCaps(false);
        button.setOnClickListener(click);
        return button;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UnifiedTvHome.catalogCreated();
        tab = UnifiedCatalogPrefs.prefs(this).getString(UnifiedCatalogPrefs.LAST_TAB, "movie");
        if (!"movie".equals(tab) && !"tv".equals(tab) && !"continue".equals(tab)) tab = "movie";
        if (!UnifiedCatalogPrefs.continueEnabled(this) && "continue".equals(tab)) tab = "movie";
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildLayout();
        created = true;
        lastSettings = fingerprint();
        load(false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!created) return;
        String current = fingerprint();
        if (!current.equals(lastSettings) || returnFromProvider) {
            lastSettings = current;
            returnFromProvider = false;
            load(false);
        }
    }

    @Override protected void onStop() {
        // Providers can update Watch Next on exit: do not keep stale progress.
        returnFromProvider = true;
        super.onStop();
    }

    private String fingerprint() {
        return UnifiedCatalogPrefs.prefs(this).getAll().toString();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 12, 18));
        root.setPadding(dp(28), dp(10), dp(18), dp(10));
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("UnifiedTV", 29, Color.WHITE);
        brand.setTypeface(null, Typeface.BOLD);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(62), 1));
        bar.addView(action("Aplicaciones", v -> UnifiedTvHome.leaveForApps(this)));
        bar.addView(action("Buscar", v -> search()));
        bar.addView(action("Ajustes", v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class))));
        bar.addView(action("Actualizar", v -> load(true)));
        root.addView(bar);

        tabBar = new LinearLayout(this);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(tabBar, new LinearLayout.LayoutParams(-1, dp(58)));
        categoryBar = new LinearLayout(this);
        categoryBar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(categoryBar, new LinearLayout.LayoutParams(-1, dp(56)));

        status = label("", 15, 0xFF9BAFBD);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shelves = new LinearLayout(this);
        shelves.setOrientation(LinearLayout.VERTICAL);
        shelves.setPadding(0, dp(8), dp(10), dp(24));
        scroll.addView(shelves);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView credit = label("Datos: TMDB / JustWatch · Seguir viendo: Android TV Watch Next", 12, 0xFF788B9B);
        root.addView(credit, new LinearLayout.LayoutParams(-1, dp(24)));
        renderNavigation();
    }

    private void switchTab(String chosen) {
        if (chosen.equals(tab)) return;
        tab = chosen;
        UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.LAST_TAB, tab).apply();
        lastSettings = fingerprint();
        load(false);
    }

    private void renderNavigation() {
        tabBar.removeAllViews();
        tabBar.addView(navigationButton("Películas", "movie".equals(tab),
                v -> switchTab("movie")));
        tabBar.addView(navigationButton("Series", "tv".equals(tab),
                v -> switchTab("tv")));
        if (UnifiedCatalogPrefs.continueEnabled(this))
            tabBar.addView(navigationButton("Seguir viendo", "continue".equals(tab),
                    v -> switchTab("continue")));

        categoryBar.removeAllViews();
        boolean browsing = !"continue".equals(tab);
        categoryBar.setVisibility(browsing ? View.VISIBLE : View.GONE);
        if (browsing) {
            categoryButton("Destacados", "featured");
            categoryButton("Más vistos", "watched");
            categoryButton("Novedades", "new");
            categoryButton("Populares", "popular");
            categoryButton("Mejor valorados (histórico)", "rated");
        }
    }

    private Button navigationButton(String title, boolean selected, View.OnClickListener listener) {
        Button button = action(title, listener);
        button.setTextColor(selected ? Color.BLACK : Color.WHITE);
        button.setBackground(shape(selected ? 0xFFE8F1FA : 0xFF252D3A,
                selected ? 0xFFE8F1FA : 0xFF3A4859));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        params.setMargins(0, 0, dp(12), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void categoryButton(String name, String id) {
        Button button = navigationButton(name, id.equals(category), v -> {
            if (id.equals(category)) return;
            category = id;
            load(false);
        });
        categoryBar.addView(button);
    }

    private void load(boolean refresh) {
        final int version = ++generation;
        shelves.removeAllViews();
        synchronized (availabilityPending) {
            availabilityPending.clear();
            callbacks.clear();
        }
        if ("continue".equals(tab) && !UnifiedCatalogPrefs.continueEnabled(this)) tab = "movie";
        renderNavigation();
        if ("continue".equals(tab)) {
            status.setText("Tu lista desde las aplicaciones que comparten Watch Next");
            addContinueShelf(version, false);
            return;
        }

        if (UnifiedCatalogPrefs.continueEnabled(this) && UnifiedCatalogPrefs.continueOnHome(this))
            addContinueShelf(version, true);

        if (UnifiedCatalogPrefs.apiKey(this).isEmpty()) {
            status.setText("Configura TMDB para ver películas y series");
            TextView message = label("Configura tu clave personal de TMDB en Ajustes. Seguir viendo funciona por separado con Android TV.", 21, Color.WHITE);
            message.setPadding(dp(15), dp(18), dp(15), dp(15));
            shelves.addView(message);
            shelves.addView(action("Configurar TMDB", v ->
                    startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class))));
            return;
        }

        String[] names = {"Destacados", "Más vistos", "Novedades", "Populares", "Mejor valorados (histórico)"};
        String[] keys = {"featured", "watched", "new", "popular", "rated"};
        String heading = "featured";
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(category)) heading = names[i];
        String typeName = "tv".equals(tab) ? "Series" : "Películas";
        String subtitle = "watched".equals(category)
                ? " · Tendencia semanal como aproximación; no son visualizaciones reales"
                : "";
        status.setText(typeName + " · " + heading + subtitle + " · " + UnifiedCatalogPrefs.region(this));
        addShelf(version, typeName + " · " + heading, tab, category, "", refresh);
    }

    private void addShelf(int version, String heading, String type, String category,
                          String query, boolean refresh) {
        TextView headingView = label(heading, 24, Color.WHITE);
        headingView.setTypeface(null, Typeface.BOLD);
        headingView.setPadding(dp(2), dp(20), 0, dp(12));
        shelves.addView(headingView);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout contents = new LinearLayout(this);
        contents.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(contents);
        shelves.addView(scroll);

        contents.addView(label("Cargando…", 17, 0xFF99A4B1));
        worker.execute(() -> {
            try {
                List<UnifiedCatalogApi.Media> result =
                        UnifiedCatalogApi.list(this, type, category, query, refresh);
                runOnUiThread(() -> {
                    if (isFinishing() || version != generation) return;
                    contents.removeAllViews();
                    if (result.isEmpty()) {
                        contents.addView(label("Sin títulos disponibles", 18, 0xFF99A4B1));
                        return;
                    }
                    int index = 0;
                    for (UnifiedCatalogApi.Media media : result)
                        addTile(contents, media, index++ < 12);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || version != generation) return;
                    contents.removeAllViews();
                    contents.addView(label("Error: " + e.getMessage(), 17, 0xFFFFAE87));
                });
            }
        });
    }

    private void addContinueShelf(int version, boolean compact) {
        if (!UnifiedCatalogPrefs.continueEnabled(this)) return;
        TextView heading = label("Seguir viendo · Todas tus plataformas", 24, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(2), dp(22), 0, dp(10));
        shelves.addView(heading);

        if (!UnifiedWatchNext.hasPermission(this)) {
            TextView info = label("Para mostrar el progreso, permite leer las entradas Watch Next que las apps comparten con Android TV.", 16, 0xFFB8C8D8);
            info.setPadding(dp(4), dp(6), 0, dp(6));
            shelves.addView(info);
            shelves.addView(action("Permitir acceso a Seguir viendo", v -> {
                if (Build.VERSION.SDK_INT >= 26)
                    requestPermissions(new String[]{UnifiedWatchNext.PERMISSION},
                            UnifiedWatchNext.PERMISSION_REQUEST);
            }));
            return;
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row);
        shelves.addView(scroll);
        row.addView(label("Leyendo Watch Next…", 16, 0xFF9BAFBD));

        worker.execute(() -> {
            List<UnifiedWatchNext.Entry> list = UnifiedWatchNext.load(this);
            runOnUiThread(() -> {
                if (isFinishing() || version != generation) return;
                row.removeAllViews();
                if (list.isEmpty()) {
                    LinearLayout empty = new LinearLayout(this);
                    empty.setOrientation(LinearLayout.VERTICAL);
                    empty.addView(label("No hay progreso compartido por tus apps. Esto no significa que no hayas visto nada.", 18, 0xFFB3C2D2));
                    empty.addView(action("Revisar fuentes en Ajustes", v ->
                            startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class))));
                    row.addView(empty);
                    return;
                }
                int count = 0;
                for (UnifiedWatchNext.Entry item : list) {
                    addContinueTile(row, item);
                    if (++count >= (compact ? 10 : 24)) break;
                }
            });
        });
    }

    private void addContinueTile(LinearLayout row, UnifiedWatchNext.Entry item) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(shape(0xFF181C24, 0xFF181C24));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(item.title + " · " + item.source
                + (item.progress() >= 0 ? " · " + item.progress() + "%" : ""));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(184), dp(268));
        params.setMargins(0, 0, dp(12), dp(8));
        row.addView(tile, params);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(0xFF293342);
        tile.addView(image, new LinearLayout.LayoutParams(-1, dp(172)));

        TextView title = label(item.title, 15, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title);

        String percentage = item.progress() >= 0 ? " · " + item.progress() + "%" : "";
        TextView source = label(item.source + percentage, 13, 0xFF9DE4B8);
        source.setSingleLine(true);
        source.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(source);

        if (item.progress() >= 0) {
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(item.progress());
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(5));
            progressParams.topMargin = dp(6);
            tile.addView(progress, progressParams);
        }

        tile.setOnFocusChangeListener((view, focused) -> {
            tile.setBackground(shape(focused ? 0xFF263446 : 0xFF181C24,
                    focused ? Color.WHITE : 0xFF181C24));
            tile.setScaleX(focused ? 1.04f : 1f);
            tile.setScaleY(focused ? 1.04f : 1f);
        });
        tile.setOnClickListener(v -> UnifiedWatchNext.open(this, item));
        worker.execute(() -> {
            Bitmap bitmap = UnifiedWatchNext.artwork(this, item.poster);
            if (bitmap != null)
                runOnUiThread(() -> { if (!isFinishing()) image.setImageBitmap(bitmap); });
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == UnifiedWatchNext.PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                load(false);
            else status.setText("Sin permiso para leer Watch Next: puedes activarlo en los ajustes de Android TV");
        }
    }

    private void addTile(LinearLayout row, UnifiedCatalogApi.Media media, boolean prefetch) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(shape(0xFF181C24, 0xFF181C24));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setContentDescription(media.title + ", " + (media.type.equals("tv") ? "serie" : "película"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(154), dp(270));
        params.setMargins(0, 0, dp(13), dp(8));
        row.addView(tile, params);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0xFF293342);
        tile.addView(poster, new LinearLayout.LayoutParams(-1, dp(190)));

        TextView title = label(media.title, 15, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(6), 0, 0);
        tile.addView(title);

        TextView badge = label("Comprobando…", 12, 0xFF95A7B7);
        badge.setSingleLine(true);
        badge.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(badge);
        tile.setOnFocusChangeListener((view, focused) -> {
            tile.setBackground(shape(focused ? 0xFF263446 : 0xFF181C24,
                    focused ? 0xFFF7F9FB : 0xFF181C24));
            tile.setScaleX(focused ? 1.035f : 1f);
            tile.setScaleY(focused ? 1.035f : 1f);
            if (focused && !media.availabilityReady)
                fetchAvailability(media, badge, false, null);
        });
        tile.setOnClickListener(v -> {
            status.setText("Buscando «" + media.title + "» en tus plataformas…");
            if (media.availabilityReady) UnifiedCatalogRoute.open(this, media);
            else fetchAvailability(media, badge, true, () -> UnifiedCatalogRoute.open(this, media));
        });

        worker.execute(() -> {
            Bitmap bitmap = UnifiedCatalogApi.poster(this, media.poster);
            if (bitmap != null) runOnUiThread(() -> {
                if (!isFinishing()) poster.setImageBitmap(bitmap);
            });
        });
        if (prefetch) fetchAvailability(media, badge, false, null);
    }

    private void fetchAvailability(UnifiedCatalogApi.Media media, TextView badge,
                                   boolean openAfter, Runnable completed) {
        if (media.availabilityReady) {
            updateBadge(media, badge);
            if (completed != null) completed.run();
            return;
        }
        final int version = generation;
        synchronized (availabilityPending) {
            if (completed != null)
                callbacks.computeIfAbsent(media, ignored -> new ArrayList<>()).add(completed);
            if (!availabilityPending.add(media)) return;
        }
        worker.execute(() -> {
            String failure = null;
            try {
                UnifiedCatalogApi.availability(this, media);
                media.availabilityError = "";
            } catch (Exception error) {
                failure = error.getMessage() == null ? "Sin conexión" : error.getMessage();
                media.availabilityError = failure;
            }
            final String error = failure;
            runOnUiThread(() -> {
                List<Runnable> toRun;
                synchronized (availabilityPending) {
                    availabilityPending.remove(media);
                    toRun = callbacks.remove(media);
                }
                if (isFinishing() || generation != version) return;
                if (error != null) {
                    badge.setText("Sin datos");
                    badge.setTextColor(0xFFFFB27A);
                    if (toRun != null && !toRun.isEmpty()) showError(error);
                } else {
                    updateBadge(media, badge);
                    if (toRun != null) for (Runnable callback : toRun) callback.run();
                }
            });
        });
    }

    private void updateBadge(UnifiedCatalogApi.Media media, TextView badge) {
        List<UnifiedCatalogPrefs.Provider> providers = UnifiedCatalogApi.eligible(this, media);
        if (providers.isEmpty()) {
            badge.setText("No en tus apps");
            badge.setTextColor(0xFFFFB27A);
        } else {
            List<String> names = new ArrayList<>();
            for (UnifiedCatalogPrefs.Provider provider : providers) names.add(provider.name);
            badge.setText(TextUtils.join(" · ", names));
            badge.setTextColor(0xFF9DE4B8);
        }
    }

    private void showError(String why) {
        new AlertDialog.Builder(this).setTitle("No se puede consultar la disponibilidad")
                .setMessage(why).setPositiveButton("Aceptar", null).show();
    }

    private void search() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Película o serie");
        new AlertDialog.Builder(this).setTitle("Buscar en todas las plataformas")
                .setView(field)
                .setPositiveButton("Buscar", (dialog, which) -> {
                    String query = field.getText().toString().trim();
                    if (query.isEmpty()) return;
                    int version = ++generation;
                    shelves.removeAllViews();
                    status.setText("Resultados para «" + query + "»");
                    if (UnifiedCatalogPrefs.apiKey(this).isEmpty()) {
                        shelves.addView(label("Configura la clave TMDB para buscar títulos.", 18, Color.WHITE));
                        return;
                    }
                    addShelf(version, "Películas · " + query, "movie", "search", query, false);
                    addShelf(version, "Series · " + query, "tv", "search", query, false);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    @Override public void onBackPressed() {
        UnifiedTvHome.leaveForApps(this);
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        super.onDestroy();
    }
}
