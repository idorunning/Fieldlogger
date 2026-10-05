package com.field.logger.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.time.*;
import org.json.JSONObject;

/** A quiet weather treatment of the real woodland photo; animations expire after six seconds. */
public final class WeatherScene extends View {
  private final Paint paint = new Paint(3);
  private final JSONObject weather;
  private final boolean effects;
  private final long started = SystemClock.elapsedRealtime();

  public WeatherScene(Context c, JSONObject weather, boolean effects) {
    super(c);
    this.weather = weather == null ? new JSONObject() : weather;
    this.effects = effects;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setClickable(false);
  }

  public static String phase(JSONObject w, ZonedDateTime now) {
    try {
      if (w != null && !w.optString("timezone").isEmpty())
        now = now.withZoneSameInstant(ZoneId.of(w.optString("timezone")));
    } catch (Exception ignored) {
    }
    try {
      for (String key : new String[] {"sunrise", "sunset"}) {
        ZonedDateTime event = LocalDateTime.parse(w.optString(key)).atZone(now.getZone());
        if (Math.abs(Duration.between(event, now).toMinutes()) <= 45) return key;
      }
    } catch (Exception ignored) {
    }
    try {
      ZonedDateTime rise = LocalDateTime.parse(w.optString("sunrise")).atZone(now.getZone()),
          set = LocalDateTime.parse(w.optString("sunset")).atZone(now.getZone());
      if (now.toLocalDate().equals(rise.toLocalDate()))
        return now.isBefore(rise) || now.isAfter(set) ? "night" : "day";
    } catch (Exception ignored) {
    }
    int h = now.getHour();
    return h < 6 || h >= 21 ? "night" : h < 8 ? "sunrise" : h >= 18 ? "sunset" : "day";
  }

  @Override
  protected void onDraw(Canvas c) {
    paint.setAlpha(255);
    float w = getWidth(), h = getHeight();
    String phase = weather.optBoolean("fixed") ? "fixed" : phase(weather, ZonedDateTime.now()),
        kind = weather.optString("condition", "");
    int top = 0x88102323, bottom = 0xaa102f27;
    if (phase.equals("night")) {
      top = 0xcc081526;
      bottom = 0xda0b1827;
    } else if (phase.equals("sunrise")) {
      top = 0x99cf9160;
      bottom = 0x99153731;
    } else if (phase.equals("sunset")) {
      top = 0xaacb774b;
      bottom = 0xb32b344b;
    } else if (kind.equals("sunny")) {
      top = 0x55e6dca7;
      bottom = 0x99122e24;
    } else if (kind.equals("rain") || kind.equals("storm")) {
      top = 0xb349657b;
      bottom = 0xc317303e;
    } else if (kind.equals("snow")) {
      top = 0xc3e5eceb;
      bottom = 0xaa668089;
    } else if (kind.equals("fog") || kind.equals("cloudy")) {
      top = 0x996f8986;
      bottom = 0xaa193d34;
    }
    paint.setShader(
        new LinearGradient(0, 0, 0, h, new int[] {top, bottom}, null, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, w, h, paint);
    paint.setShader(null);
    if ((phase.equals("sunrise") || phase.equals("sunset") || kind.equals("sunny"))
        && !phase.equals("night")) {
      paint.setShader(
          new RadialGradient(
              w * .78f, h * .16f, w * .6f, 0x44fff4bf, 0x00fff4ce, Shader.TileMode.CLAMP));
      c.drawRect(0, 0, w, h, paint);
      paint.setShader(null);
    }
    long elapsed = SystemClock.elapsedRealtime() - started;
    if (!effects || elapsed > 6000) return;
    float alpha = Math.min(1, Math.min(elapsed / 700f, (6000 - elapsed) / 900f));
    paint.setColor(Color.WHITE);
    paint.setAlpha((int) (65 * alpha));
    paint.setStrokeWidth(1.5f);
    float t = elapsed / 1000f;
    if (kind.equals("rain") || kind.equals("storm"))
      for (int i = 0; i < 30; i++) {
        float x = ((i * 73f + t * 25) % Math.max(1, w)),
            y = ((i * 137f + t * 340) % Math.max(1, h));
        c.drawLine(x, y, x - 7, y + 20, paint);
      }
    else if (kind.equals("snow"))
      for (int i = 0; i < 26; i++) {
        float x = (i * 87f + (float) Math.sin(t + i) * 20) % Math.max(1, w),
            y = (i * 123f + t * 40) % Math.max(1, h);
        c.drawCircle(x, y, 2 + i % 3, paint);
      }
    else if (kind.equals("wind"))
      for (int i = 0; i < 8; i++) {
        float x = (i * 141f + t * 180) % Math.max(1, w), y = (i * 173f) % Math.max(1, h);
        paint.setStyle(Paint.Style.STROKE);
        c.drawArc(x, y, x + 70, y + 18, 15, 150, false, paint);
        paint.setStyle(Paint.Style.FILL);
      }
    if (kind.equals("rain") || kind.equals("storm") || kind.equals("snow") || kind.equals("wind"))
      postInvalidateOnAnimation();
  }
}
