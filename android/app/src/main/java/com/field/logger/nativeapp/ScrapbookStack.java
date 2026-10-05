package com.field.logger.nativeapp;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.function.*;

/** Horizontal paper turns leave vertical diary scrolling to the parent ScrollView. */
public final class ScrapbookStack extends LinearLayout {
  private final List<Observation> photos;
  private final Function<Observation, View> factory;
  private final Consumer<String> changed;
  private final FrameLayout pile;
  private final TextView counter;
  private final int slop;
  private int index;
  private float downX, downY;
  private boolean dragging, turning;

  public ScrapbookStack(
      Context context,
      List<Observation> photos,
      String selected,
      Function<Observation, View> factory,
      Consumer<String> changed) {
    super(context);
    this.photos = photos;
    this.factory = factory;
    this.changed = changed;
    slop = ViewConfiguration.get(context).getScaledTouchSlop();
    setOrientation(VERTICAL);
    for (int i = 0; i < photos.size(); i++) if (photos.get(i).id().equals(selected)) index = i;
    pile = new FrameLayout(context);
    pile.setClipChildren(false);
    pile.setClipToPadding(false);
    float screenWidth =
        context.getResources().getDisplayMetrics().widthPixels
            / context.getResources().getDisplayMetrics().density;
    float screenHeight =
        context.getResources().getDisplayMetrics().heightPixels
            / context.getResources().getDisplayMetrics().density;
    int height =
        Math.min(425, Math.max(245, Math.round(Math.min(screenWidth * 1.12f, screenHeight - 370))));
    addView(pile, new LayoutParams(-1, dp(height)));
    LinearLayout controls = new LinearLayout(context);
    controls.setGravity(Gravity.CENTER_VERTICAL);
    counter = new TextView(context);
    counter.setTextColor(0xfffffdf7);
    counter.setTextSize(13);
    counter.setGravity(Gravity.CENTER);
    controls.addView(arrow("‹", "Previous photo", -1), new LayoutParams(dp(48), dp(48)));
    controls.addView(counter, new LayoutParams(0, dp(48), 1));
    controls.addView(arrow("›", "Next photo", 1), new LayoutParams(dp(48), dp(48)));
    addView(controls);
    rebuild();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private View arrow(String label, String description, int direction) {
    TextView view = new TextView(getContext());
    view.setText(label);
    view.setTextSize(32);
    view.setTextColor(0xfffffdf7);
    view.setGravity(Gravity.CENTER);
    view.setContentDescription(description);
    view.setFocusable(true);
    view.setOnClickListener(v -> turn(direction));
    view.setVisibility(photos.size() > 1 ? VISIBLE : INVISIBLE);
    return view;
  }

  public int currentIndex() {
    return index;
  }

  private void rebuild() {
    pile.removeAllViews();
    int count = Math.min(3, photos.size());
    for (int depth = count - 1; depth >= 0; depth--) {
      View card = factory.apply(photos.get((index + depth) % photos.size()));
      FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
      lp.setMargins(dp(10), dp(8 + depth * 7), dp(10), dp(16 - depth * 4));
      card.setRotation(depth == 0 ? -.7f : depth == 1 ? 2.6f : -3f);
      card.setElevation(dp(3 + count - depth));
      if (depth > 0)
        card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      pile.addView(card, lp);
      if (depth == 0) card.setOnTouchListener(this::touch);
    }
    counter.setText(
        photos.size() == 1
            ? "1 memory · tap to open"
            : (index + 1) + " of " + photos.size() + " · swipe to turn");
    changed.accept(photos.get(index).id());
  }

  private boolean touch(View card, MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = event.getRawX();
        downY = event.getRawY();
        dragging = false;
        return true;
      case MotionEvent.ACTION_MOVE:
        float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
        if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.25f)
          dragging = photos.size() > 1;
        if (dragging) {
          getParent().requestDisallowInterceptTouchEvent(true);
          card.setTranslationX(dx);
          card.setRotation(-.7f + dx / dp(30));
          return true;
        }
        return true;
      case MotionEvent.ACTION_UP:
        getParent().requestDisallowInterceptTouchEvent(false);
        if (dragging && Math.abs(card.getTranslationX()) > dp(65)) {
          turn(card.getTranslationX() < 0 ? 1 : -1);
        } else {
          card.animate().translationX(0).rotation(-.7f).setDuration(160).start();
          if (!dragging
              && Math.abs(event.getRawY() - downY) < slop
              && Math.abs(event.getRawX() - downX) < slop) card.performClick();
        }
        return true;
      case MotionEvent.ACTION_CANCEL:
        card.animate().translationX(0).rotation(-.7f).setDuration(160).start();
        dragging = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        return true;
      default:
        return true;
    }
  }

  public void turn(int direction) {
    if (photos.size() < 2 || turning) return;
    turning = true;
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    View top = pile.getChildAt(pile.getChildCount() - 1);
    top.animate()
        .translationX(direction > 0 ? -getWidth() : getWidth())
        .rotation(direction > 0 ? -12 : 12)
        .alpha(.3f)
        .setDuration(180)
        .withEndAction(
            () -> {
              index = Math.floorMod(index + direction, photos.size());
              turning = false;
              rebuild();
              announceForAccessibility("Photo " + (index + 1) + " of " + photos.size());
            })
        .start();
  }
}
