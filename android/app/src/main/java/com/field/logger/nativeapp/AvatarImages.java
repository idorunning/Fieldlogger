package com.field.logger.nativeapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Authenticated cartoon thumbnails, with per-account caching and no external image URLs. */
public final class AvatarImages {
  private static final String UUID =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private static final LruCache<String, Bitmap> MEMORY = new LruCache<>(12);

  private AvatarImages() {}

  public static boolean allowed(JSONObject avatar) {
    if (avatar == null || !"generated".equals(avatar.optString("kind"))) return false;
    String revision = avatar.optString("revision"), path = avatar.optString("imageUrl");
    if (!revision.matches(UUID)) return false;
    if (path.equals("/api/avatar/image") || path.equals("/api/avatar/image?revision=" + revision))
      return true;
    return path.matches("/api/social/users/" + UUID + "/avatar/image")
        || path.matches("/api/social/users/" + UUID + "/avatar/image\\?revision=" + revision);
  }

  private static String digest(String value) throws Exception {
    byte[] hash =
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    StringBuilder out = new StringBuilder();
    for (byte b : hash) out.append(String.format(Locale.US, "%02x", b & 255));
    return out.toString();
  }

  public static Bitmap load(Context c, String owner, String cookie, JSONObject descriptor) {
    if (owner.isEmpty() || cookie.isEmpty() || !allowed(descriptor)) return null;
    try {
      String key =
          digest(
              owner
                  + "\n"
                  + descriptor.optString("imageUrl")
                  + "\n"
                  + descriptor.optString("revision"));
      synchronized (MEMORY) {
        Bitmap cached = MEMORY.get(key);
        if (cached != null) return cached;
      }
      File directory = new File(c.getCacheDir(), "avatar-cartoons");
      if (!directory.exists() && !directory.mkdirs()) return null;
      File file = new File(directory, key + ".png");
      byte[] bytes;
      if (file.isFile() && file.length() <= 8 * 1024 * 1024) {
        try (InputStream in = new FileInputStream(file)) {
          bytes = Api.read(in, 8 * 1024 * 1024);
        }
      } else {
        bytes =
            new Api().request(descriptor.optString("imageUrl"), "GET", cookie, null, null).bytes;
      }
      if (bytes.length > 8 * 1024 * 1024) return null;
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
      if (bounds.outWidth <= 0
          || bounds.outHeight <= 0
          || bounds.outWidth > 2048
          || bounds.outHeight > 2048) return null;
      bounds.inJustDecodeBounds = false;
      bounds.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 512);
      Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
      if (image == null) return null;
      if (!owner.equals(new Session(c).owner())) {
        image.recycle();
        return null;
      }
      // Cache cartoons only. The image URL and revision came from the authenticated service.
      if (!file.exists()) {
        try (FileOutputStream out = new FileOutputStream(file)) {
          out.write(bytes);
        }
        File[] files = directory.listFiles();
        if (files != null && files.length > 16) {
          Arrays.sort(files, Comparator.comparingLong(File::lastModified));
          for (int i = 0; i < files.length - 16; i++) files[i].delete();
        }
      }
      synchronized (MEMORY) {
        MEMORY.put(key, image);
      }
      return image;
    } catch (Exception ignored) {
      return null;
    }
  }

  public static void clearCache(Context c) {
    synchronized (MEMORY) {
      MEMORY.evictAll();
    }
    File[] files = new File(c.getCacheDir(), "avatar-cartoons").listFiles();
    if (files != null) for (File file : files) file.delete();
  }
}
