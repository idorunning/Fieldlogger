package com.field.logger.nativeapp;

import static org.junit.Assert.*;
import java.util.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 36)
public class MapClustersTest {
  private JSONObject photo(String id, double lat, double lon, String category, int acorns) throws Exception {
    return new JSONObject().put("id", id).put("latitude", lat).put("longitude", lon).put("category", category).put("acorns", acorns);
  }
  @Test public void nearbyPhotosMergeOutAndSplitWhenZoomedIn() throws Exception {
    List<JSONObject> photos = Arrays.asList(photo("a", 51.75, -1.26, "plants", 1), photo("b", 51.7504, -1.2604, "birds", 3));
    assertEquals(1, MapClusters.group(photos, 12, 76, 256).size());
    assertEquals(2, MapClusters.group(photos, 20, 76, 256).size());
  }
  @Test public void farAwayPhotosStaySeparate() throws Exception {
    List<JSONObject> photos = Arrays.asList(photo("london", 51.5, -.12, "plants", 1), photo("york", 53.96, -1.08, "birds", 3));
    assertEquals(2, MapClusters.group(photos, 10, 76, 256).size());
  }
  @Test public void datelineNeighboursMergeAtLongitudeSeam() throws Exception {
    List<JSONObject> photos = Arrays.asList(photo("a", 10, 179.999, "plants", 1), photo("b", 10, -179.999, "plants", 2));
    List<MapClusters.Cluster> clusters = MapClusters.group(photos, 12, 76, 256);
    assertEquals(1, clusters.size());
    assertEquals(2, clusters.get(0).photos.size());
    assertTrue(Math.abs(clusters.get(0).longitude) > 179.99);
  }
  @Test public void exactSamePlaceRemainsAccessibleAsStackAndDominantCategoryWins() throws Exception {
    List<JSONObject> photos = Arrays.asList(photo("a", 51.7, -1.2, "plants", 2), photo("b", 51.7, -1.2, "birds", 99), photo("c", 51.7, -1.2, "plants", 8));
    MapClusters.Cluster cluster = MapClusters.group(photos, 23, 76, 256).get(0);
    assertTrue(MapClusters.sameSpot(cluster.photos));
    assertEquals("plants", cluster.category);
    assertEquals("c", cluster.representative.optString("id"));
    assertEquals(3, cluster.photos.size());
  }
  @Test public void invalidLocationsExcludedAndReorderingDoesNotLosePhotos() throws Exception {
    JSONObject a = photo("a", 51.7, -1.2, "plants", 1), b = photo("b", 51.7001, -1.2001, "birds", 2);
    JSONObject invalid = new JSONObject().put("id", "invalid").put("latitude", JSONObject.NULL);
    List<MapClusters.Cluster> first = MapClusters.group(Arrays.asList(a, b, invalid), 12, 76, 256);
    List<MapClusters.Cluster> second = MapClusters.group(Arrays.asList(b, invalid, a), 12, 76, 256);
    assertEquals(1, first.size()); assertEquals(2, first.get(0).photos.size());
    assertEquals(first.get(0).longitude, second.get(0).longitude, .00000001);
    assertEquals(first.get(0).latitude, second.get(0).latitude, .00000001);
  }
  @Test public void densityScalingPreservesLogicalIconSpacing() throws Exception {
    List<JSONObject> photos = Arrays.asList(photo("a", 51.75, -1.26, "plants", 1), photo("b", 51.7504, -1.2604, "birds", 3));
    assertEquals(MapClusters.group(photos, 15, 76, 256).size(), MapClusters.group(photos, 15, 228, 768).size());
  }
}
