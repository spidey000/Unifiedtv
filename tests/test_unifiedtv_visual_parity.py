"""Static regression guards for the React-review-to-native visual port."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"

class NativeVisualParityTests(unittest.TestCase):
    def test_route_names_describe_implemented_actions(self):
        catalog = (SRC / "UnifiedCatalogActivity.java").read_text()
        self.assertIn('Page.WATCH, "Seguir viendo"', catalog)
        self.assertIn('Page.LIVE, "Entradas de TV"', catalog)
        self.assertIn('title("Entradas de TV"', catalog)
        self.assertNotIn('Page.WATCH, "Mi lista"', catalog)
        self.assertNotIn('Page.LIVE, "TV en directo"', catalog)

    def test_distinct_focus_and_selection_without_resting_buttons(self):
        stream = (SRC / "StreamUi.java").read_text()
        main = (SRC / "UnifiedCatalogActivity.java").read_text()
        settings = (SRC / "UnifiedCatalogSettingsActivity.java").read_text()
        self.assertIn('static void navigation(TextView control)', stream)
        self.assertIn('static void heroPrimary(TextView control)', stream)
        self.assertIn('surfaces.addState(selected, shape(c, Color.TRANSPARENT', stream)
        self.assertIn('StreamUi.navigation(control)', main)
        self.assertIn('StreamUi.navigation(tab)', settings)
        self.assertIn('StreamUi.heroPrimary(watch)', main)

    def test_focused_posters_and_long_settings_are_revealed(self):
        main = (SRC / "UnifiedCatalogActivity.java").read_text()
        settings = (SRC / "UnifiedCatalogSettingsActivity.java").read_text()
        spatial = (SRC / "TvSpatialNavigation.java").read_text()
        viewport = (SRC / "TvFocusViewport.java").read_text()
        self.assertIn('revealContentFocus(current)', main)
        self.assertIn('revealSettingsFocus(current)', settings)
        self.assertIn('scroll.offsetDescendantRectToMyCoords(focused, bounds)', main)
        self.assertIn('scroll.offsetDescendantRectToMyCoords(focused, bounds)', settings)
        self.assertIn('TvFocusViewport.scrollDelta(', main)
        self.assertIn('TvFocusViewport.scrollDelta(', settings)
        self.assertIn('static int scrollDelta(', viewport)
        self.assertIn('new Rect(-inset, -inset, destination.getWidth() + inset,', spatial)
        self.assertIn('pinCarouselFocus(view)', main)

if __name__ == "__main__":
    unittest.main()
