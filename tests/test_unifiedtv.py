"""Fork-specific static integration checks; JVM compilation is performed by Gradle CI."""
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "launcher/app/src/main"
JAVA = MAIN / "java/com/gathin/tvhome"


class UnifiedTvTests(unittest.TestCase):
    def test_feature_files_exist(self):
        for name in (
            "UnifiedCatalogApi.java", "UnifiedCatalogActivity.java", "UnifiedCatalogPrefs.java",
            "UnifiedCatalogRoute.java", "UnifiedCatalogSettingsActivity.java", "UnifiedTvHome.java"
        ):
            with self.subTest(file=name):
                self.assertTrue((JAVA / name).exists())

    def test_manifest_registers_catalog_and_network_only(self):
        tree = ET.parse(MAIN / "AndroidManifest.xml")
        ns = {"android": "http://schemas.android.com/apk/res/android"}
        permissions = [p.get("{http://schemas.android.com/apk/res/android}name")
                       for p in tree.findall("./uses-permission", ns)]
        self.assertIn("android.permission.INTERNET", permissions)
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", permissions)
        activities = [p.get("{http://schemas.android.com/apk/res/android}name")
                      for p in tree.findall("./application/activity", ns)]
        self.assertIn(".UnifiedCatalogActivity", activities)
        self.assertIn(".UnifiedCatalogSettingsActivity", activities)

    def test_home_and_launcher_settings_are_connected(self):
        source = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("UnifiedTvHome.maybeLaunch(this)", source)
        settings = (JAVA / "LauncherSettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("UnifiedCatalogSettingsActivity.class", settings)
        layout = (MAIN / "res/layout/activity_launcher_settings.xml").read_text(encoding="utf-8")
        self.assertIn("unifiedSettingsChoice", layout)

    def test_streaming_services_and_region(self):
        source = (JAVA / "UnifiedCatalogPrefs.java").read_text(encoding="utf-8")
        for provider in ("netflix", "prime", "max", "disney", "apple", "skyshowtime", "movistar", "filmin"):
            self.assertIn(f'new Provider("{provider}"', source)
        self.assertIn('"ES"', source)

    def test_no_embedded_tmdb_api_key(self):
        source = (JAVA / "UnifiedCatalogPrefs.java").read_text(encoding="utf-8")
        self.assertIn('getString(KEY, "")', source)
        self.assertNotRegex(source, r"(?i)(?:sk|eyJ)[A-Za-z0-9._-]{35,}")

    def test_transparent_readme(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("UnifiedTV", readme)
        self.assertIn("no garantiza abrir la ficha exacta", readme)
        self.assertIn("TMDB", readme)


if __name__ == "__main__":
    unittest.main()
