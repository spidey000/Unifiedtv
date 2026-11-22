package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Split-pane settings for the streaming interface; existing preference names remain unchanged. */
public final class UnifiedCatalogSettingsActivity extends Activity {
    private static final String[] SECTIONS = {"Catálogo", "Plataformas", "Seguir viendo", "Actualizaciones", "Información"};
    private final List<TextView> tabs = new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TextView installPermissionStatus;
    private int section;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(StreamUi.BLACK);
        LinearLayout rail = StreamUi.column(this);
        rail.setBackgroundColor(0xFF101014);
        rail.setPadding(dp(14), dp(16), dp(12), dp(12));
        TextView title = text("Ajustes", 24, StreamUi.RED, true);
        title.setPadding(dp(10), 0, 0, dp(14));
        rail.addView(title);
        for (int i = 0; i < SECTIONS.length; i++) {
            final int index = i;
            TextView tab = StreamUi.control(this, SECTIONS[i], false, v -> show(index, null));
            tabs.add(tab);
            add(rail, tab);
        }
        add(rail, StreamUi.control(this, "Volver", false, v -> finish()));
        root.addView(rail, new LinearLayout.LayoutParams(dp(178), -1));
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        content = StreamUi.column(this);
        content.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
        show(state == null ? 0 : state.getInt("section", 0), null);
        tabs.get(section).requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("section", section);
    }

    @Override protected void onResume() {
        super.onResume();
        UnifiedAppUpdater.onResume(this);
        if (installPermissionStatus != null) installPermissionStatus.setText(permissionStatus());
    }

    private int dp(int n) { return StreamUi.dp(this, n); }
    private TextView text(String value, int size, int color, boolean bold) {
        return StreamUi.text(this, value, size, color, bold);
    }

    private void show(int selected, String focusTag) {
        section = selected;
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).setSelected(i == selected);
        content.removeAllViews();
        installPermissionStatus = null;
        content.addView(text(SECTIONS[selected], 32, StreamUi.WHITE, true));
        switch (selected) {
            case 0: catalog(); break;
            case 1: providers(); break;
            case 2: watch(); break;
            case 3: updates(); break;
            default:
                description("UnifiedTV combina resultados de TMDB con la disponibilidad de JustWatch. No puede comprobar suscripciones, ni garantiza que una plataforma abra la ficha exacta. No accede al historial privado de tus apps.");
        }
        if (focusTag == null) scroll.scrollTo(0, 0);
        else {
            View target = content.findViewWithTag(focusTag);
            if (target != null) target.requestFocus();
        }
    }

    private void catalog() {
        description("Elige qué se muestra al abrir UnifiedTV y conecta el catálogo de películas y series.");
        toggle("Mostrar catálogo al pulsar Home", UnifiedCatalogPrefs.ON_HOME, true);
        action(UnifiedCatalogPrefs.apiKey(this).isEmpty() ? "Conectar TMDB" : "Cambiar clave de TMDB", v -> editKey(), "tmdb");
        action("Región de disponibilidad  ·  " + UnifiedCatalogPrefs.region(this), v -> chooseRegion(), "region");
        toggle("Incluir servicios gratuitos y con anuncios", UnifiedCatalogPrefs.INCLUDE_FREE, true);
        description("La clave personal v3 de TMDB se guarda únicamente en esta TV.");
    }

    private void providers() {
        description("Selecciona las plataformas que usas. Las de arriba se intentan primero cuando hay varias opciones.");
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.ordered(this)) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            boolean installed = provider.installedPackage(this) != null;
            CheckBox use = StreamUi.toggle(this, provider.name + (installed ? "  ·  Instalada" : "  ·  No instalada"),
                    UnifiedCatalogPrefs.selected(this, provider));
            use.setOnCheckedChangeListener((v, checked) -> save("selected_" + provider.key, checked));
            left(use);
            row.addView(use, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(priority(provider, -1));
            row.addView(priority(provider, 1));
            add(content, row);
        }
    }

    private TextView priority(UnifiedCatalogPrefs.Provider provider, int direction) {
        String tag = provider.key + ":" + direction;
        TextView button = StreamUi.control(this, direction < 0 ? "↑" : "↓", false, v -> {
            UnifiedCatalogPrefs.move(this, provider, direction);
            show(section, tag);
        });
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
        toggle("Incluir siguiente episodio", UnifiedCatalogPrefs.CONTINUE_NEXT_EPISODES, true);
        toggle("Consultar canales de progreso (Stremio y Nuvio)", UnifiedCatalogPrefs.CONTINUE_PREVIEWS, true);
        toggle("Ocultar títulos completados (95% o más)", UnifiedCatalogPrefs.CONTINUE_HIDE_FINISHED, true);
        toggle("Incluir otras apps que compartan Watch Next", UnifiedCatalogPrefs.CONTINUE_OTHER_APPS, false);
        action("Antigüedad máxima  ·  " + UnifiedCatalogPrefs.continueMaxAge(this) + " días", v -> chooseAge(), "age");
        description("Fuentes que pueden alimentar esta lista:");
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.CONTINUE_SOURCES) {
            boolean installed = provider.installedPackage(this) != null;
            CheckBox use = StreamUi.toggle(this, provider.name + (installed ? "  ·  Instalada" : "  ·  No instalada"),
                    UnifiedCatalogPrefs.continueSourceSelected(this, provider));
            use.setOnCheckedChangeListener((v, checked) -> save("continue_source_" + provider.key, checked));
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

    private TextView description(String message) {
        TextView label = text(message, 15, StreamUi.DIM, false);
        label.setPadding(0, dp(9), 0, dp(9));
        add(content, label);
        return label;
    }

    private void add(LinearLayout box, View item) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(5);
        box.addView(item, lp);
    }

    private void left(View view) { view.setNextFocusLeftId(tabs.get(section).getId()); }

    private void action(String label, View.OnClickListener click, String tag) {
        TextView button = StreamUi.control(this, label, false, click);
        button.setTag(tag);
        left(button);
        add(content, button);
    }

    private void toggle(String title, String key, boolean fallback) {
        CheckBox check = StreamUi.toggle(this, title, UnifiedCatalogPrefs.prefs(this).getBoolean(key, fallback));
        check.setOnCheckedChangeListener((view, value) -> save(key, value));
        left(check);
        add(content, check);
    }

    private void save(String key, boolean value) {
        UnifiedCatalogPrefs.prefs(this).edit().putBoolean(key, value).apply();
    }

    private void editKey() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(UnifiedCatalogPrefs.apiKey(this));
        input.setHint("Clave de TMDB v3");
        new AlertDialog.Builder(this).setTitle("Conectar TMDB").setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.KEY,
                            input.getText().toString().trim()).apply();
                    show(section, "tmdb");
                }).show();
    }

    private void chooseRegion() {
        String[] regions = {"ES", "PT", "FR", "DE", "IT", "GB", "US", "MX", "AR"};
        new AlertDialog.Builder(this).setTitle("Región de disponibilidad")
                .setItems(regions, (dialog, index) -> {
                    UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.REGION, regions[index]).apply();
                    show(section, "region");
                }).show();
    }

    private void chooseAge() {
        int[] days = {30, 60, 90, 180, 365};
        String[] options = {"30 días", "60 días", "90 días", "180 días", "365 días"};
        new AlertDialog.Builder(this).setTitle("Antigüedad máxima").setItems(options, (dialog, index) -> {
            UnifiedCatalogPrefs.prefs(this).edit().putInt(UnifiedCatalogPrefs.CONTINUE_MAX_AGE, days[index]).apply();
            show(section, "age");
        }).show();
    }
}
