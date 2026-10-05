package com.field.logger.nativeapp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

public final class AcornButton extends LinearLayout {
  private boolean active;
  private int count;

  public AcornButton(Context context, boolean active, int count) {
    super(context);
    setGravity(Gravity.CENTER);
    setPadding(dp(7), 0, dp(8), 0);
    setMinimumHeight(dp(48));
    setMinimumWidth(dp(64));
    setFocusable(true);
    setClickable(true);
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(0xf5fffdf7);
    bg.setCornerRadius(dp(25));
    setBackground(bg);
    update(active, count);
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  public void update(boolean selected, int total) {
    active = selected;
    count = Math.max(0, total);
    removeAllViews();
    addView(
        new IconView(getContext(), active ? "acorn-filled" : "acorn", 0xff154e45),
        new LayoutParams(dp(28), dp(28)));
    TextView label = new TextView(getContext());
    label.setText(Integer.toString(count));
    label.setTextSize(12);
    label.setTextColor(0xff154e45);
    label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(label);
    setContentDescription((active ? "Remove acorn" : "Give an acorn") + ", " + count + " acorns");
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName("android.widget.ToggleButton");
    info.setCheckable(true);
    info.setChecked(active);
  }
}
