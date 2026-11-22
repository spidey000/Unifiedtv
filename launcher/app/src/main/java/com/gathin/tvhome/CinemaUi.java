package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

/** Shared palette and remote-control states for the UnifiedTV streaming surfaces. */
final class CinemaUi {
    static final int BASE = 0xFF101014;
    static final int SURFACE = 0xFF202024;
    static final int MUTED = 0xFFC0BBC2;
    static final int ACCENT = 0xFFFF5467;
    private static final int[] FOCUSED = {android.R.attr.state_focused};
    private static final int[] SELECTED = {android.R.attr.state_selected};
    private static final int[] DEFAULT = {};

    private CinemaUi() {}

    static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, int color, int radius, int stroke) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(c, radius));
        if (stroke != Color.TRANSPARENT) result.setStroke(dp(c, 2), stroke);
        return result;
    }

    static StateListDrawable states(Context c, int normal, int focused, int selected, int ring) {
        StateListDrawable result = new StateListDrawable();
        result.addState(FOCUSED, shape(c, focused, 8, ring));
        result.addState(SELECTED, shape(c, selected, 8, Color.TRANSPARENT));
        result.addState(DEFAULT, shape(c, normal, 8, Color.TRANSPARENT));
        return result;
    }

    static TextView text(Context c, String value, int size, int color) {
        TextView view = new TextView(c);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    static Button button(Context c, String value, View.OnClickListener click) {
        Button button = new Button(c);
        button.setText(value);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(14);
        button.setTextColor(new ColorStateList(new int[][]{FOCUSED, DEFAULT},
                new int[]{BASE, Color.WHITE}));
        button.setBackground(states(c, SURFACE, Color.WHITE, 0xFF9E2335, Color.TRANSPARENT));
        button.setStateListAnimator(null);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(c, 40));
        button.setMinimumHeight(dp(c, 40));
        button.setPadding(dp(c, 12), dp(c, 2), dp(c, 12), dp(c, 2));
        button.setOnClickListener(click);
        return button;
    }

    static CheckBox toggle(Context c) {
        CheckBox box = new CheckBox(c);
        box.setTextSize(16);
        box.setTextColor(Color.WHITE);
        box.setMinHeight(dp(c, 44));
        box.setPadding(dp(c, 6), dp(c, 2), dp(c, 8), dp(c, 2));
        box.setBackground(states(c, Color.TRANSPARENT, SURFACE, SURFACE, Color.WHITE));
        return box;
    }
}
