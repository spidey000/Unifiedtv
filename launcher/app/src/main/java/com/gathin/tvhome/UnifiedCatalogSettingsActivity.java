package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Simple D-pad friendly, local-only streaming preferences. */
public final class UnifiedCatalogSettingsActivity extends Activity {
    private LinearLayout sections;
    private LinearLayout content;
    private ScrollView scroller;
    private TextView installPermissionStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        UnifiedAppUpdater.onResume(this);
        if (installPermissionStatus != null)
            installPermissionStatus.setText(UnifiedAppUpdater.canInstall(this)
                    ? "Instalación desde UnifiedTV: permitida."
                    : "Instalación desde UnifiedTV: requiere autorización de Android TV.");
    }

    private int dp(int n) { return CinemaUi.dp(this, n); }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size) {
        TextView t = CinemaUi.text(this, value, size, size < 17 ? CinemaUi.MUTED : Color.WHITE);
        t.setPadding(dp(6), dp(5), dp(6), dp(5));
        return t;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = CinemaUi.button(this, label, listener);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
        size.setMargins(dp(4), dp(3), dp(4), dp(3));
        button.setLayoutParams(size);
        return button;
    }

    private void section(String title) {
        content = column();
        content.setBackground(CinemaUi.shape(this, CinemaUi.SURFACE, 12, Color.TRANSPARENT));
        content.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(10);
        sections.addView(content, size);
        TextView label = text(title, 20);
        label.setTypeface(null, Typeface.BOLD);
        content.addView(label);
    }

    private void render() {
        render(null);
    }

    private void render(String focusTag) {
        int scrollY = scroller == null ? 0 : scroller.getScrollY();
        LinearLayout root = column();
        root.setBackgroundColor(CinemaUi.BASE);
        root.setPadding(dp(28), dp(10), dp(28), 0);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("Ajustes · UnifiedTV", 26);
        heading.setTextColor(CinemaUi.ACCENT);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("Volver al catálogo", v -> finish()));
        root.addView(header);
        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        sections = column();
        sections.setPadding(0, 0, 0, dp(22));
        scroller.addView(sections);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        section("Inicio");
        CheckBox home = CinemaUi.toggle(this);
        home.setText("Mostrar catálogo al pulsar Home (Atrás abre Aplicaciones)");
        home.setChecked(UnifiedCatalogPrefs.enabled(this));
        home.setOnCheckedChangeListener((v, checked) ->
                UnifiedCatalogPrefs.prefs(this).edit().putBoolean(UnifiedCatalogPrefs.ON_HOME, checked).apply());
        content.addView(home);

        section("TMDB");
        String savedKey = UnifiedCatalogPrefs.apiKey(this);
        content.addView(button(savedKey.isEmpty() ? "Configurar clave API TMDB" : "Cambiar clave API TMDB (configurada)",
                v -> editKey()));
        content.addView(text("Consigue una API key v3 de TMDB. No incluyas claves en GitHub: se guardan en las preferencias locales del dispositivo.", 14));

        section("País y disponibilidad");
        content.addView(button("País: " + UnifiedCatalogPrefs.region(this), v -> chooseRegion()));
        CheckBox free = CinemaUi.toggle(this);
        free.setText("Incluir plataformas gratuitas y con anuncios");
        free.setChecked(UnifiedCatalogPrefs.allowsFree(this));
        free.setOnCheckedChangeListener((v, checked) ->
                UnifiedCatalogPrefs.prefs(this).edit().putBoolean(UnifiedCatalogPrefs.INCLUDE_FREE, checked).apply());
        content.addView(free);

        section("Plataformas • orden de prioridad");
        content.addView(text("Desmarca las que no usas. Si el título está en varias, se intentará primero la de arriba. La detección solo comprueba instalación, no suscripción.", 14));
        List<UnifiedCatalogPrefs.Provider> providers = UnifiedCatalogPrefs.ordered(this);
        for (UnifiedCatalogPrefs.Provider provider : providers) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            String installed = provider.installedPackage(this) == null ? " (no instalada)" : " (instalada)";
            CheckBox enabled = CinemaUi.toggle(this);
            enabled.setText(provider.name + installed);
            enabled.setChecked(UnifiedCatalogPrefs.selected(this, provider));
            enabled.setOnCheckedChangeListener((v, checked) ->
                    UnifiedCatalogPrefs.prefs(this).edit()
                            .putBoolean("selected_" + provider.key, checked).apply());
            row.addView(enabled, new LinearLayout.LayoutParams(0, dp(46), 1));
            row.addView(priority(provider, -1));
            row.addView(priority(provider, 1));
            content.addView(row);
        }

        section("Seguir viendo • fuentes y privacidad");
        content.addView(text("UnifiedTV solo puede leer los títulos compartidos con Android TV (Watch Next). "
                + "No puede consultar el historial privado de Netflix, Prime Video o las demás apps.", 14));
        addToggle("Activar Seguir viendo unificado", UnifiedCatalogPrefs.CONTINUE_ENABLED, true);
        addToggle("Mostrar Seguir viendo encima del catálogo", UnifiedCatalogPrefs.CONTINUE_HOME, true);
        addToggle("Incluir siguiente episodio", UnifiedCatalogPrefs.CONTINUE_NEXT_EPISODES, true);
        addToggle("Consultar canales 'Continue Watching' cuando existan (Stremio/Nuvio)",
                UnifiedCatalogPrefs.CONTINUE_PREVIEWS, true);
        addToggle("Ocultar títulos completados (95% o más)",
                UnifiedCatalogPrefs.CONTINUE_HIDE_FINISHED, true);
        addToggle("Incluir otras aplicaciones instaladas que compartan Watch Next",
                UnifiedCatalogPrefs.CONTINUE_OTHER_APPS, false);
        int days = UnifiedCatalogPrefs.continueMaxAge(this);
        content.addView(button("Antigüedad máxima: " + days + " días", v -> {
            final int[] choices = {30, 60, 90, 180, 365};
            final String[] labels = {"30 días", "60 días", "90 días", "180 días", "365 días"};
            new AlertDialog.Builder(this).setTitle("Ocultar contenido inactivo")
                    .setItems(labels, (dialog, which) -> {
                        UnifiedCatalogPrefs.prefs(this).edit()
                                .putInt(UnifiedCatalogPrefs.CONTINUE_MAX_AGE, choices[which]).apply();
                        render();
                    }).show();
        }));
        content.addView(text("Qué aplicaciones pueden alimentar la fila:", 16));
        for (UnifiedCatalogPrefs.Provider provider : UnifiedCatalogPrefs.CONTINUE_SOURCES) {
            boolean installed = provider.installedPackage(this) != null;
            CheckBox watching = CinemaUi.toggle(this);
            watching.setText(provider.name + (installed ? " · instalada" : " · no instalada"));
            watching.setChecked(UnifiedCatalogPrefs.continueSourceSelected(this, provider));
            watching.setOnCheckedChangeListener((v, checked) ->
                    UnifiedCatalogPrefs.prefs(this).edit()
                            .putBoolean("continue_source_" + provider.key, checked).apply());
            content.addView(watching);
        }
        content.addView(text("Permiso de Android TV: la app te lo pedirá al abrir Seguir viendo. "
                + "Si alguna plataforma no publica progreso visible, no aparecerá hasta que lo haga.", 14));

        section("Actualizaciones");
        addToggle("Comprobar nuevas versiones automáticamente (recomendado)",
                UnifiedCatalogPrefs.UPDATE_AUTO_CHECK, true);
        content.addView(text("La búsqueda automática se realiza al abrir UnifiedTV, como máximo una vez cada 24 horas. "
                + "No descarga ni instala nada sin tu confirmación. Las versiones se obtienen de las releases oficiales de GitHub.", 14));
        content.addView(button("Buscar actualización ahora",
                v -> UnifiedAppUpdater.checkNow(this)));
        content.addView(button("Permitir instalar APK desde UnifiedTV",
                v -> UnifiedAppUpdater.openInstallPermissionSettings(this)));
        installPermissionStatus = text(UnifiedAppUpdater.canInstall(this)
                ? "Instalación desde UnifiedTV: permitida."
                : "Instalación desde UnifiedTV: requiere autorización de Android TV.", 14);
        content.addView(installPermissionStatus);

        section("Información");
        content.addView(text("TMDB informa de plataformas por país, pero no acredita una suscripción activa ni garantiza que una app permita abrir la ficha concreta. Si la app no admite búsqueda externa, se abre su inicio.", 14));
        View target = focusTag == null ? null : sections.findViewWithTag(focusTag);
        if (scrollY > 0 || target != null) scroller.post(() -> {
            scroller.scrollTo(0, scrollY);
            if (target != null) target.requestFocus();
        });
    }

    private Button priority(UnifiedCatalogPrefs.Provider provider, int direction) {
        String tag = provider.key + ":" + direction;
        Button control = button(direction < 0 ? "↑" : "↓", v -> {
            UnifiedCatalogPrefs.move(this, provider, direction);
            render(tag);
        });
        control.setTag(tag);
        control.setContentDescription((direction < 0 ? "Subir prioridad: " : "Bajar prioridad: ") + provider.name);
        return control;
    }

    private void addToggle(String title, String key, boolean defaultValue) {
        CheckBox toggle = CinemaUi.toggle(this);
        toggle.setText(title);
        toggle.setChecked(UnifiedCatalogPrefs.prefs(this).getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((view, checked) ->
                UnifiedCatalogPrefs.prefs(this).edit().putBoolean(key, checked).apply());
        content.addView(toggle);
    }

    private void editKey() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(UnifiedCatalogPrefs.apiKey(this));
        input.setHint("TMDB API Key v3");
        new AlertDialog.Builder(this)
                .setTitle("Clave de TMDB (API v3)")
                .setMessage("Solo se usará para consultar TMDB desde esta TV.")
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String key = input.getText().toString().trim();
                    UnifiedCatalogPrefs.prefs(this).edit().putString(UnifiedCatalogPrefs.KEY, key).apply();
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void chooseRegion() {
        final String[] regions = {"ES", "PT", "FR", "DE", "IT", "GB", "US", "MX", "AR"};
        new AlertDialog.Builder(this).setTitle("País de disponibilidad")
                .setItems(regions, (dialog, index) -> {
                    UnifiedCatalogPrefs.prefs(this).edit()
                            .putString(UnifiedCatalogPrefs.REGION, regions[index]).apply();
                    render();
                })
                .show();
    }
}
