package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Streaming home. The independent CleanYourTV launcher is still reachable from Applications. */
public final class UnifiedCatalogActivity extends Activity {
    private enum Page { HOME, MOVIES, SERIES, WATCH, SEARCH, APPS }

    private final ExecutorService data = Executors.newFixedThreadPool(3);
    private final ExecutorService art = Executors.newFixedThreadPool(2);
    private final Map<Page, TextView> routes = new LinkedHashMap<>();
    private FrameLayout viewport;
    private ScrollView scroll;
    private LinearLayout pageBody;
    private Page page = Page.HOME;
    private volatile int revision;
    private String settingsSnapshot;
    private String query = "";
    private boolean returning;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UnifiedTvHome.catalogCreated();
        if (state != null) {
            page = Page.values()[state.getInt("page", 0)];
            query = state.getString("query", "");
        }
        settingsSnapshot = snapshot();
        inflateShell();
        show(page, false);
        routes.get(page).requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("page", page.ordinal());
        state.putString("query", query);
    }

    @Override protected void onResume() {
        super.onResume();
        UnifiedAppUpdater.onResume(this);
        UnifiedAppUpdater.checkAutomatically(this);
        String snapshot = snapshot();
        if (!snapshot.equals(settingsSnapshot) || (returning && page == Page.WATCH)) {
            settingsSnapshot = snapshot;
            show(page, false);
        }
        returning = false;
    }

    @Override protected void onStop() {
        returning = true;
        super.onStop();
    }

    private String snapshot() { return UnifiedCatalogPrefs.prefs(this).getAll().toString(); }
    private int dp(int n) { return StreamUi.dp(this, n); }

    private TextView text(String s, int size, int color, boolean bold) {
        return StreamUi.text(this, s, size, color, bold);
    }

    private void inflateShell() {
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(StreamUi.BLACK);
        LinearLayout rail = StreamUi.column(this);
        rail.setBackgroundColor(0xFF101014);
        rail.setPadding(dp(12), dp(18), dp(12), dp(14));
        TextView mark = text("UnifiedTV", 21, StreamUi.RED, true);
        mark.setPadding(dp(10), 0, 0, dp(14));
        rail.addView(mark);
        route(rail, Page.HOME, "Inicio");
        route(rail, Page.SEARCH, "Buscar");
        route(rail, Page.MOVIES, "Películas");
        route(rail, Page.SERIES, "Series");
        route(rail, Page.WATCH, "Seguir viendo");
        route(rail, Page.APPS, "Aplicaciones");
        TextView settings = StreamUi.control(this, "Ajustes", false,
                v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)));
        rail.addView(settings, new LinearLayout.LayoutParams(-1, -2));
        root.addView(rail, new LinearLayout.LayoutParams(dp(150), -1));
        viewport = new FrameLayout(this);
        root.addView(viewport, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
    }

    private void route(LinearLayout rail, Page destination, String label) {
        TextView control = StreamUi.control(this, label, false, v -> show(destination, false));
        control.setBackground(StreamUi.state(this, Color.TRANSPARENT, StreamUi.WHITE,
                0xFF7D2031, Color.TRANSPARENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(3);
        rail.addView(control, lp);
        routes.put(destination, control);
    }

    private void show(Page destination, boolean refresh) {
        page = destination;
        int stamp = ++revision;
        for (Map.Entry<Page, TextView> entry : routes.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == page);
            entry.getValue().setNextFocusRightId(View.NO_ID);
        }
        viewport.removeAllViews();
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        pageBody = StreamUi.column(this);
        pageBody.setPadding(0, 0, 0, dp(24));
        scroll.addView(pageBody);
        viewport.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        switch (page) {
            case HOME: home(stamp, refresh); break;
            case MOVIES: browse(stamp, "movie", "Películas", refresh); break;
            case SERIES: browse(stamp, "tv", "Series", refresh); break;
            case WATCH: title("Seguir viendo", "Progreso compartido con Android TV"); watch(stamp); break;
            case SEARCH: searchPage(stamp); break;
            case APPS: apps(stamp); break;
        }
    }

    private boolean live(int stamp) { return stamp == revision && !isFinishing(); }

    private void home(int stamp, boolean refresh) {
        boolean connected = hasKey();
        FrameLayout spotlight = null;
        if (connected) {
            spotlight = hero("ESTA NOCHE EN UNIFIEDTV", "Encuentra tu próxima historia", null);
        } else {
            hero("UNIFIEDTV", "Tu cine empieza aquí", "Conecta TMDB para descubrir películas y series.");
            connect();
        }
        if (UnifiedCatalogPrefs.continueEnabled(this) && UnifiedCatalogPrefs.continueOnHome(this)) watch(stamp);
        if (connected) {
            shelf(stamp, "Películas destacadas", "movie", "featured", "", refresh, spotlight);
            shelf(stamp, "Series en tendencia", "tv", "watched", "", refresh);
            shelf(stamp, "Novedades de cine", "movie", "new", "", refresh);
            shelf(stamp, "Series populares", "tv", "popular", "", refresh);
            link("Recargar catálogo", v -> show(Page.HOME, true));
        }
        note(pageBody, "Datos de TMDB y JustWatch · Seguir viendo usa Android TV Watch Next");
    }

    private void browse(int stamp, String type, String heading, boolean refresh) {
        title(heading, "Explorar · " + UnifiedCatalogPrefs.region(this));
        if (!hasKey()) { connect(); return; }
        shelf(stamp, "Destacados", type, "featured", "", refresh);
        shelf(stamp, "Novedades", type, "new", "", refresh);
        shelf(stamp, "Más vistos · Tendencia semanal", type, "watched", "", refresh);
        shelf(stamp, "Populares", type, "popular", "", refresh);
        shelf(stamp, "Mejor valorados", type, "rated", "", refresh);
        link("Recargar " + heading.toLowerCase(java.util.Locale.ROOT), v -> show(page, true));
    }

    private boolean hasKey() { return !UnifiedCatalogPrefs.apiKey(this).isEmpty(); }

    private void connect() {
        link("Configurar TMDB", v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)));
    }

    private void title(String heading, String subtitle) {
        LinearLayout box = StreamUi.column(this);
        box.setPadding(dp(24), dp(20), dp(24), dp(12));
        box.addView(text(subtitle, 14, StreamUi.DIM, false));
        box.addView(text(heading, 37, StreamUi.WHITE, true));
        pageBody.addView(box);
    }

    private void note(LinearLayout parent, String message) {
        TextView item = text(message, 14, StreamUi.DIM, false);
        item.setPadding(dp(24), dp(8), dp(24), dp(8));
        parent.addView(item);
    }

    private void link(String label, View.OnClickListener click) {
        TextView button = StreamUi.control(this, label, false, click);
        leftToRail(button);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(24), dp(6), dp(24), dp(8));
        pageBody.addView(button, lp);
    }

    private void leftToRail(View view) {
        view.setNextFocusLeftId(routes.get(page).getId());
    }

    private FrameLayout hero(String badge, String heading, String caption) {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackgroundColor(0xFF200C14);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF509090B, 0xD009090B, 0x2909090B}));
        panel.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{StreamUi.BLACK, Color.TRANSPARENT}));
        panel.addView(fade, new FrameLayout.LayoutParams(-1, dp(65), Gravity.BOTTOM));
        LinearLayout copy = StreamUi.column(this);
        copy.setPadding(dp(26), dp(16), dp(24), dp(17));
        copy.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(copy, new FrameLayout.LayoutParams(-1, -1));
        TextView kicker = text(badge, 12, StreamUi.RED, true);
        kicker.setLetterSpacing(.14f);
        copy.addView(kicker);
        TextView headline = text(heading, 38, StreamUi.WHITE, true);
        headline.setMaxLines(2);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        headline.setMaxWidth(dp(470));
        copy.addView(headline, new LinearLayout.LayoutParams(-2, -2));
        TextView description = text(caption, 15, StreamUi.DIM, false);
        copy.addView(description);
        panel.setTag(new Spotlight(backdrop, copy, headline, description));
        pageBody.addView(panel, new LinearLayout.LayoutParams(-1, dp(224)));
        return panel;
    }

    private static final class Spotlight {
        final ImageView backdrop;
        final LinearLayout copy;
        final TextView title, caption;

        Spotlight(ImageView backdrop, LinearLayout copy, TextView title, TextView caption) {
            this.backdrop = backdrop;
            this.copy = copy;
            this.title = title;
            this.caption = caption;
        }
    }

    private void bindHero(FrameLayout panel, UnifiedCatalogApi.Media media, int stamp) {
        Spotlight hero = (Spotlight) panel.getTag();
        hero.title.setText(media.title);
        hero.caption.setText(metadata(media));
        TextView details = StreamUi.control(this, "Ver ficha", true, v -> details(media));
        leftToRail(details);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(9);
        hero.copy.addView(details, lp);
        routes.get(page).setNextFocusRightId(details.getId());
        if (media.backdrop.startsWith("/")) image(stamp, hero.backdrop, () -> UnifiedCatalogApi.backdrop(this, media.backdrop));
    }

    private String metadata(UnifiedCatalogApi.Media media) {
        return ("tv".equals(media.type) ? "Serie" : "Película")
                + (media.year.isEmpty() ? "" : "  ·  " + media.year)
                + (media.hasRating() ? "  ·  ★ " + media.ratingText() + " TMDB" : "");
    }

    private LinearLayout row(String label) {
        TextView heading = text(label, 21, StreamUi.WHITE, true);
        heading.setPadding(dp(24), dp(12), dp(20), dp(7));
        pageBody.addView(heading);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setFocusable(false);
        LinearLayout line = new LinearLayout(this);
        line.setPadding(dp(24), 0, dp(24), dp(7));
        horizontal.addView(line);
        pageBody.addView(horizontal, new LinearLayout.LayoutParams(-1, -2));
        return line;
    }

    private interface Result { void accept(List<UnifiedCatalogApi.Media> result); }

    private void requestList(int stamp, String kind, String category, String search, boolean refresh,
                             Result done, Runnable failed) {
        data.execute(() -> {
            if (revision != stamp) return;
            try {
                List<UnifiedCatalogApi.Media> result = UnifiedCatalogApi.list(this, kind, category, search, refresh);
                runOnUiThread(() -> { if (live(stamp)) done.accept(result); });
            } catch (Exception ignored) {
                runOnUiThread(() -> { if (live(stamp)) failed.run(); });
            }
        });
    }

    private void shelf(int stamp, String heading, String kind, String category, String search, boolean refresh) {
        shelf(stamp, heading, kind, category, search, refresh, null);
    }

    private void shelf(int stamp, String heading, String kind, String category, String search,
                       boolean refresh, FrameLayout spotlight) {
        LinearLayout row = row(heading);
        row.addView(text("Cargando…", 15, StreamUi.DIM, false));
        requestList(stamp, kind, category, search, refresh, result -> {
            row.removeAllViews();
            if (result.isEmpty()) { row.addView(text("Sin títulos", 15, StreamUi.DIM, false)); return; }
            if (spotlight != null) bindHero(spotlight, result.get(0), stamp);
            List<Runnable> loads = new ArrayList<>();
            loadOnScroll(row, loads, 130);
            for (int i = 0; i < result.size(); i++) {
                UnifiedCatalogApi.Media media = result.get(i);
                LinearLayout card = card(row, 122, i, () -> details(media));
                ImageView poster = poster(card, 153);
                TextView title = singleLine(media.title, 14, StreamUi.WHITE, true);
                card.addView(title);
                if (media.hasRating()) card.addView(singleLine("★ " + media.ratingText(), 12, 0xFFFFD278, false));
                else card.addView(singleLine(media.year, 12, StreamUi.DIM, false));
                card.setContentDescription(media.title + (media.hasRating() ? ", valoración TMDB " + media.ratingText() : ""));
                loads.add(once(() -> image(stamp, poster, () -> UnifiedCatalogApi.poster(this, media.poster))));
                lazyAround(card, loads, i);
            }
        }, () -> {
            row.removeAllViews();
            row.addView(text("No disponible. Revisa tu conexión o la configuración de TMDB.", 15, StreamUi.DIM, false));
        });
    }

    private LinearLayout card(LinearLayout row, int width, int index, Runnable click) {
        LinearLayout card = StreamUi.column(this);
        card.setId(View.generateViewId());
        card.setPadding(dp(4), dp(4), dp(4), dp(5));
        card.setBackground(StreamUi.state(this, StreamUi.PANEL, 0xFF343037, StreamUi.PANEL, StreamUi.WHITE));
        card.setFocusable(true);
        card.setOnClickListener(v -> click.run());
        if (index == 0) leftToRail(card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), -2);
        lp.rightMargin = dp(8);
        row.addView(card, lp);
        return card;
    }

    private static Runnable once(Runnable operation) {
        return new Runnable() {
            private boolean ran;
            @Override public void run() { if (!ran) { ran = true; operation.run(); } }
        };
    }

    private void lazyAround(View view, List<Runnable> loads, int index) {
        if (index < 7) loads.get(index).run();
        view.setOnFocusChangeListener((v, focused) -> {
            if (focused) for (int i = Math.max(0, index - 1); i < Math.min(index + 4, loads.size()); i++) loads.get(i).run();
        });
    }

    private void loadOnScroll(LinearLayout row, List<Runnable> loads, int stride) {
        View scroll = (View) row.getParent();
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            int first = Math.max(0, (x - dp(24)) / dp(stride));
            int end = Math.min(loads.size(), first + scroll.getWidth() / dp(stride) + 3);
            for (int i = first; i < end; i++) loads.get(i).run();
        });
    }

    private TextView singleLine(String value, int size, int color, boolean bold) {
        TextView text = text(value, size, color, bold);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setPadding(dp(3), dp(2), dp(3), 0);
        return text;
    }

    private ImageView poster(LinearLayout parent, int height) {
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(StreamUi.shape(this, 0xFF302C34, 4, Color.TRANSPARENT));
        poster.setClipToOutline(true);
        poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(poster, new LinearLayout.LayoutParams(-1, dp(height)));
        return poster;
    }

    private interface Artwork { Bitmap load(); }

    private void image(int stamp, ImageView target, Artwork loader) {
        art.execute(() -> {
            if (revision != stamp) return;
            Bitmap value = loader.load();
            if (value != null) runOnUiThread(() -> { if (live(stamp)) target.setImageBitmap(value); });
        });
    }

    private void watch(int stamp) {
        if (!UnifiedCatalogPrefs.continueEnabled(this)) {
            link("Activar Seguir viendo en Ajustes", v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)));
            return;
        }
        LinearLayout line = row("Tu lista");
        if (!UnifiedWatchNext.hasPermission(this)) {
            TextView allow = StreamUi.control(this, "Permitir acceso a Watch Next", true,
                    v -> requestPermissions(new String[]{UnifiedWatchNext.PERMISSION}, UnifiedWatchNext.PERMISSION_REQUEST));
            leftToRail(allow);
            line.addView(allow);
            return;
        }
        line.addView(text("Cargando…", 15, StreamUi.DIM, false));
        data.execute(() -> {
            if (revision != stamp) return;
            List<UnifiedWatchNext.Entry> items = UnifiedWatchNext.load(this);
            runOnUiThread(() -> {
                if (!live(stamp)) return;
                line.removeAllViews();
                if (items.isEmpty()) line.addView(text("Tus apps aún no han compartido progreso.", 15, StreamUi.DIM, false));
                List<Runnable> loads = new ArrayList<>();
                loadOnScroll(line, loads, 198);
                for (int i = 0; i < Math.min(24, items.size()); i++) {
                    UnifiedWatchNext.Entry item = items.get(i);
                    LinearLayout card = card(line, 190, i, () -> UnifiedWatchNext.open(this, item));
                    ImageView art = poster(card, 101);
                    card.addView(singleLine(item.title, 15, StreamUi.WHITE, true));
                    card.addView(singleLine(item.source + (item.episode.isEmpty() ? "" : " · " + item.episode), 12, StreamUi.DIM, false));
                    card.setContentDescription(item.title + " · " + item.source
                            + (item.progress() < 0 ? "" : " · " + item.progress() + "%"));
                    if (item.progress() >= 0) {
                        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                        progress.setMax(100);
                        progress.setProgress(item.progress());
                        progress.setProgressTintList(ColorStateList.valueOf(StreamUi.RED));
                        card.addView(progress, new LinearLayout.LayoutParams(-1, dp(5)));
                    }
                    loads.add(once(() -> image(stamp, art, () -> UnifiedWatchNext.artwork(this, item.poster))));
                    lazyAround(card, loads, i);
                }
            });
        });
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == UnifiedWatchNext.PERMISSION_REQUEST) show(page, false);
    }

    private void searchPage(int stamp) {
        title("Buscar", "Películas y series");
        if (!hasKey()) { connect(); return; }
        LinearLayout form = new LinearLayout(this);
        form.setGravity(Gravity.CENTER_VERTICAL);
        form.setPadding(dp(24), 0, dp(24), dp(8));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Escribe un título");
        field.setText(query);
        field.setTextColor(StreamUi.WHITE);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setId(View.generateViewId());
        leftToRail(field);
        form.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        Runnable submit = () -> {
            query = field.getText().toString().trim();
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(field.getWindowToken(), 0);
            show(Page.SEARCH, false);
            routes.get(Page.SEARCH).requestFocus();
        };
        TextView go = StreamUi.control(this, "Buscar", true, v -> submit.run());
        form.addView(go);
        field.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            submit.run();
            return true;
        });
        pageBody.addView(form);
        routes.get(page).setNextFocusRightId(field.getId());
        if (!query.isEmpty()) {
            shelf(stamp, "Películas", "movie", "search", query, false);
            shelf(stamp, "Series", "tv", "search", query, false);
        }
    }

    private void apps(int stamp) {
        title("Aplicaciones", "Instaladas en esta TV");
        LinearLayout line = row("Tus apps");
        link("Abrir launcher clásico", v -> UnifiedTvHome.leaveForApps(this));
        PackageManager manager = getPackageManager();
        data.execute(() -> {
            Map<String, ResolveInfo> installed = new LinkedHashMap<>();
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (ResolveInfo app : manager.queryIntentActivities(launch, 0)) {
                    String pkg = app.activityInfo.packageName;
                    if (!pkg.equals(getPackageName())) installed.putIfAbsent(pkg, app);
                }
            }
            List<ResolveInfo> sorted = new ArrayList<>(installed.values());
            sorted.sort(new ResolveInfo.DisplayNameComparator(manager));
            runOnUiThread(() -> {
                if (!live(stamp)) return;
                for (int i = 0; i < sorted.size(); i++) {
                    ResolveInfo app = sorted.get(i);
                    CharSequence name = app.loadLabel(manager);
                    LinearLayout card = card(line, 124, i, () -> {
                        Intent launch = new Intent(Intent.ACTION_MAIN).setClassName(app.activityInfo.packageName, app.activityInfo.name);
                        try { startActivity(launch); }
                        catch (RuntimeException e) { android.widget.Toast.makeText(this, "No se pudo abrir la aplicación", android.widget.Toast.LENGTH_SHORT).show(); }
                    });
                    ImageView icon = poster(card, 81);
                    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    icon.setPadding(dp(12), dp(8), dp(12), dp(8));
                    card.addView(singleLine(name.toString(), 14, StreamUi.WHITE, true));
                    art.execute(() -> {
                        if (revision != stamp) return;
                        Drawable drawable = app.loadIcon(manager);
                        runOnUiThread(() -> { if (live(stamp)) icon.setImageDrawable(drawable); });
                    });
                }
                if (sorted.isEmpty()) line.addView(text("No se encontraron aplicaciones", 15, StreamUi.DIM, false));
            });
        });
    }

    private void details(UnifiedCatalogApi.Media media) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackground(StreamUi.shape(this, StreamUi.PANEL, 12, Color.TRANSPARENT));
        LinearLayout artColumn = StreamUi.column(this);
        ImageView image = poster(artColumn, 215);
        root.addView(artColumn, new LinearLayout.LayoutParams(dp(150), -2));
        LinearLayout copy = StreamUi.column(this);
        copy.setPadding(dp(18), 0, 0, 0);
        root.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = text(media.title, 26, StreamUi.WHITE, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name);
        copy.addView(text(metadata(media), 14, StreamUi.RED, true));
        TextView synopsis = text(media.overview.isEmpty() ? "No hay sinopsis disponible." : media.overview, 15, StreamUi.DIM, false);
        synopsis.setPadding(0, dp(8), 0, dp(8));
        synopsis.setMaxLines(4);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(synopsis);
        TextView platforms = text("Consultando disponibilidad…", 14, StreamUi.DIM, false);
        platforms.setMaxLines(2);
        platforms.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(platforms);
        TextView open = StreamUi.control(this, "Ver opciones", true, v -> {
            if (!media.availabilityReady) return;
            dialog.dismiss();
            UnifiedCatalogRoute.open(this, media);
        });
        open.setEnabled(false);
        copy.addView(open, new LinearLayout.LayoutParams(-2, -2));
        TextView close = StreamUi.control(this, "Cerrar ficha", false, v -> dialog.dismiss());
        copy.addView(close, new LinearLayout.LayoutParams(-2, -2));
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(660), getResources().getDisplayMetrics().widthPixels - dp(40)), -2);
        }
        close.requestFocus();
        image(revision, image, () -> UnifiedCatalogApi.poster(this, media.poster));
        data.execute(() -> {
            boolean ready;
            try { UnifiedCatalogApi.availability(this, media); ready = true; }
            catch (Exception ignored) { ready = false; }
            final boolean loaded = ready;
            runOnUiThread(() -> {
                if (!dialog.isShowing() || isFinishing()) return;
                List<String> names = new ArrayList<>();
                if (loaded) for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogApi.eligible(this, media)) names.add(provider.name);
                platforms.setText(!loaded ? "No se pudo consultar la disponibilidad"
                        : names.isEmpty() ? "No está en tus plataformas seleccionadas" : TextUtils.join(" · ", names));
                open.setEnabled(loaded);
                if (!names.isEmpty()) open.setText("Abrir en " + names.get(0));
            });
        });
    }

    @Override public void onBackPressed() {
        if (page != Page.HOME) {
            show(Page.HOME, false);
            routes.get(Page.HOME).requestFocus();
        } else {
            UnifiedTvHome.leaveForApps(this);
        }
    }

    @Override protected void onDestroy() {
        revision++;
        data.shutdownNow();
        art.shutdownNow();
        super.onDestroy();
    }
}
