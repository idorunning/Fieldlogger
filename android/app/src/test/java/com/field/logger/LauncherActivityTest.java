package com.field.logger;

import static org.junit.Assert.*;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import com.google.androidbrowserhelper.trusted.Utils;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 36})
public class LauncherActivityTest {
    private ActivityController<LauncherActivity> coldStart() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), LauncherActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return Robolectric.buildActivity(LauncherActivity.class, intent);
    }

    @Test public void coldStartWithoutBrowserOffersRecoveryInsteadOfCrashing() {
        try (ActivityController<LauncherActivity> controller = coldStart()) {
            controller.create().start().resume().visible();
            LauncherActivity activity = controller.get();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.startup_actions).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.startup_progress).getVisibility());
            assertTrue(((TextView) activity.findViewById(R.id.startup_status)).getText().toString().contains("browser"));
            assertFalse(activity.isFinishing());
        }
    }

    @Test public void browserRecoveryOpensTheJournalInAnExternalPackage() {
        try (ActivityController<LauncherActivity> controller = coldStart()) {
            controller.create().start().resume().visible();
            LauncherActivity activity = controller.get();
            ResolveInfo browser = new ResolveInfo();
            browser.activityInfo = new ActivityInfo();
            browser.activityInfo.packageName = "test.browser";
            browser.activityInfo.name = "test.browser.BrowserActivity";
            Intent query = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/"));
            query.addCategory(Intent.CATEGORY_BROWSABLE);
            Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(query, browser);
            activity.findViewById(R.id.open_browser).performClick();
            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals("test.browser", launched.getPackage());
            assertEquals("fieldlogger.co.uk", launched.getData().getHost());
            assertEquals("/", launched.getData().getPath());
            assertEquals(BuildConfig.VERSION_NAME, launched.getData().getQueryParameter("app_version"));
            assertTrue(activity.isFinishing());
        }
    }

    @Test public void browserRecoveryNeverRelaunchesFieldLoggerIntoItself() {
        try (ActivityController<LauncherActivity> controller = coldStart()) {
            controller.create().start().resume().visible();
            LauncherActivity activity = controller.get();
            ResolveInfo self = new ResolveInfo();
            self.activityInfo = new ActivityInfo();
            self.activityInfo.packageName = activity.getPackageName();
            self.activityInfo.name = LauncherActivity.class.getName();
            Intent query = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/"));
            query.addCategory(Intent.CATEGORY_BROWSABLE);
            Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(query, self);
            activity.findViewById(R.id.open_browser).performClick();
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertFalse(activity.isFinishing());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.startup_actions).getVisibility());
        }
    }

    @Test public void splashImageCanBeConvertedByTheBrowserHelper() {
        Bitmap image = Utils.convertDrawableToBitmap(RuntimeEnvironment.getApplication(), R.drawable.splash);
        assertNotNull(image);
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
    }

    @Test public void packagedAssetStatementsAreValidJson() throws Exception {
        JSONArray statements = new JSONArray(RuntimeEnvironment.getApplication().getString(R.string.asset_statements));
        assertEquals("https://fieldlogger.co.uk", statements.getJSONObject(0).getJSONObject("target").getString("site"));
    }
}
