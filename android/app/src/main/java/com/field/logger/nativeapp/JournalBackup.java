package com.field.logger.nativeapp;

import android.util.Base64;
import android.util.Base64OutputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Same importable backup format, without retaining any photo's Base64 string in memory. */
public final class JournalBackup {
  private JournalBackup() {}

  private static void json(OutputStream out, String value) throws IOException {
    out.write(value.getBytes(StandardCharsets.UTF_8));
  }

  public static void write(
      OutputStream out, String exportedAt, List<Observation> records, Map<String, String> earned)
      throws IOException {
    json(
        out,
        "{\"format\":\"fieldnotes-backup-v1\",\"exportedAt\":"
            + JSONObject.quote(exportedAt)
            + ",\"observations\":[");
    byte[] buffer = new byte[8192];
    boolean first = true;
    for (Observation record : records) {
      if (!first) json(out, ",");
      first = false;
      JSONObject metadata = Observation.copy(record.data);
      metadata.remove("photo");
      String value = metadata.toString();
      if (record.photo.length() > 4L * 1024 * 1024)
        throw new IOException("A saved photo exceeds the backup's supported size.");
      json(out, value.substring(0, value.length() - 1));
      json(out, (metadata.length() == 0 ? "" : ",") + "\"photo\":\"data:image/jpeg;base64,");
      try (InputStream photo = new FileInputStream(record.photo);
          Base64OutputStream encoded =
              new Base64OutputStream(out, Base64.NO_WRAP | Base64.NO_CLOSE)) {
        int size;
        long bytes = 0;
        while ((size = photo.read(buffer)) != -1) {
          bytes += size;
          if (bytes > 4L * 1024 * 1024)
            throw new IOException("A saved photo exceeds the backup's supported size.");
          encoded.write(buffer, 0, size);
        }
      }
      json(out, "\"}");
    }
    json(out, "],\"achievements\":{");
    first = true;
    for (Map.Entry<String, String> badge : earned.entrySet()) {
      if (!first) json(out, ",");
      first = false;
      json(out, JSONObject.quote(badge.getKey()) + ":" + JSONObject.quote(badge.getValue()));
    }
    json(out, "}}");
    out.flush();
  }
}
