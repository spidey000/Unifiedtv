package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Visual primitives for the streaming interface. No framework button insets or focus animations. */
final class StreamUi {
    static final int BLACK = 0xFF09090B;
    static final int PANEL = 0xFF19191E;
    static final int WHITE = 0xFFF7F7F8;
    static final int DIM = 0xFFB7B7C0;
    static final int RED = 0xFFFF4255;
    private static final int[] FOCUS = {android.R.attr.state_focused};
    private static final int[] SELECTED = {android.R.attr.state_selected};
    private static final int[] DEFAULT = {};

    private StreamUi() {}

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
        d.addState(FOCUS, shape(c, focus, 6, border));
        d.addState(SELECTED, shape(c, selected, 6, Color.TRANSPARENT));
        d.addState(DEFAULT, shape(c, idle, 6, Color.TRANSPARENT));
        return d;
    }

    static TextView control(Context c, String title, boolean primary, View.OnClickListener click) {
        TextView button = new TextView(c) {
            @Override public CharSequence getAccessibilityClassName() { return "android.widget.Button"; }
        };
        button.setId(View.generateViewId());
        button.setText(title);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(new ColorStateList(new int[][]{FOCUS, DEFAULT}, new int[]{BLACK, WHITE}));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(c, 44));
        button.setPadding(dp(c, 14), dp(c, 4), dp(c, 14), dp(c, 4));
        button.setBackground(state(c, primary ? 0xFFBF1F36 : PANEL, WHITE, 0xFF7D2031, Color.TRANSPARENT));
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
        box.setBackground(state(c, PANEL, 0xFF32292D, PANEL, WHITE));
        return box;
    }
}
