package com.field.logger.nativeapp;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.location.Location;
import java.io.File;
import java.time.Instant;
import java.util.*;
import org.json.*;

public final class JournalDb extends SQLiteOpenHelper {
  private static JournalDb instance;

  public static synchronized JournalDb get(Context c) {
    if (instance == null) instance = new JournalDb(c.getApplicationContext());
    return instance;
  }

  private JournalDb(Context c) {
    super(c, "fieldlogger-native.db", null, 2);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE observations(id TEXT PRIMARY KEY,owner TEXT NOT NULL,data TEXT NOT NULL,photo"
            + " TEXT NOT NULL,pending INTEGER NOT NULL,error TEXT NOT NULL DEFAULT '')");
    db.execSQL("CREATE INDEX by_owner ON observations(owner)");
    db.execSQL(
        "CREATE TABLE places(cell TEXT PRIMARY KEY,place TEXT NOT NULL,expires INTEGER NOT NULL)");
    createAchievements(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int next) {
    if (old < 2) createAchievements(db);
  }

  private void createAchievements(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS achievements(owner TEXT NOT NULL,badge TEXT NOT NULL,earned_at"
            + " TEXT NOT NULL,PRIMARY KEY(owner,badge))");
  }

  public synchronized Map<String, String> earned(String owner) {
    Map<String, String> result = new LinkedHashMap<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT badge,earned_at FROM achievements WHERE owner=?", new String[] {owner})) {
      while (c.moveToNext()) result.put(c.getString(0), c.getString(1));
    }
    return result;
  }

  public synchronized void earn(String owner, String badge, String when) {
    ContentValues v = new ContentValues();
    v.put("owner", owner);
    v.put("badge", badge);
    v.put("earned_at", when);
    getWritableDatabase()
        .insertWithOnConflict("achievements", null, v, SQLiteDatabase.CONFLICT_IGNORE);
  }

  public synchronized List<Achievements.Badge> evaluateAchievements(String owner) {
    List<Achievements.Badge> badges = Achievements.evaluate(list(owner));
    for (Achievements.Badge b : badges)
      if (b.complete()) earn(owner, b.id, Instant.now().toString());
    return badges;
  }

  private Observation read(Cursor c) {
    try {
      return new Observation(
          new JSONObject(c.getString(2)),
          c.getString(1),
          new File(c.getString(3)),
          c.getInt(4) != 0,
          c.getString(5));
    } catch (JSONException e) {
      throw new IllegalStateException("A journal record could not be read.", e);
    }
  }

