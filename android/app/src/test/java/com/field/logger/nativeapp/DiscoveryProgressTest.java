package com.field.logger.nativeapp;

import static org.junit.Assert.*;

import android.content.Context;
import java.io.File;
import java.time.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24)
public class DiscoveryProgressTest {
  private Observation photo(String date, int hour, String name, String category) {
    JSONObject data =
        Observation.fresh(UUID.randomUUID().toString(), Instant.parse(date + "T12:00:00Z"));
    Observation.put(data, "localDate", date);
    Observation.put(data, "localHour", hour);
    Observation.put(data, "name", name);
    Observation.put(data, "category", category);
    return new Observation(data, "test", new File("unused"), false, "");
  }

  private Achievements.Badge badge(List<Observation> photos, String id) {
    return Achievements.evaluate(photos).stream().filter(b -> b.id.equals(id)).findFirst().get();
  }

  @Test
  public void calendarUsesCaptureLocalDateAndEarnedBadgesSurviveArchivingAndRemovingPhotos() {
    Context c = RuntimeEnvironment.getApplication();
    JournalDb db = JournalDb.get(c);
    String owner = UUID.randomUUID().toString();
    Observation p = photo("2026-12-25", 7, "Oak", "plants");
    db.save(new Observation(p.data, owner, p.photo, false, ""));
    db.evaluateAchievements(owner);
    assertTrue(db.earned(owner).containsKey("calendar-12-25"));
    assertTrue(db.archive(p.id(), owner, true));
    db.evaluateAchievements(owner);
    db.remove(p.id());
    db.evaluateAchievements(owner);
    assertTrue(db.earned(owner).containsKey("calendar-12-25"));
    assertEquals(0, db.list(owner).size());
    db.deleteOwner(owner);
    assertTrue(db.earned(owner).isEmpty());
    assertFalse(
        badge(List.of(photo("2026-12-24", 23, "Oak", "plants")), "calendar-12-25").complete());
  }

  @Test
  public void leapDayAndEasterAreDateSpecificAndRegularDaysDoNotUnlockThem() {
    assertTrue(
        badge(List.of(photo("2024-02-29", 12, "Robin", "birds")), "calendar-02-29").complete());
    assertFalse(
        badge(List.of(photo("2026-02-28", 12, "Robin", "birds")), "calendar-02-29").complete());
    assertTrue(
        badge(List.of(photo("2026-04-05", 12, "Robin", "birds")), "calendar-easter").complete());
  }

  @Test
  public void catalogueHasUniqueIdsProgressiveLayersAndSpecificSubjectRules() {
    List<Achievements.Badge> catalog = Achievements.evaluate(List.of());
    assertTrue(catalog.size() > 300);
    assertEquals(catalog.size(), catalog.stream().map(b -> b.id).distinct().count());
    assertEquals(6, Achievements.LEVELS.length);
    assertEquals(5, Achievements.level(150));
    assertEquals(0, Achievements.level(11));
    assertTrue(
        badge(List.of(photo("2026-10-05", 8, "English oak", "plants")), "subject-oak").complete());
    assertFalse(
        badge(List.of(photo("2026-10-05", 8, "Beech", "plants")), "subject-bee").complete());
    assertFalse(badge(List.of(photo("2026-10-05", 16, "Robin", "birds")), "time-0-1").complete());
  }

  @Test
  public void weatherTimeTreatmentHonoursSunriseSunsetAndLocalTimezone() {
    JSONObject w = new JSONObject();
    Observation.put(w, "timezone", "Europe/London");
    Observation.put(w, "sunrise", "2026-12-25T08:00");
    Observation.put(w, "sunset", "2026-12-25T16:00");
    assertEquals("sunrise", WeatherScene.phase(w, ZonedDateTime.parse("2026-12-25T07:45:00Z")));
    assertEquals("day", WeatherScene.phase(w, ZonedDateTime.parse("2026-12-25T12:00:00Z")));
    assertEquals("sunset", WeatherScene.phase(w, ZonedDateTime.parse("2026-12-25T16:20:00Z")));
    assertEquals("night", WeatherScene.phase(w, ZonedDateTime.parse("2026-12-25T17:30:00Z")));
  }

