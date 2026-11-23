"""Settings must expose real Android TV permission flows, not fake granted switches."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
MANIFEST = ROOT / "launcher/app/src/main/AndroidManifest.xml"

class PermissionSettingsTests(unittest.TestCase):
    def test_navigation_and_focus_refresh(self):
        s = (SRC / "UnifiedCatalogSettingsActivity.java").read_text()
        self.assertIn('"Actualizaciones", "Permisos", "Recuperación"', s)
        self.assertIn('PERMISSIONS_SECTION = 5', s)
        self.assertIn('case PERMISSIONS_SECTION: permissionsAndSetup();', s)
        self.assertIn('refreshPermissionsOnReturn && section == PERMISSIONS_SECTION', s)
        self.assertIn('show(section, restoredFocus)', s)
        self.assertIn('"Actualizar diagnóstico"', s)

    def test_real_grant_flows_and_fallbacks(self):
        s = (SRC / "UnifiedCatalogSettingsActivity.java").read_text()
        p = (SRC / "UnifiedPermissionChecks.java").read_text()
        self.assertIn('requestPermissions(new String[]{UnifiedWatchNext.PERMISSION}', s)
        self.assertIn('onRequestPermissionsResult', s)
        self.assertIn('UnifiedWatchNext.hasPermission(this)', s)
        self.assertIn('UnifiedAppUpdater.openInstallPermissionSettings(this)', s)
        for action in ('Settings.ACTION_WIFI_SETTINGS',
                       'Settings.ACTION_ACCESSIBILITY_SETTINGS',
                       'Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS',
                       'Settings.ACTION_APPLICATION_DETAILS_SETTINGS',
                       'Settings.ACTION_SETTINGS'):
            self.assertIn(action, s)
        self.assertIn('UnifiedPermissionChecks.accessibilityEnabled(this, HomeKeyService.class)', s)
        self.assertIn('UnifiedPermissionChecks.accessibilityEnabled(this, InputKeyService.class)', s)
        self.assertIn('UnifiedPermissionChecks.notificationListenerEnabled(this)', s)
        self.assertIn('UnifiedPermissionChecks.hasSecureSettingsPermission(this)', s)
        self.assertIn('manager.getEnabledAccessibilityServiceList', p)
        self.assertIn('getEnabledListenerPackages(context)', p)
        self.assertIn('new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)', p)
        self.assertIn('manager.getActiveNetwork()', p)
        self.assertNotIn('grantRuntimePermission(', s)
        self.assertNotIn('Settings.Secure.putString', s)

    def test_manifest_privacy_and_services(self):
        tree = ET.parse(MANIFEST)
        ns = '{http://schemas.android.com/apk/res/android}'
        permissions = [p.get(ns+'name') for p in tree.findall('./uses-permission')]
        self.assertIn('android.permission.READ_TV_LISTINGS', permissions)
        self.assertIn('android.permission.REQUEST_INSTALL_PACKAGES', permissions)
        self.assertNotIn('android.permission.ACCESS_FINE_LOCATION', permissions)
        self.assertNotIn('android.permission.QUERY_ALL_PACKAGES', permissions)
        services = {s.get(ns+'name'): s for s in tree.findall('./application/service')}
        self.assertEqual(services['.GuardRevival'].get(ns+'label'),
                         '@string/unifiedtv_guard_service')
        self.assertEqual(services['.InputKeyService'].get(ns+'label'),
                         '@string/unifiedtv_input_key_service')

if __name__ == '__main__':
    unittest.main()
