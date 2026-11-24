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
            "UnifiedCatalogRoute.java", "UnifiedCatalogSettingsActivity.java", "UnifiedTvHome.java",
            "UnifiedWatchNext.java"
        ):
            with self.subTest(file=name):
                self.assertTrue((JAVA / name).exists())

    def test_manifest_registers_catalog_and_network_only(self):
        tree = ET.parse(MAIN / "AndroidManifest.xml")
        ns = {"android": "http://schemas.android.com/apk/res/android"}
        permissions = [p.get("{http://schemas.android.com/apk/res/android}name")
                       for p in tree.findall("./uses-permission", ns)]
        self.assertIn("android.permission.INTERNET", permissions)
        self.assertIn("android.permission.READ_TV_LISTINGS", permissions)
        self.assertNotIn("android.permission.QUERY_ALL_PACKAGES", permissions)
        activities = [p.get("{http://schemas.android.com/apk/res/android}name")
                      for p in tree.findall("./application/activity", ns)]
        self.assertIn(".UnifiedCatalogActivity", activities)
        self.assertIn(".UnifiedCatalogSettingsActivity", activities)

    def test_android_tv_branding_is_locale_independent(self):
        manifest = ET.parse(MAIN / "AndroidManifest.xml")
        application = manifest.find("application")
        ns = "{http://schemas.android.com/apk/res/android}"
        self.assertEqual(application.get(ns + "label"), "@string/unifiedtv_name")
        self.assertEqual(application.get(ns + "icon"), "@drawable/ic_unifiedtv")
        self.assertEqual(application.get(ns + "banner"), "@drawable/banner_unifiedtv")
        for alias in application.findall("activity-alias"):
            self.assertNotEqual(alias.get(ns + "label"), "@string/app_name")
        strings = ET.parse(MAIN / "res/values/strings.xml")
        name = strings.find("string[@name='unifiedtv_name']")
        self.assertEqual(name.text, "Unifiedtv")
        self.assertEqual(name.get("translatable"), "false")

    def test_home_and_launcher_settings_are_connected(self):
        source = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("UnifiedTvHome.maybeLaunch(this)", source)
        settings = (JAVA / "UnifiedCatalogSettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("HomeSettingsActivity.class", settings)
        self.assertIn("Recuperación", settings)
        self.assertNotIn("LauncherSettingsActivity.class", settings)

    def test_streaming_services_and_region(self):
        source = (JAVA / "UnifiedCatalogPrefs.java").read_text(encoding="utf-8")
        for provider in ("netflix", "prime", "max", "disney", "apple", "skyshowtime", "movistar", "filmin"):
            self.assertIn(f'new Provider("{provider}"', source)
        self.assertIn('"ES"', source)

    def test_no_embedded_tmdb_api_key(self):
        source = (JAVA / "UnifiedCatalogPrefs.java").read_text(encoding="utf-8")
        self.assertIn('getString(KEY, "")', source)
        self.assertNotRegex(source, r"(?i)(?:sk|eyJ)[A-Za-z0-9._-]{35,}")

    def test_short_press_opens_direct_link_and_long_press_uses_rich_details(self):
        home = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        api = (JAVA / "UnifiedCatalogApi.java").read_text(encoding="utf-8")
        watch = (JAVA / "UnifiedWatchNext.java").read_text(encoding="utf-8")
        self.assertIn("card.setOnLongClickListener(v -> { details(media); return true; })", home)
        self.assertIn("card.setOnLongClickListener(v -> { watchDetails(item); return true; })", home)
        self.assertIn("UnifiedCatalogRoute.launchTarget(this, target)", home)
        self.assertIn("UnifiedWatchNext.open(this, item)", home)
        self.assertIn("UnifiedCatalogApi.fullDetails(this, media)", home)
        self.assertIn("fullDetailsText(JSONObject info, String type)", api)
        self.assertNotIn("getLeanbackLaunchIntentForPackage(item.providerPackage)", watch)

    def test_catalog_layout_can_switch_to_two_column_category_groups(self):
        home = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        prefs = (JAVA / "UnifiedCatalogPrefs.java").read_text(encoding="utf-8")
        settings = (JAVA / "UnifiedCatalogSettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("twoColumnSections(this)", home)
        self.assertIn("private void columnCatalog(", home)
        self.assertIn("posters.setColumnCount(2)", home)
        self.assertIn("GridLayout.spec(i / 2), GridLayout.spec(i % 2)", home)
        self.assertIn("UnifiedCatalogLayout.cardWidthDp(slotWidth)", home)
        self.assertIn('CATALOG_COLUMNS = "catalog_two_column_sections"', prefs)
        self.assertIn("getBoolean(CATALOG_COLUMNS, false)", prefs)
        self.assertIn("2 pósteres por fila", settings)

    def test_movies_and_series_use_side_rail_without_hero_tabs(self):
        home = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        self.assertIn('route(rail, Page.MOVIES, "Películas", R.drawable.uic_film)', home)
        self.assertIn('route(rail, Page.SERIES, "Series", R.drawable.uic_layers)', home)
        self.assertIn('page == Page.SERIES ? "tv" : "movie"', home)
        self.assertNotIn("addHomeTabs", home)
        self.assertNotIn("R.id.moviesTab", home)
        self.assertNotIn("R.id.seriesTab", home)

    def test_discovery_categories_are_separate_by_media_type(self):
        api = (JAVA / "UnifiedCatalogApi.java").read_text(encoding="utf-8")
        home = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        for kind in ("featured", "watched", "new", "popular", "rated"):
            self.assertIn(f'"{kind}"', api if kind != "featured" else home)
            self.assertIn(f'"{kind}"', home)
        self.assertIn('"/trending/" + type + "/week', api)
        self.assertIn('"/" + type + "/top_rated', api)
        self.assertIn('"/" + type + "/popular', api)
        self.assertIn('"movie"', home)
        self.assertIn('"tv"', home)

    def test_tmdb_scores_are_in_catalog_cards_without_extra_requests(self):
        api = (JAVA / "UnifiedCatalogApi.java").read_text(encoding="utf-8")
        home = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        self.assertIn('o.optDouble("vote_average", 0.0)', api)
        self.assertIn('o.optInt("vote_count", 0)', api)
        self.assertIn("boolean hasRating()", api)
        self.assertIn("Locale.forLanguageTag(\"es-ES\")", api)
        self.assertIn('rating(card, media.ratingText())', home)
        self.assertIn("UnifiedCatalogPrefs.showRatings(this) && media.hasRating()", home)
        self.assertIn("valoración TMDB", home)

    def test_streaming_shell_preserves_apps_and_provider_routing(self):
        home = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        api = (JAVA / "UnifiedCatalogApi.java").read_text(encoding="utf-8")
        self.assertIn('route(rail, Page.APPS, "Apps y fuentes", R.drawable.ic_apps)', home)
        self.assertNotIn("Abrir launcher clásico", home)
        self.assertNotIn("leaveForApps", home)
        self.assertIn("setNextFocusLeftId(route.getId())", home)
        self.assertIn("UnifiedCatalogRoute.open(this, media)", home)
        self.assertIn("UnifiedWatchNext.open(this, item)", home)
        self.assertIn('o.optString("overview", "")', api)
        self.assertIn('o.optString("backdrop_path", "")', api)

    def test_continue_watching_is_explicit_opt_in(self):
        source = (JAVA / "UnifiedWatchNext.java").read_text(encoding="utf-8")
        settings = (JAVA / "UnifiedCatalogSettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("READ_TV_LISTINGS", source)
        self.assertIn("hasPermission(context)", source)
        self.assertIn("TvContract.WatchNextPrograms.CONTENT_URI", source)
        self.assertIn("TvContract.PreviewPrograms.CONTENT_URI", source)
        self.assertIn("null, null, null", source)
        self.assertIn("UnifiedCatalogPrefs.CONTINUE_SOURCES", settings)
        self.assertNotIn("ACCESS_ALL_EPG_DATA", source)
        sources = (JAVA / "UnifiedCatalogPrefs.java").read_text(encoding="utf-8")
        self.assertIn('new Provider("stremio"', sources)
        self.assertIn('new Provider("nuvio"', sources)

    def test_optional_app_updates_use_official_signed_releases(self):
        updater = (JAVA / "UnifiedAppUpdater.java").read_text(encoding="utf-8")
        settings = (JAVA / "UnifiedCatalogSettingsActivity.java").read_text(encoding="utf-8")
        prefs = (JAVA / "UnifiedCatalogPrefs.java").read_text(encoding="utf-8")
        home = (JAVA / "UnifiedCatalogActivity.java").read_text(encoding="utf-8")
        original_home = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        policy = (ROOT / "tools/unifiedtv_release.py").read_text(encoding="utf-8")
        manifest = ET.parse(MAIN / "AndroidManifest.xml")
        ns = "{http://schemas.android.com/apk/res/android}"
        permissions = [p.get(ns + "name") for p in manifest.findall("./uses-permission")]
        self.assertIn("android.permission.REQUEST_INSTALL_PACKAGES", permissions)
        providers = manifest.findall("./application/provider")
        matches = [p for p in providers if p.get(ns + "name") == "androidx.core.content.FileProvider"]
        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0].get(ns + "exported"), "false")
        self.assertEqual(matches[0].get(ns + "grantUriPermissions"), "true")
        file_paths = (MAIN / "res/xml/unifiedtv_update_paths.xml").read_text(encoding="utf-8")
        self.assertIn('path="unifiedtv-updates/"', file_paths)
        self.assertIn("getBoolean(UPDATE_AUTO_CHECK, true)", prefs)
        self.assertIn("checkAutomatically(this)", home)
        self.assertIn("UnifiedTvHome.maybeLaunch(this)", original_home)
        self.assertIn("Buscar actualización ahora", settings)
        self.assertIn("Permitir instalar APK desde UnifiedTV", settings)
        self.assertIn("api.github.com/repos/spidey000/Unifiedtv/releases/latest", updater)
        self.assertIn("SHA-256", updater)
        self.assertIn("verifyPackage(activity, apk)", updater)
        self.assertIn("Arrays.equals", updater)
        self.assertIn("canRequestPackageInstalls()", updater)
        self.assertIn("Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES", updater)
        self.assertIn("FileProvider.getUriForFile", updater)
        self.assertIn("Intent.ACTION_INSTALL_PACKAGE", updater)
        self.assertIn('"REQUEST_INSTALL_PACKAGES"', policy)

    def test_transparent_readme(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("UnifiedTV", readme)
        self.assertIn("no garantiza abrir la ficha exacta", readme)
        self.assertIn("TMDB", readme)


if __name__ == "__main__":
    unittest.main()
