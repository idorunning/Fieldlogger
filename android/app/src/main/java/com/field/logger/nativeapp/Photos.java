package com.field.logger.nativeapp;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.json.JSONObject;

public final class Photos {
  public static Observation prepare(File source, String owner, Repository repo, boolean gallery)
      throws Exception {
    if (source.length() > 35 * 1024 * 1024) throw new IOException("Choose a photo under 35 MB.");
    ExifInterface exif = new ExifInterface(source);
    Instant captured = Instant.now();
    String exifDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
    if (gallery && exifDate != null)
      try {
        LocalDateTime value =
            LocalDateTime.parse(exifDate, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"));
        String offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL);
        captured =
            offset == null
                ? value.atZone(ZoneId.systemDefault()).toInstant()
                : value.toInstant(ZoneOffset.of(offset));
      } catch (Exception ignored) {
      }
    String id = UUID.randomUUID().toString();
    JSONObject data = Observation.fresh(id, captured);
    double[] gps = exif.getLatLong();
    if (gps != null && Observation.coords(gps[0], gps[1])) {
      Observation.put(data, "latitude", gps[0]);
      Observation.put(data, "longitude", gps[1]);
      Observation.put(data, "locationSource", "exif");
    }
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(source.getAbsolutePath(), options);
    if (options.outWidth <= 0 || options.outHeight <= 0)
      throw new IOException("This photo format cannot be opened.");
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 3600)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    Bitmap image = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
    if (image == null) throw new IOException("The photo could not be opened.");
    Matrix matrix = new Matrix();
    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
    switch (orientation) {
      case 2:
        matrix.setScale(-1, 1);
        break;
      case 3:
        matrix.setRotate(180);
        break;
      case 4:
        matrix.setScale(1, -1);
        break;
      case 5:
        matrix.setRotate(90);
        matrix.postScale(-1, 1);
        break;
      case 6:
        matrix.setRotate(90);
        break;
      case 7:
        matrix.setRotate(-90);
        matrix.postScale(-1, 1);
        break;
      case 8:
        matrix.setRotate(-90);
        break;
    }
    float scale = Math.min(1f, 1800f / Math.max(image.getWidth(), image.getHeight()));
    matrix.postScale(scale, scale);
    Bitmap adjusted =
        Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
    if (adjusted != image) image.recycle();
    File dest = repo.photoFile(id);
    try (FileOutputStream stream = new FileOutputStream(dest)) {
      if (!adjusted.compress(Bitmap.CompressFormat.JPEG, 88, stream))
        throw new IOException("Could not save the photo.");
      stream.getFD().sync();
    } finally {
      adjusted.recycle();
    }
    if (dest.length() > 4 * 1024 * 1024) {
      dest.delete();
      throw new IOException("The photo could not be reduced for upload.");
    }
    return new Observation(data, owner, dest, true, "");
  }
}
