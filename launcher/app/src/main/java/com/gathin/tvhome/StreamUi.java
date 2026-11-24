package com.gathin.tvhome;

import android.animation.ValueAnimator;
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
import android.widget.Switch;
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
    static final int RATING = 0xFFF4C66F;
    static final int FOCUS_SURFACE = 0xFF55555D;
    static final int SELECTED_SURFACE = 0xFF28282F;
    static final long FOCUS_ANIM_MS = 180;
    static final int SAFE_HORIZONTAL_DP = 48;
    static final int SAFE_VERTICAL_DP = 27;
    static final int RAIL_DP = 52;
    static final long ART_ANIM_MS = 190;
    private static final String ACCENT_KEY = "accent_color";
    private static final int[] FOCUS = {android.R.attr.state_focused};
    private static final int[] SELECTED = {android.R.attr.state_selected};
    private static final int[] DEFAULT = {};

    private StreamUi() {}

    /** Honor the system-wide animator duration scale, including animations disabled. */
    static boolean motionEnabled() { return ValueAnimator.areAnimatorsEnabled(); }

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
        return glass(c, 0xF21C1C21, 0, 0);
    }

    static GradientDrawable rail(Context c) {
        return shape(c, 0xFF121216, 0, Color.TRANSPARENT);
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
        Drawable active = glass(c, 0xFF34343A, 0, 0);
        drawable.addState(new int[]{-android.R.attr.state_enabled}, glass(c, 0x301B1B21, 0, 0));
        drawable.addState(FOCUS, glass(c, 0xFF55555D, 0, 0));
        drawable.addState(new int[]{android.R.attr.state_activated}, glass(c, 0xFF34343A, 0, 0));
        drawable.addState(SELECTED, active);
        drawable.addState(DEFAULT, primary ? glass(c, 0xFF45454C, 0, 0)
                : bare ? shape(c, Color.TRANSPARENT, 10, Color.TRANSPARENT) : panel(c));
        return drawable;
    }

    /**
     * The selected route is identified by its accent icon/text only. The stronger
     * neutral surface is reserved for the item under DPAD focus; this avoids
     * making two adjacent menu entries look simultaneously focused.
     */
    static void navigation(TextView control) {
        Context c = control.getContext();
        int[] focused = {android.R.attr.state_focused};
        int[] selected = {android.R.attr.state_selected};
        int[] idle = {};
        StateListDrawable surfaces = new StateListDrawable();
        surfaces.addState(focused, shape(c, FOCUS_SURFACE, 9, Color.TRANSPARENT));
        surfaces.addState(selected, shape(c, SELECTED_SURFACE, 9, Color.TRANSPARENT));
        surfaces.addState(idle, shape(c, Color.TRANSPARENT, 9, Color.TRANSPARENT));
        int left = control.getPaddingLeft(), top = control.getPaddingTop();
        int right = control.getPaddingRight(), bottom = control.getPaddingBottom();
        control.setBackground(surfaces);
        control.setPadding(left, top, right, bottom);
        ColorStateList colors = new ColorStateList(
                new int[][]{focused, selected, idle},
                new int[]{WHITE, readableAccent(c), DIM});
        control.setTextColor(colors);
        control.setCompoundDrawableTintList(colors);
    }

    /** Only the principal hero action gets a light Netflix-style treatment. */
    static void heroPrimary(TextView control) {
        Context c = control.getContext();
        StateListDrawable surface = new StateListDrawable();
        surface.addState(new int[]{android.R.attr.state_focused},
                shape(c, 0xFFFFFFFF, 8, 0xFF777780));
        surface.addState(new int[]{},
                shape(c, 0xFFF3F3F4, 8, Color.TRANSPARENT));
        control.setBackground(surface);
        control.setTextColor(0xFF151519);
        control.setCompoundDrawableTintList(ColorStateList.valueOf(0xFF151519));
        control.setOnFocusChangeListener((view, focused) -> {
            view.animate().cancel();
            if (motionEnabled()) {
                view.animate().scaleX(focused ? 1.035f : 1f)
                        .scaleY(focused ? 1.035f : 1f)
                        .setDuration(FOCUS_ANIM_MS).start();
            } else {
                view.setScaleX(focused ? 1.035f : 1f);
                view.setScaleY(focused ? 1.035f : 1f);
            }
        });
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

    /**
     * Native TV switch with a 44dp pill and end-aligned thumb. Keep the OS switch
     * semantics so OK/DPAD, TalkBack and checked-state persistence still work.
     */
    static Switch toggle(Context c, String title, boolean value) {
        Switch control = new Switch(c);
        control.setText(title);
        control.setTextSize(15);
        control.setTextColor(WHITE);
        control.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        control.setShowText(false);
        control.setSwitchMinWidth(dp(c, 45));
        control.setSwitchPadding(dp(c, 12));
        control.setSplitTrack(false);
        control.setThumbTintList(ColorStateList.valueOf(WHITE));
        control.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, DEFAULT},
                new int[]{visibleAccent(c), 0xFF575761}));
        control.setChecked(value);
        control.setMinHeight(dp(c, 48));
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6));
        background(control, false, true);
        control.setFocusable(true);
        return control;
    }

    static void field(EditText input) {
        Context c = input.getContext();
        input.setTextColor(WHITE);
        input.setHintTextColor(DIM);
        StateListDrawable surfaces = new StateListDrawable();
        surfaces.addState(FOCUS, shape(c, 0xFF393940, 8, WHITE));
        surfaces.addState(DEFAULT, shape(c, 0xFF24242B, 8, Color.TRANSPARENT));
        input.setBackground(surfaces);
        input.setPadding(dp(c, 15), dp(c, 10), dp(c, 15), dp(c, 10));
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, R.style.Theme_UnifiedDialog) {
            @Override public AlertDialog create() {
                AlertDialog dialog = super.create();
                dialog.setOnShowListener(ignored -> {
                    dialog.getWindow().setBackgroundDrawable(panel(c));
                    if (dialog.getListView() != null) {
                        dialog.getListView().setSelector(glass(c, 0xFF55555D, 0, 0));
                        dialog.getListView().setDrawSelectorOnTop(false);
                    }
                    for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                            AlertDialog.BUTTON_NEUTRAL}) {
                        Button button = dialog.getButton(which);
                        if (button == null) continue;
                        if (which == AlertDialog.BUTTON_POSITIVE) {
                            heroPrimary(button);
                        } else {
                            background(button, false, true);
                            button.setTextColor(WHITE);
                        }
                        button.setMinHeight(dp(c, 40));
                        button.setAllCaps(false);
                    }
                });
                return dialog;
            }
        };
    }
}
