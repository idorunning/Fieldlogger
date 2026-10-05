package com.field.logger;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.work.*;
import com.field.logger.nativeapp.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

/** Opt-in renders of the actual native view hierarchy, using licensed example photos. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StoreScreenshotsTest {
  @Test
  public void renderNativeStoreScreenshots() throws Exception {
    Assume.assumeTrue("1".equals(System.getenv("MYTRAILLOG_SCREENSHOTS")));
    Context context = RuntimeEnvironment.getApplication();
    Configuration configuration =
        new Configuration.Builder()
            .setExecutor(Runnable::run)
            .setWorkerFactory(
                new WorkerFactory() {
                  @Override
                  public ListenableWorker createWorker(Context c, String name, WorkerParameters p) {
                    return new Worker(c, p) {
                      @Override
                      public Result doWork() {
                        return Result.success();
                      }
                    };
                  }
                })
            .build();
    androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
        context, configuration);
    Repository repo = new Repository(context);
    String owner = repo.owner();
    String[][] examples = {
      {
        "robin",
        "European robin",
        "Erithacus rubecula",
        "birds",
        "field-robin.jpg",
        "2026-10-05T09:45:00Z"
      },
      {"fox", "Red fox", "Vulpes vulpes", "animals", "field-fox.jpg", "2026-10-05T08:20:00Z"},
      {"woodland", "Woodland canopy", "", "landmarks", "woodland.jpg", "2026-10-03T11:10:00Z"}
    };
    File publicDir = new File(System.getenv("MYTRAILLOG_PHOTO_DIR"));
    for (String[] example : examples) {
      String id = "store-demo-" + example[0];
      File photo = repo.photoFile(id);
      Files.copy(
          new File(publicDir, example[4]).toPath(),
          photo.toPath(),
          StandardCopyOption.REPLACE_EXISTING);
      JSONObject data = Observation.fresh(id, Instant.parse(example[5]));
      Observation.put(data, "name", example[1]);
      Observation.put(data, "scientificName", example[2]);
      Observation.put(data, "category", example[3]);
      Observation.put(data, "place", "Example woodland");
      Observation.put(data, "confirmed", true);
      Observation.put(data, "analysisState", "complete");
      Observation.put(data, "note", "Example journal entry · licensed demonstration photograph.");
      JSONObject ai = new JSONObject();
      Observation.put(ai, "confidence", "medium");
      Observation.put(
          ai,
          "summary",
          example[0].equals("robin")
              ? "A small songbird with a bright orange breast, often seen around woodland edges and"
                    + " gardens."
              : "A moment from the woodland, saved in my trail journal.");
      Observation.put(
          ai,
          "seasonalContext",
          "Robins can sing through autumn, when many other birds become quieter.");
      Observation.put(data, "identification", ai);
      repo.db.save(new Observation(data, owner, photo, true, ""));
    }
    try (ActivityController<LauncherActivity> controller =
        Robolectric.buildActivity(LauncherActivity.class).setup().visible()) {
      LauncherActivity app = controller.get();
      render(app, "01-journal.png");
      Bitmap story = StoryCard.render(context, repo.db.find("store-demo-robin"), true);
      File storyFile =
          new File(
              new File(System.getenv("MYTRAILLOG_SCREENSHOT_DIR")).getParentFile(),
              "story-example.png");
      try (FileOutputStream out = new FileOutputStream(storyFile)) {
        story.compress(Bitmap.CompressFormat.PNG, 100, out);
      }
      story.recycle();
      Method detail = LauncherActivity.class.getDeclaredMethod("showDetail", Observation.class);
      detail.setAccessible(true);
      detail.invoke(app, repo.db.find("store-demo-robin"));
      render(app, "02-discovery-detail.png");
      findScroll(app.findViewById(android.R.id.content)).scrollTo(0, 920);
      render(app, "05-discovery-notes-sharing.png");
      invoke(app, "showJournal");
      render(app, "01-journal.png");
      findScroll(app.findViewById(android.R.id.content)).scrollTo(0, 1100);
      render(app, "06-journal-wildlife.png");
      invoke(app, "showCollection");
      render(app, "03-collection.png");
      invoke(app, "showMilestones");
      render(app, "04-milestones.png");
    }
  }

  private ScrollView findScroll(View view) {
    if (view instanceof ScrollView) return (ScrollView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        ScrollView found = findScroll(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }

  private void invoke(LauncherActivity app, String method) throws Exception {
    Method m = LauncherActivity.class.getDeclaredMethod(method);
    m.setAccessible(true);
    m.invoke(app);
  }

  private void render(LauncherActivity app, String name) throws Exception {
    View view = app.findViewById(android.R.id.content);
    int width = 1080, height = 1920;
    for (int i = 0; i < 35; i++) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      view.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
      view.layout(0, 0, width, height);
      Thread.sleep(30);
    }
    Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    view.draw(new Canvas(image));
    File dir = new File(System.getenv("MYTRAILLOG_SCREENSHOT_DIR"));
    dir.mkdirs();
    try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
      assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
    }
    assertEquals(width, image.getWidth());
    image.recycle();
  }
}
