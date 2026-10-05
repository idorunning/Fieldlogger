package com.field.logger.nativeapp;

import java.io.File;
import java.time.*;
import java.util.*;
import org.json.*;

public final class Observation {
  public final JSONObject data;
  public final String owner;
  public final File photo;
  public final boolean pending;
  public final String error;

  public Observation(JSONObject data, String owner, File photo, boolean pending, String error) {
    this.data = data;
    this.owner = owner;
    this.photo = photo;
    this.pending = pending;
    this.error = error;
  }

  public String id() {
    return data.optString("id");
  }

  public String name() {
    return data.optString("name").isEmpty() ? "A little mystery" : data.optString("name");
  }

  public String category() {
    return data.optString("category", "other");
  }

  public String place() {
    return data.optString("place");
  }

  public boolean hasGps() {
    return coords(data.opt("latitude"), data.opt("longitude"));
  }

  public int revision() {
    return data.optInt("revision", 1);
  }

  public static boolean coords(Object lat, Object lon) {
    return lat instanceof Number
        && lon instanceof Number
        && Double.isFinite(((Number) lat).doubleValue())
        && Double.isFinite(((Number) lon).doubleValue())
        && Math.abs(((Number) lat).doubleValue()) <= 90
        && Math.abs(((Number) lon).doubleValue()) <= 180;
  }

  public static JSONObject copy(JSONObject data) {
    try {
      return new JSONObject(data.toString());
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static void put(JSONObject data, String key, Object value) {
    try {
      data.put(key, value == null ? JSONObject.NULL : value);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static JSONObject fresh(String id, Instant instant) {
    JSONObject data = new JSONObject();
    ZonedDateTime date = instant.atZone(ZoneId.systemDefault());
    put(data, "id", id);
    put(data, "capturedAt", instant.toString());
    put(data, "localDate", date.toLocalDate().toString());
    put(data, "localHour", date.getHour());
    put(data, "timezone", date.getZone().getId());
    for (String key : new String[] {"latitude", "longitude", "accuracy", "identification"})
      put(data, key, null);
    put(data, "locationSource", "none");
    for (String key : new String[] {"place", "note", "name", "scientificName"}) put(data, key, "");
    put(data, "category", "other");
    put(data, "confirmed", false);
    put(data, "analysisState", "pending");
    put(data, "updatedAt", Instant.now().toString());
    put(data, "revision", 1);
    return data;
  }

  public static final String[] CATEGORIES = {
    "plants", "flowers", "bugs", "birds", "animals", "fungi", "landmarks", "other"
  };
  public static final int[] COLORS = {
    0xff40845b, 0xffb65091, 0xffb87717, 0xff397cb0, 0xffb5643a, 0xff8456a4, 0xff66788b, 0xff73776b
  };

  public static int color(String kind) {
    for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(kind)) return COLORS[i];
    return COLORS[7];
  }

  public static boolean knownSpecies(Observation r) {
    String name = r.data.optString("scientificName").trim();
    JSONObject ai = r.data.optJSONObject("identification");
    return name.split("\\s+").length >= 2
        && !name.matches("(?i).*\\b(spp?|species)\\.?$")
        && (r.data.optBoolean("confirmed")
            || ai == null
            || !ai.optString("confidence").equals("low"));
  }
}
