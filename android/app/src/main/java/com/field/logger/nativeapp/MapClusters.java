package com.field.logger.nativeapp;

import java.util.*;
import org.json.JSONObject;

/** Screen-distance photo clustering in Web Mercator, including the longitude seam. */
public final class MapClusters {
  private MapClusters() {}
  public static final class Cluster {
    public final List<JSONObject> photos;
    public final double latitude, longitude;
    public final String category;
    public final JSONObject representative;
    Cluster(List<JSONObject> photos, double latitude, double longitude) {
      this.photos = Collections.unmodifiableList(new ArrayList<>(photos));
      this.latitude = latitude; this.longitude = longitude;
      TreeMap<String, Integer> counts = new TreeMap<>();
      for (JSONObject p : photos) counts.merge(p.optString("category", "other"), 1, Integer::sum);
      String dominant = "other"; int most = 0;
      for (Map.Entry<String, Integer> count : counts.entrySet())
        if (count.getValue() > most) { dominant = count.getKey(); most = count.getValue(); }
      category = dominant;
      JSONObject best = photos.get(0);
      for (JSONObject p : photos)
        if (p.optString("category", "other").equals(category)
            && (!best.optString("category", "other").equals(category) || p.optInt("acorns") > best.optInt("acorns"))) best = p;
      representative = best;
    }
  }
  private static final class Pending {
    final List<JSONObject> photos = new ArrayList<>();
    double sin, cos, y, x;
    long key;
    void add(JSONObject p, double py, double world) {
      double angle = Math.toRadians(p.optDouble("longitude"));
      sin += Math.sin(angle); cos += Math.cos(angle); y += py; photos.add(p);
      double lon = Math.toDegrees(Math.atan2(sin, cos));
      x = (lon + 180) / 360 * world;
    }
    double centreY() { return y / photos.size(); }
  }
  private static long key(long x, long y) { return (x << 32) ^ (y & 0xffffffffL); }
  private static double wrapDistance(double a, double b, double world) { double d = Math.abs(a - b); return Math.min(d, world - d); }
  private static double projectedY(double latitude, double world) {
    double lat = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, latitude)));
    return (1 - Math.log(Math.tan(lat) + 1 / Math.cos(lat)) / Math.PI) / 2 * world;
  }

  public static List<Cluster> group(List<JSONObject> source, double zoom, double radiusPixels, double tilePixels) {
    if (!Double.isFinite(zoom) || !Double.isFinite(radiusPixels) || !Double.isFinite(tilePixels)
        || radiusPixels <= 0 || tilePixels <= 0) throw new IllegalArgumentException("Invalid map scale.");
    double world = tilePixels * Math.pow(2, Math.max(0, Math.min(24, zoom)));
    long cells = Math.max(1, (long) Math.floor(world / radiusPixels));
    double cellWidth = world / cells;
    List<JSONObject> valid = new ArrayList<>();
    for (JSONObject p : source)
      if (p != null && Observation.coords(p.opt("latitude"), p.opt("longitude"))) valid.add(p);
    // Stable input order keeps clusters calm when the server reorders the same result set.
    valid.sort(Comparator.comparing(p -> p.optString("id")));
    Map<Long, List<Pending>> grid = new HashMap<>();
    List<Pending> all = new ArrayList<>();
    for (JSONObject p : valid) {
      double x = ((p.optDouble("longitude") + 180) / 360 * world) % world;
      double y = projectedY(p.optDouble("latitude"), world);
      long cx = (long) Math.floor(x / cellWidth), cy = (long) Math.floor(y / cellWidth);
      Pending nearest = null; double distance = radiusPixels * radiusPixels;
      for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
        long neighborX = Math.floorMod(cx + dx, cells);
        List<Pending> bin = grid.get(key(neighborX, cy + dy));
        if (bin == null) continue;
        for (Pending candidate : bin) {
          double deltaX = wrapDistance(x, candidate.x, world), deltaY = y - candidate.centreY();
          double squared = deltaX * deltaX + deltaY * deltaY;
          if (squared <= distance) { nearest = candidate; distance = squared; }
        }
      }
      if (nearest == null) { nearest = new Pending(); all.add(nearest); }
      else grid.get(nearest.key).remove(nearest);
      nearest.add(p, y, world);
      long binX = Math.floorMod((long) Math.floor(nearest.x / cellWidth), cells);
      nearest.key = key(binX, (long) Math.floor(nearest.centreY() / cellWidth));
      grid.computeIfAbsent(nearest.key, k -> new ArrayList<>()).add(nearest);
    }
    List<Cluster> result = new ArrayList<>();
    for (Pending p : all) {
      double lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * p.centreY() / world))));
      double lon = Math.toDegrees(Math.atan2(p.sin, p.cos));
      result.add(new Cluster(p.photos, lat, lon));
    }
    return result;
  }

  public static boolean sameSpot(List<JSONObject> photos) {
    if (photos.isEmpty()) return true;
    JSONObject first = photos.get(0);
    for (JSONObject p : photos) {
      double longitudeDifference = Math.abs(p.optDouble("longitude") - first.optDouble("longitude"));
      if (Math.abs(p.optDouble("latitude") - first.optDouble("latitude")) > .000001
          || Math.min(longitudeDifference, 360 - longitudeDifference) > .000001) return false;
    }
    return true;
  }
}