  @Test
  public void avatarIsPerAccountAndPersonalisationDoesNotUsePhotographs() {
    Context c = RuntimeEnvironment.getApplication();
    JSONObject a = new JSONObject();
    Observation.put(a, "skin", 7);
    Observation.put(a, "cut", 10);
    Observation.put(a, "pose", 1);
    TrailPreferences.avatar(c, "one", a);
    assertEquals(7, TrailPreferences.avatar(c, "one").optInt("skin"));
    assertEquals(0, TrailPreferences.avatar(c, "two").optInt("skin"));
    AvatarView v = new AvatarView(c, a);
    v.layout(0, 0, 120, 120);
    v.draw(
        new android.graphics.Canvas(
            android.graphics.Bitmap.createBitmap(
                120, 120, android.graphics.Bitmap.Config.ARGB_8888)));
    assertEquals("Personalised explorer avatar", v.getContentDescription());
  }

  @Test
  public void natureMaterialsHaveDistinctAccessibleColoursAndKeepExistingBadgeIdentity() {
    assertArrayEquals(
        new String[] {"Soil", "Clay", "Flint", "Quartz", "Amber", "Gold"}, Achievements.LEVELS);
    Set<Integer> colours = new HashSet<>();
    for (int i = 0; i < 6; i++) {
      colours.add(Achievements.colour(i));
      double l1 = luminance(Achievements.colour(i));
      double l2 = luminance(Achievements.onColour(i));
      double contrast = (Math.max(l1, l2) + .05) / (Math.min(l1, l2) + .05);
      assertTrue(
          "Tier " + i + " requires legible normal-sized labels: " + contrast, contrast >= 4.5);
      double ink = luminance(Achievements.ink(i)), paper = luminance(Achievements.background(i));
      assertTrue(
          "Tier text on paper " + i,
          (Math.max(ink, paper) + .05) / (Math.min(ink, paper) + .05) >= 4.5);
      assertFalse(Achievements.LEVEL_DESCRIPTION[i].isBlank());
    }
    assertEquals(6, colours.size());
    assertEquals(0, Achievements.level(11));
    assertEquals(1, Achievements.level(12));
    assertTrue(
        badge(List.of(photo("2026-12-25", 7, "Oak", "plants")), "calendar-12-25").complete());
  }

  private double luminance(int colour) {
    double[] channels = {
      (colour >> 16 & 255) / 255d, (colour >> 8 & 255) / 255d, (colour & 255) / 255d
    };
    for (int i = 0; i < channels.length; i++)
      channels[i] =
          channels[i] <= .04045 ? channels[i] / 12.92 : Math.pow((channels[i] + .055) / 1.055, 2.4);
    return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
  }

  @Test
  public void generatedAvatarsCannotPointToExternalOrUnrelatedAuthenticatedResources() {
    String revision = "4e5fa365-a781-45a1-87d5-0f7c6858be3b";
    JSONObject avatar = new JSONObject();
    Observation.put(avatar, "kind", "generated");
    Observation.put(avatar, "revision", revision);
    Observation.put(avatar, "imageUrl", "/api/avatar/image?revision=" + revision);
    assertTrue(AvatarImages.allowed(avatar));
    Observation.put(
        avatar,
        "imageUrl",
        "/api/social/users/98cbb601-fb32-40c1-8fa1-f43a0d0145b4/avatar/image?revision=" + revision);
    assertTrue(AvatarImages.allowed(avatar));
    for (String unsafe :
        new String[] {
          "https://example.com/portrait.png",
          "//example.com/photo",
          "/api/admin/members",
          "/api/avatar/image?revision=wrong",
          "/api/social/users/../../avatar/image",
          "/api/avatar/image?revision=" + revision + "&token=secret"
        }) {
      Observation.put(avatar, "imageUrl", unsafe);
      assertFalse(unsafe, AvatarImages.allowed(avatar));
    }
  }

