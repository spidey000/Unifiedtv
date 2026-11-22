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
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
    private final ExecutorService worker = Executors.newFixedThreadPool(3);
    private final ExecutorService artwork = Executors.newFixedThreadPool(2);
    private LinearLayout shelves;
    private TextView status;
    private LinearLayout tabBar;
    private LinearLayout categoryBar;
    private ScrollView shelfScroll;
    private boolean searching;
    private String tab = "movie";
    private String category = "featured";
    private boolean created;
    private boolean returnFromProvider;
    private volatile int generation;
    private String lastSettings;
    private final Set<UnifiedCatalogApi.Media> availabilityPending =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UnifiedCatalogApi.Media, List<Runnable>> callbacks = new IdentityHashMap<>();

    private int dp(int n) { return CinemaUi.dp(this, n); }

    private TextView label(String value, int size, int color) {
        return CinemaUi.text(this, value, size, color);
    }

    private Button action(String title, View.OnClickListener click) {
        Button button = CinemaUi.button(this, title, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(dp(6));
        button.setLayoutParams(lp);
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
        UnifiedAppUpdater.onResume(this);
        UnifiedAppUpdater.checkAutomatically(this);
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
        root.setBackgroundColor(CinemaUi.BASE);
        root.setPadding(dp(28), dp(10), dp(28), dp(8));
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("UnifiedTV", 24, CinemaUi.ACCENT);
        brand.setTypeface(null, Typeface.BOLD);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        bar.addView(action("Buscar", v -> search()));
        bar.addView(action("Aplicaciones", v -> UnifiedTvHome.leaveForApps(this)));
        bar.addView(action("Ajustes", v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class))));
        bar.addView(action("Recargar", v -> load(true)));
        root.addView(bar);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        tabBar = new LinearLayout(this);
        navigation.addView(tabBar);
        categoryBar = new LinearLayout(this);
        categoryBar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(navigation, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(categoryBar, new LinearLayout.LayoutParams(-1, dp(46)));

        status = label("", 13, CinemaUi.MUTED);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(23)));

        shelfScroll = new ScrollView(this);
        shelfScroll.setVerticalScrollBarEnabled(false);
        shelfScroll.setFillViewport(true);
        shelves = new LinearLayout(this);
        shelves.setOrientation(LinearLayout.VERTICAL);
        shelves.setPadding(0, 0, 0, dp(20));
        shelfScroll.addView(shelves);
        root.addView(shelfScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView credit = label("TMDB / JustWatch   ·   Android TV Watch Next", 11, CinemaUi.MUTED);
        root.addView(credit, new LinearLayout.LayoutParams(-1, dp(18)));
        tabBar.addView(navigationButton("Películas", "movie", v -> switchTab("movie")));
        tabBar.addView(navigationButton("Series", "tv", v -> switchTab("tv")));
        tabBar.addView(navigationButton("Seguir viendo", "continue", v -> switchTab("continue")));
        categoryButton("Destacados", "featured");
        categoryButton("Más vistos", "watched");
        categoryButton("Novedades", "new");
        categoryButton("Populares", "popular");
        categoryButton("Mejor valorados", "rated");
    }

    private void switchTab(String chosen) {
        if (chosen.equals(tab) && !searching) return;
        tab = chosen;
        UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.LAST_TAB, tab).apply();
        lastSettings = fingerprint();
        load(false);
    }

    private void renderNavigation() {
        tabBar.getChildAt(2).setVisibility(UnifiedCatalogPrefs.continueEnabled(this) ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            View item = tabBar.getChildAt(i);
            item.setSelected(!searching && tab.equals(item.getTag()));
        }
        categoryBar.setVisibility(!searching && !"continue".equals(tab) ? View.VISIBLE : View.GONE);
        for (int i = 0; i < categoryBar.getChildCount(); i++) {
            View item = categoryBar.getChildAt(i);
            item.setSelected(category.equals(item.getTag()));
        }
    }

    private Button navigationButton(String title, String id, View.OnClickListener listener) {
        Button button = action(title, listener);
        button.setTag(id);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    private void categoryButton(String name, String id) {
        Button button = navigationButton(name, id, v -> {
            if (id.equals(category)) return;
            category = id;
            load(false);
        });
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) button.getLayoutParams();
        lp.width = 0;
        lp.weight = 1;
        categoryBar.addView(button);
    }

    private void load(boolean refresh) {
        final int version = resetShelves(false);
        if ("continue".equals(tab)) {
            status.setText("Tu lista desde las aplicaciones que comparten Watch Next");
            addContinueShelf(version, false);
            return;
        }

        boolean continueOnHome = UnifiedCatalogPrefs.continueEnabled(this)
                && UnifiedCatalogPrefs.continueOnHome(this);
        if (UnifiedCatalogPrefs.apiKey(this).isEmpty()) {
            status.setText("Tu catálogo de cine y series");
            addWelcome();
            if (continueOnHome) addContinueShelf(version, true);
            return;
        }

        String[] names = {"Destacados", "Más vistos", "Novedades", "Populares", "Mejor valorados"};
        String[] keys = {"featured", "watched", "new", "popular", "rated"};
        String heading = names[0];
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(category)) heading = names[i];
        String typeName = "tv".equals(tab) ? "Series" : "Películas";
        status.setText(typeName + " · " + UnifiedCatalogPrefs.region(this)
                + ("watched".equals(category) ? " · Tendencia semanal (no son reproducciones)" : ""));
        boolean featured = "featured".equals(category);
        FrameLayout hero = featured ? heroPanel("EN PORTADA", typeName, null) : null;
        if (continueOnHome) addContinueShelf(version, true);
        addShelf(version, heading, tab, category, "", refresh, hero);
        if (featured) addShelf(version, "Tendencias de la semana", tab, "watched", "", refresh, null);
    }

    private int resetShelves(boolean search) {
        searching = search;
        final int version = ++generation;
        shelves.removeAllViews();
        shelfScroll.scrollTo(0, 0);
        synchronized (availabilityPending) {
            availabilityPending.clear();
            callbacks.clear();
        }
        if ("continue".equals(tab) && !UnifiedCatalogPrefs.continueEnabled(this)) tab = "movie";
        renderNavigation();
        return version;
    }

    private void addWelcome() {
        heroPanel("UNIFIEDTV", "Tu próxima historia", "Conecta TMDB para explorar películas y series.");
        shelves.addView(action("Configurar TMDB", v ->
                startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class))));
    }

    private FrameLayout heroPanel(String eyebrow, String title, String detail) {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(CinemaUi.shape(this, CinemaUi.SURFACE, 12, Color.TRANSPARENT));
        panel.setClipToOutline(true);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(168));
        size.bottomMargin = dp(8);
        shelves.addView(panel, size);

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams artSize = new FrameLayout.LayoutParams(-1, -1);
        artSize.leftMargin = dp(112);
        panel.addView(art, artSize);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF32151E, 0xE719151D, 0x35101418}));
        panel.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(24), dp(10), dp(20), dp(10));
        panel.addView(copy, new FrameLayout.LayoutParams(-1, -1));
        TextView kicker = label(eyebrow, 12, CinemaUi.ACCENT);
        kicker.setLetterSpacing(0.14f);
        kicker.setTypeface(null, Typeface.BOLD);
        copy.addView(kicker);
        TextView headline = label(title, 32, Color.WHITE);
        headline.setTypeface(null, Typeface.BOLD);
        headline.setMaxLines(2);
        headline.setMaxWidth(dp(440));
        headline.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(headline);
        TextView meta = label(detail == null ? "Explora el catálogo" : detail, 14, Color.WHITE);
        copy.addView(meta);
        panel.setTag(new Hero(art, headline, meta));
        return panel;
    }

    private static final class Hero {
        final ImageView image;
        final TextView title, detail;

        Hero(ImageView image, TextView title, TextView detail) {
            this.image = image;
            this.title = title;
            this.detail = detail;
        }
    }

    private void showFeatured(FrameLayout panel, UnifiedCatalogApi.Media media, View firstTile, int version) {
        Hero hero = (Hero) panel.getTag();
        hero.title.setText(media.title);
        String kind = "tv".equals(media.type) ? "Serie" : "Película";
        hero.detail.setText(kind + (media.year.isEmpty() ? "" : "  ·  " + media.year)
                + (media.hasRating() ? "  ·  ★ " + media.ratingText() : ""));
        panel.setFocusable(true);
        panel.setContentDescription("En portada: " + media.title + ". Buscar en tus plataformas");
        panel.setForeground(CinemaUi.states(this, Color.TRANSPARENT, Color.TRANSPARENT,
                Color.TRANSPARENT, Color.WHITE));
        panel.setOnClickListener(v -> firstTile.performClick());
        artwork.execute(() -> {
            if (generation != version) return;
            Bitmap bitmap = UnifiedCatalogApi.backdrop(this, media.backdrop);
            if (bitmap != null) runOnUiThread(() -> {
                if (!isFinishing() && generation == version) hero.image.setImageBitmap(bitmap);
            });
        });
    }

    private void addShelf(int version, String heading, String type, String category,
                          String query, boolean refresh, FrameLayout hero) {
        TextView headingView = label(heading, 22, Color.WHITE);
        headingView.setTypeface(null, Typeface.BOLD);
        headingView.setPadding(dp(2), dp(10), 0, dp(6));
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
                    if (hero != null) {
                        addTile(contents, result.get(0), true);
                        showFeatured(hero, result.get(0), contents.getChildAt(0), version);
                    }
                    for (int i = hero == null ? 0 : 1; i < result.size(); i++)
                        addTile(contents, result.get(i), i < 6);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || version != generation) return;
                    contents.removeAllViews();
                    contents.addView(label("No se pudo cargar. Revisa TMDB en Ajustes o pulsa Recargar.", 17, 0xFFFFAE87));
                });
            }
        });
    }

    private void addContinueShelf(int version, boolean compact) {
        if (!UnifiedCatalogPrefs.continueEnabled(this)) return;
        TextView heading = label("Seguir viendo", 22, Color.WHITE);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(2), dp(10), 0, dp(6));
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
        styleTile(tile);
        tile.setContentDescription(item.title + " · " + item.source
                + (item.progress() >= 0 ? " · " + item.progress() + "%" : ""));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(184), dp(172));
        params.setMargins(dp(2), dp(2), dp(8), dp(7));
        row.addView(tile, params);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        styleArtwork(image);
        tile.addView(image, new LinearLayout.LayoutParams(-1, dp(95)));

        TextView title = label(item.title, 15, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(title);

        String percentage = item.progress() >= 0 ? " · " + item.progress() + "%" : "";
        String episode = item.episode.isEmpty() ? "" : item.episode + " · ";
        TextView source = label(episode + item.source + percentage, 13, 0xFF9DE4B8);
        source.setSingleLine(true);
        source.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(source);

        if (item.progress() >= 0) {
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(item.progress());
            progress.setProgressTintList(ColorStateList.valueOf(CinemaUi.ACCENT));
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(5));
            progressParams.topMargin = dp(6);
            tile.addView(progress, progressParams);
        }

        tile.setOnClickListener(v -> UnifiedWatchNext.open(this, item));
        final int version = generation;
        artwork.execute(() -> {
            if (generation != version) return;
            Bitmap bitmap = UnifiedWatchNext.artwork(this, item.poster);
            if (bitmap != null) runOnUiThread(() -> {
                if (!isFinishing() && generation == version) image.setImageBitmap(bitmap);
            });
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

    private void styleTile(LinearLayout tile) {
        tile.setPadding(dp(5), dp(5), dp(5), dp(5));
        tile.setBackground(CinemaUi.states(this, CinemaUi.SURFACE, 0xFF343039,
                CinemaUi.SURFACE, Color.WHITE));
        tile.setFocusable(true);
        tile.setClickable(true);
    }

    private void styleArtwork(ImageView image) {
        image.setBackground(CinemaUi.shape(this, 0xFF2C2830, 5, Color.TRANSPARENT));
        image.setClipToOutline(true);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private void addTile(LinearLayout row, UnifiedCatalogApi.Media media, boolean prefetch) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        styleTile(tile);
        tile.setContentDescription(media.title + ", " + (media.type.equals("tv") ? "serie" : "película")
                + (media.hasRating() ? ", valoración TMDB " + media.ratingText() + " sobre 10" : ""));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(130), dp(239));
        params.setMargins(dp(2), dp(2), dp(8), dp(7));
        row.addView(tile, params);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        styleArtwork(poster);
        tile.addView(poster, new LinearLayout.LayoutParams(-1, dp(171)));

        // Title and a tiny TMDB rating share one row: no badges, circles or extra
        // vertical space. The title retains two lines and truncates gracefully.
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.TOP);
        tile.addView(titleRow, new LinearLayout.LayoutParams(-1, dp(40)));

        TextView title = label(media.title, 14, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(6), 0, 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        if (media.hasRating()) {
            // Gold only for the star; the score remains neutral white.
            String score = "★ " + media.ratingText();
            SpannableString styled = new SpannableString(score);
            styled.setSpan(new ForegroundColorSpan(0xFFE9C46A),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView rating = label("", 13, 0xFFE7EDF3);
            rating.setTypeface(null, Typeface.BOLD);
            rating.setText(styled);
            rating.setGravity(Gravity.RIGHT | Gravity.TOP);
            rating.setPadding(dp(5), dp(7), 0, 0);
            rating.setContentDescription("TMDB: " + media.ratingText() + " sobre 10");
            titleRow.addView(rating, new LinearLayout.LayoutParams(-2, dp(43)));
        }

        TextView badge = label("Disponibilidad", 12, CinemaUi.MUTED);
        badge.setSingleLine(true);
        badge.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(badge);
        tile.setOnFocusChangeListener((view, focused) -> {
            if (focused && !media.availabilityReady)
                fetchAvailability(media, badge, false, null);
        });
        tile.setOnClickListener(v -> {
            status.setText("Buscando «" + media.title + "» en tus plataformas…");
            if (media.availabilityReady) UnifiedCatalogRoute.open(this, media);
            else fetchAvailability(media, badge, true, () -> UnifiedCatalogRoute.open(this, media));
        });

        final int version = generation;
        artwork.execute(() -> {
            if (generation != version) return;
            Bitmap bitmap = UnifiedCatalogApi.poster(this, media.poster);
            if (bitmap != null) runOnUiThread(() -> {
                if (!isFinishing() && generation == version) poster.setImageBitmap(bitmap);
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
                    int version = resetShelves(true);
                    status.setText("Resultados para «" + query + "»");
                    if (UnifiedCatalogPrefs.apiKey(this).isEmpty()) {
                        shelves.addView(label("Configura la clave TMDB para buscar títulos.", 18, Color.WHITE));
                        return;
                    }
                    addShelf(version, "Películas", "movie", "search", query, false, null);
                    addShelf(version, "Series", "tv", "search", query, false, null);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    @Override public void onBackPressed() {
        if (searching) load(false);
        else UnifiedTvHome.leaveForApps(this);
    }

    @Override protected void onDestroy() {
        generation++;
        worker.shutdownNow();
        artwork.shutdownNow();
        super.onDestroy();
    }
}
