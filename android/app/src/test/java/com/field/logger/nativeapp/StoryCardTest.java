package com.field.logger.nativeapp;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.time.Instant;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StoryCardTest {
  @Test
  public void longStoryRemainsBoundedAndSharingDoesNotExposeExifOrEditTheJournal()
      throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    File photo = File.createTempFile("story-photo-", ".jpg");
    try (InputStream in = getClass().getResourceAsStream("/robin.jpg");
        FileOutputStream out = new FileOutputStream(photo)) {
      out.write(Api.read(in, 4 * 1024 * 1024));
    }
    JSONObject data = Observation.fresh("test-story", Instant.parse("2026-10-05T11:30:00Z"));
    Observation.put(data, "name", "A remarkably long saved identification name ".repeat(8));
    Observation.put(data, "place", "Example village, woodland, county and country ".repeat(4));
    Observation.put(data, "latitude", 51.123456);
    Observation.put(data, "longitude", -1.123456);
    JSONObject ai = new JSONObject();
    Observation.put(ai, "summary", "Saved identification summary with lots of detail. ".repeat(80));
    Observation.put(
        ai, "seasonalContext", "Saved interesting information about the season. ".repeat(80));
    Observation.put(data, "identification", ai);
    Observation record = new Observation(data, "guest", photo, false, "");
    String before = data.toString();
    Bitmap shown = StoryCard.render(context, record, true);
    Bitmap hidden = StoryCard.render(context, record, false);
    assertEquals(1080, shown.getWidth());
    assertEquals(1350, shown.getHeight());
    assertEquals(shown.getPixel(400, 400), hidden.getPixel(400, 400));
    boolean differs = false;
    for (int x = 40; x < 1000; x += 3)
      for (int y = 906; y < 945; y += 3)
        if (shown.getPixel(x, y) != hidden.getPixel(x, y)) differs = true;
    assertTrue("Place preview can hide the location", differs);
    File shared = File.createTempFile("story-share-", ".jpg");
    try (FileOutputStream out = new FileOutputStream(shared)) {
      assertTrue(shown.compress(Bitmap.CompressFormat.JPEG, 92, out));
    }
    ExifInterface exif = new ExifInterface(shared);
    assertNull(exif.getLatLong());
    assertNull(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
    assertEquals(before, data.toString());
    assertTrue(StoryCard.shortCopy(ai.optString("summary"), 140).length() <= 140);
    shown.recycle();
    hidden.recycle();
    photo.delete();
    shared.delete();
  }
}
