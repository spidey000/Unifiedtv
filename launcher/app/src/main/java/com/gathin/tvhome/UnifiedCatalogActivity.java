package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
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
    private static final int CONTENT_START = 21;
    private static final int CONTENT_END = 12;
    private static final int CARD_GAP = 7;
    private static final int RAIL_COLLAPSED = 52;
    private boolean series;
    private int accent;

    private final ExecutorService data = Executors.newFixedThreadPool(3);
    private final ExecutorService art = Executors.newFixedThreadPool(2);
    private final ExecutorService providerArt = Executors.newFixedThreadPool(2);
    private final Map<Page, TextView> routes = new LinkedHashMap<>();
    private FrameLayout viewport;
    private ViewTreeObserver.OnGlobalFocusChangeListener railFocus;
    private ValueAnimator railAnimation;
    private ScrollView scroll;
    private LinearLayout pageBody;
    private Page page = Page.HOME;
    private volatile int revision;
    private String settingsSnapshot;
    private String query = "";
    private boolean returning;
    private boolean focusWatchGrid;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UnifiedTvHome.catalogCreated();
        if (state != null) {
            String savedPage = state.getString("catalogPage", Page.HOME.name());
            page = Page.valueOf(savedPage);
            query = state.getString("query", "");
            series = state.getBoolean("series");
        }
        settingsSnapshot = snapshot();
        accent = StreamUi.accent(this);
        inflateShell();
        show(page, false);
        if (page == Page.HOME) findViewById(series ? R.id.seriesTab : R.id.moviesTab).requestFocus();
        else routes.get(page).requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("catalogPage", page.name());
        state.putString("query", query);
        state.putBoolean("series", series);
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
        clearRailListener();
        routes.clear();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(StreamUi.BLACK);
        LinearLayout rail = StreamUi.column(this);
        rail.setBackgroundColor(0xF516161C);
        rail.setPadding(dp(7), dp(15), dp(7), dp(41));
        TextView mark = text(getString(R.string.unifiedtv_name), 17, StreamUi.WHITE, true);
        StreamUi.icon(mark, R.drawable.uic_play);
        mark.getCompoundDrawablesRelative()[0].setTint(StreamUi.visibleAccent(this));
        mark.setPadding(dp(10), 0, 0, dp(36));
        mark.setContentDescription(mark.getText());
        mark.setSingleLine(true);
        rail.addView(mark);
        route(rail, Page.HOME, "Inicio", R.drawable.uic_house);
        route(rail, Page.SEARCH, "Buscar", R.drawable.uic_search);
        route(rail, Page.WATCH, "Mi lista", R.drawable.uic_layers);
        route(rail, Page.APPS, "Apps y fuentes", R.drawable.ic_apps);
        route(rail, Page.LIVE, "TV en directo", R.drawable.ic_livetv);
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView settings = StreamUi.control(this, "Ajustes", R.drawable.ic_settings, false,
                v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)));
        styleRailControl(settings);
        rail.addView(settings, new LinearLayout.LayoutParams(-1, dp(29)));
        viewport = new FrameLayout(this);
        FrameLayout.LayoutParams body = new FrameLayout.LayoutParams(-1, -1);
        body.leftMargin = dp(RAIL_COLLAPSED);
        root.addView(viewport, body);
        root.addView(rail, new FrameLayout.LayoutParams(dp(RAIL_COLLAPSED), -1, Gravity.LEFT));
        List<TextView> labels = new ArrayList<>(routes.values());
        labels.add(mark);
        labels.add(settings);
        for (TextView label : labels) label.setText("");
        setContentView(root);
        railFocus = (previous, current) -> {
            boolean hadFocus = previous != null && previous.getParent() == rail;
            boolean hasFocus = current != null && current.getParent() == rail;
            if (hadFocus == hasFocus) return;
            if (railAnimation != null) railAnimation.cancel();
            for (TextView label : labels) label.setText(hasFocus ? label.getContentDescription() : "");
            railAnimation = ValueAnimator.ofInt(rail.getLayoutParams().width,
                    dp(hasFocus ? railWidth() : RAIL_COLLAPSED));
            railAnimation.setDuration(220);
            railAnimation.addUpdateListener(animation -> {
                android.view.ViewGroup.LayoutParams size = rail.getLayoutParams();
                size.width = (int) animation.getAnimatedValue();
                rail.setLayoutParams(size);
            });
            railAnimation.start();
        };
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(railFocus);
    }

    private void clearRailListener() {
        if (railFocus != null) {
            getWindow().getDecorView().getViewTreeObserver().removeOnGlobalFocusChangeListener(railFocus);
            railFocus = null;
        }
        if (railAnimation != null) {
            railAnimation.cancel();
            railAnimation = null;
        }
    }

    private void route(LinearLayout rail, Page destination, String label, int icon) {
        TextView control = StreamUi.control(this, label, icon, false, v -> show(destination, false));
        styleRailControl(control);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(29));
        lp.bottomMargin = dp(9);
        rail.addView(control, lp);
        routes.put(destination, control);
    }

    private int railWidth() {
        return Math.max(128, Math.min(152, getResources().getConfiguration().screenWidthDp * 12 / 100));
    }

    private void styleRailControl(TextView control) {
        StreamUi.background(control, false, true);
        control.setTextSize(11);
        control.setContentDescription(control.getText());
        control.setPadding(dp(11), 0, dp(4), 0);
        control.setSingleLine(true);
        control.setEllipsize(TextUtils.TruncateAt.END);
        resizeLeadingIcon(control, 16);
        control.setCompoundDrawablePadding(dp(6));
    }

    private void resizeLeadingIcon(TextView control, int size) {
        Drawable icon = control.getCompoundDrawablesRelative()[0];
        icon.setBounds(0, 0, dp(size), dp(size));
        control.setCompoundDrawablesRelative(icon, null, null, null);
    }

    private void show(Page destination, boolean refresh) {
        page = destination;
        if (page != Page.WATCH) focusWatchGrid = false;
        int stamp = ++revision;
        for (Map.Entry<Page, TextView> entry : routes.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == page);
            entry.getValue().setNextFocusRightId(View.NO_ID);
        }
        viewport.removeAllViews();
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        pageBody = StreamUi.column(this);
        pageBody.setClipChildren(false);
        pageBody.setPadding(0, 0, 0, dp(24));
        scroll.addView(pageBody);
        viewport.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        switch (page) {
            case HOME: home(stamp, refresh); break;
            case WATCH: title("Seguir viendo", "Todos los títulos compartidos con Android TV"); watch(stamp); break;
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
        if (!connected) connect();
        if (UnifiedCatalogPrefs.continueEnabled(this) && UnifiedCatalogPrefs.continueOnHome(this)) watch(stamp);
        if (connected) {
            // All five shelves coexist; the featured shelf also drives the hero.
            for (int i = 0; i < FILTERS.length; i++) {
                shelf(stamp, LABELS[i], kind, FILTERS[i], "", refresh, i == 0 ? spotlight : null);
            }
            link("Recargar catálogo", R.drawable.uic_refresh_cw, v -> {
                show(Page.HOME, true);
                findViewById(series ? R.id.seriesTab : R.id.moviesTab).requestFocus();
            });
            note(pageBody, "Datos de TMDB y JustWatch · Más vistos usa la tendencia semanal de TMDB");
        }
    }

    private void addHomeTabs(FrameLayout spotlight) {
        LinearLayout toolbar = StreamUi.column(this);
        toolbar.setGravity(Gravity.CENTER_HORIZONTAL);
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
            tab.setMinimumWidth(dp(116));
            tab.setMinHeight(dp(28));
            tab.setTextSize(12);
            tab.setPadding(dp(14), 0, dp(14), 0);
            StreamUi.background(tab, false, true);
            if (i == 0) leftToRail(tab);
            tabs.addView(tab);
        }
        toolbar.addView(tabs, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        top.setMargins(dp(CONTENT_START), dp(7), dp(CONTENT_END), 0);
        spotlight.addView(toolbar, top);
        routes.get(Page.HOME).setNextFocusRightId(series ? R.id.seriesTab : R.id.moviesTab);
        TextClock clock = new TextClock(this);
        ClockFormat.apply(clock, getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getString(Config.CLOCK_FORMAT_KEY, "system"));
        clock.setTextColor(StreamUi.WHITE);
        clock.setTextSize(12);
        FrameLayout.LayoutParams edge = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        edge.setMargins(0, dp(15), dp(15), 0);
        spotlight.addView(clock, edge);
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
        TextView route = routes.get(page);
        view.setNextFocusLeftId(route.getId());
        if (route.getNextFocusRightId() == View.NO_ID) route.setNextFocusRightId(view.getId());
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
                new int[]{0xF50B0B0F, 0x910B0B0F, 0x020B0B0F}));
        panel.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{StreamUi.BLACK, Color.TRANSPARENT}));
        panel.addView(fade, new FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM));
        LinearLayout copy = StreamUi.column(this);
        copy.setPadding(dp(CONTENT_START + 15), dp(71), dp(24), dp(10));
        copy.setGravity(Gravity.BOTTOM);
        panel.addView(copy, new FrameLayout.LayoutParams(-1, -1));
        TextView kicker = text(badge, 9, StreamUi.readableAccent(this), true);
        kicker.setLetterSpacing(.23f);
        copy.addView(kicker);
        TextView headline = text(heading, 36, StreamUi.WHITE, true);
        headline.setAllCaps(true);
        headline.setLetterSpacing(.09f);
        headline.setIncludeFontPadding(false);
        headline.setMaxLines(2);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        headline.setMaxWidth(dp(330));
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-2, -2);
        titleSize.topMargin = dp(4);
        copy.addView(headline, titleSize);
        LinearLayout facts = new LinearLayout(this);
        facts.setGravity(Gravity.CENTER_VERTICAL);
        TextView score = ratingBadge("");
        score.setVisibility(View.GONE);
        facts.addView(score);
        TextView meta = text("", 11, StreamUi.DIM, false);
        facts.addView(meta);
        LinearLayout.LayoutParams metaSize = new LinearLayout.LayoutParams(-2, -2);
        metaSize.topMargin = dp(5);
        metaSize.bottomMargin = dp(3);
        copy.addView(facts, metaSize);
        TextView description = text(caption, 11, StreamUi.DIM, false);
        description.setMaxWidth(dp(318));
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(description, new LinearLayout.LayoutParams(-2, -2));
        panel.setTag(new Spotlight(backdrop, copy, headline, score, meta, description));
        int height = Math.max(255, Math.min(400, getResources().getConfiguration().screenHeightDp * 41 / 100 + 35));
        pageBody.addView(panel, new LinearLayout.LayoutParams(-1, dp(height)));
        return panel;
    }

    private static final class Spotlight {
        final ImageView backdrop;
        final LinearLayout copy;
        final TextView title, score, meta, caption;
        int index;

        Spotlight(ImageView backdrop, LinearLayout copy, TextView title, TextView score, TextView meta, TextView caption) {
            this.backdrop = backdrop;
            this.copy = copy;
            this.title = title;
            this.score = score;
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
        styleHeroAction(watch);
        actions.addView(watch, new LinearLayout.LayoutParams(-2, dp(28)));
        TextView details = StreamUi.control(this, "Más información", R.drawable.uic_info, false,
                v -> details(result.get(hero.index)));
        styleHeroAction(details);
        LinearLayout.LayoutParams detailSize = new LinearLayout.LayoutParams(-2, dp(28));
        detailSize.setMarginStart(dp(7));
        actions.addView(details, detailSize);

        LinearLayout selection = new LinearLayout(this);
        selection.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        position.setMargins(0, 0, dp(15), dp(12));
        panel.addView(selection, position);

        int count = Math.min(4, result.size());
        List<View> dots = new ArrayList<>(count);
        Runnable update = () -> {
            int index = hero.index;
            UnifiedCatalogApi.Media current = result.get(index);
            hero.title.setText(current.title);
            hero.score.setText("★ " + current.ratingText());
            hero.score.setVisibility(current.hasRating() ? View.VISIBLE : View.GONE);
            hero.meta.setPadding(current.hasRating() ? dp(8) : 0, 0, 0, 0);
            hero.meta.setText(kindAndYear(current));
            hero.caption.setText(current.overview);
            hero.backdrop.animate().cancel();
            hero.backdrop.setAlpha(0f);
            hero.backdrop.setImageDrawable(null);
            watch.setEnabled(true);
            for (int i = 0; i < dots.size(); i++) dots.get(i).setSelected(i == index);
            art.execute(() -> {
                if (revision != stamp) return;
                Bitmap bitmap = current.backdrop.startsWith("/")
                        ? UnifiedCatalogApi.backdrop(this, current.backdrop)
                        : UnifiedCatalogApi.poster(this, current.poster);
                runOnUiThread(() -> {
                    if (live(stamp) && hero.index == index) {
                        hero.backdrop.setImageBitmap(bitmap);
                        if (StreamUi.motionEnabled() && bitmap != null) {
                            hero.backdrop.animate().alpha(1f).setDuration(StreamUi.ART_ANIM_MS).start();
                        } else {
                            hero.backdrop.setAlpha(1f);
                        }
                    }
                });
            });
        };
        for (int i = 0; i < count && count > 1; i++) {
            int target = i;
            FrameLayout dot = new FrameLayout(this);
            dot.setId(View.generateViewId());
            dot.setFocusable(true);
            dot.setContentDescription(getString(R.string.spotlight_position, i + 1, count));
            dot.setForeground(StreamUi.state(this, Color.TRANSPARENT, Color.TRANSPARENT,
                    Color.TRANSPARENT, StreamUi.WHITE));
            View light = new View(this);
            light.setDuplicateParentStateEnabled(true);
            android.graphics.drawable.StateListDrawable fill = new android.graphics.drawable.StateListDrawable();
            fill.addState(new int[]{android.R.attr.state_selected},
                    StreamUi.shape(this, StreamUi.visibleAccent(this), 3, Color.TRANSPARENT));
            fill.addState(new int[]{}, StreamUi.shape(this, 0xFFA3A3AD, 3, Color.TRANSPARENT));
            light.setBackground(fill);
            dot.addView(light, new FrameLayout.LayoutParams(dp(5), dp(5), Gravity.CENTER));
            dot.setOnClickListener(v -> {
                if (hero.index == target) return;
                hero.index = target;
                update.run();
            });
            selection.addView(dot, new LinearLayout.LayoutParams(dp(17), dp(27)));
            dots.add(dot);
        }
        update.run();
    }

    private void styleHeroAction(TextView button) {
        button.setTextSize(12);
        button.setPadding(dp(16), 0, dp(16), 0);
        resizeLeadingIcon(button, 15);
    }

    private void openMedia(UnifiedCatalogApi.Media media, View button, int stamp) {
        if (button != null) button.setEnabled(false);
        data.execute(() -> {
            try { UnifiedCatalogApi.availability(this, media); }
            catch (Exception ignored) { /* Local players work without the catalog network. */ }
            UnifiedCatalogRoute.Target target = UnifiedCatalogRoute.resolve(this, media);
            runOnUiThread(() -> {
                if (!live(stamp) || !hasWindowFocus()) {
                    if (live(stamp) && button != null) button.setEnabled(true);
                    return;
                }
                if (button != null) button.setEnabled(true);
                // Exact provider content deep link, not merely a launcher home intent.
                if (UnifiedCatalogRoute.launchTarget(this, target)) return;
                UnifiedCatalogRoute.open(this, media);
            });
        });
    }

    private String kindAndYear(UnifiedCatalogApi.Media media) {
        return ("tv".equals(media.type) ? "Serie" : "Película")
                + (media.year.isEmpty() ? "" : "  ·  " + media.year);
    }

    private String metadata(UnifiedCatalogApi.Media media) {
        return kindAndYear(media) + (media.hasRating() ? "  ·  ★ " + media.ratingText() : "");
    }

    private LinearLayout row(String label) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(CONTENT_START), dp(3), dp(CONTENT_END), dp(3));
        header.addView(text(label, 16, StreamUi.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (page == Page.HOME && label.equals("Seguir viendo")) {
            TextView more = StreamUi.control(this, "Ver todo", R.drawable.uic_arrow_right, false, v -> {
                focusWatchGrid = true;
                show(Page.WATCH, false);
                routes.get(Page.WATCH).requestFocus();
            });
            StreamUi.background(more, false, true);
            more.setMinHeight(dp(26));
            more.setTextSize(11);
            more.setPadding(dp(8), 0, dp(5), 0);
            resizeLeadingIcon(more, 13);
            Drawable arrow = more.getCompoundDrawablesRelative()[0];
            more.setCompoundDrawablesRelative(null, null, arrow, null);
            header.addView(more);
        }
        pageBody.addView(header);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setFocusable(false);
        horizontal.setClipChildren(false);
        horizontal.setClipToPadding(false);
        LinearLayout line = new LinearLayout(this);
        line.setClipChildren(false);
        line.setPadding(dp(CONTENT_START), dp(9), dp(CONTENT_END), dp(16));
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
        // Keep shelf positions stable while all five TMDB requests resolve independently.
        row.setMinimumHeight(dp(tileHeight() + 25));
        row.addView(text("Cargando…", 15, StreamUi.DIM, false));
        requestList(stamp, kind, category, search, refresh, result -> {
            row.removeAllViews();
            if (result.isEmpty()) { row.addView(text("Sin títulos", 15, StreamUi.DIM, false)); return; }
            if (spotlight != null) bindHero(spotlight, result, stamp);
            List<Runnable> loads = new ArrayList<>();
            loadOnScroll(row, loads, coverWidth() + CARD_GAP);
            int first = spotlight != null && result.size() > 5 ? 1 : 0;
            for (int i = 0; i < result.size() - first; i++) {
                UnifiedCatalogApi.Media media = result.get(i + first);
                FrameLayout card = mediaTile(row, i, () -> openMedia(media, null, stamp));
                card.setOnLongClickListener(v -> { details(media); return true; });
                ImageView cover = tileArt(card);
                ImageView thumbnail = tileArt(card);
                thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout caption = caption(card);
                TextView title = singleLine(media.title, 9, StreamUi.WHITE, true);
                caption.addView(title);
                TextView facts = singleLine(kindAndYear(media), 10, StreamUi.DIM, false);
                caption.addView(facts);
                if (media.hasRating()) rating(card, media.ratingText());
                ImageView providerIcon = providerIcon(card);
                card.setContentDescription(media.title + (media.hasRating() ? ", valoración TMDB " + media.ratingText() : ""));
                loads.add(once(() -> {
                    image(stamp, cover, () -> UnifiedCatalogApi.poster(this, media.poster));
                    loadProviderIcon(stamp, media, card, providerIcon);
                }));
                Runnable loadThumbnail = once(() -> image(stamp, thumbnail, () -> {
                    Bitmap wide = UnifiedCatalogApi.shelfArtwork(this, media.backdrop);
                    return wide != null ? wide : UnifiedCatalogApi.poster(this, media.poster);
                }, () -> { if (card.isFocused()) revealThumbnail(thumbnail); }));
                bindTileFocus(card, loads, i, thumbnail, loadThumbnail, title, facts);
            }
            finishCarousel(row);
        }, () -> {
            row.removeAllViews();
            row.addView(text("No disponible. Revisa tu conexión o la configuración de TMDB.", 15, StreamUi.DIM, false));
        });
    }

    /** Dark-glass provider logo belongs to the service that will open automatically. */
    private ImageView providerIcon(FrameLayout card) {
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(3), dp(3), dp(3), dp(3));
        icon.setBackground(StreamUi.shape(this, 0xE51A1A22, 7, 0x44FFFFFF));
        icon.setClipToOutline(true);
        icon.setVisibility(View.GONE);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams spot = new FrameLayout.LayoutParams(dp(29), dp(29),
                Gravity.TOP | Gravity.START);
        spot.setMargins(dp(6), dp(4), 0, 0);
        card.addView(icon, spot);
        return icon;
    }

    private void loadProviderIcon(int stamp, UnifiedCatalogApi.Media media, FrameLayout card,
                                  ImageView icon) {
        providerArt.execute(() -> {
            if (revision != stamp) return;
            try { UnifiedCatalogApi.availability(this, media); }
            catch (Exception ignored) { return; }
            UnifiedCatalogRoute.Target target = UnifiedCatalogRoute.resolve(this, media);
            if (target == null || revision != stamp) return;
            Bitmap logo = UnifiedCatalogApi.logo(this,
                    UnifiedCatalogApi.logoPath(media, target.provider));
            if (logo == null) return;
            runOnUiThread(() -> {
                if (!live(stamp)) return;
                icon.setImageBitmap(logo);
                icon.setVisibility(View.VISIBLE);
                card.setContentDescription(media.title + " · Se abrirá en "
                        + target.provider.name + (media.hasRating()
                        ? " · ★ " + media.ratingText() : ""));
            });
        });
    }

    /** End space allows even the last card to settle in the stationary focus slot. */
    private void finishCarousel(LinearLayout line) {
        int room = getResources().getConfiguration().screenWidthDp - RAIL_COLLAPSED;
        int focusWidth = Math.round(tileHeight() * 16f / 9f);
        int trailing = Math.max(0, room - CONTENT_START - focusWidth - CONTENT_END);
        line.addView(new View(this), new LinearLayout.LayoutParams(dp(trailing), dp(1)));
    }

    private void pinCarouselFocus(View focused) {
        if (!(focused.getParent() instanceof LinearLayout)) return;
        View candidate = (View) focused.getParent().getParent();
        if (!(candidate instanceof HorizontalScrollView)) return;
        HorizontalScrollView shelf = (HorizontalScrollView) candidate;
        int position = Math.max(0, focused.getLeft() - dp(CONTENT_START));
        shelf.smoothScrollTo(position, 0);
    }

    private int coverWidth() {
        return UnifiedCatalogLayout.posterWidthDp(
                getResources().getConfiguration().screenWidthDp,
                RAIL_COLLAPSED, CONTENT_START, CONTENT_END, CARD_GAP);
    }

    private int tileHeight() {
        return UnifiedCatalogLayout.posterHeightDp(coverWidth());
    }

    private FrameLayout mediaCard(Runnable click) {
        FrameLayout card = new FrameLayout(this);
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setOnClickListener(v -> click.run());
        card.setBackground(StreamUi.panel(this));
        card.setClipToOutline(true);
        card.setForeground(StreamUi.state(this, Color.TRANSPARENT, Color.TRANSPARENT,
                Color.TRANSPARENT, StreamUi.WHITE));
        return card;
    }

    private FrameLayout mediaTile(LinearLayout row, int index, Runnable click) {
        FrameLayout card = mediaCard(click);
        if (index == 0) leftToRail(card);
        LinearLayout.LayoutParams tile = new LinearLayout.LayoutParams(dp(coverWidth()), dp(tileHeight()));
        tile.setMarginEnd(dp(CARD_GAP));
        row.addView(card, tile);
        return card;
    }

    /** Six fixed columns: only the parent ScrollView scrolls, never a horizontal carousel. */
    private GridLayout watchGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(UnifiedCatalogLayout.COLUMNS);
        grid.setOrientation(GridLayout.HORIZONTAL);
        grid.setPadding(dp(CONTENT_START), dp(4), dp(CONTENT_END), dp(12));
        grid.setClipChildren(false);
        pageBody.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        return grid;
    }

    private FrameLayout gridTile(GridLayout grid, int index, Runnable click) {
        FrameLayout card = mediaCard(click);
        int column = UnifiedCatalogLayout.column(index);
        GridLayout.LayoutParams tile = new GridLayout.LayoutParams(
                GridLayout.spec(UnifiedCatalogLayout.row(index)), GridLayout.spec(column));
        tile.width = dp(coverWidth());
        tile.height = dp(tileHeight());
        tile.setMargins(0, 0, dp(column == UnifiedCatalogLayout.COLUMNS - 1 ? 0 : CARD_GAP),
                dp(UnifiedCatalogLayout.GRID_ROW_GAP_DP));
        grid.addView(card, tile);
        if (column == 0) leftToRail(card);
        return card;
    }

    private ImageView tileArt(FrameLayout card) {
        ImageView artwork = new ImageView(this);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        return artwork;
    }

    private LinearLayout caption(FrameLayout card) {
        LinearLayout copy = StreamUi.column(this);
        copy.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000B0B0F, 0xBD09090B, 0xF2121217}));
        copy.setPadding(dp(7), dp(12), dp(7), dp(7));
        card.addView(copy, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return copy;
    }

    private TextView ratingBadge(String label) {
        TextView badge = text(label, 8, StreamUi.RATING, true);
        badge.setPadding(dp(5), dp(2), dp(5), dp(2));
        badge.setBackground(StreamUi.shape(this, 0xEE121217, 5, Color.TRANSPARENT));
        return badge;
    }

    private void rating(FrameLayout card, String value) {
        TextView badge = ratingBadge("★ " + value);
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

    private void revealThumbnail(ImageView thumbnail) {
        thumbnail.animate().cancel();
        thumbnail.setVisibility(View.VISIBLE);
        if (!StreamUi.motionEnabled()) {
            thumbnail.setAlpha(1f);
            return;
        }
        thumbnail.setAlpha(0f);
        thumbnail.animate().alpha(1f).setDuration(StreamUi.ART_ANIM_MS).start();
    }

    private void bindTileFocus(FrameLayout card, List<Runnable> loads, int index,
                               ImageView thumbnail, Runnable loadThumbnail, TextView title, View focusedInfo) {
        int available = getResources().getConfiguration().screenWidthDp - RAIL_COLLAPSED - CONTENT_START - CONTENT_END;
        if (index < available / (coverWidth() + CARD_GAP) + 2) loads.get(index).run();
        focusedInfo.setVisibility(View.GONE);
        if (thumbnail != null) {
            thumbnail.setAlpha(0f);
            thumbnail.setVisibility(View.INVISIBLE);
        }
        final ValueAnimator[] resize = {null};
        card.setOnFocusChangeListener((view, focused) -> {
            if (resize[0] != null) resize[0].cancel();
            int normalWidth = dp(coverWidth());
            int focusedWidth = dp(Math.round(tileHeight() * 16f / 9f));
            int startWidth = ((LinearLayout.LayoutParams) view.getLayoutParams()).width;
            int targetWidth = focused ? focusedWidth : normalWidth;
            focusedInfo.animate().cancel();
            if (focused) {
                focusedInfo.setAlpha(StreamUi.motionEnabled() ? 0f : 1f);
                focusedInfo.setVisibility(View.VISIBLE);
                if (StreamUi.motionEnabled()) {
                    focusedInfo.animate().alpha(1f).setDuration(StreamUi.ART_ANIM_MS).start();
                }
            } else {
                focusedInfo.setVisibility(View.GONE);
            }
            card.animate().cancel();
            if (StreamUi.motionEnabled()) {
                card.animate().scaleY(focused ? 1.018f : 1f)
                        .translationZ(focused ? dp(3) : 0)
                        .setDuration(StreamUi.FOCUS_ANIM_MS).start();
                resize[0] = ValueAnimator.ofInt(startWidth, targetWidth);
                resize[0].setDuration(StreamUi.FOCUS_ANIM_MS);
                resize[0].setInterpolator(new DecelerateInterpolator());
                resize[0].addUpdateListener(anim -> {
                    int width = (int) anim.getAnimatedValue();
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
                    params.width = width;
                    view.setLayoutParams(params);
                    float progress = Math.max(0f, Math.min(1f,
                            (width - normalWidth) / (float) (focusedWidth - normalWidth)));
                    title.setTextSize(9f + 3f * progress);
                });
                resize[0].addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (view.isFocused()) pinCarouselFocus(view);
                    }
                });
                resize[0].start();
            } else {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
                params.width = targetWidth;
                view.setLayoutParams(params);
                title.setTextSize(focused ? 12 : 9);
                card.setScaleY(1f);
                card.setTranslationZ(0f);
                if (focused) view.post(() -> { if (view.isFocused()) pinCarouselFocus(view); });
            }
            if (thumbnail != null) {
                thumbnail.animate().cancel();
                if (focused) {
                    if (thumbnail.getDrawable() != null) revealThumbnail(thumbnail);
                    else loadThumbnail.run();
                } else {
                    thumbnail.setAlpha(0f);
                    thumbnail.setVisibility(View.INVISIBLE);
                }
            }
            if (!focused) return;
            view.post(() -> { if (view.isFocused()) pinCarouselFocus(view); });
            for (int i = Math.max(0, index - 1); i < Math.min(index + 4, loads.size()); i++) loads.get(i).run();
        });
    }

    private void loadOnScroll(LinearLayout row, List<Runnable> loads, int stride) {
        View scroll = (View) row.getParent();
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            int first = Math.max(0, (x - dp(CONTENT_START)) / dp(stride));
            int end = Math.min(loads.size(), first + scroll.getWidth() / dp(stride) + 3);
            for (int i = first; i < end; i++) loads.get(i).run();
        });
    }

    /** Fetch only artwork in and near the viewport, including when scrolling by D-pad. */
    private void loadGridOnScroll(GridLayout grid, List<Runnable> loads) {
        Runnable preload = () -> {
            if (grid.getParent() == null) return;
            int stride = dp(tileHeight() + UnifiedCatalogLayout.GRID_ROW_GAP_DP);
            int top = scroll.getScrollY() - grid.getTop();
            int firstRow = Math.max(0, top / stride - 1);
            int lastRow = Math.max(firstRow,
                    (top + scroll.getHeight()) / stride + 1);
            int end = Math.min(loads.size(), (lastRow + 1) * UnifiedCatalogLayout.COLUMNS);
            for (int i = firstRow * UnifiedCatalogLayout.COLUMNS; i < end; i++) loads.get(i).run();
        };
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> preload.run());
        grid.post(preload);
    }

    private void connectGridFocus(List<FrameLayout> cards, List<Runnable> loads) {
        for (int i = 0; i < cards.size(); i++) {
            FrameLayout card = cards.get(i);
            int column = UnifiedCatalogLayout.column(i);
            if (column > 0) card.setNextFocusLeftId(cards.get(i - 1).getId());
            if (column < UnifiedCatalogLayout.COLUMNS - 1 && i + 1 < cards.size())
                card.setNextFocusRightId(cards.get(i + 1).getId());
            if (i >= UnifiedCatalogLayout.COLUMNS)
                card.setNextFocusUpId(cards.get(i - UnifiedCatalogLayout.COLUMNS).getId());
            if (i + UnifiedCatalogLayout.COLUMNS < cards.size())
                card.setNextFocusDownId(cards.get(i + UnifiedCatalogLayout.COLUMNS).getId());
            final int index = i;
            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                if (StreamUi.motionEnabled()) {
                    view.animate().scaleX(focused ? 1.015f : 1f).scaleY(focused ? 1.015f : 1f)
                            .translationZ(focused ? dp(3) : 0)
                            .setDuration(StreamUi.FOCUS_ANIM_MS).start();
                } else {
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    view.setTranslationZ(focused ? dp(3) : 0);
                }
                if (focused) {
                    loads.get(index).run();
                    int end = Math.min(loads.size(), index + UnifiedCatalogLayout.COLUMNS + 1);
                    for (int next = index + 1; next < end; next++) loads.get(next).run();
                }
            });
        }
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
        image(stamp, target, loader, null);
    }

    private void image(int stamp, ImageView target, Artwork loader, Runnable onLoaded) {
        art.execute(() -> {
            if (revision != stamp) return;
            Bitmap value = loader.load();
            if (value != null) runOnUiThread(() -> {
                if (live(stamp)) {
                    target.setImageBitmap(value);
                    if (onLoaded != null) {
                        onLoaded.run(); // Focused 16:9 thumbnails have their own reveal.
                    } else if (StreamUi.motionEnabled() && target.isAttachedToWindow()) {
                        target.animate().cancel();
                        target.setAlpha(0f);
                        target.animate().alpha(1f).setDuration(StreamUi.ART_ANIM_MS).start();
                    } else {
                        target.setAlpha(1f);
                    }
                }
            });
        });
    }

    /** Show the platform artwork immediately; TMDB never blocks first paint. */
    private void watchImage(int stamp, UnifiedWatchNext.Entry item, FrameLayout card,
                            ImageView poster, ImageView thumbnail) {
        art.execute(() -> {
            if (revision != stamp) return;
            UnifiedWatchArtwork.Pair platform = UnifiedWatchArtwork.platform(getApplicationContext(), item);
            runOnUiThread(() -> showWatchImage(stamp, card, poster, thumbnail, platform));
            if (revision != stamp) return;
            UnifiedWatchArtwork.Pair enhanced = UnifiedWatchArtwork.withTmdb(
                    getApplicationContext(), item, platform);
            if (enhanced != platform) {
                runOnUiThread(() -> showWatchImage(stamp, card, poster, thumbnail, enhanced));
            }
        });
    }

    private void showWatchImage(int stamp, FrameLayout card,
                                ImageView poster, ImageView thumbnail,
                                UnifiedWatchArtwork.Pair pair) {
        if (!live(stamp)) return;
        if (pair.portrait != null && !showingBitmap(poster, pair.portrait)) {
            poster.setImageBitmap(pair.portrait);
            if (StreamUi.motionEnabled() && poster.isAttachedToWindow()) {
                poster.animate().cancel();
                poster.setAlpha(0f);
                poster.animate().alpha(1f).setDuration(StreamUi.ART_ANIM_MS).start();
            } else {
                poster.setAlpha(1f);
            }
        }
        if (thumbnail != null && pair.landscape != null) {
            if (!showingBitmap(thumbnail, pair.landscape)) {
                thumbnail.setImageBitmap(pair.landscape);
                if (card.isFocused()) revealThumbnail(thumbnail);
            }
        }
    }

    private static boolean showingBitmap(ImageView image, Bitmap bitmap) {
        Drawable current = image.getDrawable();
        return current instanceof android.graphics.drawable.BitmapDrawable
                && ((android.graphics.drawable.BitmapDrawable) current).getBitmap() == bitmap;
    }

    private void watch(int stamp) {
        if (!UnifiedCatalogPrefs.continueEnabled(this)) {
            link("Activar Seguir viendo en Ajustes", v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)));
            return;
        }
        boolean fullGrid = page == Page.WATCH;
        LinearLayout line = fullGrid ? null : row("Seguir viendo");
        GridLayout grid = fullGrid ? watchGrid() : null;
        if (line != null) line.setMinimumHeight(dp(tileHeight() + 25));
        if (!UnifiedWatchNext.hasPermission(this)) {
            TextView allow = StreamUi.control(this, "Permitir acceso a Watch Next", true,
                    v -> requestPermissions(new String[]{UnifiedWatchNext.PERMISSION}, UnifiedWatchNext.PERMISSION_REQUEST));
            leftToRail(allow);
            if (fullGrid) {
                pageBody.addView(allow);
                if (focusWatchGrid) allow.requestFocus();
            } else {
                line.addView(allow);
            }
            return;
        }
        TextView status = text("Cargando…", 15, StreamUi.DIM, false);
        if (fullGrid) pageBody.addView(status);
        else line.addView(status);
        data.execute(() -> {
            if (revision != stamp) return;
            List<UnifiedWatchNext.Entry> items = UnifiedWatchNext.load(this);
            runOnUiThread(() -> {
                if (!live(stamp)) return;
                if (fullGrid) pageBody.removeView(status);
                else line.removeAllViews();
                if (items.isEmpty()) {
                    if (fullGrid) note(pageBody, "Ningún título disponible cumple los ajustes de Seguir viendo.");
                    else line.addView(text("Ningún título disponible cumple los ajustes de Seguir viendo.", 15, StreamUi.DIM, false));
                    return;
                }
                List<Runnable> loads = new ArrayList<>();
                List<FrameLayout> cards = new ArrayList<>();
                int count = fullGrid ? items.size() : Math.min(24, items.size());
                if (!fullGrid) loadOnScroll(line, loads, coverWidth() + CARD_GAP);
                for (int i = 0; i < count; i++) {
                    UnifiedWatchNext.Entry item = items.get(i);
                    FrameLayout card = fullGrid
                            ? gridTile(grid, i, () -> UnifiedWatchNext.open(this, item))
                            : mediaTile(line, i, () -> UnifiedWatchNext.open(this, item));
                    cards.add(card);
                    ImageView artwork = tileArt(card);
                    ImageView thumbnail = fullGrid ? null : tileArt(card);
                    if (thumbnail != null) {
                        // The landscape artwork is shown completely, without CENTER_CROP.
                        thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    }
                    LinearLayout copy = caption(card);
                    TextView title = singleLine(item.title, 9, StreamUi.WHITE, true);
                    copy.addView(title);
                    copy.addView(singleLine(item.source, 9, StreamUi.WHITE, true));
                    card.setContentDescription(item.title + " · " + item.source
                            + (item.progress() < 0 ? "" : " · " + item.progress() + "%"));
                    TextView episode = singleLine(item.episode, 9, StreamUi.DIM, false);
                    if (item.progress() >= 0) {
                        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                        progress.setMax(100);
                        progress.setProgress(item.progress());
                        progress.setProgressTintList(ColorStateList.valueOf(StreamUi.visibleAccent(this)));
                        progress.setProgressBackgroundTintList(ColorStateList.valueOf(StreamUi.BORDER));
                        LinearLayout meter = new LinearLayout(this);
                        meter.setGravity(Gravity.CENTER_VERTICAL);
                        if (!item.episode.isEmpty()) {
                            episode.setPadding(dp(2), 0, dp(7), 0);
                            meter.addView(episode);
                        }
                        meter.addView(progress, new LinearLayout.LayoutParams(0, dp(4), 1));
                        TextView percent = text(item.progress() + "%", 9, StreamUi.DIM, false);
                        percent.setPadding(dp(6), 0, 0, 0);
                        meter.addView(percent);
                        copy.addView(meter);
                    } else if (!item.episode.isEmpty()) {
                        copy.addView(singleLine(item.episode, 9, StreamUi.DIM, false));
                    }
                    Runnable loadArtwork = once(() -> watchImage(stamp, item, card, artwork, thumbnail));
                    loads.add(loadArtwork);
                    if (!fullGrid) bindTileFocus(card, loads, i, thumbnail, loadArtwork, title, episode);
                }
                if (!fullGrid) finishCarousel(line);
                if (fullGrid) {
                    connectGridFocus(cards, loads);
                    loadGridOnScroll(grid, loads);
                    if (focusWatchGrid && !cards.isEmpty()) {
                        focusWatchGrid = false;
                        cards.get(0).requestFocus();
                    }
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
        platforms.setMaxLines(4);
        platforms.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(platforms);
        TextView open = StreamUi.control(this, "Ver ahora", R.drawable.uic_play, true, v -> {
            dialog.dismiss();
            openMedia(media, null, revision);
        });
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
                List<String> players = UnifiedCatalogRoute.localAppNames(this);
                String tmdb = !loaded ? "Disponibilidad TMDB no consultada"
                        : names.isEmpty() ? "Sin servicios disponibles según TMDB"
                        : "Según TMDB: " + TextUtils.join(" · ", names);
                platforms.setText(tmdb + (players.isEmpty() ? ""
                        : "\nApps de reproducción: " + TextUtils.join(" · ", players)));
                open.setText("Ver ahora");
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
        clearRailListener();
        revision++;
        data.shutdownNow();
        art.shutdownNow();
        super.onDestroy();
    }
}
