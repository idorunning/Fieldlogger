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
    if (view instanceof TextView && ((TextView) view).getText().toString().equals(description))
      return view;
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
      assertNull(find(root, "Add from gallery"));
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
      assertTrue(hasText(root, "explorer"));
      find(root, "Journal").performClick();
      assertTrue(hasText(root, "Your field journal"));
      assertNull(Shadows.shadowOf(app).getNextStartedActivity());
    }
  }

  @Test
  public void nativeAccountFormUsesExistingFieldLoggerLogin() {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      View root = app.getWindow().getDecorView();
      find(root, "Your profile and settings").performClick();
      assertTrue(hasText(root, "Your community"));
      assertTrue(hasText(root, "Your journal"));
      assertTrue(hasText(root, "App preferences"));
      assertTrue(hasText(root, "Archive"));
      assertTrue(hasText(root, "Sync now"));
      assertTrue(hasText(root, "Make my avatar"));
      assertTrue(hasText(root, "Photo allowance & subscription"));
      assertTrue(hasText(root, "Download all photos & data (ZIP)"));
      assertNull(org.robolectric.shadows.ShadowDialog.getLatestDialog());
      find(root, "Sign in or register. Sync your journal and join your trail circle")
          .performClick();
      androidx.appcompat.app.AlertDialog dialog =
          (androidx.appcompat.app.AlertDialog)
              org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertTrue(hasText(dialog.getWindow().getDecorView(), "Welcome back"));
      assertTrue(hasText(dialog.getWindow().getDecorView(), "Import web journal backup"));
    }
  }

  @Test
  public void membershipOpensNativeSubscriptionPlansWithoutLaunchingABrowser() {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      View root = app.getWindow().getDecorView();
      find(root, "Your profile and settings").performClick();
      find(
              root,
              "Photo allowance & subscription. Free and paid plans, trial, restore or manage in"
                  + " Google Play")
          .performClick();
      Intent opened = Shadows.shadowOf(app).getNextStartedActivity();
      assertNotNull(opened);
      assertEquals(SubscriptionActivity.class.getName(), opened.getComponent().getClassName());
      assertNull(Shadows.shadowOf(app).getNextStartedActivity());
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
  public void tentativeIdentificationExplainsUncertaintyAndOffersAnExplicitCloserReview()
      throws Exception {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      org.json.JSONObject data =
          Observation.fresh(java.util.UUID.randomUUID().toString(), java.time.Instant.now());
      org.json.JSONObject identification = new org.json.JSONObject();
      identification.put("confidence", "low");
      identification.put("summary", "A possible oak leaf. More visible detail is needed.");
      identification.put("recognition", new org.json.JSONObject().put("mode", "tentative"));
      Observation.put(data, "identification", identification);
      Observation.put(data, "analysisState", "complete");
      Observation record =
          new Observation(
              data,
              new Repository(app).owner(),
              new java.io.File(app.getCacheDir(), "closer-review-ui-fixture.jpg"),
              false,
              "");
      java.lang.reflect.Method detail =
          LauncherActivity.class.getDeclaredMethod("showDetail", Observation.class);
      detail.setAccessible(true);
      detail.invoke(app, record);
      View root = app.getWindow().getDecorView();
      assertTrue(hasText(root, "suggested identity as tentative"));
      assertNotNull(find(root, "Try a closer look"));
      identification.getJSONObject("recognition").put("mode", "quick");
      detail.invoke(app, record);
      assertNull(find(root, "Try a closer look"));
    }
  }

  @Test
  public void failedCloudIdentificationOffersManualRetryWhilePendingUploadsKeepTheirSyncFlow()
      throws Exception {
    try (ActivityController<LauncherActivity> controller = start()) {
      LauncherActivity app = controller.get();
      org.json.JSONObject data =
          Observation.fresh(java.util.UUID.randomUUID().toString(), java.time.Instant.now());
      String owner = new Repository(app).owner();
      java.io.File photo = new java.io.File(app.getCacheDir(), "failed-identification-ui.jpg");
      java.lang.reflect.Method detail =
          LauncherActivity.class.getDeclaredMethod("showDetail", Observation.class);
      detail.setAccessible(true);
      View root = app.getWindow().getDecorView();

      detail.invoke(
          app,
          new Observation(data, owner, photo, false, "The previous analysis could not finish."));
      assertNotNull(find(root, "Try again"));
      assertNull(find(root, "Try a closer look"));
      assertTrue(hasText(root, "previous analysis could not finish"));

      detail.invoke(app, new Observation(data, owner, photo, true, "Waiting for a connection."));
      assertNull(find(root, "Try again"));

      detail.invoke(app, new Observation(data, owner, photo, false, ""));
      assertNull(find(root, "Try again"));
      Observation.put(data, "analysisState", "error");
      detail.invoke(app, new Observation(data, owner, photo, false, ""));
      assertNotNull(find(root, "Try again"));
      assertTrue(hasText(root, "Identification could not finish"));
      assertNull(Shadows.shadowOf(app).getNextStartedActivity());
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
