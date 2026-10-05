package com.field.logger;

import static org.junit.Assert.*;

import android.content.Intent;
import android.view.*;
import android.widget.*;
import com.field.logger.nativeapp.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 36})
public class LauncherActivityTest {
  @Before
  public void initializeBackgroundScheduler() {
    androidx.work.Configuration configuration =
        new androidx.work.Configuration.Builder()
            .setExecutor(Runnable::run)
            .setWorkerFactory(
                new androidx.work.WorkerFactory() {
                  @Override
                  public androidx.work.ListenableWorker createWorker(
                      android.content.Context context,
                      String className,
                      androidx.work.WorkerParameters parameters) {
                    return new androidx.work.Worker(context, parameters) {
                      @Override
                      public Result doWork() {
                        return Result.success();
                      }
                    };
                  }
                })
            .build();
    androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
        RuntimeEnvironment.getApplication(), configuration);
  }

  private View find(View view, String description) {
    if (view.getContentDescription() != null
        && description.contentEquals(view.getContentDescription())) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View match = find(group.getChildAt(i), description);
        if (match != null) return match;
      }
    }
    return null;
  }

  private boolean hasText(View view, String text) {
    if (view instanceof TextView && ((TextView) view).getText().toString().contains(text))
      return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (hasText(group.getChildAt(i), text)) return true;
    }
    return false;
  }

  private boolean containsWebView(View view) {
    if (view instanceof android.webkit.WebView) return true;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++)
        if (containsWebView(group.getChildAt(i))) return true;
    }
    return false;
  }

  private ActivityController<LauncherActivity> start() {
    return Robolectric.buildActivity(
            LauncherActivity.class,
            new Intent(RuntimeEnvironment.getApplication(), LauncherActivity.class))
        .setup()
        .visible();
  }

  @Test
  public void nativeJournalStartsWithoutLaunchingAnyBrowser() {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      View root = app.getWindow().getDecorView();
      assertTrue(hasText(root, "Your field journal"));
      assertNotNull(find(root, "Camera"));
      assertNull(find(root, "Identification settings"));
      assertFalse(hasText(root, "API key"));
      assertNull(Shadows.shadowOf(app).getNextStartedActivity());
      assertFalse(containsWebView(root));
      assertFalse(app.isFinishing());
    }
  }

  @Test
  public void nativeNavigationShowsCollectionAndMilestones() {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      View root = app.getWindow().getDecorView();
      find(root, "Collection").performClick();
      assertTrue(hasText(root, "Your collection"));
      find(root, "Milestones").performClick();
      assertTrue(hasText(root, "Stay curious"));
      find(root, "Journal").performClick();
      assertTrue(hasText(root, "Your field journal"));
      assertNull(Shadows.shadowOf(app).getNextStartedActivity());
    }
  }

  @Test
  public void nativeAccountFormUsesExistingFieldLoggerLogin() {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      find(app.getWindow().getDecorView(), "Your account").performClick();
      androidx.appcompat.app.AlertDialog dialog =
          (androidx.appcompat.app.AlertDialog)
              org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertNotNull(dialog);
      assertTrue(dialog.isShowing());
      assertEquals("Archive", dialog.getListView().getAdapter().getItem(1));
      dialog.getListView().performItemClick(dialog.getListView().getChildAt(0), 0, 0);
      dialog =
          (androidx.appcompat.app.AlertDialog)
              org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertTrue(hasText(dialog.getWindow().getDecorView(), "Welcome back"));
      assertTrue(hasText(dialog.getWindow().getDecorView(), "Import web journal backup"));
    }
  }

  @Test
  public void cameraDenialDoesNotLaunchBrowserOrDestroyJournal() {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      View root = app.getWindow().getDecorView();
      find(root, "Camera").performClick();
      assertFalse(app.isFinishing());
      Intent permission = Shadows.shadowOf(app).getNextStartedActivity();
      assertNotNull(permission);
      assertEquals("android.content.pm.action.REQUEST_PERMISSIONS", permission.getAction());
      assertTrue(hasText(root, "Your field journal"));
    }
  }

  @Test
  public void appLinksLeavePrivacyAndSharingRulesInTheBrowser() {
    android.content.pm.PackageManager pm = RuntimeEnvironment.getApplication().getPackageManager();
    for (String path : new String[] {"/", "/invite?token=test"}) {
      Intent intent =
          new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://fieldlogger.co.uk" + path))
              .addCategory(Intent.CATEGORY_BROWSABLE)
              .addCategory(Intent.CATEGORY_DEFAULT);
      assertTrue(
          pm.queryIntentActivities(intent, 0).stream()
              .anyMatch(r -> r.activityInfo.name.equals(LauncherActivity.class.getName())));
    }
    for (String path : new String[] {"/privacy", "/community-rules", "/delete-account"}) {
      Intent intent =
          new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://fieldlogger.co.uk" + path))
              .addCategory(Intent.CATEGORY_BROWSABLE)
              .addCategory(Intent.CATEGORY_DEFAULT);
      assertFalse(
          pm.queryIntentActivities(intent, 0).stream()
              .anyMatch(r -> r.activityInfo.name.equals(LauncherActivity.class.getName())));
    }
  }
}
