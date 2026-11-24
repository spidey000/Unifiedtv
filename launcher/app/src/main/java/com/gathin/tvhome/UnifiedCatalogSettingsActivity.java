package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Switch;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Split-pane settings for the streaming interface; existing preference names remain unchanged. */
public final class UnifiedCatalogSettingsActivity extends Activity {
    static final String EXTRA_SECTION = "unifiedtv_settings_section";
    static final int PLATFORMS_SECTION = 2;
    static final int PERMISSIONS_SECTION = 5;
    private static final String[] SECTIONS = {"Apariencia", "Catálogo", "Plataformas", "Seguir viendo",
            "Actualizaciones", "Permisos", "Recuperación", "Información"};
    private static final int[] SECTION_ICONS = {R.drawable.uic_palette, R.drawable.uic_film, R.drawable.ic_apps,
            R.drawable.uic_layers, R.drawable.uic_download, R.drawable.uic_check,
            R.drawable.uic_house, R.drawable.uic_info};
    private static final int[] ACCENTS = {StreamUi.DEFAULT_ACCENT, 0xFFFF9B45, 0xFF26BC91, 0xFF468DFF, 0xFFB472F1};
    private static final String[] ACCENT_NAMES = {"Rojo", "Ámbar", "Verde", "Azul", "Violeta"};
    private final List<TextView> tabs = new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TvSpatialNavigation spatial;
    private ViewTreeObserver.OnGlobalFocusChangeListener focusObserver;
    private TextView installPermissionStatus;
    private TmdbPairingDialog pairing;
    private boolean refreshPermissionsOnReturn;
    private int section;
    private String restoredFocus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(StreamUi.BLACK);
        root.setPadding(dp(StreamUi.SAFE_HORIZONTAL_DP), dp(StreamUi.SAFE_VERTICAL_DP),
                dp(StreamUi.SAFE_HORIZONTAL_DP), dp(StreamUi.SAFE_VERTICAL_DP));
        LinearLayout rail = StreamUi.column(this);
        ScrollView railScroll = new ScrollView(this);
        railScroll.setBackground(StreamUi.rail(this));
        railScroll.setFillViewport(true);
        railScroll.setVerticalScrollBarEnabled(false);
        rail.setPadding(dp(14), dp(16), dp(12), dp(12));
        TextView title = text("Ajustes", 24, StreamUi.WHITE, true);
        title.setPadding(dp(10), 0, 0, dp(14));
        rail.addView(title);
        for (int i = 0; i < SECTIONS.length; i++) {
            final int index = i;
            TextView tab = StreamUi.control(this, SECTIONS[i], SECTION_ICONS[i], false, v -> show(index, null));
            StreamUi.navigation(tab);
            tab.setMinHeight(dp(38));
            tab.setTextSize(12);
            tab.setPadding(dp(10), dp(3), dp(9), dp(3));
            tabs.add(tab);
            add(rail, tab);
        }
        TextView back = StreamUi.control(this, "Volver", R.drawable.uic_arrow_left,
                false, v -> finish());
        StreamUi.navigation(back);
        back.setMinHeight(dp(38));
        back.setTextSize(12);
        back.setPadding(dp(10), dp(3), dp(9), dp(3));
        add(rail, back);
        railScroll.addView(rail);
        root.addView(railScroll, new LinearLayout.LayoutParams(dp(178), -1));
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = StreamUi.column(this);
        content.setPadding(dp(24), dp(16), dp(24), dp(40));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
        spatial = new TvSpatialNavigation(railScroll, scroll);
        focusObserver = (previous, current) -> {
            spatial.remember(current);
            if (TvSpatialNavigation.inside(scroll, current)) revealSettingsFocus(current);
            if (TvSpatialNavigation.inside(railScroll, current)) {
                restoredFocus = null;
            } else if (TvSpatialNavigation.inside(scroll, current)
                    && current.getTag() instanceof String) {
                restoredFocus = (String) current.getTag();
            }
        };
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(focusObserver);
        int selected = state == null ? getIntent().getIntExtra(EXTRA_SECTION, 0) : state.getInt("section", 0);
        if (selected < 0 || selected >= SECTIONS.length) selected = 0;
        show(selected, state == null ? null : state.getString("focus"));
        if (state == null || state.getString("focus") == null || getCurrentFocus() == null) {
            tabs.get(section).requestFocus();
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return (spatial != null && spatial.onKey(getCurrentFocus(), event))
                || super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        if (focusObserver != null) {
            getWindow().getDecorView().getViewTreeObserver()
                    .removeOnGlobalFocusChangeListener(focusObserver);
            focusObserver = null;
        }
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("section", section);
        state.putString("focus", restoredFocus);
    }

