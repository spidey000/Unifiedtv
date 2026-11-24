package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** UnifiedTV main interface. Classic launcher components are not exposed. */
public final class UnifiedCatalogActivity extends Activity {
    private enum Page { HOME, MOVIES, SERIES, WATCH, SEARCH, APPS, LIVE }
    private static final String[] FILTERS = {"featured", "watched", "new", "popular", "rated"};
    private static final String[] LABELS = {"Destacados", "Más vistos", "Novedades", "Populares", "Mejor valorados"};
    private static final int CONTENT_START = 21;
    private static final int CONTENT_END = 12;
    private static final int CARD_GAP = 7;
    private static final int RAIL_COLLAPSED = StreamUi.RAIL_DP;
    private int accent;

    private final ExecutorService data = Executors.newFixedThreadPool(3);
    private final ExecutorService art = Executors.newFixedThreadPool(2);
    private final ExecutorService watchMeta = Executors.newFixedThreadPool(2);
    private final ExecutorService providerArt = Executors.newFixedThreadPool(2);
    private final Map<Page, TextView> routes = new LinkedHashMap<>();
    private FrameLayout viewport;
    private TvSpatialNavigation spatial;
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
    private String returnFocusKey, pendingFocusKey;
    private int returnScrollY;

    private void restoreContentFocus() {
        if (pendingFocusKey == null) return;
        View target = pageBody.findViewWithTag(pendingFocusKey);
        if (target == null) return; // Async shelf may not have arrived yet.
        pendingFocusKey = null;
        target.post(() -> {
            if (!target.isAttachedToWindow()) return;
            scroll.scrollTo(0, returnScrollY);
            target.requestFocus();
            revealContentFocus(target);
        });
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UnifiedTvHome.catalogCreated();
        if (state != null) {
            String savedPage = state.getString("catalogPage", Page.HOME.name());
            try { page = Page.valueOf(savedPage); }
            catch (IllegalArgumentException ignored) { page = Page.HOME; }
            if (page == Page.HOME && state.getBoolean("series", false))
                page = Page.SERIES; // Migrate the former top tab.
            query = state.getString("query", "");
            pendingFocusKey = state.getString("focusKey");
            returnScrollY = state.getInt("scrollY", 0);
        }
        if (state == null) openRequestedPage(getIntent());
        settingsSnapshot = snapshot();
        accent = StreamUi.accent(this);
        inflateShell();
        show(page, false);
        routes.get(page).requestFocus();
    }

    static final String EXTRA_PAGE = "unifiedtv_page";

    private void openRequestedPage(Intent request) {
        if (request == null) return;
        String requested = request.getStringExtra(EXTRA_PAGE);
        if (requested == null) return;
        try { page = Page.valueOf(requested); }
        catch (IllegalArgumentException ignored) { page = Page.HOME; }
    }

