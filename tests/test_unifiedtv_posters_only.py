"""Regression checks for the user's portrait-only TV carousel layout."""
import unittest
from pathlib import Path

JAVA = Path(__file__).resolve().parents[1] / "launcher/app/src/main/java/com/gathin/tvhome"


class PortraitOnlyCarouselTests(unittest.TestCase):
    def test_focus_never_resizes_poster_or_swaps_to_wide_preview(self):
        source = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        focus = source.split("private void bindTileFocus(", 1)[1].split(
            "private void loadOnScroll(", 1)[0]
        self.assertNotIn("setLayoutParams", focus)
        self.assertNotIn("focusedWidth", focus)
        self.assertNotIn("thumbnail", focus)
        self.assertIn("scaleX(focused ? 1.025f : 1f)", focus)
        self.assertIn("scaleY(focused ? 1.025f : 1f)", focus)
        self.assertIn("pinCarouselFocus(view)", focus)
        self.assertNotIn("revealThumbnail", source)
        self.assertNotIn("16f / 9f", source)

    def test_watch_next_uses_only_the_portrait_poster(self):
        source = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        artwork = (JAVA / "UnifiedWatchArtwork.java").read_text(encoding="utf-8")
        self.assertIn("UnifiedWatchArtwork.platformPoster(", source)
        self.assertIn("UnifiedWatchArtwork.withTmdbPoster(", source)
        self.assertNotIn("UnifiedWatchArtwork.withTmdb(", source)
        self.assertNotIn("ImageView thumbnail = fullGrid", source)
        self.assertNotIn("shelfArtwork(", artwork)
        self.assertIn("isPortrait(tmdb)", artwork)
        self.assertIn("needsBetterPoster(existing, minWidth)", artwork)


if __name__ == "__main__":
    unittest.main()
