package com.field.logger;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.google.androidbrowserhelper.trusted.TwaLauncher;
import java.util.ArrayList;
import java.util.List;

/** Keep Chrome's offline journal, camera and account cookies on their HTTPS origin. */
public class LauncherActivity extends com.google.androidbrowserhelper.trusted.LauncherActivity {
    private final Handler startupHandler = new Handler(Looper.getMainLooper());

    @Override protected boolean shouldLaunchImmediately() { return false; }

    @Override protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            if (isFinishing()) return;
            showOpeningScreen();
            startupHandler.postDelayed(() -> showRecovery(R.string.startup_paused), 10000);
            launchTwa();
        } catch (RuntimeException error) {
            Log.e("FieldLogger", "Browser startup failed: " + error.getClass().getSimpleName());
            showOpeningScreen();
            showRecovery(R.string.startup_paused);
        }
    }

    private void showOpeningScreen() {
        setContentView(R.layout.startup);
        findViewById(R.id.retry_launch).setOnClickListener(view -> recreate());
        findViewById(R.id.open_browser).setOnClickListener(view -> {
            if (openExternalBrowser(null, getLaunchingUrl())) finish();
        });
        findViewById(R.id.browser_settings).setOnClickListener(view -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
            catch (ActivityNotFoundException | SecurityException error) {
                showRecovery(R.string.browser_settings_unavailable);
            }
        });
    }

    private void showRecovery(int message) {
        if (isFinishing() || isDestroyed()) return;
        startupHandler.removeCallbacksAndMessages(null);
        findViewById(R.id.startup_progress).setVisibility(View.GONE);
        ((TextView) findViewById(R.id.startup_status)).setText(message);
        findViewById(R.id.startup_actions).setVisibility(View.VISIBLE);
    }

    @Override protected TwaLauncher.FallbackStrategy getFallbackStrategy() {
        return (context, builder, provider, completion) -> {
            if (openExternalBrowser(provider, builder.getUri()) && completion != null) completion.run();
        };
    }

    private boolean openExternalBrowser(String preferredPackage, Uri url) {
        List<String> candidates = new ArrayList<>();
        if (preferredPackage != null && !preferredPackage.equals(getPackageName())) candidates.add(preferredPackage);
        // A neutral address finds browsers without selecting our own verified domain link.
        Intent browserQuery = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/"));
        browserQuery.addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager manager = getPackageManager();
        ResolveInfo defaultBrowser = manager.resolveActivity(browserQuery, PackageManager.MATCH_DEFAULT_ONLY);
        if (defaultBrowser != null && defaultBrowser.activityInfo != null) {
            String name = defaultBrowser.activityInfo.packageName;
            if (!name.equals(getPackageName()) && !name.equals("android") && !candidates.contains(name)) candidates.add(name);
        }
        for (ResolveInfo browser : manager.queryIntentActivities(browserQuery, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (browser.activityInfo == null) continue;
            String name = browser.activityInfo.packageName;
            if (!name.equals(getPackageName()) && !name.equals("android") && !candidates.contains(name)) candidates.add(name);
        }
        for (String candidate : candidates) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, url);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setPackage(candidate);
                startActivity(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException error) {
                // Try another installed browser; never send this URL back into this app.
            }
        }
        showRecovery(R.string.browser_needed);
        return false;
    }

    @Override protected void onDestroy() {
        startupHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected Uri getLaunchingUrl() {
        Uri requested = getIntent().getData();
        if (requested != null && "https".equals(requested.getScheme())
                && "fieldlogger.co.uk".equals(requested.getHost())
                && (requested.getPort() == -1 || requested.getPort() == 443)
                && requested.getUserInfo() == null) {
            return requested.buildUpon().appendQueryParameter("app_version", BuildConfig.VERSION_NAME).build();
        }
        return Uri.parse("https://fieldlogger.co.uk/").buildUpon().appendQueryParameter("app_version", BuildConfig.VERSION_NAME).build();
    }
}
