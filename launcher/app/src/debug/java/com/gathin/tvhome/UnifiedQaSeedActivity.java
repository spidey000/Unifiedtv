package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Screenshot QA ONLY, included in debug builds of the isolated test branch.
 * Generates harmless off-line synthetic TMDB-style cache entries so screenshots
 * can exercise the actual production carousel / 2-column layout and focus logic.
 * Never included in a signed release.
 */
public final class UnifiedQaSeedActivity extends Activity {
    private static final int COUNT = 24;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try {
                seed();
                runOnUiThread(() -> {
                    String page = getIntent().getStringExtra("qa_page");
                    if ("settings".equals(page)) {
                        Intent open = new Intent(this, UnifiedCatalogSettingsActivity.class)
                                .putExtra(UnifiedCatalogSettingsActivity.EXTRA_SECTION,
                                        getIntent().getIntExtra("qa_section", 1));
                        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(open);
                    } else {
                        Intent open = new Intent(this, UnifiedCatalogActivity.class)
                                .putExtra("unifiedtv_qa", true)
                                .putExtra("unifiedtv_qa_page",
                                        getIntent().getStringExtra("qa_target") == null ? "HOME"
                                                : getIntent().getStringExtra("qa_target"))
                                .putExtra("unifiedtv_qa_focus",
                                        getIntent().getStringExtra("qa_focus") == null ? ""
                                                : getIntent().getStringExtra("qa_focus"))
                                .putExtra("unifiedtv_qa_details",
                                        getIntent().getBooleanExtra("qa_details", false));
                        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(open);
                    }
                    finish();
                });
            } catch (Exception exception) {
                android.util.Log.e("UnifiedTvQa", "Could not seed QA fixtures", exception);
                finish();
            }
        }).start();
    }

    private void seed() throws Exception {
        UnifiedCatalogPrefs.prefs(this).edit()
                .putString(UnifiedCatalogPrefs.KEY, "qa-not-a-real-tmdb-api-key")
                .putString(UnifiedCatalogPrefs.REGION, "ES")
                .putBoolean(UnifiedCatalogPrefs.UPDATE_AUTO_CHECK, false)
                .putBoolean(UnifiedCatalogPrefs.CONTINUE_HOME, false)
                .putBoolean(UnifiedCatalogPrefs.CATALOG_COLUMNS,
                        "columns".equals(getIntent().getStringExtra("qa_layout")))
                .commit();
        android.util.Log.i("UnifiedTvQa", "Columns readback "
                + UnifiedCatalogPrefs.twoColumnSections(this));
        File posters = new File(getCacheDir(), "unifiedtv_posters");
        File json = new File(getCacheDir(), "unifiedtv_json");
        if ((!posters.isDirectory() && !posters.mkdirs())
                || (!json.isDirectory() && !json.mkdirs()))
            throw new IllegalStateException("QA cache folder unavailable");
        for (int i = 1; i <= COUNT; i++) poster(posters, i);

        for (String type : new String[]{"movie", "tv"}) {
            String[] urls = new String[]{
                    "/trending/" + type + "/day?language=es-ES",
                    "/trending/" + type + "/week?language=es-ES",
                    "movie".equals(type) ? "/movie/now_playing?language=es-ES&region=ES"
                            : "/tv/on_the_air?language=es-ES",
                    "/" + type + "/popular?language=es-ES",
                    "/" + type + "/top_rated?language=es-ES"
            };
            for (int category = 0; category < urls.length; category++) {
                JSONArray items = new JSONArray();
                for (int i = 1; i <= COUNT; i++) {
                    int number = ((i + 4 * category - 1) % COUNT) + 1;
                    JSONObject item = new JSONObject();
                    item.put("id", ("movie".equals(type) ? 10000 : 20000) + number);
                    item.put("media_type", type);
                    item.put("poster_path", String.format(java.util.Locale.ROOT,
                            "/qa_poster_%02d.jpg", number));
                    item.put("backdrop_path", "");
                    item.put("vote_average", 6.3 + (number % 30) / 10.0);
                    item.put("vote_count", 150 + number * 19);
                    item.put("overview", "Metadatos sintéticos de prueba para inspeccionar"
                            + " el foco, las proporciones 2:3 y el recorte de las tarjetas."
                            + " Este título no existe en TMDB.");
                    item.put("release_date", "2026-07-09");
                    item.put("first_air_date", "2026-07-09");
                    item.put("title", String.format(java.util.Locale.ROOT,
                            "PELÍCULA QA %02d", number));
                    item.put("name", String.format(java.util.Locale.ROOT,
                            "SERIE QA %02d", number));
                    items.put(item);
                }
                writeJson(json, urls[category], new JSONObject().put("results", items));
            }
            JSONObject full = new JSONObject()
                    .put("title", "PELÍCULA QA 01")
                    .put("original_title", "QA Movie One")
                    .put("overview", "Sinopsis completa de prueba. Una aventura ficticia"
                            + " creada únicamente para comprobar la maquetación de UnifiedTV.")
                    .put("runtime", 124)
                    .put("release_date", "2026-07-09")
                    .put("tagline", "Prueba visual de interfaz")
                    .put("vote_count", 200)
                    .put("status", "Released")
                    .put("genres", new JSONArray()
                            .put(new JSONObject().put("name", "Aventura"))
                            .put(new JSONObject().put("name", "Ciencia ficción")))
                    .put("credits", new JSONObject()
                            .put("cast", new JSONArray()
                                    .put(new JSONObject().put("name", "Actor QA Uno")))
                            .put("crew", new JSONArray()
                                    .put(new JSONObject().put("name", "Directora QA Uno")
                                            .put("job", "Director"))));
            writeJson(json, "/movie/10001?language=es-ES&append_to_response=credits", full);
        }
    }

    private void writeJson(File dir, String endpoint, JSONObject data) throws Exception {
        File output = new File(dir, "v2_ES_"
                + Integer.toHexString(endpoint.hashCode()) + ".json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(data.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void poster(File dir, int index) throws Exception {
        final int width = 780, height = 1170;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int hue = (index * 53) % 360;
        int top = Color.HSVToColor(new float[]{hue, .72f, .54f});
        int bottom = Color.HSVToColor(new float[]{(hue + 35) % 360, .88f, .10f});
        paint.setShader(new LinearGradient(0, 0, width, height,
                top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setColor(0x33FFFFFF);
        c.drawCircle(width * .77f, height * .31f, 215, paint);
        c.drawCircle(width * .16f, height * .60f, 170, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(16);
        paint.setColor(0xE6FFDF6C);
        c.drawRoundRect(new RectF(12, 12, width - 12, height - 12), 28, 28, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        paint.setColor(Color.WHITE);
        paint.setTextSize(170);
        c.drawText("QA", 80, 570, paint);
        paint.setTextSize(135);
        c.drawText(String.format(java.util.Locale.ROOT, "%02d", index), 80, 725, paint);
        paint.setTextSize(49);
        c.drawText("2:3 • 780 × 1170", 78, 900, paint);
        paint.setTextSize(39);
        c.drawText("POSTER SINTÉTICO", 78, 960, paint);
        String key = "w780/qa_poster_" + String.format(java.util.Locale.ROOT, "%02d", index)
                + ".jpg";
        File output = new File(dir, Integer.toHexString(key.hashCode()) + ".jpg");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 89, stream);
        } finally {
            bitmap.recycle();
        }
    }
}
