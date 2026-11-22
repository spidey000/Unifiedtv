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
import android.widget.TextClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Streaming home. The independent CleanYourTV launcher is still reachable from Applications. */
public final class UnifiedCatalogActivity extends Activity {
    private enum Page { HOME, WATCH, SEARCH, APPS, LIVE }
    private static final String[] FILTERS = {"featured", "watched", "new", "popular", "rated"};
    private static final String[] LABELS = {"Destacados", "Más vistos", "Novedades", "Populares", "Mejor valorados"};
    private static final int[] FILTER_ICONS = {R.drawable.uic_star, R.drawable.uic_chart_no_axes_column_increasing,
            R.drawable.uic_sparkles, R.drawable.uic_flame, R.drawable.uic_star};
    private final TextView[] filters = new TextView[FILTERS.length];
    private boolean series;
    private int filter;
    private int accent;

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
            String savedPage = state.getString("catalogPage", Page.HOME.name());
            page = Page.valueOf(savedPage);
            query = state.getString("query", "");
            series = state.getBoolean("series");
            filter = state.getInt("filter");
        }
        settingsSnapshot = snapshot();
        accent = StreamUi.accent(this);
        inflateShell();
        show(page, false);
        routes.get(page).requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("catalogPage", page.name());
        state.putString("query", query);
        state.putBoolean("series", series);
        state.putInt("filter", filter);
    }

    @Override protected void onResume() {
        super.onResume();
        UnifiedAppUpdater.onResume(this);
        UnifiedAppUpdater.checkAutomatically(this);
        String snapshot = snapshot();
        int currentAccent = StreamUi.accent(this);
        boolean paletteChanged = currentAccent != accent;
        if (paletteChanged) {
            accent = currentAccent;
            inflateShell();
        }
        if (paletteChanged || !snapshot.equals(settingsSnapshot)
                || (returning && (page == Page.WATCH || (page == Page.HOME && UnifiedCatalogPrefs.continueOnHome(this))))) {
            settingsSnapshot = snapshot;
            show(page, false);
            if (paletteChanged) routes.get(page).requestFocus();
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
        routes.clear();
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(StreamUi.BLACK);
        LinearLayout rail = StreamUi.column(this);
        rail.setBackground(StreamUi.rail(this));
        rail.setPadding(dp(9), dp(16), dp(9), dp(14));
        TextView mark = text(getString(R.string.unifiedtv_name), 19, StreamUi.WHITE, true);
        StreamUi.icon(mark, R.drawable.uic_play);
        mark.getCompoundDrawablesRelative()[0].setTint(StreamUi.readableAccent(this));
        mark.setPadding(dp(8), 0, 0, dp(23));
        rail.addView(mark);
        route(rail, Page.HOME, "Inicio", R.drawable.uic_house);
        route(rail, Page.SEARCH, "Buscar", R.drawable.uic_search);
        route(rail, Page.WATCH, "Mi lista", R.drawable.uic_layers);
        route(rail, Page.APPS, "Apps y fuentes", R.drawable.ic_apps);
        route(rail, Page.LIVE, "TV en directo", R.drawable.ic_livetv);
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView settings = StreamUi.control(this, "Ajustes", R.drawable.ic_settings, false,
                v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)));
        StreamUi.background(settings, false, true);
        rail.addView(settings, new LinearLayout.LayoutParams(-1, -2));
        root.addView(rail, new LinearLayout.LayoutParams(dp(147), -1));
        viewport = new FrameLayout(this);
        root.addView(viewport, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
    }

    private void route(LinearLayout rail, Page destination, String label, int icon) {
        TextView control = StreamUi.control(this, label, icon, false, v -> show(destination, false));
        StreamUi.background(control, false, true);
        control.setSingleLine(true);
        control.setEllipsize(TextUtils.TruncateAt.END);
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
            case WATCH: title("Mi lista", "Progreso compartido con Android TV"); watch(stamp); break;
            case LIVE:
                title("TV en directo", "Sintonizador y entradas de la TV");
                link("Ver entradas disponibles", R.drawable.ic_input, v -> UnifiedTvHome.openInputs(this));
                break;
            case SEARCH: searchPage(stamp); break;
            case APPS: apps(stamp); break;
        }
    }

    private boolean live(int stamp) { return stamp == revision && !isFinishing(); }

    private void home(int stamp, boolean refresh) {
        boolean connected = hasKey();
        String kind = series ? "tv" : "movie";
        FrameLayout spotlight = hero("DESTACADO", connected ? "Descubrir" : "Tu cine empieza aquí",
                connected ? "" : "Conecta TMDB para descubrir películas y series.");
        addHomeTabs(spotlight);
        addFilters(connected);
        if (!connected) connect();
        if (UnifiedCatalogPrefs.continueEnabled(this) && UnifiedCatalogPrefs.continueOnHome(this)) watch(stamp);
        if (connected) {
            shelf(stamp, filter == 0 ? "En tendencia en UnifiedTV" : LABELS[filter],
                    kind, FILTERS[filter], "", refresh, spotlight);
            link("Recargar catálogo", R.drawable.uic_refresh_cw, v -> {
                show(Page.HOME, true);
                filters[filter].requestFocus();
            });
            note(pageBody, "Datos de TMDB y JustWatch · Más vistos usa la tendencia semanal de TMDB");
        }
    }

    private void addHomeTabs(FrameLayout spotlight) {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackground(StreamUi.panel(this));
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < 2; i++) {
            boolean showSeries = i == 1;
            TextView tab = StreamUi.control(this, showSeries ? "Series" : "Películas",
                    showSeries ? R.drawable.ic_livetv : R.drawable.uic_film, false, v -> {
                        if (series == showSeries) return;
                        series = showSeries;
                        show(Page.HOME, false);
                        findViewById(showSeries ? R.id.seriesTab : R.id.moviesTab).requestFocus();
                    });
            tab.setId(showSeries ? R.id.seriesTab : R.id.moviesTab);
            tab.setSelected(showSeries == series);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(36));
            StreamUi.background(tab, false, true);
            if (i == 0) leftToRail(tab);
            tabs.addView(tab);
        }
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        top.topMargin = dp(9);
        spotlight.addView(tabs, top);
        routes.get(Page.HOME).setNextFocusRightId(series ? R.id.seriesTab : R.id.moviesTab);
        TextClock clock = new TextClock(this);
        ClockFormat.apply(clock, getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getString(Config.CLOCK_FORMAT_KEY, "system"));
        clock.setTextColor(StreamUi.WHITE);
        clock.setTextSize(15);
        FrameLayout.LayoutParams edge = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        edge.setMargins(0, dp(15), dp(15), 0);
        spotlight.addView(clock, edge);
    }

    private void addFilters(boolean enabled) {
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(dp(19), 0, dp(14), dp(4));
        for (int i = 0; i < FILTERS.length; i++) {
            int choice = i;
            TextView button = StreamUi.control(this, LABELS[i], FILTER_ICONS[i], false, v -> {
                if (filter == choice) return;
                filter = choice;
                show(Page.HOME, false);
                filters[choice].requestFocus();
            });
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(dp(40));
            button.setTextSize(13);
            button.setPadding(dp(6), 0, dp(6), 0);
            button.setSelected(i == filter);
            button.setEnabled(enabled);
            button.setFocusable(enabled);
            if (i == 0) leftToRail(button);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
            cell.setMarginEnd(dp(6));
            filters[i] = button;
            strip.addView(button, cell);
        }
        pageBody.addView(strip, new LinearLayout.LayoutParams(-1, dp(44)));
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
        link(label, R.drawable.uic_arrow_right, click);
    }

    private void link(String label, int icon, View.OnClickListener click) {
        TextView button = StreamUi.control(this, label, icon, false, click);
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
        panel.setBackground(StreamUi.rail(this));
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFA0B0B0F, 0xB90B0B0F, 0x080B0B0F}));
        panel.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{StreamUi.BLACK, Color.TRANSPARENT}));
        panel.addView(fade, new FrameLayout.LayoutParams(-1, dp(65), Gravity.BOTTOM));
        LinearLayout copy = StreamUi.column(this);
        copy.setPadding(dp(24), dp(56), dp(24), dp(12));
        copy.setGravity(Gravity.BOTTOM);
        panel.addView(copy, new FrameLayout.LayoutParams(-1, -1));
        TextView kicker = text(badge, 11, StreamUi.readableAccent(this), true);
        kicker.setLetterSpacing(.14f);
        copy.addView(kicker);
        TextView headline = text(heading, 35, StreamUi.WHITE, true);
        headline.setMaxLines(2);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        headline.setMaxWidth(dp(380));
        copy.addView(headline, new LinearLayout.LayoutParams(-2, -2));
        TextView meta = text("", 13, StreamUi.RATING, true);
        copy.addView(meta);
        TextView description = text(caption, 13, StreamUi.DIM, false);
        description.setMaxWidth(dp(370));
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(description);
        panel.setTag(new Spotlight(backdrop, copy, headline, meta, description));
        int height = Math.max(250, Math.min(380, getResources().getConfiguration().screenHeightDp * 42 / 100));
        pageBody.addView(panel, new LinearLayout.LayoutParams(-1, dp(height)));
        return panel;
    }

    private static final class Spotlight {
        final ImageView backdrop;
        final LinearLayout copy;
        final TextView title, meta, caption;
        int index;

        Spotlight(ImageView backdrop, LinearLayout copy, TextView title, TextView meta, TextView caption) {
            this.backdrop = backdrop;
            this.copy = copy;
            this.title = title;
            this.meta = meta;
            this.caption = caption;
        }
    }

    private void bindHero(FrameLayout panel, List<UnifiedCatalogApi.Media> result, int stamp) {
        Spotlight hero = (Spotlight) panel.getTag();
        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(6);
        hero.copy.addView(actions, lp);
        TextView watch = StreamUi.control(this, "Ver ahora", R.drawable.uic_play, true,
                v -> openMedia(result.get(hero.index), (TextView) v, stamp));
        leftToRail(watch);
        actions.addView(watch);
        TextView details = StreamUi.control(this, "Más información", R.drawable.uic_info, false,
                v -> details(result.get(hero.index)));
        LinearLayout.LayoutParams detailSize = new LinearLayout.LayoutParams(-2, -2);
        detailSize.setMarginStart(dp(7));
        actions.addView(details, detailSize);

        LinearLayout selection = new LinearLayout(this);
        selection.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        position.setMargins(0, 0, dp(15), dp(12));
        panel.addView(selection, position);

        int count = Math.min(4, result.size());
        TextView indicator = text("", 13, StreamUi.WHITE, true);
        Runnable update = () -> {
            int index = hero.index;
            UnifiedCatalogApi.Media current = result.get(index);
            hero.title.setText(current.title);
            hero.meta.setText(metadata(current));
            hero.caption.setText(current.overview);
            hero.backdrop.setImageDrawable(null);
            watch.setEnabled(true);
            indicator.setText(getString(R.string.spotlight_position, index + 1, count));
            art.execute(() -> {
                if (revision != stamp) return;
                Bitmap bitmap = current.backdrop.startsWith("/")
                        ? UnifiedCatalogApi.backdrop(this, current.backdrop)
                        : UnifiedCatalogApi.poster(this, current.poster);
                runOnUiThread(() -> {
                    if (live(stamp) && hero.index == index) hero.backdrop.setImageBitmap(bitmap);
                });
            });
        };
        if (count > 1) {
            for (int step : new int[]{-1, 1}) {
                TextView arrow = StreamUi.control(this, "",
                        step < 0 ? R.drawable.uic_chevron_left : R.drawable.uic_chevron_right, false, v -> {
                            hero.index = (hero.index + count + step) % count;
                            update.run();
                        });
                arrow.setContentDescription(step < 0 ? "Destacado anterior" : "Siguiente destacado");
                arrow.setMinHeight(dp(36));
                arrow.setPadding(dp(8), 0, dp(8), 0);
                selection.addView(arrow);
                if (step < 0) {
                    indicator.setPadding(dp(8), 0, dp(8), 0);
                    selection.addView(indicator);
                }
            }
        }
        update.run();
    }

    private void openMedia(UnifiedCatalogApi.Media media, TextView button, int stamp) {
        button.setEnabled(false);
        data.execute(() -> {
            boolean available;
            try {
                UnifiedCatalogApi.availability(this, media);
                available = true;
            } catch (Exception ignored) {
                available = false;
            }
            boolean loaded = available;
            runOnUiThread(() -> {
                if (!live(stamp) || !hasWindowFocus()) {
                    if (live(stamp)) button.setEnabled(true);
                    return;
                }
                button.setEnabled(true);
                if (loaded) UnifiedCatalogRoute.open(this, media);
                else details(media);
            });
        });
    }

    private String metadata(UnifiedCatalogApi.Media media) {
        return ("tv".equals(media.type) ? "Serie" : "Película")
                + (media.year.isEmpty() ? "" : "  ·  " + media.year)
                + (media.hasRating() ? "  ·  TMDB " + media.ratingText() : "");
    }

    private LinearLayout row(String label) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(19), dp(5), dp(14), dp(5));
        header.addView(text(label, 20, StreamUi.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (page == Page.HOME && label.equals("Seguir viendo")) {
            TextView more = StreamUi.control(this, "Ver todo", R.drawable.uic_arrow_right, false, v -> {
                show(Page.WATCH, false);
                routes.get(Page.WATCH).requestFocus();
            });
            StreamUi.background(more, false, true);
            more.setMinHeight(dp(32));
            header.addView(more);
        }
        pageBody.addView(header);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setFocusable(false);
        LinearLayout line = new LinearLayout(this);
        line.setPadding(dp(19), dp(2), dp(19), dp(7));
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
            if (spotlight != null) bindHero(spotlight, result, stamp);
            List<Runnable> loads = new ArrayList<>();
            int width = cardWidth();
            loadOnScroll(row, loads, width + 8);
            for (int i = 0; i < result.size(); i++) {
                UnifiedCatalogApi.Media media = result.get(i);
                FrameLayout card = landscape(row, width, i, () -> details(media));
                ImageView artwork = landscapeArt(card);
                LinearLayout caption = caption(card);
                caption.addView(singleLine(media.title, 14, StreamUi.WHITE, true));
                caption.addView(singleLine(("tv".equals(media.type) ? "Serie" : "Película")
                        + (media.year.isEmpty() ? "" : " · " + media.year), 12, StreamUi.DIM, false));
                if (media.hasRating()) rating(card, media.ratingText());
                card.setContentDescription(media.title + (media.hasRating() ? ", valoración TMDB " + media.ratingText() : ""));
                loads.add(once(() -> image(stamp, artwork, () -> media.backdrop.startsWith("/")
                        ? UnifiedCatalogApi.shelfArtwork(this, media.backdrop)
                        : UnifiedCatalogApi.poster(this, media.poster))));
                lazyAround(card, loads, i);
            }
        }, () -> {
            row.removeAllViews();
            row.addView(text("No disponible. Revisa tu conexión o la configuración de TMDB.", 15, StreamUi.DIM, false));
        });
    }

    private int cardWidth() {
        int available = getResources().getConfiguration().screenWidthDp - 147 - 38;
        return Math.max(143, Math.min(242, (available - 8 * 4) / 5));
    }

    private FrameLayout landscape(LinearLayout row, int width, int index, Runnable click) {
        FrameLayout card = new FrameLayout(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setOnClickListener(v -> click.run());
        card.setBackground(StreamUi.panel(this));
        card.setClipToOutline(true);
        card.setForeground(StreamUi.state(this, Color.TRANSPARENT, Color.TRANSPARENT,
                Color.TRANSPARENT, StreamUi.WHITE));
        if (index == 0) leftToRail(card);
        LinearLayout.LayoutParams tile = new LinearLayout.LayoutParams(dp(width), dp(width * 62 / 100));
        tile.setMarginEnd(dp(8));
        row.addView(card, tile);
        return card;
    }

    private ImageView landscapeArt(FrameLayout card) {
        ImageView artwork = new ImageView(this);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        return artwork;
    }

    private LinearLayout caption(FrameLayout card) {
        LinearLayout copy = StreamUi.column(this);
        copy.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000B0B0F, 0xE609090B, 0xFF121217}));
        copy.setPadding(dp(7), dp(12), dp(7), dp(7));
        card.addView(copy, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return copy;
    }

    private void rating(FrameLayout card, String value) {
        TextView badge = text("TMDB " + value, 10, StreamUi.RATING, true);
        badge.setPadding(dp(5), dp(2), dp(5), dp(2));
        badge.setBackground(StreamUi.shape(this, 0xEE121217, 5, Color.TRANSPARENT));
        FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        size.setMargins(0, dp(5), dp(6), 0);
        card.addView(badge, size);
    }

    private LinearLayout card(LinearLayout row, int width, int index, Runnable click) {
        LinearLayout card = StreamUi.column(this);
        card.setId(View.generateViewId());
        card.setPadding(dp(4), dp(4), dp(4), dp(5));
        StreamUi.background(card, false, false);
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
        LinearLayout line = row("Seguir viendo");
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
                int width = cardWidth();
                loadOnScroll(line, loads, width + 8);
                for (int i = 0; i < Math.min(24, items.size()); i++) {
                    UnifiedWatchNext.Entry item = items.get(i);
                    FrameLayout card = landscape(line, width, i, () -> UnifiedWatchNext.open(this, item));
                    ImageView art = landscapeArt(card);
                    LinearLayout copy = caption(card);
                    copy.addView(singleLine(item.title, 14, StreamUi.WHITE, true));
                    copy.addView(singleLine(item.source + (item.episode.isEmpty() ? "" : " · " + item.episode), 11, StreamUi.DIM, false));
                    card.setContentDescription(item.title + " · " + item.source
                            + (item.progress() < 0 ? "" : " · " + item.progress() + "%"));
                    if (item.progress() >= 0) {
                        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                        progress.setMax(100);
                        progress.setProgress(item.progress());
                        progress.setProgressTintList(ColorStateList.valueOf(StreamUi.visibleAccent(this)));
                        progress.setProgressBackgroundTintList(ColorStateList.valueOf(StreamUi.BORDER));
                        LinearLayout meter = new LinearLayout(this);
                        meter.setGravity(Gravity.CENTER_VERTICAL);
                        meter.addView(progress, new LinearLayout.LayoutParams(0, dp(5), 1));
                        TextView percent = text(item.progress() + "%", 11, StreamUi.DIM, false);
                        percent.setPadding(dp(6), 0, 0, 0);
                        meter.addView(percent);
                        copy.addView(meter);
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
        StreamUi.field(field);
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
        TextView go = StreamUi.control(this, "Buscar", R.drawable.uic_search, true, v -> submit.run());
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
        title("Apps y fuentes", "Aplicaciones instaladas");
        LinearLayout line = row("Tus apps");
        link("Gestionar fuentes de streaming", R.drawable.ic_settings,
                v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)
                        .putExtra(UnifiedCatalogSettingsActivity.EXTRA_SECTION, UnifiedCatalogSettingsActivity.PLATFORMS_SECTION)));
        link("Abrir launcher clásico", R.drawable.uic_house, v -> UnifiedTvHome.leaveForApps(this));
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
        root.setBackground(StreamUi.panel(this));
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
        copy.addView(text(metadata(media), 14, StreamUi.RATING, true));
        TextView synopsis = text(media.overview.isEmpty() ? "No hay sinopsis disponible." : media.overview, 15, StreamUi.DIM, false);
        synopsis.setPadding(0, dp(8), 0, dp(8));
        synopsis.setMaxLines(4);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(synopsis);
        TextView platforms = text("Consultando disponibilidad…", 14, StreamUi.DIM, false);
        platforms.setMaxLines(2);
        platforms.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(platforms);
        TextView open = StreamUi.control(this, "Ver opciones", R.drawable.uic_play, true, v -> {
            if (!media.availabilityReady) return;
            dialog.dismiss();
            UnifiedCatalogRoute.open(this, media);
        });
        open.setEnabled(false);
        copy.addView(open, new LinearLayout.LayoutParams(-2, -2));
        TextView close = StreamUi.control(this, "Cerrar ficha", R.drawable.uic_x, false, v -> dialog.dismiss());
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
