package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private boolean created;
    private int generation;
    private String lastSettings;
    private final Set<String> availabilityPending = new HashSet<>();

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
        if (!current.equals(lastSettings)) {
            lastSettings = current;
            load(false);
        }
    }

    private String fingerprint() {
        return UnifiedCatalogPrefs.prefs(this).getAll().toString();
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 12, 18));
        root.setPadding(dp(28), dp(12), dp(18), dp(10));
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("UnifiedTV", 29, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(70), 1));
        bar.addView(action("Aplicaciones", v -> UnifiedTvHome.leaveForApps(this)));
        bar.addView(action("Buscar", v -> search()));
        bar.addView(action("Ajustes", v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class))));
        bar.addView(action("Actualizar", v -> load(true)));
        root.addView(bar);

        status = label("", 15, 0xFF9BAFBD);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shelves = new LinearLayout(this);
        shelves.setOrientation(LinearLayout.VERTICAL);
        shelves.setPadding(0, dp(10), dp(10), dp(20));
        scroll.addView(shelves);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        title.requestFocus();
    }

    private void load(boolean refresh) {
        final int version = ++generation;
        shelves.removeAllViews();
        synchronized (availabilityPending) { availabilityPending.clear(); }
        if (UnifiedCatalogPrefs.apiKey(this).isEmpty()) {
            status.setText("Configuración necesaria");
            TextView text = label("Configura tu clave gratuita de TMDB en Ajustes para cargar películas, series y disponibilidad por país.", 22, Color.WHITE);
            text.setPadding(dp(16), dp(40), dp(16), dp(26));
            shelves.addView(text);
            shelves.addView(action("Configurar TMDB", v ->
                    startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class))));
            return;
        }
        status.setText(refresh ? "Actualizando catálogo…" : "Tendencias y disponibilidad en " + UnifiedCatalogPrefs.region(this));
        addShelf(version, "Tendencias de la semana", "trending", "", refresh);
        addShelf(version, "Últimas películas en cartelera", "movies", "", refresh);
        addShelf(version, "Series del momento", "series", "", refresh);
    }

    private void addShelf(int version, String heading, String kind, String query, boolean refresh) {
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
                List<UnifiedCatalogApi.Media> result = UnifiedCatalogApi.list(this, kind, query, refresh);
                runOnUiThread(() -> {
                    if (isFinishing() || version != generation) return;
                    contents.removeAllViews();
                    if (result.isEmpty()) {
                        contents.addView(label("Sin títulos disponibles", 18, 0xFF99A4B1));
                        return;
                    }
                    int index = 0;
                    for (UnifiedCatalogApi.Media media : result) {
                        addTile(contents, media, index++ < 12);
                    }
                    status.setText("Catálogo disponible · " + UnifiedCatalogPrefs.region(this));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || version != generation) return;
                    contents.removeAllViews();
                    contents.addView(label("Error: " + e.getMessage() + " · Comprueba la conexión o la clave", 17, 0xFFFFAE87));
                });
            }
        });
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
        String id = media.type + ":" + media.id;
        synchronized (availabilityPending) {
            if (availabilityPending.contains(id)) {
                // A tap while a prefetch runs is promoted to launch when the same request finishes.
                if (openAfter) worker.execute(() -> {
                    for (int i = 0; i < 60 && !media.availabilityReady && media.availabilityError.isEmpty(); i++) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) { return; }
                    }
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        if (media.availabilityReady && completed != null) completed.run();
                        else if (!media.availabilityError.isEmpty()) showError(media.availabilityError);
                    });
                });
                return;
            }
            availabilityPending.add(id);
        }
        worker.execute(() -> {
            try {
                UnifiedCatalogApi.availability(this, media);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    updateBadge(media, badge);
                    if (completed != null) completed.run();
                });
            } catch (Exception e) {
                media.availabilityError = e.getMessage() == null ? "Sin conexión" : e.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    badge.setText("Sin datos");
                    if (openAfter) showError(media.availabilityError);
                });
            } finally {
                synchronized (availabilityPending) { availabilityPending.remove(id); }
            }
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
                    addShelf(version, "Resultados de búsqueda", "search", query, false);
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
