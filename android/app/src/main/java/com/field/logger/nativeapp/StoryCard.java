package com.field.logger.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import androidx.core.content.res.ResourcesCompat;
import com.field.logger.R;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONObject;

/** A bounded, branded image, rendered from the saved discovery rather than invented copy. */
public final class StoryCard {
  public static final int WIDTH = 1080, HEIGHT = 1350;
  private static final int INK = 0xff154e45, PAPER = 0xfffffdf7, MUTED = 0xff647970;

  private StoryCard() {}

  public static String shortCopy(String value, int limit) {
    String clean = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    if (clean.length() <= limit) return clean;
    int cut = clean.lastIndexOf(' ', limit - 1);
    return clean.substring(0, cut > limit / 2 ? cut : limit - 1).replaceAll("[,;:]$", "") + "…";
  }

  public static String when(Observation record) {
    try {
      return Instant.parse(record.data.optString("capturedAt"))
          .atZone(ZoneId.of(record.data.optString("timezone", ZoneId.systemDefault().getId())))
          .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.UK));
    } catch (Exception ignored) {
      return record.day();
    }
  }

  public static Bitmap render(Context context, Observation record, boolean includePlace)
      throws IOException {
    Bitmap original = BitmapFactory.decodeFile(record.photo.getAbsolutePath());
    if (original == null) throw new IOException("Could not open this photo.");
    Bitmap card = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(card);
    c.drawColor(PAPER);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    p.setColor(INK);
    c.drawRect(0, 0, WIDTH, 8, p);
    Bitmap icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.trail_app_icon);
    if (icon != null) {
      c.drawBitmap(icon, null, new RectF(40, 24, 108, 92), p);
      icon.recycle();
    }
    text(
        c,
        "My Trail Log",
        120,
        25,
        650,
        48,
        INK,
        1,
        ResourcesCompat.getFont(context, R.font.trail_log_wordmark));
    text(c, "A MOMENT OUTDOORS", 760, 48, 280, 19, MUTED, 1, Typeface.DEFAULT_BOLD);
    RectF photo = new RectF(40, 116, 1040, 714);
    float ratio =
        Math.max(photo.width() / original.getWidth(), photo.height() / original.getHeight());
    float w = original.getWidth() * ratio, h = original.getHeight() * ratio;
    Path clip = new Path();
    clip.addRoundRect(photo, 24, 24, Path.Direction.CW);
    c.save();
    c.clipPath(clip);
    c.drawBitmap(
        original,
        null,
        new RectF(
            photo.centerX() - w / 2,
            photo.centerY() - h / 2,
            photo.centerX() + w / 2,
            photo.centerY() + h / 2),
        p);
    c.restore();
    original.recycle();
    String category = record.category().toUpperCase(Locale.UK);
    TextPaint badge = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    badge.setTypeface(Typeface.DEFAULT_BOLD);
    badge.setTextSize(23);
    p.setColor(Observation.color(record.category()));
    c.drawRoundRect(new RectF(64, 140, 100 + badge.measureText(category), 190), 25, 25, p);
    badge.setColor(Color.WHITE);
    c.drawText(category, 82, 174, badge);
    text(c, record.name(), 44, 738, 992, 52, INK, 2, Typeface.DEFAULT_BOLD);
    String scientific = record.data.optString("scientificName");
    text(c, scientific, 44, 866, 992, 25, MUTED, 1, Typeface.create("sans-serif", Typeface.ITALIC));
    if (includePlace) {
      pin(c, 57, 925);
      text(
          c,
          record.place().isEmpty()
              ? (record.hasGps() ? "Place name being added" : "Location unavailable")
              : record.place(),
          82,
          906,
          930,
          28,
          INK,
          1,
          Typeface.DEFAULT_BOLD);
    }
    clock(c, 57, 974);
    text(c, when(record), 82, 956, 930, 27, MUTED, 1, Typeface.DEFAULT);
    p.setColor(0xffedf2e6);
    c.drawRoundRect(new RectF(40, 1010, 1040, 1255), 24, 24, p);
    JSONObject ai = record.data.optJSONObject("identification");
    String summary = ai == null ? "" : ai.optString("summary");
    if (summary.isBlank()) summary = record.data.optString("note");
    if (summary.isBlank())
      summary = "A discovery saved in my trail journal. Its story is still unfolding.";
    text(c, shortCopy(summary, 140), 66, 1026, 948, 30, INK, 2, Typeface.DEFAULT);
    String detail = "";
    if (ai != null) {
      detail = ai.optString("interestingFact");
      if (detail.isBlank()) detail = ai.optString("seasonalContext");
      if (detail.isBlank()) detail = ai.optString("lookCloser");
      if (detail.isBlank() && ai.optJSONArray("identifyingFeatures") != null)
        detail = ai.optJSONArray("identifyingFeatures").optString(0);
    }
    if (!detail.isBlank()) {
      text(c, "A LITTLE MORE", 66, 1110, 900, 19, MUTED, 1, Typeface.DEFAULT_BOLD);
      text(c, shortCopy(detail, 155), 66, 1142, 948, 27, INK, 2, Typeface.DEFAULT);
    }
    String footer =
        ai != null && !record.data.optBoolean("confirmed")
            ? "Suggested identification · " + ai.optString("confidence", "unknown") + " confidence"
            : "Small wonders. Good memories.";
    text(c, footer, 44, 1281, 695, 20, MUTED, 1, Typeface.DEFAULT);
    text(c, "fieldlogger.co.uk", 768, 1280, 272, 22, INK, 1, Typeface.DEFAULT_BOLD);
    return card;
  }

  private static void text(
      Canvas c,
      String value,
      int x,
      int y,
      int width,
      float size,
      int color,
      int lines,
      Typeface typeface) {
    TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(color);
    paint.setTextSize(size);
    paint.setTypeface(typeface);
    StaticLayout layout =
        StaticLayout.Builder.obtain(value, 0, value.length(), paint, width)
            .setMaxLines(lines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(4, 1)
            .build();
    c.save();
    c.translate(x, y);
    layout.draw(c);
    c.restore();
  }

  private static Paint line() {
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(INK);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(3);
    return p;
  }

  private static void pin(Canvas c, float x, float y) {
    Paint p = line();
    Path path = new Path();
    path.moveTo(x, y + 17);
    path.cubicTo(x - 30, y - 10, x - 7, y - 30, x, y - 14);
    path.cubicTo(x + 7, y - 30, x + 30, y - 10, x, y + 17);
    c.drawPath(path, p);
    c.drawCircle(x, y - 5, 4, p);
  }

  private static void clock(Canvas c, float x, float y) {
    Paint p = line();
    c.drawCircle(x, y, 14, p);
    c.drawLine(x, y, x, y - 8, p);
    c.drawLine(x, y, x + 7, y + 3, p);
  }
}