    @Override protected void onResume() {
        super.onResume();
        UnifiedAppUpdater.onResume(this);
        if (installPermissionStatus != null) installPermissionStatus.setText(permissionStatus());
        if (refreshPermissionsOnReturn && section == PERMISSIONS_SECTION) {
            refreshPermissionsOnReturn = false;
            // The Android TV settings app may change permissions while we are paused.
            // Rebuild controls and restore the last focused row when the user returns.
            show(section, restoredFocus);
        }
    }

    @Override protected void onStop() {
        if (pairing != null) pairing.dismiss();
        super.onStop();
    }

    private int dp(int n) { return StreamUi.dp(this, n); }
    private TextView text(String value, int size, int color, boolean bold) {
        return StreamUi.text(this, value, size, color, bold);
    }

    /**
     * Preserve the entire focused settings row after DPAD navigation, including
     * on 540p TVs and the long Permissions/Platforms panels.
     */
    private void revealSettingsFocus(View focused) {
        focused.post(() -> {
            if (!focused.isFocused() || scroll.getHeight() == 0
                    || !TvSpatialNavigation.inside(scroll, focused)) return;
            android.graphics.Rect bounds = new android.graphics.Rect(
                    0, 0, focused.getWidth(), focused.getHeight());
            scroll.offsetDescendantRectToMyCoords(focused, bounds);
            // Android already returns content coordinates; adding scrollY would double-count it.
            int shift = TvFocusViewport.scrollDelta(bounds.top, bounds.bottom,
                    scroll.getScrollY(), scroll.getHeight(), dp(12));
            if (shift != 0) scroll.smoothScrollBy(0, shift);
        });
    }

