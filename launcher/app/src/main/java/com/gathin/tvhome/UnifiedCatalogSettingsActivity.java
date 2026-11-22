package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
    static final String EXTRA_SECTION = "unifiedtv_settings_section";
    static final int PLATFORMS_SECTION = 2;
    private static final String[] SECTIONS = {"Apariencia", "Catálogo", "Plataformas", "Seguir viendo",
            "Actualizaciones", "Launcher", "Información"};
    private static final int[] SECTION_ICONS = {R.drawable.uic_palette, R.drawable.uic_film, R.drawable.ic_apps,
            R.drawable.uic_layers, R.drawable.uic_download, R.drawable.uic_house, R.drawable.uic_info};
    private static final int[] ACCENTS = {StreamUi.DEFAULT_ACCENT, 0xFFFF9B45, 0xFF26BC91, 0xFF468DFF, 0xFFB472F1};
    private static final String[] ACCENT_NAMES = {"Rojo", "Ámbar", "Verde", "Azul", "Violeta"};
    private final List<TextView> tabs = new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TextView installPermissionStatus;
    private int section;
    private String restoredFocus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(StreamUi.BLACK);
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
            StreamUi.background(tab, false, true);
            tabs.add(tab);
            add(rail, tab);
        }
        add(rail, StreamUi.control(this, "Volver", R.drawable.uic_arrow_left, false, v -> finish()));
        railScroll.addView(rail);
        root.addView(railScroll, new LinearLayout.LayoutParams(dp(178), -1));
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        content = StreamUi.column(this);
        content.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
        int selected = state == null ? getIntent().getIntExtra(EXTRA_SECTION, 0) : state.getInt("section", 0);
        if (selected < 0 || selected >= SECTIONS.length) selected = 0;
        show(selected, state == null ? null : state.getString("focus"));
        if (state == null || state.getString("focus") == null || getCurrentFocus() == null) {
            tabs.get(section).requestFocus();
        }
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
            case 0: appearance(); break;
            case 1: catalog(); break;
            case PLATFORMS_SECTION: providers(); break;
            case 3: watch(); break;
            case 4: updates(); break;
            case 5:
                description("Ajustes del dock, cuadrícula, fondo, reloj y app de inicio.");
                action("Abrir ajustes del launcher", v -> startActivity(new Intent(this, LauncherSettingsActivity.class)), "launcher");
                break;
            default:
                description("UnifiedTV combina resultados de TMDB con la disponibilidad de JustWatch. No puede comprobar suscripciones, ni garantiza que una plataforma abra la ficha exacta. No accede al historial privado de tus apps.");
        }
        if (focusTag == null) scroll.scrollTo(0, 0);
        else {
            View target = content.findViewWithTag(focusTag);
            if (target != null) target.requestFocus();
        }
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
            android.graphics.drawable.GradientDrawable swatch = StreamUi.shape(this, color, 10, StreamUi.BORDER);
            swatch.setBounds(0, 0, dp(15), dp(15));
            sample.setCompoundDrawablesRelative(swatch, null, null, null);
            sample.setCompoundDrawablePadding(dp(6));
            sample.setPadding(dp(7), 0, dp(7), 0);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            size.setMarginEnd(dp(5));
            choices.addView(sample, size);
        }
        add(content, choices);
        action("Color personalizado  ·  " + hex(active), v -> customAccent(), "custom");
        action("Restablecer el color", v -> setAccent(StreamUi.DEFAULT_ACCENT, "custom"), "reset");
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
        TextView button = StreamUi.control(this, "", direction < 0 ? R.drawable.uic_arrow_up : R.drawable.uic_arrow_down, false, v -> {
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
        TextView button = StreamUi.control(this, label, R.drawable.uic_chevron_right, false, click);
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
        StreamUi.field(input);
        input.setText(UnifiedCatalogPrefs.apiKey(this));
        input.setHint("Clave de TMDB v3");
        StreamUi.dialog(this).setTitle("Conectar TMDB").setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.KEY,
                            input.getText().toString().trim()).apply();
                    show(section, "tmdb");
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
