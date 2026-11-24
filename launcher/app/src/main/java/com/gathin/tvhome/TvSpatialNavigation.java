package com.gathin.tvhome;

import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.SeekBar;

import java.lang.ref.WeakReference;

/**
 * Native Android-TV counterpart to Norigin's spatial focus concepts.
 *
 * Android Views already owns DPAD focus and scrolling; this class only resolves
 * ambiguous transitions between the rail and the active panel. Within scrolling
 * shelves/grids it defers to Android's focus search (including off-screen items).
 * No React runtime, additional dependency or global input hook is required.
 */
final class TvSpatialNavigation {
    private final ViewGroup rail;
    private final ViewGroup panel;
    private WeakReference<View> lastPanel = new WeakReference<>(null);
    private View preferredRail;

    TvSpatialNavigation(ViewGroup rail, ViewGroup panel) {
        this.rail = rail;
        this.panel = panel;
    }

    void setPreferredRail(View view) {
        preferredRail = view;
    }

    void remember(View focused) {
        if (inside(panel, focused)) lastPanel = new WeakReference<>(focused);
    }

    static boolean inside(ViewGroup group, View view) {
        if (group == null || view == null) return false;
        if (view == group) return true;
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == group) return true;
        }
        return false;
    }

    /** Return true only when this controller actually moved focus. */
    boolean onKey(View focused, KeyEvent event) {
        if (focused == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int direction = direction(event.getKeyCode());
        if (direction == 0) return false;
        // Text editors and sliders need their own directional keys.
        if (focused instanceof EditText || focused instanceof SeekBar) return false;
        boolean inRail = inside(rail, focused);
        boolean inPanel = inside(panel, focused);
        if (!inRail && !inPanel) return false; // Another window/dialog owns focus.

        if (inRail && direction == View.FOCUS_RIGHT) {
            View remembered = lastPanel.get();
            if (valid(remembered, panel)) return move(remembered);
            View explicit = explicit(focused, direction);
            if (valid(explicit, panel)) return move(explicit);
            return move(best(panel, focused, direction, false));
        }
        if (inPanel && direction == View.FOCUS_LEFT) {
            View explicit = explicit(focused, direction);
            if (valid(explicit, rail)) return move(explicit);
            // Honour native focus search for a previous poster/app outside the
            // visible viewport: it must scroll the row, not jump to the rail.
            View nativeNext = focused.focusSearch(direction);
            if (valid(nativeNext, panel) && aligned(focused, nativeNext, direction))
                return false;
            View left = best(panel, focused, direction, true);
            if (left != null) return move(left);
            if (valid(preferredRail, rail)) return move(preferredRail);
            return move(best(rail, focused, direction, false));
        }

        // Keep Android's established UP/DOWN scrolling and explicit grid links.
        // Intervene only when the framework would leave the active region despite
        // an aligned, visible neighbour in that same region.
        ViewGroup group = inRail ? rail : panel;
        View nativeNext = focused.focusSearch(direction);
        if (valid(nativeNext, group)) return false;
        View candidate = best(group, focused, direction, true);
        return candidate != null && move(candidate);
    }

    private static int direction(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }

    private static View explicit(View source, int direction) {
        int id;
        switch (direction) {
            case View.FOCUS_LEFT: id = source.getNextFocusLeftId(); break;
            case View.FOCUS_RIGHT: id = source.getNextFocusRightId(); break;
            case View.FOCUS_UP: id = source.getNextFocusUpId(); break;
            case View.FOCUS_DOWN: id = source.getNextFocusDownId(); break;
            default: return null;
        }
        return id == View.NO_ID ? null : source.getRootView().findViewById(id);
    }

    private static boolean valid(View candidate, ViewGroup region) {
        return candidate != null && inside(region, candidate) && candidate.isEnabled()
                && candidate.isFocusable() && candidate.isShown();
    }

    private static boolean move(View destination) {
        if (destination == null || !destination.requestFocus()) return false;
        // The actual focus outline can extend outside the view's measured bounds.
        int inset = Math.round(6 * destination.getResources().getDisplayMetrics().density);
        destination.requestRectangleOnScreen(
                new Rect(-inset, -inset, destination.getWidth() + inset,
                        destination.getHeight() + inset), false);
        return true;
    }

    private static boolean aligned(View from, View to, int direction) {
        Rect a = bounds(from, false);
        Rect b = bounds(to, false);
        return a != null && b != null && SpatialScore.inDirection(a.left, a.top, a.right,
                a.bottom, b.left, b.top, b.right, b.bottom, direction)
                && SpatialScore.overlapsBeam(a.left, a.top, a.right, a.bottom,
                b.left, b.top, b.right, b.bottom, direction);
    }

    private static Rect bounds(View view, boolean visibleOnly) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Rect rect = new Rect();
        if (visibleOnly) {
            if (!view.getGlobalVisibleRect(rect) || rect.isEmpty()) return null;
        } else {
            int[] xy = new int[2];
            view.getLocationOnScreen(xy);
            rect.set(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
        }
        return rect;
    }

    private static View best(ViewGroup group, View from, int direction, boolean beamOnly) {
        Rect origin = bounds(from, false);
        if (origin == null) return null;
        Result choice = new Result();
        search(group, group, from, origin, direction, beamOnly, choice);
        return choice.view;
    }

    private static void search(View node, ViewGroup root, View from, Rect source,
                               int direction, boolean beamOnly, Result choice) {
        if (node != root && node != from && node.isFocusable() && node.isEnabled() && node.isShown()) {
            Rect target = bounds(node, true);
            if (target != null && SpatialScore.inDirection(source.left, source.top, source.right,
                    source.bottom, target.left, target.top, target.right, target.bottom, direction)) {
                boolean beam = SpatialScore.overlapsBeam(source.left, source.top, source.right,
                        source.bottom, target.left, target.top, target.right, target.bottom, direction);
                if ((!beamOnly || beam)) {
                    long score = SpatialScore.score(source.left, source.top, source.right,
                            source.bottom, target.left, target.top, target.right, target.bottom,
                            direction);
                    if (choice.view == null || (beam && !choice.beam)
                            || (beam == choice.beam && score < choice.score)) {
                        choice.view = node;
                        choice.beam = beam;
                        choice.score = score;
                    }
                }
            }
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    search(child, root, from, source, direction, beamOnly, choice);
                }
            }
        }
    }

    private static final class Result {
        View view;
        long score = Long.MAX_VALUE;
        boolean beam;
    }

    /** Pure geometry; kept separate so DPAD ranking can be unit-tested on the JVM. */
    static final class SpatialScore {
        private SpatialScore() { }

        static boolean inDirection(int al, int at, int ar, int ab,
                                   int bl, int bt, int br, int bb, int direction) {
            switch (direction) {
                case View.FOCUS_LEFT: return bl + br < al + ar;
                case View.FOCUS_RIGHT: return bl + br > al + ar;
                case View.FOCUS_UP: return bt + bb < at + ab;
                case View.FOCUS_DOWN: return bt + bb > at + ab;
                default: return false;
            }
        }

        static boolean overlapsBeam(int al, int at, int ar, int ab,
                                    int bl, int bt, int br, int bb, int direction) {
            return direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT
                    ? bt < ab && bb > at : bl < ar && br > al;
        }

        static long score(int al, int at, int ar, int ab,
                          int bl, int bt, int br, int bb, int direction) {
            int major, minor;
            switch (direction) {
                case View.FOCUS_LEFT:
                    major = Math.max(0, al - br);
                    minor = Math.max(0, Math.max(at - bb, bt - ab));
                    break;
                case View.FOCUS_RIGHT:
                    major = Math.max(0, bl - ar);
                    minor = Math.max(0, Math.max(at - bb, bt - ab));
                    break;
                case View.FOCUS_UP:
                    major = Math.max(0, at - bb);
                    minor = Math.max(0, Math.max(al - br, bl - ar));
                    break;
                case View.FOCUS_DOWN:
                    major = Math.max(0, bt - ab);
                    minor = Math.max(0, Math.max(al - br, bl - ar));
                    break;
                default: return Long.MAX_VALUE;
            }
            return 13L * major * major + (long) minor * minor;
        }
    }
}
