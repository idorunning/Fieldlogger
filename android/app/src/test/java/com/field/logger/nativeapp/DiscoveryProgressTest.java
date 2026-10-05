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
}