    private void show(int selected, String focusTag) {
        section = selected;
        if (spatial != null) spatial.setPreferredRail(tabs.get(section));
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).setSelected(i == selected);
        content.removeAllViews();
        installPermissionStatus = null;
        TextView heading = text(selected == PERMISSIONS_SECTION ? "Permisos y funcionamiento"
                : SECTIONS[selected], 32, StreamUi.WHITE, true);
        heading.setLetterSpacing(-.025f);
        LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1, -2);
        headingSize.bottomMargin = dp(11);
        content.addView(heading, headingSize);
        switch (selected) {
            case 0: appearance(); break;
            case 1: catalog(); break;
            case PLATFORMS_SECTION: providers(); break;
            case 3: watch(); break;
            case 4: updates(); break;
            case PERMISSIONS_SECTION: permissionsAndSetup(); break;
            case 6:
                description("Permite volver a la pantalla de inicio original del televisor. "
                        + "Selecciona su launcher en los ajustes de Android TV.");
                action("Recuperar launcher original / Cambiar Home",
                        v -> confirmRecovery(), "recover-home");
                if (UnifiedTvHome.isSystemHomeRecovery(this)) {
                    description("Modo de recuperación activado: las teclas Home y las redirecciones "
                            + "automáticas del launcher antiguo quedan desactivadas.");
                    action("Reactivar accesos Home de UnifiedTV", v -> {
                        UnifiedTvHome.enableUnifiedHomeRouting(this);
                        show(section, "recover-home");
                    }, "enable-unified-home");
                }
                description("Si Android TV no ofrece el selector de Home, se mostrarán "
                        + "las preferencias de aplicaciones predeterminadas o ajustes del sistema.");
                break;
            default:
                description("UnifiedTV combina resultados de TMDB con la disponibilidad de JustWatch. No puede comprobar suscripciones, ni garantiza que una plataforma abra la ficha exacta. No accede al historial privado de tus apps.");
        }
        if (focusTag == null) scroll.scrollTo(0, 0);
        else {
            View target = content.findViewWithTag(focusTag);
            if (target != null) target.requestFocus();
            else tabs.get(section).requestFocus();
        }
    }

    private void confirmRecovery() {
        StreamUi.dialog(this).setTitle("Recuperar inicio original")
                .setMessage("Se desactivará la redirección de teclas Home de UnifiedTV "
                        + "y se abrirá el selector de Android TV (o los ajustes del fabricante). "
                        + "Selecciona el inicio original de tu televisor. "
                        + "UnifiedTV seguirá instalada y podrás volver a activarla desde esta sección.")
                .setPositiveButton("Abrir selector de inicio", (dialog, which) ->
                        startActivity(new Intent(this, HomeSettingsActivity.class)))
                .setNegativeButton("Cancelar", null).show();
    }

    private void appearance() {
        int active = StreamUi.accent(this);
        description("Color de acento");
        LinearLayout choices = new LinearLayout(this);
        for (int i = 0; i < ACCENTS.length; i++) {
            int color = ACCENTS[i];
            String tag = "swatch" + i;
            TextView sample = StreamUi.control(this, ACCENT_NAMES[i], false, v -> setAccent(color, tag));
            sample.setTag(tag);
            sample.setSelected(active == color);
            if (i == 0) left(sample);
            StreamUi.background(sample, false, true);
            sample.setMinHeight(dp(41));
            sample.setTextSize(12);
            android.graphics.drawable.GradientDrawable swatch = StreamUi.shape(this, color, 10, Color.TRANSPARENT);
            swatch.setBounds(0, 0, dp(15), dp(15));
            sample.setCompoundDrawablesRelative(swatch, null, null, null);
            sample.setCompoundDrawablePadding(dp(6));
            sample.setPadding(dp(9), dp(3), dp(7), dp(3));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            size.setMarginEnd(dp(5));
            choices.addView(sample, size);
        }
        add(content, choices);
        action("Color personalizado  ·  " + hex(active), v -> customAccent(), "custom");
        action("Restablecer el color", v -> setAccent(StreamUi.DEFAULT_ACCENT, "custom"), "reset");
        description("CARÁTULAS");
        description("Insignia de plataforma sobre las carátulas");
        action(posterBadgeDescription(), v -> selectPosterBadge(), "poster-badge");
        toggle("Mostrar puntuación TMDB (★)", UnifiedCatalogPrefs.SHOW_TMDB_RATINGS, true);
        description("Puedes ocultar las insignias y las valoraciones sin cambiar la disponibilidad ni la reproducción.");
    }

    private String posterBadgeDescription() {
        String style = UnifiedCatalogPrefs.posterBadge(this);
        String label = UnifiedCatalogPrefs.BADGE_HIDDEN.equals(style) ? "Oculta"
                : UnifiedCatalogPrefs.BADGE_APP_ICON.equals(style) ? "Icono de la app" : "Logotipo con nombre";
        return "Overlay de la plataforma  ·  " + label;
    }

    private void selectPosterBadge() {
        String[] labels = {"Logotipo con nombre", "Icono de la app", "No mostrar"};
        String[] values = {UnifiedCatalogPrefs.BADGE_WORDMARK,
                UnifiedCatalogPrefs.BADGE_APP_ICON, UnifiedCatalogPrefs.BADGE_HIDDEN};
        String saved = UnifiedCatalogPrefs.posterBadge(this);
        int selected = 0;
        for (int i = 0; i < values.length; i++)
            if (saved.equals(values[i])) selected = i;
        StreamUi.dialog(this).setTitle("Plataforma en los pósteres")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    UnifiedCatalogPrefs.prefs(this).edit()
                            .putString(UnifiedCatalogPrefs.POSTER_PLATFORM_BADGE, values[which]).apply();
                    show(section, "poster-badge");
                }).setNegativeButton("Cancelar", null).show();
    }

    private static String hex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private void setAccent(int color, String tag) {
        if (color == StreamUi.accent(this)) return;
        StreamUi.saveAccent(this, color);
        restoredFocus = tag;
        recreate();
    }

    private void customAccent() {
        EditText input = new EditText(this);
        StreamUi.field(input);
        input.setSingleLine(true);
        input.setHint("#RRGGBB");
        input.setText(hex(StreamUi.accent(this)));
        LinearLayout field = StreamUi.column(this);
        field.setPadding(dp(20), dp(8), dp(20), dp(8));
        field.addView(input);
        android.app.AlertDialog dialog = StreamUi.dialog(this).setTitle("Color personalizado")
                .setView(field).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null).show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.matches("#?[0-9A-Fa-f]{6}")) {
                input.setError("Introduce un color en formato #RRGGBB");
                input.requestFocus();
                return;
            }
            dialog.dismiss();
            setAccent(Color.parseColor(value.startsWith("#") ? value : "#" + value), "custom");
        });
    }

    private void catalog() {
        description("Elige qué se muestra al abrir UnifiedTV y conecta el catálogo de películas y series.");
        action(UnifiedCatalogPrefs.apiKey(this).isEmpty() ? "Configurar TMDB desde el móvil"
                : "Cambiar clave de TMDB desde el móvil", v -> pairKey(), "tmdb");
        action("Introducir clave de TMDB con el mando", v -> editKey(), "tmdb-manual");
        action("Región de disponibilidad  ·  " + UnifiedCatalogPrefs.region(this), v -> chooseRegion(), "region");
        description("DISTRIBUCIÓN");
        description("Filas horizontales (predeterminado) o secciones en columnas, "
                + "cada categoría con dos subcolumnas de pósteres 2:3.");
        toggle("Categorías en columnas · 2 pósteres por fila",
                UnifiedCatalogPrefs.CATALOG_COLUMNS, false);
        toggle("Incluir servicios gratuitos y con anuncios", UnifiedCatalogPrefs.INCLUDE_FREE, true);
        description("La clave personal v3 de TMDB se guarda únicamente en esta TV.");
    }

    private void providers() {
        description("Servicios disponibles según TMDB en tu región. Solo aparecen al abrir un título si TMDB indica que está disponible.");
        description("UnifiedTV puede reutilizar enlaces directos a fichas publicados por las apps"
                + " en los canales de Android TV, también en Destacados, Más vistos y Novedades.");
        if (!UnifiedWatchNext.hasPermission(this)) {
            action("Permitir enlaces de fichas de Android TV", v ->
                    requestPermissions(new String[]{UnifiedWatchNext.PERMISSION},
                            UnifiedWatchNext.PERMISSION_REQUEST), "tv-program-links");
        } else {
            description("Enlaces de Android TV autorizados. Se usan solo con coincidencias fiables"
                    + " del título y de la plataforma.");
        }
        description("PLATAFORMAS DISPONIBLES");
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.ordered(this)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            boolean installed = provider.installedPackage(this) != null;
            Switch use = StreamUi.toggle(this, provider.name + (installed ? "  ·  Instalada" : "  ·  No instalada"),
                    UnifiedCatalogPrefs.selected(this, provider));
            use.setOnCheckedChangeListener((v, checked) -> save("selected_" + provider.key, checked));
            use.setTag("provider:" + provider.key);
            left(use);
            row.addView(use, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(priority(provider, -1));
            row.addView(priority(provider, 1));
            add(content, row);
        }

        description("PROVEEDOR DE ÚLTIMO RECURSO");
        description("Nuvio es un proveedor P2P. Se usa automáticamente cuando ninguno de los"
                + " proveedores seleccionados e instalados tiene el título según TMDB.");
        Switch nuvio = StreamUi.toggle(this, "Nuvio"
                + (UnifiedCatalogPrefs.NUVIO.installedPackage(this) != null
                ? "  ·  Instalada" : "  ·  No instalada"), UnifiedCatalogPrefs.nuvioSelected(this));
        nuvio.setOnCheckedChangeListener((v, checked) -> save("selected_nuvio", checked));
        nuvio.setTag("provider:nuvio");
        left(nuvio);
        add(content, nuvio);

        description("REPRODUCTORES");
        description("Apps para abrir títulos descubiertos en TMDB. Stremio puede abrir fichas; "
                + "otras apps pueden requerir buscar manualmente. No implica disponibilidad del contenido.");
        for (UnifiedPlaybackApps.Known app : UnifiedPlaybackApps.KNOWN) {
            boolean installed = app.installedPackage(this) != null;
            Switch use = StreamUi.toggle(this, app.name + (installed ? "  ·  Instalada" : "  ·  No instalada"),
                    UnifiedPlaybackApps.selected(this, app));
            use.setOnCheckedChangeListener((v, checked) -> save("playback_source_" + app.key, checked));
            use.setTag("playback:" + app.key);
            left(use);
            add(content, use);
        }
        action("Elegir otras apps instaladas para reproducir", v -> chooseOtherPlaybackApps(), "otherPlayback");
        description("Las aplicaciones adicionales se activan manualmente y solo se abren si están instaladas.");
    }

    private void chooseOtherPlaybackApps() {
        List<UnifiedPlaybackApps.App> apps = UnifiedPlaybackApps.otherInstalledApps(this);
        if (apps.isEmpty()) {
            StreamUi.dialog(this).setTitle("No se encontraron otras apps")
                    .setMessage("Las apps de Android TV disponibles ya están incluidas en las listas anteriores.")
                    .setPositiveButton("Aceptar", null).show();
            return;
        }
        CharSequence[] names = new CharSequence[apps.size()];
        boolean[] enabled = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            UnifiedPlaybackApps.App app = apps.get(i);
            names[i] = app.name;
            enabled[i] = UnifiedPlaybackApps.selectedOther(this, app.packageName);
        }
        StreamUi.dialog(this).setTitle("Otras apps instaladas")
                .setMultiChoiceItems(names, enabled, (dialog, which, chosen) ->
                        save("playback_package_" + apps.get(which).packageName, chosen))
                .setPositiveButton("Listo", (dialog, which) -> show(section, "otherPlayback"))
                .show();
    }

    private TextView priority(UnifiedCatalogPrefs.Provider provider, int direction) {
        String tag = provider.key + ":" + direction;
        TextView button = StreamUi.control(this, "", direction < 0 ? R.drawable.uic_arrow_up : R.drawable.uic_arrow_down, false, v -> {
            UnifiedCatalogPrefs.move(this, provider, direction);
            show(section, tag);
        });
        StreamUi.background(button, false, true);
        button.setMinHeight(dp(38));
        button.setMinWidth(dp(38));
        button.setPadding(dp(8), dp(6), dp(8), dp(6));
        button.setContentDescription((direction < 0 ? "Subir prioridad: " : "Bajar prioridad: ") + provider.name);
        button.setTag(tag);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
        size.setMarginStart(dp(6));
        button.setLayoutParams(size);
        return button;
    }

    private void watch() {
        description("Solo se muestran los títulos que las aplicaciones comparten con Android TV Watch Next.");
        toggle("Activar Seguir viendo unificado", UnifiedCatalogPrefs.CONTINUE_ENABLED, true);
        toggle("Mostrar Seguir viendo en Inicio", UnifiedCatalogPrefs.CONTINUE_HOME, true);
        int percent = UnifiedCatalogPrefs.continueMinPercent(this);
        int minutes = UnifiedCatalogPrefs.continueMinMinutes(this);
        String minimum = percent == 0 && minutes == 0 ? "desactivado"
                : percent == 0 ? minutes + " min"
                : minutes == 0 ? percent + "%" : percent + "% o " + minutes + " min";
        action("Progreso mínimo  ·  " + minimum, v -> chooseMinimum(), "minimum");
        toggle("Incluir siguiente episodio", UnifiedCatalogPrefs.CONTINUE_NEXT_EPISODES, true);
        toggle("Consultar canales de progreso (Stremio y Nuvio)", UnifiedCatalogPrefs.CONTINUE_PREVIEWS, true);
        toggle("Ocultar títulos completados (95% o más)", UnifiedCatalogPrefs.CONTINUE_HIDE_FINISHED, true);
        toggle("Incluir otras apps que compartan Watch Next", UnifiedCatalogPrefs.CONTINUE_OTHER_APPS, false);
        action("Antigüedad máxima  ·  " + UnifiedCatalogPrefs.continueMaxAge(this) + " días", v -> chooseAge(), "age");
        description("FUENTES DE SEGUIR VIENDO");
        description("Fuentes que pueden alimentar esta lista:");
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.CONTINUE_SOURCES) {
            boolean installed = provider.installedPackage(this) != null;
            Switch use = StreamUi.toggle(this, provider.name + (installed ? "  ·  Instalada" : "  ·  No instalada"),
                    UnifiedCatalogPrefs.continueSourceSelected(this, provider));
            use.setOnCheckedChangeListener((v, checked) -> save("continue_source_" + provider.key, checked));
            use.setTag("continue:" + provider.key);
            left(use);
            add(content, use);
        }
    }

    private void updates() {
        description("Las actualizaciones se obtienen de GitHub y se comprueban antes de instalarse. Nunca se descargan ni instalan sin tu confirmación.");
        toggle("Comprobar nuevas versiones automáticamente", UnifiedCatalogPrefs.UPDATE_AUTO_CHECK, true);
        action("Buscar actualización ahora", v -> UnifiedAppUpdater.checkNow(this), "check");
        action("Permitir instalar APK desde UnifiedTV", v -> UnifiedAppUpdater.openInstallPermissionSettings(this), "permission");
        installPermissionStatus = description(permissionStatus());
    }

    private String permissionStatus() {
        return UnifiedAppUpdater.canInstall(this) ? "Instalación desde UnifiedTV: permitida."
                : "Instalación desde UnifiedTV: requiere autorización de Android TV.";
    }

    /**
     * One place to finish Android TV setup. Only permissions needed for a feature are
     * requested: powerful accessibility / notification / ADB access stays opt-in.
     * Status is recomputed whenever the system settings screens return to this Activity.
     */
    private void permissionsAndSetup() {
        boolean tmdb = !UnifiedCatalogPrefs.apiKey(this).isEmpty();
        boolean online = UnifiedPermissionChecks.networkPresent(this);
        boolean tv = UnifiedWatchNext.hasPermission(this);
        boolean apk = UnifiedAppUpdater.canInstall(this);
        boolean home = UnifiedPermissionChecks.isDefaultHome(this);

        description("Configura lo que necesita UnifiedTV y comprueba qué funciones están listas. "
                + "Android TV decide qué permisos y pantallas de configuración están disponibles; "
                + "ninguna autorización se concede automáticamente.");

        description("CATÁLOGO Y REPRODUCCIÓN");
        setupRow("Clave de TMDB", tmdb ? "Configurada" : "Pendiente",
                "Necesaria para descubrir películas, series y puntuaciones. Puedes introducirla por QR.",
                v -> pairKey(), "setup-tmdb");
        setupRow("Conexión a Internet", online ? "Red detectada" : "Sin red detectada",
                "Necesaria para TMDB y las actualizaciones. El QR también requiere una red local accesible.",
                v -> openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "ajustes de red"), "setup-network");
        setupRow("Leer canales de Android TV / Seguir viendo", tv ? "Permitido" : "Sin autorizar",
                "Necesario para Watch Next y enlaces de fichas que las plataformas publiquen. "
                        + "No permite leer el historial privado de Netflix u otras apps.",
                v -> requestTvListings(), "setup-tv-listings");
        setupRow("Instalar actualizaciones APK", apk ? "Permitido" : "Sin autorizar",
                "Solo es necesario para instalar las actualizaciones descargadas desde GitHub, "
                        + "siempre tras confirmación.",
                v -> {
                    refreshPermissionsOnReturn = true;
                    UnifiedAppUpdater.openInstallPermissionSettings(this);
                }, "setup-install");

        description("INICIO Y MANDO (OPCIONAL)");
        setupRow("UnifiedTV como pantalla de inicio", home ? "Seleccionado" : "Otro inicio / sin seleccionar",
                "Cambia la aplicación Home predeterminada desde Android TV. No se modifican "
                        + "automáticamente los launchers del fabricante.",
                v -> confirmRecovery(), "setup-home");
        boolean homeKeys = UnifiedPermissionChecks.accessibilityEnabled(this, HomeKeyService.class);
        boolean inputKeys = UnifiedPermissionChecks.accessibilityEnabled(this, InputKeyService.class);
        setupRow("Accesibilidad · teclas Home especiales", homeKeys ? "Activado" : "Desactivado",
                "Solo para mandos de algunos fabricantes. El servicio puede capturar teclas "
                        + "del mando; no es necesario para navegar por UnifiedTV.",
                v -> confirmAccessibility(), "setup-access-home");
        setupRow("Accesibilidad · botón Entradas/HDMI", inputKeys ? "Activado" : "Desactivado",
                "Opcional cuando el televisor no gestiona por sí mismo la tecla de entradas.",
                v -> confirmAccessibility(), "setup-access-input");

        description("COMPATIBILIDAD AVANZADA (OPCIONAL)");
        boolean listener = UnifiedPermissionChecks.notificationListenerEnabled(this);
        setupRow("Reactivación tras cierres del fabricante",
                listener ? "Activada" : "Desactivada",
                "Acceso especial a notificaciones: Android concede un permiso amplio. "
                        + "UnifiedTV no lee su contenido, pero esta función no es necesaria "
                        + "para el catálogo.",
                v -> confirmNotificationListener(), "setup-notification");
        boolean privileged = UnifiedPermissionChecks.hasSecureSettingsPermission(this);
        setupRow("Ajustes seguros de Android (ADB)",
                privileged ? "Concedido" : "No concedido",
                "Solo para restaurar ciertos servicios OEM por herramientas avanzadas. "
                        + "No existe un botón normal de Android TV para concederlo.",
                v -> showAdbHelp(), "setup-adb");

        description("SOLUCIÓN DE PROBLEMAS");
        action("Permisos e información de UnifiedTV en Android",
                v -> openAppSettings(), "setup-app-details");
        action("Actualizar diagnóstico", v -> show(PERMISSIONS_SECTION, "setup-refresh"), "setup-refresh");
        description("Internet, acceso a las apps instaladas y navegación con mando no "
                + "necesitan permisos adicionales. Las opciones avanzadas se dejan apagadas "
                + "a menos que tu televisor las requiera.");
    }

    private void setupRow(String name, String status, String detail,
                          View.OnClickListener click, String tag) {
        action(name + "  ·  " + status, click, tag);
        description(detail);
    }

    private void requestTvListings() {
        if (UnifiedWatchNext.hasPermission(this)) {
            Toast.makeText(this, "El acceso a Watch Next ya está concedido", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{UnifiedWatchNext.PERMISSION},
                UnifiedWatchNext.PERMISSION_REQUEST);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                    int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != UnifiedWatchNext.PERMISSION_REQUEST) return;
        if (section == PERMISSIONS_SECTION) show(section, "setup-tv-listings");
        if (!UnifiedWatchNext.hasPermission(this)) {
            StreamUi.dialog(this).setTitle("Permiso de Android TV sin conceder")
                    .setMessage("Seguir viendo puede continuar vacío. Si el televisor no muestra "
                            + "la solicitud, revisa los permisos de UnifiedTV en los ajustes del sistema. "
                            + "Algunos fabricantes no permiten acceder a los canales de otras apps.")
                    .setPositiveButton("Abrir permisos de la app", (dialog, which) -> openAppSettings())
                    .setNegativeButton("Cerrar", null).show();
        }
    }

    private void confirmAccessibility() {
        StreamUi.dialog(this).setTitle("Servicios de accesibilidad opcionales")
                .setMessage("Android TV puede conceder a los servicios de accesibilidad "
                        + "acceso al comportamiento del mando. Solo habilita HomeKeyService "
                        + "o InputKeyService si las teclas especiales de tu TV no funcionan. "
                        + "Las funciones normales de UnifiedTV no lo requieren.")
                .setPositiveButton("Abrir accesibilidad", (d, w) ->
                        openSystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS,
                                "servicios de accesibilidad"))
                .setNegativeButton("Cancelar", null).show();
    }

    private void confirmNotificationListener() {
        StreamUi.dialog(this).setTitle("Permiso opcional y sensible")
                .setMessage("Android concede a cualquier servicio de acceso a notificaciones "
                        + "un permiso amplio para leerlas. UnifiedTV lo utiliza únicamente para "
                        + "que algunos televisores puedan reactivar su proceso y no lee su contenido. "
                        + "Actívalo solo si lo necesitas.")
                .setPositiveButton("Abrir acceso a notificaciones", (d, w) ->
                        openSystemSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                                "acceso a notificaciones"))
                .setNegativeButton("Cancelar", null).show();
    }

    private void showAdbHelp() {
        String command = "adb shell pm grant " + getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS";
        StreamUi.dialog(this).setTitle("Permiso técnico: solo ADB")
                .setMessage("No es necesario para TMDB, los pósteres ni Seguir viendo. "
                        + "Algunas funciones avanzadas de reparación de teclas lo utilizan. "
                        + "Solo se concede desde un ordenador con depuración ADB activada "
                        + "y proporciona privilegios sobre la configuración del dispositivo.\n\n"
                        + command)
                .setPositiveButton("Entendido", null).show();
    }

    private void openAppSettings() {
        refreshPermissionsOnReturn = true;
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException unavailable) {
            openSystemSettings(Settings.ACTION_SETTINGS, "ajustes de aplicaciones");
        }
    }

    private void openSystemSettings(String action, String feature) {
        refreshPermissionsOnReturn = true;
        try {
            startActivity(new Intent(action));
        } catch (RuntimeException unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                Toast.makeText(this, "Busca " + feature + " en Ajustes del televisor",
                        Toast.LENGTH_LONG).show();
            } catch (RuntimeException unsupported) {
                refreshPermissionsOnReturn = false;
                StreamUi.dialog(this).setTitle("Ajuste no disponible")
                        .setMessage("Este televisor no dispone de un acceso accesible desde UnifiedTV "
                                + "a " + feature + ". Abre los ajustes del fabricante manualmente.")
                        .setPositiveButton("Cerrar", null).show();
            }
        }
    }

    private TextView description(String message) {
        // React parity: uppercase section captions are subdued, letter-spaced
        // group headings instead of paragraphs or decorative cards.
        boolean group = message.equals(message.toUpperCase(java.util.Locale.ROOT))
                && message.matches(".*[A-ZÁÉÍÓÚ].*") && message.length() < 64;
        TextView label = text(message, group ? 11 : 13,
                group ? 0xFF9898A4 : StreamUi.DIM, group);
        if (group) label.setLetterSpacing(.16f);
        label.setPadding(0, dp(group ? 20 : 7), 0, dp(group ? 5 : 9));
        add(content, label);
        return label;
    }

    private void add(LinearLayout box, View item) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(5);
        box.addView(item, lp);
    }

    private void left(View view) { view.setNextFocusLeftId(tabs.get(section).getId()); }

    /**
     * React-style split setting row. The end value and chevron remain visible
     * at TV distance, while the native clickable row retains the focus tag,
     * click handler, TalkBack label and DPAD navigation.
     */
    private void action(String label, View.OnClickListener click, String tag) {
        String marker = "  ·  ";
        int split = label.indexOf(marker);
        String name = split < 0 ? label : label.substring(0, split);
        String value = split < 0 ? "" : label.substring(split + marker.length());
        LinearLayout row = new LinearLayout(this);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(14), dp(6), dp(12), dp(6));
        row.setBackground(StreamUi.buttonBackground(this, false, true));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(click);
        row.setContentDescription(label);
        row.setTag(tag);

        TextView nameView = text(name, 14, StreamUi.WHITE, true);
        nameView.setMaxLines(2);
        nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1));

        TextView valueView = text(value, 12, StreamUi.DIM, false);
        valueView.setMaxWidth(dp(185));
        valueView.setMaxLines(2);
        valueView.setGravity(Gravity.END);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        valueView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (!value.isEmpty()) {
            LinearLayout.LayoutParams meta = new LinearLayout.LayoutParams(-2, -2);
            meta.setMarginStart(dp(8));
            row.addView(valueView, meta);
        }

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.uic_chevron_right);
        chevron.setColorFilter(0xFF91919B);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams arrow = new LinearLayout.LayoutParams(dp(16), dp(16));
        arrow.setMarginStart(dp(10));
        row.addView(chevron, arrow);
        row.setOnFocusChangeListener((view, focused) -> {
            valueView.setTextColor(focused ? StreamUi.WHITE : StreamUi.DIM);
            chevron.setColorFilter(focused ? StreamUi.WHITE : 0xFF91919B);
        });
        left(row);
        add(content, row);
    }

    private void toggle(String title, String key, boolean fallback) {
        Switch check = StreamUi.toggle(this, title, UnifiedCatalogPrefs.prefs(this).getBoolean(key, fallback));
        check.setOnCheckedChangeListener((view, value) -> save(key, value));
        check.setTag("toggle:" + key);
        left(check);
        add(content, check);
    }

    private void save(String key, boolean value) {
        UnifiedCatalogPrefs.prefs(this).edit().putBoolean(key, value).apply();
    }

    private void pairKey() {
        if (pairing != null) return;
        try {
            pairing = new TmdbPairingDialog(this, () -> {
                show(section, section == PERMISSIONS_SECTION ? "setup-tmdb" : "tmdb");
                Toast.makeText(this, "TMDB conectado", Toast.LENGTH_SHORT).show();
            }, () -> pairing = null);
        } catch (IOException | SecurityException e) {
            StreamUi.dialog(this).setTitle("No se ha podido abrir el QR")
                    .setMessage("Comprueba que la TV esté conectada por Wi-Fi o Ethernet e inténtalo de nuevo.")
                    .setNegativeButton("Cerrar", null)
                    .setPositiveButton("Introducir con el mando", (dialog, which) -> editKey())
                    .show();
        }
    }

    private void editKey() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        StreamUi.field(input);
        input.setText(UnifiedCatalogPrefs.apiKey(this));
        input.setHint("Clave de TMDB v3");
        StreamUi.dialog(this).setTitle("Conectar TMDB").setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.KEY,
                            input.getText().toString().trim()).apply();
                    show(section, section == PERMISSIONS_SECTION ? "setup-tmdb" : "tmdb-manual");
                }).show();
    }

    private void chooseRegion() {
        String[] regions = {"ES", "PT", "FR", "DE", "IT", "GB", "US", "MX", "AR"};
        StreamUi.dialog(this).setTitle("Región de disponibilidad")
                .setItems(regions, (dialog, index) -> {
                    UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.REGION, regions[index]).apply();
                    show(section, "region");
                }).show();
    }

    private void chooseMinimum() {
        LinearLayout fields = StreamUi.column(this);
        fields.setPadding(dp(24), dp(8), dp(24), dp(8));
        fields.addView(text("Se muestra el título cuando alcanza cualquiera de los mínimos. "
                + "Introduce 0 para desactivar ese criterio.", 14, StreamUi.DIM, false));
        EditText percent = addMinimumField(fields, "Porcentaje reproducido", UnifiedCatalogPrefs.continueMinPercent(this));
        EditText minutes = addMinimumField(fields, "Minutos reproducidos", UnifiedCatalogPrefs.continueMinMinutes(this));
        android.app.AlertDialog dialog = StreamUi.dialog(this).setTitle("Progreso mínimo")
                .setView(fields)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null).create();
        dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            Integer percentValue = minimumValue(percent, 100);
            if (percentValue == null) return;
            Integer minutesValue = minimumValue(minutes, 1440);
            if (minutesValue == null) return;
            UnifiedCatalogPrefs.prefs(this).edit()
                    .putInt(UnifiedCatalogPrefs.CONTINUE_MIN_PERCENT, percentValue)
                    .putInt(UnifiedCatalogPrefs.CONTINUE_MIN_MINUTES, minutesValue).apply();
            dialog.dismiss();
            show(section, "minimum");
        });
    }

    private EditText addMinimumField(LinearLayout fields, String label, int value) {
        TextView caption = text(label, 14, StreamUi.WHITE, true);
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        caption.setLabelFor(input.getId());
        fields.addView(caption);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(Integer.toString(value));
        input.setSelectAllOnFocus(true);
        StreamUi.field(input);
        add(fields, input);
        return input;
    }

    private Integer minimumValue(EditText input, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value >= 0 && value <= max) return value;
        } catch (NumberFormatException ignored) {}
        input.setError("Introduce un número entre 0 y " + max);
        input.requestFocus();
        return null;
    }

    private void chooseAge() {
        int[] days = {30, 60, 90, 180, 365};
        String[] options = {"30 días", "60 días", "90 días", "180 días", "365 días"};
        StreamUi.dialog(this).setTitle("Antigüedad máxima").setItems(options, (dialog, index) -> {
            UnifiedCatalogPrefs.prefs(this).edit().putInt(UnifiedCatalogPrefs.CONTINUE_MAX_AGE, days[index]).apply();
            show(section, "age");
        }).show();
    }
}