  public synchronized List<Observation> list(String owner) {
    List<Observation> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,owner,data,photo,pending,error FROM observations WHERE owner=?",
                new String[] {owner})) {
      while (c.moveToNext()) result.add(read(c));
    }
    result.sort((a, b) -> b.data.optString("capturedAt").compareTo(a.data.optString("capturedAt")));
    return result;
  }

  public synchronized Observation toggleFlag(String id, String owner, String flag) {
    if (!flag.equals("acorned") && !flag.equals("checkLater"))
      throw new IllegalArgumentException("Unknown journal flag");
    Observation latest = find(id);
    if (latest == null || !latest.owner.equals(owner)) return null;
    JSONObject data = Observation.copy(latest.data);
    boolean active = !data.optBoolean(flag);
    Observation.put(data, flag, active);
    if (flag.equals("acorned"))
      Observation.put(
          data, "acornCount", Math.max(0, data.optInt("acornCount") + (active ? 1 : -1)));
    edit(latest, data);
    return find(id);
  }

  public synchronized Observation find(String id) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,owner,data,photo,pending,error FROM observations WHERE id=?",
                new String[] {id})) {
      return c.moveToFirst() ? read(c) : null;
    }
  }

  public synchronized List<Observation> listActive(String owner) {
    List<Observation> result = list(owner);
    result.removeIf(Observation::archived);
    return result;
  }

  public synchronized List<Observation> listArchive(String owner) {
    List<Observation> result = list(owner);
    result.removeIf(r -> !r.archived());
    return result;
  }

  public synchronized boolean archive(String id, String owner, boolean archived) {
    Observation latest = find(id);
    if (latest == null || !latest.owner.equals(owner)) return false;
    if (latest.archived() == archived) return true;
    JSONObject data = Observation.copy(latest.data);
    Observation.put(data, "archived", archived);
    edit(latest, data);
    return true;
  }

  public synchronized void save(Observation r) {
    ContentValues v = new ContentValues();
    v.put("id", r.id());
    v.put("owner", r.owner);
    v.put("data", r.data.toString());
    v.put("photo", r.photo.getAbsolutePath());
    v.put("pending", r.pending ? 1 : 0);
    v.put("error", r.error);
    getWritableDatabase()
        .insertWithOnConflict("observations", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public synchronized boolean markUploaded(String id, int revision, String owner) {
    Observation latest = find(id);
    if (latest == null || !latest.owner.equals(owner) || latest.revision() != revision)
      return false;
    save(new Observation(latest.data, owner, latest.photo, false, ""));
    return true;
  }

  public synchronized void error(String id, String owner, String error) {
    Observation r = find(id);
    if (r != null && r.owner.equals(owner))
      save(new Observation(r.data, owner, r.photo, r.pending, error));
  }

  public synchronized void enrichGps(String id, Location p) {
    Observation r = find(id);
    if (r == null
        || (r.hasGps()
            && (!r.data.optString("locationSource").equals("gps")
                || r.data.optDouble("accuracy", Double.MAX_VALUE) <= p.getAccuracy()))) return;
    JSONObject data = Observation.copy(r.data);
    Observation.put(data, "latitude", p.getLatitude());
    Observation.put(data, "longitude", p.getLongitude());
    Observation.put(data, "accuracy", (double) p.getAccuracy());
    Observation.put(data, "locationSource", "gps");
    edit(r, data);
  }

  public synchronized void enrichPlace(String id, double lat, double lon, String place) {
    Observation r = find(id);
    if (r == null
        || !r.hasGps()
        || !r.place().trim().isEmpty()
        || r.data.optDouble("latitude") != lat
        || r.data.optDouble("longitude") != lon) return;
    JSONObject data = Observation.copy(r.data);
    Observation.put(data, "place", place);
    edit(r, data);
  }

  public synchronized void edit(Observation prior, JSONObject data) {
    Observation.put(data, "revision", prior.revision() + 1);
    Observation.put(data, "updatedAt", Instant.now().toString());
    save(new Observation(data, prior.owner, prior.photo, true, ""));
  }

  public synchronized void mergeAnalysis(String id, String owner, JSONObject ai) {
    Observation r = find(id);
    if (r == null || !r.owner.equals(owner)) return;
    JSONObject data = Observation.copy(r.data);
    Observation.put(data, "identification", ai);
    Observation.put(data, "analysisState", "complete");
    if (!data.optBoolean("confirmed")) {
      for (String key : new String[] {"name", "scientificName", "category"})
        Observation.put(data, key, ai.optString(key, key.equals("category") ? "other" : ""));
    }
    save(new Observation(data, owner, r.photo, r.pending, ""));
  }

  public synchronized boolean mergeRemote(JSONObject remote, String owner, File file) {
    String id = remote.optString("id");
    Observation old = find(id);
    if (old != null
        && (!old.owner.equals(owner) || old.pending || old.revision() > remote.optInt("revision")))
      return false;
    save(new Observation(remote, owner, file, false, ""));
    return true;
  }

  public synchronized void adoptGuest(String owner) {
    ContentValues v = new ContentValues();
    v.put("owner", owner);
    v.put("pending", 1);
    getWritableDatabase().update("observations", v, "owner=?", new String[] {"guest"});
    for (Map.Entry<String, String> entry : earned("guest").entrySet())
      earn(owner, entry.getKey(), entry.getValue());
    getWritableDatabase().delete("achievements", "owner=?", new String[] {"guest"});
  }

  public synchronized String place(String cell) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT place FROM places WHERE cell=? AND expires>?",
                new String[] {cell, Long.toString(System.currentTimeMillis())})) {
      return c.moveToFirst() ? c.getString(0) : null;
    }
  }

  public synchronized void cachePlace(String cell, String place, long expires) {
    ContentValues v = new ContentValues();
    v.put("cell", cell);
    v.put("place", place);
    v.put("expires", expires);
    getWritableDatabase().insertWithOnConflict("places", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  public synchronized void remove(String id) {
    getWritableDatabase().delete("observations", "id=?", new String[] {id});
  }

  public synchronized void deleteOwner(String owner) {
    for (Observation r : list(owner)) r.photo.delete();
    getWritableDatabase().delete("observations", "owner=?", new String[] {owner});
    getWritableDatabase().delete("achievements", "owner=?", new String[] {owner});
  }
}
