package com.field.logger.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONObject;

/** Small original vector portraits; no photographs or external avatar service. */
public final class AvatarView extends View {
  public static final String[] KEYS = {
    "skin", "hair", "cut", "eyes", "expression", "glasses", "presentation", "pose", "outfit"
  };
  public static final String[][] CHOICES = {
    {"Porcelain", "Light", "Warm beige", "Golden", "Olive", "Brown", "Deep brown", "Rich dark"},
    {"Black", "Brown", "Chestnut", "Blonde", "Silver", "Red", "Blue", "Pink"},
    {
      "Short",
      "Side swept",
      "Bob",
      "Long",
      "Curls",
      "Afro",
      "Bun",
      "Pixie",
      "Bald",
      "Mohawk",
      "Braids",
      "Quiff"
    },
    {"Brown", "Blue", "Green", "Hazel", "Grey"},
    {"Smile", "Big grin", "Calm", "Wink", "Surprised", "Laughing"},
    {"None", "Round", "Square", "Sunglasses"},
    {"Neutral", "Feminine", "Masculine"},
    {"Relaxed", "Waving", "Peace sign"},
    {"Forest", "Blue", "Gold", "Coral", "Purple", "Teal", "Pink", "Slate"}
  };
  private JSONObject avatar;
  private final Paint p = new Paint(3);
  private static final int[] SKIN = {
    0xffffdfcb, 0xffefc7a5, 0xffe2b18c, 0xffcb976b, 0xffb99370, 0xff9b684d, 0xff754b38, 0xff503427
  };
  private static final int[] HAIR = {
    0xff282a2c, 0xff593c2c, 0xff87553c, 0xffe4c16f, 0xffc8c9c5, 0xffb45132, 0xff4e80a5, 0xffc87896
  };
  private static final int[] EYES = {0xff714c31, 0xff397eac, 0xff447d5d, 0xff958544, 0xff718491};
  private static final int[] OUTFIT = {
    0xff256555, 0xff498bc2, 0xffdbb453, 0xffcc735e, 0xff8765aa, 0xff469f9a, 0xffc17e9d, 0xff687c90
  };

  public AvatarView(Context c, JSONObject value) {
    super(c);
    update(value);
  }

  public void update(JSONObject value) {
    avatar = value == null ? new JSONObject() : Observation.copy(value);
    setContentDescription("Personalised explorer avatar");
    invalidate();
  }

  private int v(String key, int count) {
    return Math.max(0, Math.min(count - 1, avatar.optInt(key)));
  }

  private void fill(Canvas c, int color, float l, float t, float r, float b, float radius) {
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    c.drawRoundRect(l, t, r, b, radius, radius, p);
  }

