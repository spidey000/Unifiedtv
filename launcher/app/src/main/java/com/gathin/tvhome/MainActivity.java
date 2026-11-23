package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** HOME compatibility bridge; there is no classic dock or legacy visual interface. */
public final class MainActivity extends Activity {
    static final String ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.action.LAUNCHER_SETTINGS";
    static final String ACTION_APP_LIBRARY = "com.gathin.tvhome.action.APP_LIBRARY";
    static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";
    static final String EXTRA_CATALOG_INPUTS = "unifiedtv_catalog_inputs";
    private boolean opening;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dispatch(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dispatch(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!opening) dispatch(getIntent());
    }

    @Override protected void onStop() {
        opening = false;
        super.onStop();
    }

    private void dispatch(Intent request) {
        if (opening) return;
        opening = true;
        String action = request == null ? null : request.getAction();
        if (ACTION_LAUNCHER_SETTINGS.equals(action)) {
            startActivity(new Intent(this, UnifiedCatalogSettingsActivity.class));
        } else if (ACTION_VIEW_INPUTS.equals(action)) {
            UnifiedTvHome.openInputs(this);
        } else if (ACTION_APP_LIBRARY.equals(action)) {
            startActivity(new Intent(this, UnifiedCatalogActivity.class)
                    .putExtra(UnifiedCatalogActivity.EXTRA_PAGE, "APPS")
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } else {
            UnifiedTvHome.maybeLaunch(this);
        }
    }
}
