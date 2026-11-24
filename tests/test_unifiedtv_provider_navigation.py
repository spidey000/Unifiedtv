"""Integration guards for provider availability, dialog opening and TV focus."""
import unittest
from pathlib import Path

JAVA = Path(__file__).resolve().parents[1] / "launcher/app/src/main/java/com/gathin/tvhome"


class ProviderNavigationTests(unittest.TestCase):
    def test_badges_do_not_depend_on_wikidata_or_opening_a_title(self):
        source = (JAVA / "UnifiedCatalogActivity.java").read_text()
        badge = source.split("private void loadProviderIcon(")[1].split("private void finishCarousel")[0]
        self.assertIn("badgeProvider(this, media)", badge)
        self.assertNotIn("UnifiedCatalogRoute.resolve(", badge)
        self.assertNotIn("UnifiedCatalogApi.logo(", badge)
        icon = source.split("private ImageView providerIcon(")[1].split("private void loadProviderIcon")[0]
        self.assertIn("setBackground(null)", icon)
        self.assertNotIn("StreamUi.shape", icon)

    def test_dialog_open_uses_lifecycle_instead_of_window_focus(self):
        source = (JAVA / "UnifiedCatalogActivity.java").read_text()
        opening = source.split("private void openMedia(")[1].split("private String kindAndYear")[0]
        self.assertNotIn("hasWindowFocus()", opening)
        self.assertIn("!resumed", opening)
        self.assertIn("resumed = false", source)
        self.assertIn("resumed = true", source)

    def test_column_mode_has_no_horizontal_scroll_container(self):
        source = (JAVA / "UnifiedCatalogActivity.java").read_text()
        columns = source.split("private void columnCatalog(")[1].split("private ImageView providerIcon")[0]
        self.assertNotIn("HorizontalScrollView", columns)
        self.assertIn("categories.setColumnCount(3)", columns)
        self.assertIn("category / 3", columns)
        self.assertIn("category % 3", columns)

    def test_nuvio_is_migrated_to_fallback_provider(self):
        prefs = (JAVA / "UnifiedCatalogPrefs.java").read_text()
        players = (JAVA / "UnifiedPlaybackApps.java").read_text()
        route = (JAVA / "UnifiedCatalogRoute.java").read_text()
        self.assertIn('getBoolean("playback_source_nuvio", true)', prefs)
        self.assertNotIn('new Known("nuvio"', players)
        fallback = route.split("if (eligible.isEmpty())")[1].split("} else {")[0]
        self.assertIn("nuvioUri(media.type, media.id)", fallback)
        self.assertIn('intent.putExtra("source", "30")', route)
        self.assertIn("intent.setComponent(launcher.getComponent())", route)
