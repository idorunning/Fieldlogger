package com.field.logger.nativeapp;

import static org.junit.Assert.*;

import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24)
public class JournalBackupTest {
  @Test
  public void streamedBackupPreservesPhotosMetadataAndPermanentAchievements() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    File photo = new File(c.getCacheDir(), "stream-backup-photo.jpg");
    byte[] original = new byte[8197];
    new Random(13).nextBytes(original);
    try {
      try (OutputStream file = new FileOutputStream(photo)) {
        file.write(original);
      }
      JSONObject data =
          Observation.fresh(UUID.randomUUID().toString(), Instant.parse("2026-12-25T12:00:00Z"));
      Observation.put(data, "name", "A \"winter\" robin");
      Observation.put(data, "note", "Line one\nLine two");
      Observation.put(data, "latitude", 51.752);
      Observation.put(data, "longitude", -1.257);
      Observation.put(data, "archived", true);
      Observation record = new Observation(data, "test", photo, true, "");
      JSONObject other =
          Observation.fresh(UUID.randomUUID().toString(), Instant.parse("2026-12-26T12:00:00Z"));
      Observation.put(other, "name", "Oak");
      Observation second = new Observation(other, "test", photo, false, "");
      Map<String, String> badges = Map.of("calendar-12-25", "2026-12-25T12:00:00Z");
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      JournalBackup.write(output, "2026-12-25T13:00:00Z", List.of(record, second), badges);
      // A new record can still be written after Base64 finalisation: the parent wasn't closed.
      JSONObject backup = new JSONObject(output.toString(java.nio.charset.StandardCharsets.UTF_8));
      assertEquals("fieldnotes-backup-v1", backup.getString("format"));
      assertEquals(2, backup.getJSONArray("observations").length());
      JSONObject result = backup.getJSONArray("observations").getJSONObject(0);
      assertEquals(data.getString("note"), result.getString("note"));
      assertEquals(data.getString("name"), result.getString("name"));
      assertEquals(51.752, result.getDouble("latitude"), .00001);
      assertTrue(result.getBoolean("archived"));
      assertEquals(
          badges.get("calendar-12-25"),
          backup.getJSONObject("achievements").getString("calendar-12-25"));
      assertArrayEquals(
          original,
          Base64.decode(
              result.getString("photo").substring("data:image/jpeg;base64,".length()),
              Base64.NO_WRAP));
      assertEquals("Oak", backup.getJSONArray("observations").getJSONObject(1).getString("name"));
    } finally {
      photo.delete();
    }
  }

  @Test
  public void largeJournalWritesBoundedChunksWithoutClosingTheDestination() throws Exception {
    Context c = RuntimeEnvironment.getApplication();
    File photo = new File(c.getCacheDir(), "stream-backup-large.jpg");
    try {
      byte[] block = new byte[8192];
      try (OutputStream file = new FileOutputStream(photo)) {
        for (int i = 0; i < 128; i++) file.write(block);
      }
      List<Observation> records = new ArrayList<>();
      for (int i = 0; i < 30; i++)
        records.add(
            new Observation(
                Observation.fresh(UUID.randomUUID().toString(), Instant.now()),
                "test",
                photo,
                true,
                ""));
      final long[] count = {0};
      final boolean[] closed = {false};
      OutputStream sink =
          new OutputStream() {
            @Override
            public void write(int value) {
              count[0]++;
            }

            @Override
            public void write(byte[] bytes, int start, int length) {
              assertTrue(
                  "Write should be bounded, not a complete image or journal", length <= 32 * 1024);
              count[0] += length;
            }

            @Override
            public void close() {
              closed[0] = true;
            }
          };
      JournalBackup.write(sink, Instant.now().toString(), records, Map.of());
      assertTrue(count[0] > 40L * 1024 * 1024);
      assertFalse(closed[0]);
    } finally {
      photo.delete();
    }
  }
}
