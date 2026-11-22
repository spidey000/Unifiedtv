package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
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
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setPadding(dp(7), dp(8), dp(7), dp(8));
        return t;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private void section(String title) {
        TextView label = text(title, 24);
        label.setPadding(0, dp(24), 0, dp(8));
        content.addView(label);
    }

    private void render() {
        ScrollView scroller = new ScrollView(this);
        scroller.setBackgroundColor(Color.rgb(12, 16, 21));
        content = column();
        content.setPadding(dp(48), dp(24), dp(48), dp(48));
        scroller.addView(content);
        setContentView(scroller);

        content.addView(text("Ajustes de UnifiedTV", 30));
        content.addView(text("Catálogo de TMDB y plataformas instaladas; todas las preferencias se guardan solo en esta TV.", 15));

        section("Inicio");
        CheckBox home = new CheckBox(this);
        home.setText("Mostrar catálogo al pulsar Home (Atrás abre el launcher original)");
        home.setTextColor(Color.WHITE);
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
        CheckBox free = new CheckBox(this);
        free.setText("Incluir plataformas gratuitas y con anuncios");
        free.setTextColor(Color.WHITE);
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
            CheckBox enabled = new CheckBox(this);
            enabled.setText(provider.name + installed);
            enabled.setTextColor(Color.WHITE);
            enabled.setTextSize(16);
            enabled.setChecked(UnifiedCatalogPrefs.selected(this, provider));
            enabled.setOnCheckedChangeListener((v, checked) ->
                    UnifiedCatalogPrefs.prefs(this).edit()
                            .putBoolean("selected_" + provider.key, checked).apply());
            row.addView(enabled, new LinearLayout.LayoutParams(0, dp(60), 1));
            row.addView(button("↑", v -> { UnifiedCatalogPrefs.move(this, provider, -1); render(); }));
            row.addView(button("↓", v -> { UnifiedCatalogPrefs.move(this, provider, 1); render(); }));
            content.addView(row);
        }

        section("Información");
        content.addView(text("TMDB informa de plataformas por país, pero no acredita una suscripción activa ni garantiza que una app permita abrir la ficha concreta. Si la app no admite búsqueda externa, se abre su inicio.", 14));
        content.addView(button("Volver al catálogo", v -> finish()));
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
