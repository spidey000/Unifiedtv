package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Visual primitives for the streaming interface. No framework button insets or focus animations. */
final class StreamUi {
    static final int BLACK = 0xFF0B0B0F;
    static final int PANEL = 0xFF19191E;
    static final int WHITE = 0xFFF7F7F8;
    static final int DIM = 0xFFB7B7C0;
    static final int DEFAULT_ACCENT = 0xFFE3293F;
    static final int BORDER = 0xFF34343E;
    static final int RATING = 0xFF99E3B6;
    private static final String ACCENT_KEY = "accent_color";
    private static final int[] FOCUS = {android.R.attr.state_focused};
    private static final int[] SELECTED = {android.R.attr.state_selected};
    private static final int[] DEFAULT = {};

    private StreamUi() {}

    static int accent(Context context) {
        return UnifiedCatalogPrefs.prefs(context).getInt(ACCENT_KEY, DEFAULT_ACCENT);
    }

    static void saveAccent(Context context, int color) {
        UnifiedCatalogPrefs.prefs(context).edit().putInt(ACCENT_KEY, color).apply();
    }

    static int shade(int color, float amount) {
        return Color.rgb(Math.round(Color.red(color) * amount),
                Math.round(Color.green(color) * amount), Math.round(Color.blue(color) * amount));
    }

    static int visibleAccent(Context context) {
        int color = accent(context);
        return Color.luminance(color) < .13f ? readableAccent(context) : color;
    }

    static int readableAccent(Context context) {
        int color = accent(context);
        return Color.rgb((Color.red(color) + 255) / 2, (Color.green(color) + 255) / 2,
                (Color.blue(color) + 255) / 2);
    }

    private static GradientDrawable glass(Context c, int fill, int stroke, int width) {
        GradientDrawable d = shape(c, fill, 10, Color.TRANSPARENT);
        if (width > 0) d.setStroke(dp(c, width), stroke);
        return d;
    }

    static GradientDrawable panel(Context c) {
        return glass(c, 0xE9212128, 0x44FFFFFF, 1);
    }

    static GradientDrawable rail(Context c) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                0xFF121217, BLACK, shade(accent(c), .14f), BLACK});
    }

    static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static LinearLayout column(Context c) {
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static TextView text(Context c, String text, int size, int color, boolean bold) {
        TextView view = new TextView(c);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    static GradientDrawable shape(Context c, int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 2), stroke);
        return d;
    }

    static StateListDrawable state(Context c, int idle, int focus, int selected, int border) {
        StateListDrawable d = new StateListDrawable();
        d.addState(FOCUS, shape(c, focus, 10, border));
        d.addState(SELECTED, shape(c, selected, 10, Color.TRANSPARENT));
        d.addState(DEFAULT, shape(c, idle, 10, Color.TRANSPARENT));
        return d;
    }

    static StateListDrawable buttonBackground(Context c, boolean primary, boolean bare) {
        StateListDrawable drawable = new StateListDrawable();
        Drawable active = glass(c, 0x503F3F49, visibleAccent(c), 1);
        drawable.addState(new int[]{-android.R.attr.state_enabled}, glass(c, 0x301B1B21, 0, 0));
        drawable.addState(FOCUS, glass(c, 0xE64B4B57, WHITE, 2));
        drawable.addState(new int[]{android.R.attr.state_activated}, glass(c, 0x553F3F49, visibleAccent(c), 1));
        drawable.addState(SELECTED, active);
        drawable.addState(DEFAULT, primary ? glass(c, 0xE63B3B46, 0x66FFFFFF, 1)
                : bare ? shape(c, Color.TRANSPARENT, 10, Color.TRANSPARENT) : panel(c));
        return drawable;
    }

    static void background(View view, boolean primary, boolean bare) {
        int left = view.getPaddingLeft(), top = view.getPaddingTop();
        int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
        view.setBackground(buttonBackground(view.getContext(), primary, bare));
        view.setPadding(left, top, right, bottom);
    }

    static void icon(TextView view, int resource) {
        if (resource == 0) return;
        Context c = view.getContext();
        Drawable icon = c.getDrawable(resource).mutate();
        icon.setTint(WHITE);
        int size = dp(c, 19);
        icon.setBounds(0, 0, size, size);
        view.setCompoundDrawablesRelative(icon, null, null, null);
        view.setCompoundDrawablePadding(dp(c, 8));
    }

    static TextView control(Context c, String title, boolean primary, View.OnClickListener click) {
        return control(c, title, 0, primary, click);
    }

    static TextView control(Context c, String title, int resource, boolean primary, View.OnClickListener click) {
        TextView button = new TextView(c) {
            @Override public CharSequence getAccessibilityClassName() { return "android.widget.Button"; }
        };
        button.setId(View.generateViewId());
        button.setText(title);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, DEFAULT}, new int[]{DIM, WHITE}));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(c, 44));
        button.setPadding(dp(c, 14), dp(c, 4), dp(c, 14), dp(c, 4));
        background(button, primary, false);
        icon(button, resource);
        button.setFocusable(true);
        button.setOnClickListener(click);
        return button;
    }

    static CheckBox toggle(Context c, String title, boolean value) {
        CheckBox box = new CheckBox(c);
        box.setText(title);
        box.setTextSize(16);
        box.setTextColor(WHITE);
        box.setChecked(value);
        box.setMinHeight(dp(c, 46));
        box.setPadding(dp(c, 8), dp(c, 4), dp(c, 10), dp(c, 4));
        background(box, false, false);
        box.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, DEFAULT},
                new int[]{readableAccent(c), DIM}));
        return box;
    }

    static void field(EditText input) {
        Context c = input.getContext();
        input.setTextColor(WHITE);
        input.setHintTextColor(DIM);
        background(input, false, false);
        input.setPadding(dp(c, 13), dp(c, 8), dp(c, 13), dp(c, 8));
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, R.style.Theme_UnifiedDialog) {
            @Override public AlertDialog create() {
                AlertDialog dialog = super.create();
                dialog.setOnShowListener(ignored -> {
                    dialog.getWindow().setBackgroundDrawable(panel(c));
                    if (dialog.getListView() != null) {
                        dialog.getListView().setSelector(glass(c, 0xC83F3F49, WHITE, 2));
                        dialog.getListView().setDrawSelectorOnTop(false);
                    }
                    for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                            AlertDialog.BUTTON_NEUTRAL}) {
                        Button button = dialog.getButton(which);
                        if (button == null) continue;
                        background(button, which == AlertDialog.BUTTON_POSITIVE, false);
                        button.setTextColor(WHITE);
                        button.setAllCaps(false);
                    }
                });
                return dialog;
            }
        };
    }
}
