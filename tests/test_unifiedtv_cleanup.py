"""Prevent CleanYourTV visual resources from re-entering UnifiedTV runtime."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "launcher/app/src/main"
JAVA = MAIN / "java/com/gathin/tvhome"

class LegacyCleanupTests(unittest.TestCase):
    def test_only_one_runtime_home_interface(self):
        main = (JAVA / "MainActivity.java").read_text()
        catalog = (JAVA / "UnifiedCatalogActivity.java").read_text()
        self.assertIn("UnifiedTvHome.maybeLaunch(this)", main)
        self.assertIn("new Intent(this, UnifiedCatalogSettingsActivity.class)", main)
        for old in ("ClockScene", "SkyView", "AppAdapter", "AppLibraryDialog",
                    "LauncherSettingsActivity", "Festival", "RaceWeekend"):
            self.assertFalse((JAVA / (old + ".java")).exists(), old)
            self.assertNotIn(old, catalog)

    def test_compat_config_has_no_old_visual_parameters(self):
        config = (JAVA / "Config.java").read_text()
        self.assertIn('static final String FILE_NAME = "config.json"', config)
        self.assertIn('final int[] inputKeycodes;', config)
        self.assertIn('final boolean restoreKeyServices;', config)
        for old in ("dockApps", "wallpaperRotationMinutes", "raceCalendar",
                    "skyIntensity", "clockFormat", "WallpaperRotation",
                    "RaceWeekend", "ThemeFiles"):
            self.assertNotIn(old, config)
        defaults = (MAIN / "res/values/config.xml").read_text()
        self.assertIn('name="settings_fallback_components"', defaults)
        self.assertIn('name="settings_key_scancodes"', defaults)
        self.assertNotIn('name="dock_apps"', defaults)
        self.assertNotIn('name="wallpaper_moods"', defaults)

    def test_legacy_resources_not_packaged(self):
        resources = MAIN / "res"
        self.assertFalse((resources / "xml/locales_config.xml").exists())
        self.assertFalse((resources / "raw/keep.xml").exists())
        self.assertFalse((resources / "font").exists())
        strings = (resources / "values/strings.xml").read_text()
        self.assertIn('name="unifiedtv_name"', strings)
        self.assertIn('name="spotlight_position"', strings)
        self.assertNotIn("festival_", strings)
        self.assertNotIn("race_kicker", strings)
        self.assertNotIn("dock_opacity_label", strings)

    def test_home_recovery_and_addons_survive(self):
        manifest = ET.parse(MAIN / "AndroidManifest.xml")
        ns = '{http://schemas.android.com/apk/res/android}'
        names = {n.get(ns + "name") for n in manifest.findall("./application/*")}
        for component in (".MainActivity", ".HomeSettingsActivity",
                          ".UnifiedCatalogActivity", ".HomeKeyService",
                          ".InputKeyService", ".GuardRevival"):
            self.assertIn(component, names)
        guard = (JAVA / "GuardKeep.java").read_text()
        self.assertIn("UnifiedTvHome.isSystemHomeRecovery(context)", guard)
        self.assertTrue((ROOT / "addons").is_dir())

if __name__ == "__main__":
    unittest.main()
