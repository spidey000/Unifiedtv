"""Keep the native Android menus visually aligned with the React Chromium reference."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"

class ReactMenuParityTests(unittest.TestCase):
    def read(self, name):
        return (SRC / name).read_text(encoding="utf-8")

    def test_split_setting_rows_and_visual_focus(self):
        s = self.read("UnifiedCatalogSettingsActivity.java")
        ui = self.read("StreamUi.java")
        self.assertIn('String marker = "  ·  "', s)
        self.assertIn("LinearLayout row = new LinearLayout(this)", s)
        self.assertIn("nameView.setMaxLines(2)", s)
        self.assertIn("valueView.setGravity(Gravity.END)", s)
        self.assertIn("R.drawable.uic_chevron_right", s)
        self.assertIn("row.setOnClickListener(click)", s)
        self.assertIn("row.setTag(tag)", s)
        self.assertIn("row.setContentDescription(label)", s)
        self.assertIn("StreamUi.buttonBackground(this, false, true)", s)
        self.assertIn("surfaces.addState(selected, shape(c, SELECTED_SURFACE", ui)
        self.assertIn("surfaces.addState(focused, shape(c, FOCUS_SURFACE", ui)

    def test_natively_accessible_pill_switches(self):
        s = self.read("UnifiedCatalogSettingsActivity.java")
        ui = self.read("StreamUi.java")
        self.assertIn("static Switch toggle(", ui)
        self.assertIn("control.setShowText(false)", ui)
        self.assertIn("control.setSwitchMinWidth", ui)
        self.assertIn("control.setTrackTintList", ui)
        self.assertIn("control.setFocusable(true)", ui)
        self.assertIn("Switch check = StreamUi.toggle", s)
        self.assertIn("Switch use = StreamUi.toggle", s)
        self.assertNotIn("CheckBox use = StreamUi.toggle", s)

    def test_consistent_settings_typography_and_grouping(self):
        s = self.read("UnifiedCatalogSettingsActivity.java")
        for label in ("CARÁTULAS", "DISTRIBUCIÓN", "PLATAFORMAS DISPONIBLES",
                      "REPRODUCTORES", "FUENTES DE SEGUIR VIENDO"):
            self.assertIn('description("' + label + '")', s)
        self.assertIn("label.setLetterSpacing(.16f)", s)
        self.assertIn("heading.setLetterSpacing(-.025f)", s)
        self.assertIn("tab.setMinHeight(dp(38))", s)

    def test_expanding_rail_preserves_content_and_reduces_motion(self):
        main = self.read("UnifiedCatalogActivity.java")
        self.assertIn("resizeNavigation(rail, target)", main)
        self.assertNotIn("body.leftMargin = width", main)
        self.assertIn("if (!StreamUi.motionEnabled())", main)
        self.assertIn("railAnimation.setDuration(StreamUi.FOCUS_ANIM_MS)", main)

    def test_focused_cards_scroll_in_content_coordinates(self):
        for name in ("UnifiedCatalogActivity.java", "UnifiedCatalogSettingsActivity.java"):
            source = self.read(name)
            self.assertNotIn("bounds.offset(0, scroll.getScrollY())", source)
            self.assertIn("TvFocusViewport.scrollDelta(", source)
        self.assertIn("new Rect(-inset, -inset, destination.getWidth() + inset",
                      self.read("TvSpatialNavigation.java"))

    def test_dialogs_and_inputs_share_design_language(self):
        ui = self.read("StreamUi.java")
        self.assertIn("heroPrimary(button)", ui)
        self.assertIn("StateListDrawable surfaces = new StateListDrawable()", ui)
        self.assertIn("input.setBackground(surfaces)", ui)
        self.assertIn("control.setThumbTintList(ColorStateList.valueOf(WHITE))", ui)

if __name__ == "__main__":
    unittest.main()