  @Override
  protected void onDraw(Canvas c) {
    c.save();
    float size = Math.min(getWidth(), getHeight());
    c.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
    c.scale(size / 100, size / 100);
    p.setColor(0xffe9eddf);
    p.setStyle(Paint.Style.FILL);
    c.drawCircle(50, 50, 49, p);
    int skin = SKIN[v("skin", 8)],
        hair = HAIR[v("hair", 8)],
        cut = v("cut", 12),
        exp = v("expression", 6),
        pose = v("pose", 3),
        gender = v("presentation", 3);
    if (cut == 2 || cut == 3 || cut == 10) fill(c, hair, 22, 15, 78, 78, 20);
    p.setColor(OUTFIT[v("outfit", 8)]);
    c.drawOval(18, 69, 82, 122, p);
    fill(c, skin, 43, 62, 57, 78, 5);
    fill(c, skin, 25, 32, 34, 47, 5);
    fill(c, skin, 66, 32, 75, 47, 5);
    fill(c, skin, 30, 17, 70, 69, gender == 2 ? 15 : 21);
    p.setColor(hair);
    if (cut != 8) {
      if (cut == 4 || cut == 5) {
        for (int i = 0; i < 9; i++) {
          double a = Math.PI + (Math.PI * i / 8);
          c.drawCircle(
              50 + (float) Math.cos(a) * 23, 27 + (float) Math.sin(a) * 17, cut == 5 ? 11 : 7, p);
        }
      } else if (cut == 6) {
        c.drawCircle(50, 10, 11, p);
        c.drawArc(27, 10, 73, 41, 180, 180, true, p);
      } else if (cut == 9) {
        c.drawRoundRect(44, 5, 57, 30, 7, 7, p);
      } else {
        c.drawArc(27, 9, 73, 42, 180, 180, true, p);
        if (cut == 1 || cut == 7 || cut == 11) c.drawOval(31, 14, 61, 34, p);
      }
      if (cut == 10)
        for (int i = 0; i < 4; i++) {
          fill(c, hair, 23 + i * 2, 32 + i * 10, 29 + i * 2, 45 + i * 10, 3);
          fill(c, hair, 70 - i * 2, 32 + i * 10, 76 - i * 2, 45 + i * 10, 3);
        }
    }
    p.setColor(0xff354338);
    p.setStrokeWidth(1.5f);
    p.setStyle(Paint.Style.STROKE);
    c.drawLine(37, 35, 44, 34, p);
    c.drawLine(56, 34, 63, 35, p);
    p.setStyle(Paint.Style.FILL);
    p.setColor(Color.WHITE);
    c.drawOval(36, 38, 46, 46, p);
    c.drawOval(54, 38, 64, 46, p);
    p.setColor(EYES[v("eyes", 5)]);
    c.drawCircle(41, 42, 3, p);
    c.drawCircle(59, 42, 3, p);
    p.setColor(0xff24302b);
    c.drawCircle(41, 42, 1.5f, p);
    c.drawCircle(59, 42, 1.5f, p);
    if (exp == 3 || exp == 5) {
      fill(c, skin, 53, 37, 66, 47, 0);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(2);
      p.setColor(0xff354338);
      c.drawArc(55, 40, 63, 46, 180, 180, false, p);
    }
    p.setStyle(Paint.Style.STROKE);
    p.setColor(0xffad705e);
    p.setStrokeWidth(1.4f);
    c.drawLine(50, 44, 48, 51, p);
    c.drawLine(48, 51, 52, 51, p);
    p.setColor(0xff783e39);
    p.setStrokeWidth(2);
    if (exp == 2) c.drawLine(44, 57, 56, 57, p);
    else if (exp == 4) c.drawOval(46, 54, 54, 62, p);
    else if (exp == 1 || exp == 5) {
      p.setStyle(Paint.Style.FILL);
      c.drawOval(41, 52, 59, 65, p);
      fill(c, Color.WHITE, 43, 53, 57, 57, 2);
    } else c.drawArc(41, 49, 59, 62, 0, 180, false, p);
    int glasses = v("glasses", 4);
    if (glasses > 0) {
      p.setColor(0xff35483e);
      p.setStrokeWidth(2);
      p.setStyle(glasses == 3 ? Paint.Style.FILL : Paint.Style.STROKE);
      c.drawRoundRect(33, 36, 48, 48, glasses == 1 ? 7 : 3, glasses == 1 ? 7 : 3, p);
      c.drawRoundRect(52, 36, 67, 48, glasses == 1 ? 7 : 3, glasses == 1 ? 7 : 3, p);
      p.setStyle(Paint.Style.STROKE);
      c.drawLine(48, 40, 52, 40, p);
    }
    if (gender == 2) {
      p.setColor(hair);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(2);
      c.drawArc(37, 51, 63, 68, 0, 180, false, p);
    }
    if (pose > 0) {
      p.setStyle(Paint.Style.FILL);
      p.setColor(skin);
      c.drawOval(76, 40, 88, 57, p);
      fill(c, skin, 78, 51, 87, 83, 5);
      for (int i = 0; i < (pose == 2 ? 2 : 4); i++)
        fill(c, skin, 76 + i * 3, pose == 2 ? 29 : 32 - i % 2 * 3, 78 + i * 3, 46, 2);
    }
    c.restore();
  }
}