  @Test
  public void changingToManualAvatarDropsGeneratedIdentifiersAndKeepsOnlyValidPortraitChoices() {
    JSONObject generated = new JSONObject();
    Observation.put(generated, "kind", "generated");
    Observation.put(generated, "revision", "4e5fa365-a781-45a1-87d5-0f7c6858be3b");
    Observation.put(generated, "imageUrl", "/api/avatar/image");
    Observation.put(generated, "skin", -4);
    Observation.put(generated, "cut", 100);
    JSONObject manual = AvatarPicker.manualDraft(generated);
    assertEquals(AvatarView.KEYS.length, manual.length());
    assertFalse(manual.has("kind"));
    assertFalse(manual.has("imageUrl"));
    assertFalse(manual.has("revision"));
    assertEquals(0, manual.optInt("skin"));
    assertEquals(11, manual.optInt("cut"));
  }

  @Test
  public void sourceAvatarPhotoIsReencodedWithoutLocationOrDeviceMetadata() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    File source = new File(c.getCacheDir(), "selfie-source-test.jpg");
    File reduced = new File(c.getCacheDir(), "selfie-reencoded-test.jpg");
    try {
      try (java.io.InputStream in = getClass().getResourceAsStream("/robin.jpg");
          java.io.OutputStream out = new java.io.FileOutputStream(source)) {
        out.write(Api.read(in, 2 * 1024 * 1024));
      }
      androidx.exifinterface.media.ExifInterface exif =
          new androidx.exifinterface.media.ExifInterface(source);
      exif.setLatLong(51.752, -1.257);
      exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE, "Private phone");
      exif.saveAttributes();
      byte[] jpeg = AvatarPhotoController.sanitise(source);
      assertTrue(jpeg.length > 0 && jpeg.length <= 2 * 1024 * 1024);
      try (java.io.OutputStream out = new java.io.FileOutputStream(reduced)) {
        out.write(jpeg);
      }
      androidx.exifinterface.media.ExifInterface clean =
          new androidx.exifinterface.media.ExifInterface(reduced);
      assertNull(clean.getLatLong());
      assertNull(clean.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE));
      android.graphics.BitmapFactory.Options size = new android.graphics.BitmapFactory.Options();
      size.inJustDecodeBounds = true;
      android.graphics.BitmapFactory.decodeFile(reduced.getAbsolutePath(), size);
      assertTrue(size.outWidth > 0 && size.outWidth <= 1024);
      assertTrue(size.outHeight > 0 && size.outHeight <= 1024);
    } finally {
      source.delete();
      reduced.delete();
    }
  }

  @Test
  public void cloudPhotoAllowanceFailurePreservesItsActionableMessageAndLocalData() {
    String message =
        "Your cloud photo allowance is used up. Photos stay on this phone; ZIP downloads remain"
            + " free.";
    Api.Failure quota = new Api.Failure(429, message, "photo_limit");
    assertTrue(quota.photoLimit());
    assertEquals(message, Repository.identificationMessage(quota));
    assertFalse(new Api.Failure(429, "Slow down.").photoLimit());
    Context c = RuntimeEnvironment.getApplication();
    JournalDb db = JournalDb.get(c);
    String owner = UUID.randomUUID().toString();
    Observation photo = photo("2026-10-05", 13, "Oak", "plants");
    db.save(new Observation(photo.data, owner, photo.photo, true, ""));
    db.error(photo.id(), owner, Repository.identificationMessage(quota));
    assertTrue(db.find(photo.id()).pending);
    assertEquals(message, db.find(photo.id()).error);
    db.deleteOwner(owner);
  }
}
