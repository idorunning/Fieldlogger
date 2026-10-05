package com.field.logger.nativeapp;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class IconView extends View {
  private final String icon;
  private final Paint paint = new Paint(3);
  private final int color;

  public IconView(Context c, String icon, int color) {
    super(c);
    this.icon = icon;
    this.color = color;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.save();
    float scale = Math.min(getWidth(), getHeight()) / 32f;
    canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
    canvas.scale(scale, scale);
    paint.setColor(color);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1.8f);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    switch (icon) {
      case "camera":
        rounded(canvas, 3, 6, 21, 20, 2);
        line(canvas, 7, 6, 9, 3);
        line(canvas, 9, 3, 15, 3);
        line(canvas, 15, 3, 17, 6);
        canvas.drawCircle(12, 13, 4, paint);
        break;
      case "journal":
        rounded(canvas, 3, 3, 12, 19, 1);
        rounded(canvas, 12, 3, 21, 19, 1);
        line(canvas, 12, 3, 12, 22);
        break;
      case "map":
        line(canvas, 3, 5, 9, 2);
        line(canvas, 9, 2, 15, 5);
        line(canvas, 15, 5, 21, 2);
        line(canvas, 21, 2, 21, 19);
        line(canvas, 21, 19, 15, 22);
        line(canvas, 15, 22, 9, 19);
        line(canvas, 9, 19, 3, 22);
        line(canvas, 3, 22, 3, 5);
        line(canvas, 9, 2, 9, 19);
        line(canvas, 15, 5, 15, 22);
        break;
      case "leaf":
      case "collection":
        Path p = new Path();
        p.moveTo(4, 18);
        p.cubicTo(1, 5, 16, 6, 20, 2);
        p.cubicTo(22, 21, 8, 25, 4, 18);
        canvas.drawPath(p, paint);
        line(canvas, 3, 22, 16, 9);
        break;
      case "milestones":
        canvas.drawCircle(12, 8, 6, paint);
        line(canvas, 8, 14, 5, 23);
        line(canvas, 5, 23, 11, 20);
        line(canvas, 11, 20, 12, 15);
        line(canvas, 16, 14, 19, 23);
        line(canvas, 19, 23, 13, 20);
        break;
      case "search":
        canvas.drawCircle(10, 10, 7, paint);
        line(canvas, 15, 15, 22, 22);
        break;
      case "gallery":
        rounded(canvas, 2, 4, 21, 21, 2);
        canvas.drawCircle(8, 9, 2, paint);
        line(canvas, 3, 19, 9, 13);
        line(canvas, 9, 13, 14, 18);
        line(canvas, 14, 18, 19, 13);
        line(canvas, 18, 1, 18, 7);
        line(canvas, 15, 4, 22, 4);
        break;
      case "close":
        line(canvas, 5, 5, 19, 19);
        line(canvas, 19, 5, 5, 19);
        break;
      case "key":
        canvas.drawCircle(15, 7, 5, paint);
        line(canvas, 11, 11, 2, 20);
        line(canvas, 2, 20, 5, 23);
        line(canvas, 5, 23, 8, 20);
        line(canvas, 6, 16, 9, 19);
        break;
      case "share":
        canvas.drawCircle(19, 4, 3, paint);
        canvas.drawCircle(4, 12, 3, paint);
        canvas.drawCircle(19, 20, 3, paint);
        line(canvas, 7, 10, 16, 5);
        line(canvas, 7, 14, 16, 19);
        break;
      case "plus":
        line(canvas, 12, 3, 12, 21);
        line(canvas, 3, 12, 21, 12);
        break;
      case "pin":
        canvas.drawCircle(12, 9, 3, paint);
        Path q = new Path();
        q.moveTo(12, 23);
        q.cubicTo(-6, 5, 5, -3, 12, 2);
        q.cubicTo(28, -2, 28, 8, 12, 23);
        canvas.drawPath(q, paint);
        break;
      case "flash":
        Path bolt = new Path();
        bolt.moveTo(14, 1);
        bolt.lineTo(3, 14);
        bolt.lineTo(11, 14);
        bolt.lineTo(10, 23);
        bolt.lineTo(21, 10);
        bolt.lineTo(13, 10);
        bolt.close();
        canvas.drawPath(bolt, paint);
        break;
      case "check":
        line(canvas, 3, 12, 9, 18);
        line(canvas, 9, 18, 21, 5);
        break;
      case "person":
        canvas.drawCircle(12, 7, 4, paint);
        canvas.drawArc(4, 13, 20, 28, 180, 180, false, paint);
        break;
      default:
        canvas.drawCircle(12, 12, 9, paint);
        line(canvas, 12, 7, 12, 13);
        canvas.drawPoint(12, 17, paint);
    }
    canvas.restore();
  }

  private void line(Canvas c, float a, float b, float d, float e) {
    c.drawLine(a, b, d, e, paint);
  }

  private void rounded(Canvas c, float a, float b, float d, float e, float radius) {
    c.drawRoundRect(a, b, d, e, radius, radius, paint);
  }
}
