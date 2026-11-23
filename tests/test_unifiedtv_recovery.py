"""Native HOME entry and recovery stay available after legacy UI removal."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
MANIFEST = ROOT / "launcher/app/src/main/AndroidManifest.xml"

class RecoveryTests(unittest.TestCase):
    def test_home_is_a_bridge_without_classic_ui(self):
        main = (JAVA / "MainActivity.java").read_text()
        self.assertIn("UnifiedTvHome.maybeLaunch(this)", main)
        self.assertNotIn("AppAdapter", main)
        self.assertNotIn("ClockScene", main)
        self.assertNotIn("SkyView", main)
        catalog = (JAVA / "UnifiedCatalogActivity.java").read_text()
        self.assertNotIn("leaveForApps", catalog)
        self.assertNotIn("Abrir launcher clásico", catalog)

    def test_recovery_calls_system_picker(self):
        settings = (JAVA / "UnifiedCatalogSettingsActivity.java").read_text()
        picker = (JAVA / "HomeSettingsActivity.java").read_text()
        self.assertIn("Recuperación", settings)
        self.assertIn("HomeSettingsActivity.class", settings)
        self.assertIn("Settings.ACTION_HOME_SETTINGS", picker)
        self.assertIn("Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS", picker)
        self.assertIn("Settings.ACTION_SETTINGS", picker)
        bridge = (JAVA / "UnifiedTvHome.java").read_text()
        key_service = (JAVA / "HomeKeyService.java").read_text()
        self.assertIn("enterSystemHomeRecovery(this)", picker)
        self.assertIn("isSystemHomeRecovery(this)", key_service)
        self.assertIn("enableUnifiedHomeRouting", settings)
        self.assertIn("system_home_recovery_no_redirect", bridge)

    def test_manifest_has_no_old_screensaver_or_settings(self):
        tree = ET.parse(MANIFEST)
        ns = "{http://schemas.android.com/apk/res/android}"
        names = [node.get(ns + "name") for node in tree.findall("./application/*")]
        for old in (".LauncherSettingsActivity", ".ClockActivity", ".ClockDream",
                    ".QuickSettingsActivity", ".ThemeProvider"):
            self.assertNotIn(old, names)
        self.assertIn(".MainActivity", names)
        self.assertIn(".HomeSettingsActivity", names)
        self.assertIn(".UnifiedCatalogActivity", names)

if __name__ == "__main__":
    unittest.main()