    @Override protected void onNewIntent(Intent request) {
        super.onNewIntent(request);
        setIntent(request);
        if (request != null && request.hasExtra(EXTRA_PAGE)) {
            openRequestedPage(request);
            show(page, false);
            TextView selected = routes.get(page);
            if (selected != null) selected.requestFocus();
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() >= KeyEvent.KEYCODE_DPAD_UP
                && event.getKeyCode() <= KeyEvent.KEYCODE_DPAD_CENTER) pendingFocusKey = null;
        return (spatial != null && spatial.onKey(getCurrentFocus(), event))
                || super.dispatchKeyEvent(event);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("catalogPage", page.name());
        state.putString("query", query);
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String)
            state.putString("focusKey", (String) focused.getTag());
        state.putInt("scrollY", scroll == null ? 0 : scroll.getScrollY());
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
            pendingFocusKey = returnFocusKey;
            show(page, false);
            if (paletteChanged) routes.get(page).requestFocus();
        }
        returning = false;
    }

    @Override protected void onStop() {
        returning = true;
        View focused = getCurrentFocus();
        returnFocusKey = focused != null && focused.getTag() instanceof String
                ? (String) focused.getTag() : null;
        returnScrollY = scroll == null ? 0 : scroll.getScrollY();
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
        root.setPadding(dp(StreamUi.SAFE_HORIZONTAL_DP), dp(StreamUi.SAFE_VERTICAL_DP),
                dp(StreamUi.SAFE_HORIZONTAL_DP), dp(StreamUi.SAFE_VERTICAL_DP));
        LinearLayout rail = StreamUi.column(this);
        rail.setBackgroundColor(0xF516161C);
        rail.setPadding(dp(7), dp(10), dp(7), dp(10));
        TextView mark = text(getString(R.string.unifiedtv_name), 17, StreamUi.WHITE, true);
        StreamUi.icon(mark, R.drawable.uic_play);
        mark.getCompoundDrawablesRelative()[0].setTint(StreamUi.visibleAccent(this));
        mark.setPadding(dp(10), 0, 0, dp(20));
        mark.setContentDescription(mark.getText());
        mark.setSingleLine(true);
        rail.addView(mark);
        route(rail, Page.HOME, "Inicio", R.drawable.uic_house);
        route(rail, Page.MOVIES, "Películas", R.drawable.uic_film);
        route(rail, Page.SERIES, "Series", R.drawable.uic_layers);
        route(rail, Page.SEARCH, "Buscar", R.drawable.uic_search);
        route(rail, Page.WATCH, "Seguir viendo", R.drawable.uic_layers);
        route(rail, Page.APPS, "Apps y fuentes", R.drawable.ic_apps);
        route(rail, Page.LIVE, "Entradas de TV", R.drawable.ic_livetv);
        rail.addView(new View(this), new LinearLayout.LayoutParams(1, dp(12)));
        TextView settings = StreamUi.control(this, "Ajustes", R.drawable.ic_settings, false,
                v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)));
        styleRailControl(settings);
        rail.addView(settings, new LinearLayout.LayoutParams(-1, dp(33)));
        viewport = new FrameLayout(this);
        viewport.setClipChildren(false);
        viewport.setClipToPadding(false);
        FrameLayout.LayoutParams body = new FrameLayout.LayoutParams(-1, -1);
        body.leftMargin = dp(RAIL_COLLAPSED);
        root.addView(viewport, body);
        root.addView(rail, new FrameLayout.LayoutParams(dp(RAIL_COLLAPSED), -1, Gravity.LEFT));
        List<TextView> labels = new ArrayList<>(routes.values());
        labels.add(mark);
        labels.add(settings);
        for (TextView label : labels) label.setText("");
        setContentView(root);
        spatial = new TvSpatialNavigation(rail, viewport);
        spatial.setPreferredRail(routes.get(page));
        railFocus = (previous, current) -> {
            spatial.remember(current);
            if (TvSpatialNavigation.inside(scroll, current)) revealContentFocus(current);
            boolean hadFocus = previous != null && previous.getParent() == rail;
            boolean hasFocus = current != null && current.getParent() == rail;
            if (hadFocus == hasFocus) return;
            if (railAnimation != null) railAnimation.cancel();
            for (TextView label : labels) label.setText(hasFocus ? label.getContentDescription() : "");
            int target = dp(hasFocus ? railWidth() : RAIL_COLLAPSED);
            if (!StreamUi.motionEnabled()) {
                resizeNavigation(rail, target);
                return;
            }
            railAnimation = ValueAnimator.ofInt(rail.getLayoutParams().width, target);
            railAnimation.setDuration(StreamUi.FOCUS_ANIM_MS);
            railAnimation.addUpdateListener(animation ->
                    resizeNavigation(rail, (int) animation.getAnimatedValue()));
            railAnimation.start();
        };
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(railFocus);
    }

    /**
     * Focus can grow 2.5% beyond its poster bounds. Scroll the outer page by
     * enough to expose that outline, without changing the horizontal focus slot
     * managed by pinCarouselFocus().
     */
    private void revealContentFocus(View focused) {
        focused.post(() -> {
            if (!focused.isFocused() || scroll == null || scroll.getHeight() == 0
                    || !TvSpatialNavigation.inside(scroll, focused)) return;
            int extra = dp(5);
            android.graphics.Rect bounds = new android.graphics.Rect(
                    -extra, -extra, focused.getWidth() + extra,
                    focused.getHeight() + extra);
            scroll.offsetDescendantRectToMyCoords(focused, bounds);
            // Android already returns content coordinates; adding scrollY would double-count it.
            int delta = TvFocusViewport.scrollDelta(bounds.top, bounds.bottom,
                    scroll.getScrollY(), scroll.getHeight(), dp(14));
            if (delta != 0) scroll.smoothScrollBy(0, delta);
        });
    }

    /** Expand over the catalog so poster widths and focus positions stay fixed. */
    private void resizeNavigation(LinearLayout rail, int width) {
        android.view.ViewGroup.LayoutParams size = rail.getLayoutParams();
        if (size.width != width) {
            size.width = width;
            rail.setLayoutParams(size);
        }

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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(33));
        lp.bottomMargin = dp(7);
        rail.addView(control, lp);
        routes.put(destination, control);
    }

    private int railWidth() {
        return Math.max(136, Math.min(156,
                getResources().getConfiguration().screenWidthDp * 15 / 100));
    }

    private void styleRailControl(TextView control) {
        StreamUi.navigation(control);
        control.setTextSize(12);
        control.setMinHeight(dp(32));
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
        if (spatial != null) spatial.setPreferredRail(routes.get(page));
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
        scroll.setClipChildren(false);
        pageBody = StreamUi.column(this);
        pageBody.setClipChildren(false);
        pageBody.setClipToPadding(false);
        pageBody.setPadding(0, 0, 0, dp(40));
        scroll.addView(pageBody);
        viewport.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        switch (page) {
            case HOME: case MOVIES: case SERIES: home(stamp, refresh); break;
            case WATCH: title("Seguir viendo", "Todos los títulos compartidos con Android TV"); watch(stamp); break;
            case LIVE:
                title("Entradas de TV", "Sintonizador y fuentes conectadas al televisor");
                link("Abrir entradas y fuentes", R.drawable.ic_input, v -> UnifiedTvHome.openInputs(this));
                break;
            case SEARCH: searchPage(stamp); break;
            case APPS: apps(stamp); break;
        }
    }

    private boolean live(int stamp) { return stamp == revision && !isFinishing(); }

    private void home(int stamp, boolean refresh) {
        boolean connected = hasKey();
        String kind = page == Page.SERIES ? "tv" : "movie";
        FrameLayout spotlight = hero("DESTACADO", connected ? "Descubrir" : "Tu cine empieza aquí",
                connected ? "" : "Conecta TMDB para descubrir películas y series.");
        if (!connected) connect();
        if (page == Page.HOME && UnifiedCatalogPrefs.continueEnabled(this)
                && UnifiedCatalogPrefs.continueOnHome(this)) watch(stamp);
        if (connected) {
            if (UnifiedCatalogPrefs.twoColumnSections(this)) {
                columnCatalog(stamp, kind, refresh, spotlight);
            } else {
                // Default: all five horizontally scrolling shelves coexist.
                for (int i = 0; i < FILTERS.length; i++) {
                    shelf(stamp, LABELS[i], kind, FILTERS[i], "", refresh,
                            i == 0 ? spotlight : null);
                }
            }
            link("Recargar catálogo", R.drawable.uic_refresh_cw, v -> {
                Page current = page;
                show(current, true);
                routes.get(current).requestFocus();
            });
            note(pageBody, "Datos de TMDB y JustWatch · Más vistos usa la tendencia semanal de TMDB");
        }
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
        copy.setPadding(dp(CONTENT_START), dp(12), dp(24), dp(14));
        copy.setGravity(Gravity.BOTTOM);
        panel.addView(copy, new FrameLayout.LayoutParams(-1, -1));
        TextView kicker = text(badge, 9, StreamUi.readableAccent(this), true);
        kicker.setLetterSpacing(.23f);
        copy.addView(kicker);
        TextView headline = text(heading, 28, StreamUi.WHITE, true);
        headline.setAllCaps(false);
        headline.setLetterSpacing(-.02f);
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
        int height = UnifiedCatalogLayout.heroHeightDp(
                getResources().getConfiguration().screenHeightDp - 2 * StreamUi.SAFE_VERTICAL_DP,
                getResources().getConfiguration().fontScale);
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
        watch.setTag("hero:watch");
        leftToRail(watch);
        styleHeroAction(watch);
        StreamUi.heroPrimary(watch);
        actions.addView(watch, new LinearLayout.LayoutParams(-2, dp(36)));
        TextView details = StreamUi.control(this, "Más información", R.drawable.uic_info, false,
                v -> details(result.get(hero.index)));
        details.setTag("hero:details");
        styleHeroAction(details);
        LinearLayout.LayoutParams detailSize = new LinearLayout.LayoutParams(-2, dp(36));
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
            boolean showRating = UnifiedCatalogPrefs.showRatings(this) && current.hasRating();
            hero.score.setVisibility(showRating ? View.VISIBLE : View.GONE);
            hero.meta.setPadding(showRating ? dp(8) : 0, 0, 0, 0);
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
        restoreContentFocus();
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
        return kindAndYear(media) + (UnifiedCatalogPrefs.showRatings(this) && media.hasRating()
                ? "  ·  ★ " + media.ratingText() : "");
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
        line.setClipToPadding(false);
        // The carousel allocates space above and below each fixed poster cell.
        line.setPadding(dp(CONTENT_START), dp(14), dp(CONTENT_END), dp(18));
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
        row.setMinimumHeight(dp(tileHeight() + 32));
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
                card.setTag("media:" + category + ":" + media.type + ":" + media.id);
                card.setOnLongClickListener(v -> { details(media); return true; });
                ImageView cover = tileArt(card);
                LinearLayout caption = caption(card);
                TextView title = posterTitle(media.title);
                caption.addView(title);
                TextView facts = singleLine(kindAndYear(media), 10, StreamUi.DIM, false);
                caption.addView(facts);
                if (UnifiedCatalogPrefs.showRatings(this) && media.hasRating()) rating(card, media.ratingText());
                ImageView providerIcon = providerIcon(card);
                card.setContentDescription(media.title + (UnifiedCatalogPrefs.showRatings(this) && media.hasRating() ? ", valoración TMDB " + media.ratingText() : ""));
                loads.add(once(() -> {
                    image(stamp, cover, () -> UnifiedCatalogApi.poster(this, media.poster));
                    loadProviderIcon(stamp, media, card, providerIcon);
                }));
                bindTileFocus(card, loads, i, facts);
            }
            finishCarousel(row);
            restoreContentFocus();
        }, () -> {
            row.removeAllViews();
            row.addView(text("No disponible. Revisa tu conexión o la configuración de TMDB.", 15, StreamUi.DIM, false));
        });
    }

    /**
     * Optional catalogue mode: category groups become horizontal columns.
     * Every group has two inner poster columns (2:3 cards); the whole page
     * scrolls vertically and categories scroll sideways. No 16:9 previews.
     */
    private void columnCatalog(int stamp, String kind, boolean refresh, FrameLayout spotlight) {
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        horizontal.setClipChildren(false);
        horizontal.setClipToPadding(false);
        horizontal.setFocusable(false);
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.HORIZONTAL);
        categories.setClipChildren(false);
        categories.setClipToPadding(false);
        categories.setPadding(dp(CONTENT_START), dp(9), dp(CONTENT_END), dp(18));
        horizontal.addView(categories);
        pageBody.addView(horizontal, new LinearLayout.LayoutParams(-1, -2));

        final int slotWidth = Math.max(96, Math.min(145, coverWidth()));
        final int sectionWidth = 2 * (slotWidth + CARD_GAP) + 14;
        List<FrameLayout> cards = new ArrayList<>();
        List<Runnable> loaders = new ArrayList<>();
        Runnable preload = () -> {
            for (int i = 0; i < cards.size(); i++) {
                android.graphics.Rect visible = new android.graphics.Rect();
                if (cards.get(i).getGlobalVisibleRect(visible)) loaders.get(i).run();
            }
        };
        horizontal.setOnScrollChangeListener((v, x, y, oldX, oldY) -> preload.run());
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> preload.run());

        for (int categoryIndex = 0; categoryIndex < FILTERS.length; categoryIndex++) {
            final int category = categoryIndex;
            LinearLayout section = StreamUi.column(this);
            section.setClipChildren(false);
            section.setClipToPadding(false);
            section.setPadding(dp(5), 0, dp(5), dp(6));
            LinearLayout.LayoutParams sectionParams =
                    new LinearLayout.LayoutParams(dp(sectionWidth), -2);
            sectionParams.setMarginEnd(dp(CARD_GAP));
            categories.addView(section, sectionParams);
            TextView heading = text(LABELS[category], 16, StreamUi.WHITE, true);
            heading.setPadding(dp(6), dp(4), 0, dp(11));
            section.addView(heading);

            GridLayout posters = new GridLayout(this);
            posters.setColumnCount(2);
            posters.setOrientation(GridLayout.HORIZONTAL);
            posters.setClipChildren(false);
            posters.setClipToPadding(false);
            section.addView(posters);
            TextView status = text("Cargando…", 12, StreamUi.DIM, false);
            posters.addView(status);
            requestList(stamp, kind, FILTERS[category], "", refresh, result -> {
                posters.removeAllViews();
                if (result.isEmpty()) {
                    posters.addView(text("Sin títulos", 12, StreamUi.DIM, false));
                    return;
                }
                if (category == 0 && spotlight != null) bindHero(spotlight, result, stamp);
                for (int i = 0; i < result.size(); i++) {
                    UnifiedCatalogApi.Media media = result.get(i);
                    FrameLayout card = mediaCard(() -> openMedia(media, null, stamp));
                    card.setTag("media:" + category + ":" + media.type + ":" + media.id);
                    card.setOnLongClickListener(v -> { details(media); return true; });
                    if (i == 0) leftToRail(card);
                    GridLayout.LayoutParams layout = new GridLayout.LayoutParams(
                            GridLayout.spec(i / 2), GridLayout.spec(i % 2));
                    layout.width = dp(UnifiedCatalogLayout.cardWidthDp(slotWidth));
                    layout.height = dp(UnifiedCatalogLayout.cardHeightDp(slotWidth));
                    layout.setMargins(dp(UnifiedCatalogLayout.FOCUS_INSET_X_DP),
                            dp(UnifiedCatalogLayout.FOCUS_INSET_TOP_DP),
                            dp(UnifiedCatalogLayout.FOCUS_INSET_X_DP + CARD_GAP),
                            dp(UnifiedCatalogLayout.focusBottomInsetDp(slotWidth) + 8));
                    posters.addView(card, layout);
                    ImageView cover = tileArt(card);
                    LinearLayout caption = caption(card);
                    TextView title = posterTitle(media.title);
                    caption.addView(title);
                    caption.addView(singleLine(kindAndYear(media), 9, StreamUi.DIM, false));
                    if (UnifiedCatalogPrefs.showRatings(this) && media.hasRating()) rating(card, media.ratingText());
                    ImageView icon = providerIcon(card);
                    card.setContentDescription(media.title + (UnifiedCatalogPrefs.showRatings(this) && media.hasRating()
                            ? " · ★ " + media.ratingText() : ""));
                    Runnable load = once(() -> {
                        image(stamp, cover, () -> UnifiedCatalogApi.poster(this, media.poster));
                        loadProviderIcon(stamp, media, card, icon);
                    });
                    cards.add(card);
                    loaders.add(load);
                    card.setOnFocusChangeListener((view, focused) -> {
                        view.animate().cancel();
                        if (StreamUi.motionEnabled()) {
                            view.animate().scaleX(focused ? 1.025f : 1f)
                                    .scaleY(focused ? 1.025f : 1f)
                                    .translationZ(focused ? dp(3) : 0)
                                    .setDuration(StreamUi.FOCUS_ANIM_MS).start();
                        } else {
                            view.setScaleX(focused ? 1.025f : 1f);
                            view.setScaleY(focused ? 1.025f : 1f);
                            view.setTranslationZ(focused ? dp(3) : 0);
                        }
                        if (focused) {
                            load.run();
                            view.post(() -> {
                                if (!view.isFocused()) return;
                                int left = section.getLeft();
                                int right = section.getRight();
                                int visibleLeft = horizontal.getScrollX();
                                int visibleRight = visibleLeft + horizontal.getWidth();
                                if (left < visibleLeft)
                                    horizontal.smoothScrollTo(Math.max(0, left - dp(9)), 0);
                                else if (right > visibleRight)
                                    horizontal.smoothScrollTo(right - horizontal.getWidth() + dp(9), 0);
                            });
                        }
                    });
                }
                posters.post(preload);
                restoreContentFocus();
            }, () -> {
                posters.removeAllViews();
                posters.addView(text("No disponible", 12, StreamUi.DIM, false));
            });
        }
    }

    /** Dark-glass provider logo belongs to the service that will open automatically. */
    private ImageView providerIcon(FrameLayout card) {
        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dp(5), dp(3), dp(5), dp(3));
        icon.setBackground(StreamUi.shape(this, 0xE51A1A22, 7, 0x44FFFFFF));
        icon.setClipToOutline(true);
        icon.setVisibility(View.GONE);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Horizontal wordmark badge: avoids the unreadable 29x29 TMDB icon.
        FrameLayout.LayoutParams spot = new FrameLayout.LayoutParams(dp(70), dp(30),
                Gravity.TOP | Gravity.START);
        spot.setMargins(dp(6), dp(5), 0, 0);
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
            String badge = UnifiedCatalogPrefs.posterBadge(this);
            if (UnifiedCatalogPrefs.BADGE_HIDDEN.equals(badge)) return;
            boolean appIcon = UnifiedCatalogPrefs.BADGE_APP_ICON.equals(badge);
            int wordmark = appIcon ? 0 : UnifiedProviderWordmarks.forKey(target.provider.key);
            Drawable installedIcon = null;
            if (appIcon) {
                try { installedIcon = getPackageManager().getApplicationIcon(target.packageName); }
                catch (PackageManager.NameNotFoundException ignored) { }
            }
            final Drawable appDrawable = installedIcon;
            Bitmap tmdbLogo = !appIcon && wordmark == 0 ? UnifiedCatalogApi.logo(this,
                    UnifiedCatalogApi.logoPath(media, target.provider)) : null;
            if ((appIcon && appDrawable == null) || (!appIcon && wordmark == 0 && tmdbLogo == null)) return;
            runOnUiThread(() -> {
                if (!live(stamp)) return;
                FrameLayout.LayoutParams slot = (FrameLayout.LayoutParams) icon.getLayoutParams();
                slot.width = dp(appIcon ? 30 : 70);
                slot.height = dp(30);
                icon.setLayoutParams(slot);
                icon.clearColorFilter();
                if (appIcon) {
                    icon.setImageDrawable(appDrawable);
                } else if (wordmark != 0) {
                    icon.setImageResource(wordmark);
                    UnifiedProviderWordmarks.applyTint(icon, wordmark);
                } else {
                    icon.setImageBitmap(tmdbLogo);
                }
                icon.setVisibility(View.VISIBLE);
                card.setContentDescription(media.title + " · Se abrirá en "
                        + target.provider.name + (UnifiedCatalogPrefs.showRatings(this) && media.hasRating()
                        ? " · ★ " + media.ratingText() : ""));
            });
        });
    }

    /** End space allows even the last card to settle in the stationary focus slot. */
    private void finishCarousel(LinearLayout line) {
        int room = getResources().getConfiguration().screenWidthDp
                - 2 * StreamUi.SAFE_HORIZONTAL_DP - RAIL_COLLAPSED;
        int focusWidth = coverWidth();
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
                getResources().getConfiguration().screenWidthDp - 2 * StreamUi.SAFE_HORIZONTAL_DP,
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
        int slotWidth = coverWidth();
        LinearLayout.LayoutParams tile = new LinearLayout.LayoutParams(
                dp(UnifiedCatalogLayout.cardWidthDp(slotWidth)),
                dp(UnifiedCatalogLayout.cardHeightDp(slotWidth)));
        // Poster + border/zoom occupies LESS than the cell; its slot remains stable.
        tile.setMargins(dp(UnifiedCatalogLayout.FOCUS_INSET_X_DP),
                dp(UnifiedCatalogLayout.FOCUS_INSET_TOP_DP),
                dp(UnifiedCatalogLayout.FOCUS_INSET_X_DP + CARD_GAP),
                dp(UnifiedCatalogLayout.focusBottomInsetDp(slotWidth)));
        row.addView(card, tile);
        return card;
    }

    /** Six fixed columns: only the parent ScrollView scrolls, never a horizontal carousel. */
    private GridLayout watchGrid() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(UnifiedCatalogLayout.COLUMNS);
        grid.setOrientation(GridLayout.HORIZONTAL);
        grid.setPadding(dp(CONTENT_START), dp(10), dp(CONTENT_END), dp(23));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        pageBody.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        return grid;
    }

    private FrameLayout gridTile(GridLayout grid, int index, Runnable click) {
        FrameLayout card = mediaCard(click);
        int column = UnifiedCatalogLayout.column(index);
        GridLayout.LayoutParams tile = new GridLayout.LayoutParams(
                GridLayout.spec(UnifiedCatalogLayout.row(index)), GridLayout.spec(column));
        int slotWidth = coverWidth();
        tile.width = dp(UnifiedCatalogLayout.cardWidthDp(slotWidth));
        tile.height = dp(UnifiedCatalogLayout.cardHeightDp(slotWidth));
        tile.setMargins(dp(UnifiedCatalogLayout.FOCUS_INSET_X_DP),
                dp(UnifiedCatalogLayout.FOCUS_INSET_TOP_DP),
                dp(UnifiedCatalogLayout.FOCUS_INSET_X_DP
                        + (column == UnifiedCatalogLayout.COLUMNS - 1 ? 0 : CARD_GAP)),
                dp(UnifiedCatalogLayout.focusBottomInsetDp(slotWidth)
                        + UnifiedCatalogLayout.GRID_ROW_GAP_DP));
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

    private TextView posterTitle(String title) {
        TextView view = text(title, 11, StreamUi.WHITE, true);
        view.setMaxLines(2);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private TextView ratingBadge(String label) {
        TextView badge = text(label, 8, StreamUi.RATING, true);
        badge.setPadding(dp(5), dp(2), dp(5), dp(2));
        badge.setBackground(StreamUi.shape(this, 0xEE121217, 5, Color.TRANSPARENT));
        return badge;
    }

    /** Shared TMDB star badge for catalog and lazily identified Watch Next titles. */
    private TextView ratingSlot(FrameLayout card) {
        TextView badge = ratingBadge("");
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        size.setMargins(0, dp(5), dp(6), 0);
        card.addView(badge, size);
        return badge;
    }

    private void rating(FrameLayout card, String value) {
        TextView badge = ratingSlot(card);
        badge.setText("★ " + value);
        badge.setVisibility(View.VISIBLE);
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

    /**
     * Every carousel card stays at its fixed 2:3 poster dimensions.
     * Focus is a subtle uniform zoom and elevation; the row scrolls
     * underneath the same focus slot, never resizing its children.
     */
    private void bindTileFocus(FrameLayout card, List<Runnable> loads, int index,
                               View focusedInfo) {
        int available = getResources().getConfiguration().screenWidthDp
                - RAIL_COLLAPSED - CONTENT_START - CONTENT_END;
        if (index < available / (coverWidth() + CARD_GAP) + 2) loads.get(index).run();
        focusedInfo.setVisibility(View.GONE);
        card.setOnFocusChangeListener((view, focused) -> {
            focusedInfo.animate().cancel();
            if (focused) {
                focusedInfo.setAlpha(StreamUi.motionEnabled() ? 0f : 1f);
                focusedInfo.setVisibility(View.VISIBLE);
                if (StreamUi.motionEnabled()) {
                    focusedInfo.animate().alpha(1f)
                            .setDuration(StreamUi.ART_ANIM_MS).start();
                }
            } else {
                focusedInfo.setVisibility(View.GONE);
            }
            card.animate().cancel();
            if (StreamUi.motionEnabled()) {
                card.animate().scaleX(focused ? 1.025f : 1f)
                        .scaleY(focused ? 1.025f : 1f)
                        .translationZ(focused ? dp(3) : 0)
                        .setDuration(StreamUi.FOCUS_ANIM_MS).start();
            } else {
                card.setScaleX(focused ? 1.025f : 1f);
                card.setScaleY(focused ? 1.025f : 1f);
                card.setTranslationZ(focused ? dp(3) : 0);
            }
            if (!focused) return;
            view.post(() -> { if (view.isFocused()) pinCarouselFocus(view); });
            for (int i = Math.max(0, index - 1); i < Math.min(index + 4, loads.size()); i++)
                loads.get(i).run();
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
        loadGridOnScroll(grid, loads, tileHeight() + UnifiedCatalogLayout.GRID_ROW_GAP_DP);
    }

    /** App banners and posters have different row heights but identical vertical scrolling. */
    private void loadGridOnScroll(GridLayout grid, List<Runnable> loads, int rowStrideDp) {
        Runnable preload = () -> {
            if (grid.getParent() == null) return;
            int stride = dp(rowStrideDp);
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
        connectGridFocus(cards, loads, 1.015f);
    }

    /** Distinct focus scale for the 16:9 app launcher; poster focus stays unchanged. */
    private void connectGridFocus(List<FrameLayout> cards, List<Runnable> loads,
                                  float focusScale) {
        for (int i = 0; i < cards.size(); i++) {
            FrameLayout card = cards.get(i);
            int column = UnifiedCatalogLayout.column(i);
            if (column > 0) card.setNextFocusLeftId(cards.get(i - 1).getId());
            if (column < UnifiedCatalogLayout.COLUMNS - 1 && i + 1 < cards.size())
                card.setNextFocusRightId(cards.get(i + 1).getId());
            if (i >= UnifiedCatalogLayout.COLUMNS)
                card.setNextFocusUpId(cards.get(i - UnifiedCatalogLayout.COLUMNS).getId());
            int below = UnifiedCatalogLayout.below(i, cards.size());
            if (below >= 0) card.setNextFocusDownId(cards.get(below).getId());
            final int index = i;
            card.setOnFocusChangeListener((view, focused) -> {
                view.animate().cancel();
                if (StreamUi.motionEnabled()) {
                    view.animate().scaleX(focused ? focusScale : 1f).scaleY(focused ? focusScale : 1f)
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
        art.execute(() -> {
            if (revision != stamp) return;
            Bitmap value = loader.load();
            if (value != null) runOnUiThread(() -> {
                if (!live(stamp)) return;
                target.setImageBitmap(value);
                if (StreamUi.motionEnabled() && target.isAttachedToWindow()) {
                    target.animate().cancel();
                    target.setAlpha(0f);
                    target.animate().alpha(1f).setDuration(StreamUi.ART_ANIM_MS).start();
                } else {
                    target.setAlpha(1f);
                }
            });
        });
    }

    /** Paint the platform poster first; one conservative TMDB match supplies both rating and fallback art. */
    private void watchImage(int stamp, UnifiedWatchNext.Entry item, FrameLayout card,
                            ImageView poster, TextView score) {
        art.execute(() -> {
            if (revision != stamp) return;
            Bitmap platform = UnifiedWatchArtwork.platformPoster(getApplicationContext(), item);
            runOnUiThread(() -> showWatchImage(stamp, poster, platform));
            // TMDB is intentionally isolated: slow rating/search requests must not
            // queue behind other images or delay their first paint on low-end TVs.
            watchMeta.execute(() -> {
                if (revision != stamp) return;
                UnifiedCatalogApi.MediaArt match = null;
                try {
                    match = UnifiedCatalogApi.watchArtwork(getApplicationContext(),
                            item.title, item.mediaType, item.releaseDate);
                } catch (Exception ignored) { /* No false rating when offline or ambiguous. */ }
                if (revision != stamp) return;
                Bitmap enhanced = UnifiedWatchArtwork.withTmdbPoster(
                        getApplicationContext(), platform, match);
                if (enhanced != platform)
                    runOnUiThread(() -> showWatchImage(stamp, poster, enhanced));
                if (UnifiedCatalogPrefs.showRatings(this) && match != null && match.hasRating()) {
                    String rating = match.ratingText();
                    runOnUiThread(() -> {
                        if (!live(stamp)) return;
                        score.setText("★ " + rating);
                        score.setVisibility(View.VISIBLE);
                        card.setContentDescription(item.title + " · " + item.source
                                + " · ★ " + rating
                                + (item.progress() < 0 ? "" : " · " + item.progress() + "%"));
                    });
                }
            });
        });
    }

    private void showWatchImage(int stamp, ImageView poster, Bitmap bitmap) {
        if (!live(stamp) || bitmap == null || showingBitmap(poster, bitmap)) return;
        poster.setImageBitmap(bitmap);
        if (StreamUi.motionEnabled() && poster.isAttachedToWindow()) {
            poster.animate().cancel();
            poster.setAlpha(0f);
            poster.animate().alpha(1f).setDuration(StreamUi.ART_ANIM_MS).start();
        } else {
            poster.setAlpha(1f);
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
        if (line != null) line.setMinimumHeight(dp(tileHeight() + 32));
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
                    card.setTag("watch:" + item.providerPackage + ":" + item.intentUri);
                    cards.add(card);
                    // Short press opens the publisher's own item intent. Long press
                    // resolves a richer TMDB information sheet without playing media.
                    card.setOnLongClickListener(v -> { watchDetails(item); return true; });
                    ImageView artwork = tileArt(card);
                    TextView score = ratingSlot(card);
                    LinearLayout copy = caption(card);
                    TextView title = posterTitle(item.title);
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
                    Runnable loadArtwork = once(() -> watchImage(stamp, item, card, artwork, score));
                    loads.add(loadArtwork);
                    if (!fullGrid) bindTileFocus(card, loads, i, episode);
                }
                restoreContentFocus();
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
        Runnable search = () -> {
            if (!field.isAttachedToWindow() || page != Page.SEARCH) return;
            query = field.getText().toString().trim();
            int searchStamp = ++revision; // Late results from the previous query cannot replace these.
            while (pageBody.getChildCount() > 2) pageBody.removeViewAt(2);
            if (!query.isEmpty()) {
                shelf(searchStamp, "Películas", "movie", "search", query, false);
                shelf(searchStamp, "Series", "tv", "search", query, false);
            }
        };
        Runnable submit = () -> {
            field.removeCallbacks(search);
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(field.getWindowToken(), 0);
            search.run();
        };
        TextView go = StreamUi.control(this, "Buscar", R.drawable.uic_search, true, v -> submit.run());
        form.addView(go);
        field.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            submit.run();
            go.requestFocus();
            return true;
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                field.removeCallbacks(search);
                field.postDelayed(search, 450);
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        pageBody.addView(form);
        routes.get(page).setNextFocusRightId(field.getId());
        if (!query.isEmpty()) field.post(search);
    }

    /**
     * App library is a six-column vertically scrolling grid of official TV
     * 16:9 banners. Square-only apps keep their icon centered inside the banner.
     * The shared ScrollView owns scrolling; there is no horizontal carousel.
     */
    private void apps(int stamp) {
        title("Apps y fuentes", "Aplicaciones disponibles en este dispositivo");
        int bannerWidth = UnifiedCatalogLayout.appBannerWidthDp(
                getResources().getConfiguration().screenWidthDp - 2 * StreamUi.SAFE_HORIZONTAL_DP,
                RAIL_COLLAPSED, CONTENT_START, CONTENT_END);
        int bannerHeight = UnifiedCatalogLayout.appBannerHeightDp(bannerWidth);
        int cardHeight = UnifiedCatalogLayout.appCardHeightDp(bannerWidth);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(UnifiedCatalogLayout.COLUMNS);
        grid.setOrientation(GridLayout.HORIZONTAL);
        grid.setPadding(dp(CONTENT_START), dp(7), dp(CONTENT_END), dp(23));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        pageBody.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        // Configuration actions follow the content rather than consuming the first row.
        link("Gestionar fuentes de streaming", R.drawable.ic_settings,
                v -> startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class)
                        .putExtra(UnifiedCatalogSettingsActivity.EXTRA_SECTION,
                                UnifiedCatalogSettingsActivity.PLATFORMS_SECTION)));

        PackageManager manager = getPackageManager();
        data.execute(() -> {
            Map<String, ResolveInfo> installed = new LinkedHashMap<>();
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,
                    Intent.CATEGORY_LAUNCHER}) {
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
                if (sorted.isEmpty()) {
                    note(pageBody, "No se encontraron aplicaciones disponibles.");
                    return;
                }
                List<FrameLayout> cards = new ArrayList<>();
                List<Runnable> loads = new ArrayList<>();
                for (int i = 0; i < sorted.size(); i++) {
                    ResolveInfo app = sorted.get(i);
                    String appName = app.loadLabel(manager).toString();
                    String packageName = app.activityInfo.packageName;
                    FrameLayout card = mediaCard(() -> {
                        try {
                            Intent launch = manager.getLeanbackLaunchIntentForPackage(packageName);
                            if (launch == null) launch = manager.getLaunchIntentForPackage(packageName);
                            if (launch == null) launch = new Intent(Intent.ACTION_MAIN)
                                    .setClassName(packageName, app.activityInfo.name);
                            startActivity(launch);
                        } catch (RuntimeException e) {
                            android.widget.Toast.makeText(this, "No se pudo abrir la aplicación",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                    card.setContentDescription(appName);
                    int column = UnifiedCatalogLayout.column(i);
                    GridLayout.LayoutParams tile = new GridLayout.LayoutParams(
                            GridLayout.spec(UnifiedCatalogLayout.row(i)), GridLayout.spec(column));
                    tile.width = dp(bannerWidth);
                    tile.height = dp(cardHeight);
                    tile.setMargins(dp(UnifiedCatalogLayout.APP_FOCUS_INSET_X_DP),
                            dp(UnifiedCatalogLayout.APP_FOCUS_INSET_TOP_DP),
                            dp(UnifiedCatalogLayout.APP_FOCUS_INSET_X_DP
                                    + UnifiedCatalogLayout.APP_CELL_GAP_DP),
                            dp(UnifiedCatalogLayout.APP_FOCUS_INSET_BOTTOM_DP));
                    grid.addView(card, tile);
                    if (column == 0) leftToRail(card);

                    ImageView artwork = new ImageView(this);
                    artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    artwork.setBackgroundColor(0xFF24242C);
                    artwork.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    card.addView(artwork, new FrameLayout.LayoutParams(-1, dp(bannerHeight),
                            Gravity.TOP));
                    TextView label = singleLine(appName, 11, StreamUi.WHITE, true);
                    label.setGravity(Gravity.CENTER);
                    label.setPadding(dp(3), 0, dp(3), 0);
                    label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    card.addView(label, new FrameLayout.LayoutParams(-1,
                            dp(UnifiedCatalogLayout.APP_LABEL_HEIGHT_DP), Gravity.BOTTOM));
                    cards.add(card);

                    int wordmark = UnifiedProviderWordmarks.forPackage(packageName);
                    if (wordmark != 0) {
                        // Use a full-name branded banner even when Android only offers a square icon.
                        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        artwork.setPadding(dp(14), dp(7), dp(14), dp(7));
                        artwork.setImageResource(wordmark);
                        UnifiedProviderWordmarks.applyTint(artwork, wordmark);
                        loads.add(() -> { }); // Local bitmap: ready without an async fetch.
                    } else loads.add(once(() -> art.execute(() -> {
                        if (revision != stamp) return;
                        Drawable loaded = null;
                        try {
                            loaded = manager.getActivityBanner(new ComponentName(
                                    packageName, app.activityInfo.name));
                        } catch (PackageManager.NameNotFoundException ignored) { }
                        if (loaded == null) {
                            try { loaded = manager.getApplicationBanner(packageName); }
                            catch (PackageManager.NameNotFoundException ignored) { }
                        }
                        boolean hasBanner = loaded != null;
                        if (!hasBanner) loaded = app.loadIcon(manager);
                        final Drawable image = loaded;
                        runOnUiThread(() -> {
                            if (!live(stamp) || grid.getParent() == null) return;
                            artwork.setImageDrawable(image);
                            if (!hasBanner) {
                                // A square icon must not stretch into a 16:9 banner.
                                artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                artwork.setPadding(dp(18), dp(6), dp(18), dp(6));
                            }
                        });
                    })));
                }
                // LEFT/RIGHT stay within each row; DOWN scrolls to the next six apps.
                connectGridFocus(cards, loads, 1.035f);
                routes.get(page).setNextFocusRightId(cards.get(0).getId());
                loadGridOnScroll(grid, loads,
                        UnifiedCatalogLayout.appRowStrideDp(bannerWidth));
            });
        });
    }

    /** Long press: comprehensive TMDB sheet; never starts playback on its own. */
    private void details(UnifiedCatalogApi.Media media) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackground(StreamUi.panel(this));
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(StreamUi.shape(this, 0xFF302C34, 6, Color.TRANSPARENT));
        cover.setClipToOutline(true);
        root.addView(cover, new LinearLayout.LayoutParams(dp(157), dp(236)));
        LinearLayout right = StreamUi.column(this);
        right.setPadding(dp(17), 0, 0, 0);
        root.addView(right, new LinearLayout.LayoutParams(0, -1, 1));

        ScrollView information = new ScrollView(this);
        information.setFillViewport(false);
        information.setFocusable(true);
        information.setContentDescription("Sinopsis y ficha completa");
        information.setVerticalScrollBarEnabled(false);
        LinearLayout copy = StreamUi.column(this);
        copy.setPadding(0, 0, dp(8), dp(8));
        information.addView(copy);
        right.addView(information, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView name = text(media.title, 25, StreamUi.WHITE, true);
        copy.addView(name);
        copy.addView(text(metadata(media), 13, StreamUi.RATING, true));
        TextView synopsis = text(media.overview.isEmpty()
                ? "Buscando sinopsis…" : media.overview, 14, StreamUi.WHITE, false);
        synopsis.setPadding(0, dp(13), 0, dp(11));
        copy.addView(synopsis);
        TextView expanded = text("Consultando ficha completa de TMDB…",
                13, StreamUi.DIM, false);
        expanded.setPadding(0, 0, 0, dp(10));
        copy.addView(expanded);
        TextView platforms = text("Consultando disponibilidad…",
                12, StreamUi.DIM, false);
        copy.addView(platforms);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        TextView open = StreamUi.control(this, "Abrir título", R.drawable.uic_play, true, v -> {
            dialog.dismiss();
            openMedia(media, null, revision);
        });
        StreamUi.heroPrimary(open);
        buttons.addView(open, new LinearLayout.LayoutParams(-2, dp(36)));
        TextView close = StreamUi.control(this, "Cerrar", R.drawable.uic_x, false,
                v -> dialog.dismiss());
        StreamUi.background(close, false, true);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-2, dp(36));
        closeParams.setMarginStart(dp(10));
        buttons.addView(close, closeParams);
        right.addView(buttons);

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int displayWidth = getResources().getDisplayMetrics().widthPixels;
            int displayHeight = getResources().getDisplayMetrics().heightPixels;
            window.setLayout(Math.min(dp(825), displayWidth - dp(32)),
                    Math.min(dp(480), displayHeight - dp(30)));
        }
        close.requestFocus();
        image(revision, cover, () -> UnifiedCatalogApi.poster(this, media.poster));
        data.execute(() -> {
            boolean available;
            try { UnifiedCatalogApi.availability(this, media); available = true; }
            catch (Exception ignored) { available = false; }
            org.json.JSONObject additional = null;
            try { additional = UnifiedCatalogApi.fullDetails(this, media); }
            catch (Exception ignored) { /* Display the known summary offline. */ }
            final boolean ready = available;
            final org.json.JSONObject info = additional;
            runOnUiThread(() -> {
                if (!dialog.isShowing() || isFinishing()) return;
                if (info != null) {
                    String fullOverview = info.optString("overview", "");
                    if (!fullOverview.isEmpty()) synopsis.setText(fullOverview);
                    String facts = UnifiedCatalogApi.fullDetailsText(info, media.type);
                    expanded.setText(facts.isEmpty() ? "No hay más detalles publicados en TMDB." : facts);
                } else {
                    expanded.setText("Ficha ampliada no disponible sin conexión a TMDB.");
                }
                List<String> names = new ArrayList<>();
                if (ready) for (UnifiedCatalogPrefs.Provider provider :
                        UnifiedCatalogApi.eligible(this, media)) names.add(provider.name);
                List<String> players = UnifiedCatalogRoute.localAppNames(this);
                String availability = !ready ? "Disponibilidad no consultada"
                        : names.isEmpty() ? "Sin proveedores configurados disponibles según TMDB"
                        : "Disponible en: " + TextUtils.join(" · ", names);
                platforms.setText(availability + (players.isEmpty() ? ""
                        : "\nReproductores instalados: " + TextUtils.join(" · ", players)));
            });
        });
    }

    /** A platform-published Watch Next item may have no TMDB id; never guess one. */
    private void watchDetails(UnifiedWatchNext.Entry item) {
        android.app.AlertDialog lookup = StreamUi.dialog(this)
                .setTitle(item.title)
                .setMessage("Buscando la ficha de información completa…")
                .setNegativeButton("Cancelar", null)
                .create();
        lookup.show();
        watchMeta.execute(() -> {
            UnifiedCatalogApi.MediaArt art = null;
            try {
                art = UnifiedCatalogApi.watchArtwork(getApplicationContext(),
                        item.title, item.mediaType, item.releaseDate);
            } catch (Exception ignored) { /* Local metadata is still available. */ }
            final UnifiedCatalogApi.MediaArt result = art;
            runOnUiThread(() -> {
                if (!lookup.isShowing() || isFinishing()) return;
                lookup.dismiss();
                if (result != null && result.id > 0
                        && ("movie".equals(result.type) || "tv".equals(result.type))) {
                    String date = item.releaseDate;
                    String year = date.length() >= 4 ? date.substring(0, 4) : "";
                    details(new UnifiedCatalogApi.Media(result.id, result.type,
                            item.title, result.poster, year, result.voteAverage,
                            result.voteCount, result.backdrop, result.overview));
                } else {
                    String message = "Aplicación: " + item.source
                            + (item.releaseDate.isEmpty() ? ""
                            : "\nEstreno: " + item.releaseDate)
                            + (item.episode.isEmpty() ? "" : "\nEpisodio: " + item.episode)
                            + (item.progress() < 0 ? "" : "\nProgreso: " + item.progress() + "%")
                            + "\n\nNo se ha encontrado una coincidencia inequívoca en TMDB."
                            + " El enlace original de la plataforma sigue disponible"
                            + " mediante pulsación corta.";
                    StreamUi.dialog(this).setTitle(item.title).setMessage(message)
                            .setPositiveButton("Abrir título",
                                    (d, which) -> UnifiedWatchNext.open(this, item))
                            .setNegativeButton("Cerrar", null).show();
                }
            });
        });
    }

    @Override public void onBackPressed() {
        if (TvSpatialNavigation.inside(viewport, getCurrentFocus())) {
            routes.get(page).requestFocus();
        } else if (page != Page.HOME) {
            show(Page.HOME, false);
            routes.get(Page.HOME).requestFocus();
        } // Home stays on Home when Back is pressed; no classic launcher fallback.
    }

    @Override protected void onDestroy() {
        clearRailListener();
        revision++;
        data.shutdownNow();
        art.shutdownNow();
        watchMeta.shutdownNow();
        providerArt.shutdownNow();
        super.onDestroy();
    }
}
