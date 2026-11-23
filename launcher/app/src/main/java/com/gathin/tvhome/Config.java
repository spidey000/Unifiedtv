package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Device-specific remote and HOME compatibility configuration.
 *
 * UnifiedTV's cinematic UI, catalogue and permissions use UnifiedCatalogPrefs.
 * This class intentionally exposes no old dock, wallpaper, clock, holiday or
 * theme settings. Existing app-specific config.json files may still configure
 * only the allowlisted OEM remote keys below.
 */
final class Config {
    static final String FILE_NAME = "config.json";
    static final String SETTINGS_NAME = "launcher_settings";
    private static final int MAX_JSON_BYTES = 128 * 1024;

    final String locale;
    final String liveTvPackage;
    final boolean bootToLiveTv;
    final boolean openOnStartChoice;
    final boolean keyFilter;
    final boolean gamepadPassthrough;
    final boolean consumeVolumeKeys;
    final boolean volumePausesFilter;
    final boolean settingsKeyPanel;
    final boolean settingsKeyClearTask;
    final boolean restoreKeyServices;
    final boolean homeLongPressHome;
    final boolean powerOnHome;
    final boolean powerOnHomeAlways;
    final String stockHomeRedirect;
    final int[] settingsKeycodes;
    final int[] inputKeycodes;
    final List<String> appKeys;

    private Config(String locale, String liveTvPackage, boolean bootToLiveTv,
                   boolean openOnStartChoice, boolean keyFilter, boolean gamepadPassthrough,
                   boolean consumeVolumeKeys, boolean volumePausesFilter,
                   boolean settingsKeyPanel, boolean settingsKeyClearTask,
                   boolean restoreKeyServices, boolean homeLongPressHome,
                   boolean powerOnHome, boolean powerOnHomeAlways, String stockHomeRedirect,
                   int[] settingsKeycodes, int[] inputKeycodes, List<String> appKeys) {
        this.locale = locale;
        this.liveTvPackage = liveTvPackage;
        this.bootToLiveTv = bootToLiveTv;
        this.openOnStartChoice = openOnStartChoice;
        this.keyFilter = keyFilter;
        this.gamepadPassthrough = gamepadPassthrough;
        this.consumeVolumeKeys = consumeVolumeKeys;
        this.volumePausesFilter = volumePausesFilter;
        this.settingsKeyPanel = settingsKeyPanel;
        this.settingsKeyClearTask = settingsKeyClearTask;
        this.restoreKeyServices = restoreKeyServices;
        this.homeLongPressHome = homeLongPressHome;
        this.powerOnHome = powerOnHome;
        this.powerOnHomeAlways = powerOnHomeAlways;
        this.stockHomeRedirect = stockHomeRedirect;
        this.settingsKeycodes = settingsKeycodes.clone();
        this.inputKeycodes = inputKeycodes.clone();
        this.appKeys = Collections.unmodifiableList(new ArrayList<>(appKeys));
    }

    static Config load(Context context) {
        Resources resources = context.getResources();
        JSONObject json = overlay(context);
        return new Config(
                string(json, "locale", resources.getString(R.string.locale)),
                string(json, "live_tv_package", resources.getString(R.string.live_tv_package)),
                bool(json, "boot_to_live_tv", resources.getBoolean(R.bool.boot_to_live_tv)),
                bool(json, "open_on_start_choice", resources.getBoolean(R.bool.open_on_start_choice)),
                bool(json, "key_filter", resources.getBoolean(R.bool.key_filter)),
                bool(json, "gamepad_passthrough", resources.getBoolean(R.bool.gamepad_passthrough)),
                bool(json, "consume_volume_keys", resources.getBoolean(R.bool.consume_volume_keys)),
                bool(json, "volume_pauses_filter", resources.getBoolean(R.bool.volume_pauses_filter)),
                bool(json, "settings_key_panel", resources.getBoolean(R.bool.settings_key_panel)),
                bool(json, "settings_key_clear_task", resources.getBoolean(R.bool.settings_key_clear_task)),
                bool(json, "restore_key_services", resources.getBoolean(R.bool.restore_key_services)),
                bool(json, "home_long_press_home", resources.getBoolean(R.bool.home_long_press_home)),
                bool(json, "power_on_home", resources.getBoolean(R.bool.power_on_home)),
                bool(json, "power_on_home_always", resources.getBoolean(R.bool.power_on_home_always)),
                string(json, "stock_home_redirect", resources.getString(R.string.stock_home_redirect)),
                ints(json, "settings_key_keycodes", resources.getIntArray(R.array.settings_key_keycodes)),
                ints(json, "input_key_keycodes", resources.getIntArray(R.array.input_key_keycodes)),
                strings(json, "app_keys", Arrays.asList(resources.getStringArray(R.array.app_keys))));
    }

    private static JSONObject overlay(Context context) {
        // OEM services can start before app-specific external storage is mounted.
        // Keep the last valid remote config in internal storage, without reviving
        // the former visual theme loader or interpreting visual preference keys.
        File cached = new File(context.getFilesDir(), FILE_NAME);
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
            return readJson(cached);
        File directory = context.getExternalFilesDir(null);
        if (directory == null) return readJson(cached);
        File source = new File(directory, FILE_NAME);
        JSONObject config = readJson(source);
        if (config != null) {
            try (FileOutputStream output = new FileOutputStream(cached)) {
                output.write(config.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException | SecurityException failure) {
                Log.w("UnifiedTv.Config", "Could not cache OEM remote settings");
            }
        } else if (!source.isFile() || source.length() > MAX_JSON_BYTES) {
            // A deleted/oversized external file must not keep an obsolete overlay active.
            cached.delete();
        }
        return config;
    }

    private static JSONObject readJson(File source) {
        if (!source.isFile() || source.length() <= 0 || source.length() > MAX_JSON_BYTES) return null;
        try (FileInputStream input = new FileInputStream(source)) {
            byte[] data = new byte[(int) source.length()];
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count <= 0) return null;
                offset += count;
            }
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (IOException | JSONException | SecurityException failure) {
            // Invalid or unreadable optional OEM configuration must not block HOME.
            Log.w("UnifiedTv.Config", "Ignoring unreadable device compatibility config");
            return null;
        }
    }

    private static String string(JSONObject object, String key, String fallback) {
        if (object == null) return fallback.trim();
        Object value = object.opt(key);
        return value instanceof String && ((String) value).length() <= 256
                ? ((String) value).trim() : fallback.trim();
    }

    private static boolean bool(JSONObject object, String key, boolean fallback) {
        if (object == null) return fallback;
        Object value = object.opt(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static int[] ints(JSONObject object, String key, int[] fallback) {
        JSONArray list = object == null ? null : object.optJSONArray(key);
        if (list == null || list.length() > 64) return fallback;
        int[] accepted = new int[list.length()];
        for (int i = 0; i < list.length(); i++) {
            Object value = list.opt(i);
            if (!(value instanceof Number)) return fallback;
            int n = ((Number) value).intValue();
            if (n < 0 || n > 65535) return fallback;
            accepted[i] = n;
        }
        return accepted;
    }

    private static List<String> strings(JSONObject object, String key, List<String> fallback) {
        JSONArray list = object == null ? null : object.optJSONArray(key);
        if (list == null || list.length() > 128) return fallback;
        List<String> accepted = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            Object value = list.opt(i);
            if (!(value instanceof String) || ((String) value).length() > 256) return fallback;
            String entry = ((String) value).trim();
            if (!entry.isEmpty()) accepted.add(entry);
        }
        return accepted;
    }

}
