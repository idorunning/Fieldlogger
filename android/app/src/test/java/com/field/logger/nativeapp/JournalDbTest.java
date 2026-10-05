package com.field.logger.nativeapp;

import static org.junit.Assert.*;

import android.location.Location;
import java.io.File;
import java.time.Instant;
import java.util.UUID;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 36})
public class JournalDbTest {
  private JournalDb db;
  private String owner;

  @Before
  public void setup() {
    db = JournalDb.get(RuntimeEnvironment.getApplication());
    owner = UUID.randomUUID().toString();
  }

  private Observation record() {
    String id = UUID.randomUUID().toString();
    return new Observation(
        Observation.fresh(id, Instant.parse("2026-10-05T10:00:00Z")),
        owner,
        new File(RuntimeEnvironment.getApplication().getFilesDir(), id + ".jpg"),
        true,
        "");
  }

  private Location location(double lat, double lon, float accuracy) {
    Location p = new Location("gps");
    p.setLatitude(lat);
    p.setLongitude(lon);
    p.setAccuracy(accuracy);
    return p;
  }

  @Test
  public void localJournalIsSeparatedByAccountAndGuestAdoption() {
    Observation guest = record();
    db.save(new Observation(guest.data, "guest", guest.photo, true, ""));
    Observation own = record();
    db.save(own);
    assertEquals(1, db.list(owner).size());
    db.adoptGuest(owner);
    assertTrue(db.list("guest").isEmpty());
    assertTrue(db.list(owner).stream().anyMatch(r -> r.id().equals(guest.id())));
    assertTrue(db.list("another-account").isEmpty());
  }

  @Test
  public void slowGpsArrivingAfterSavePreservesNoteAndPlace() {
    Observation r = record();
    Observation.put(r.data, "place", "My own woodland");
    Observation.put(r.data, "note", "Something tiny");
    db.save(r);
    db.enrichGps(r.id(), location(51.7, -1.2, 8));
    Observation next = db.find(r.id());
    assertTrue(next.hasGps());
    assertEquals("My own woodland", next.place());
    assertEquals("Something tiny", next.data.optString("note"));
    assertTrue(next.pending);
    assertEquals(2, next.revision());
  }

  @Test
  public void staleUploadCompletionCannotMarkANewerEditSynced() {
    Observation r = record();
    db.save(r);
    JSONObject change = Observation.copy(r.data);
    Observation.put(change, "note", "New note");
    db.edit(r, change);
    assertFalse(db.markUploaded(r.id(), 1, owner));
    assertTrue(db.find(r.id()).pending);
    assertTrue(db.markUploaded(r.id(), 2, owner));
    assertFalse(db.find(r.id()).pending);
  }

  @Test
  public void manualPlaceAndChangedCoordinatesSurviveDelayedLookup() {
    Observation r = record();
    Observation.put(r.data, "latitude", 51d);
    Observation.put(r.data, "longitude", 0d);
    db.save(r);
    db.enrichPlace(r.id(), 52, 0, "Wrong place");
    assertEquals("", db.find(r.id()).place());
    JSONObject changed = Observation.copy(r.data);
    Observation.put(changed, "place", "My label");
    db.edit(r, changed);
    db.enrichPlace(r.id(), 51, 0, "Automatic label");
    assertEquals("My label", db.find(r.id()).place());
  }

  @Test
  public void lateAnalysisCannotOverwriteConfirmedIdentityOrEditedNote() {
    Observation r = record();
    Observation.put(r.data, "confirmed", true);
    Observation.put(r.data, "name", "My robin");
    Observation.put(r.data, "note", "Edited while analysis ran");
    db.save(r);
    JSONObject ai = new JSONObject();
    Observation.put(ai, "name", "Suggested bird");
    Observation.put(ai, "category", "birds");
    db.mergeAnalysis(r.id(), owner, ai);
    Observation next = db.find(r.id());
    assertEquals("My robin", next.name());
    assertEquals("Edited while analysis ran", next.data.optString("note"));
    assertEquals("complete", next.data.optString("analysisState"));
    assertTrue(next.pending);
  }

  @Test
  public void remoteCannotReplacePendingLocalEditOrAnotherAccount() {
    Observation r = record();
    db.save(r);
    JSONObject remote = Observation.copy(r.data);
    Observation.put(remote, "note", "Remote");
    assertFalse(db.mergeRemote(remote, owner, r.photo));
    assertEquals("", db.find(r.id()).data.optString("note"));
    assertFalse(db.mergeRemote(remote, "another-account", r.photo));
  }

  @Test
  public void exifCoordinatesAreNotReplacedByCurrentLocation() {
    Observation r = record();
    Observation.put(r.data, "latitude", 0d);
    Observation.put(r.data, "longitude", 0d);
    Observation.put(r.data, "locationSource", "exif");
    db.save(r);
    db.enrichGps(r.id(), location(51, -1, 5));
    assertEquals(0d, db.find(r.id()).data.optDouble("latitude"), 0d);
    assertTrue(db.find(r.id()).hasGps());
  }

  @Test
  public void betterGpsImprovesCaptureAccuracyWithoutWorseFixOverwriting() {
    Observation r = record();
    db.save(r);
    db.enrichGps(r.id(), location(51, -1, 100));
    db.enrichGps(r.id(), location(51.001, -1, 5));
    db.enrichGps(r.id(), location(52, -1, 80));
    Observation next = db.find(r.id());
    assertEquals(51.001, next.data.optDouble("latitude"), 0d);
    assertEquals(5, next.data.optDouble("accuracy"), 0d);
  }

  @Test
  public void cachedPlaceExpiresAndInvalidCoordinatesAreRejected() {
    db.cachePlace("zero", "Oxford", System.currentTimeMillis() + 10000);
    assertEquals("Oxford", db.place("zero"));
    db.cachePlace("expired", "Oxford", 0);
    assertNull(db.place("expired"));
    assertTrue(Observation.coords(0, 0));
    assertFalse(Observation.coords(null, 0));
    assertFalse(Observation.coords(Double.NaN, 0));
    assertFalse(Observation.coords(100, 0));
  }

  @Test
  public void interruptedDraftDoesNotAppearInJournalUntilSaved() {
    Observation capture = record();
    db.save(new Observation(capture.data, "draft:" + owner, capture.photo, true, ""));
    assertTrue(db.list(owner).isEmpty());
    assertEquals(1, db.list("draft:" + owner).size());
    db.save(capture);
    assertEquals(1, db.list(owner).size());
    assertTrue(db.list("draft:" + owner).isEmpty());
  }
}
