package com.field.logger.nativeapp;

import static org.junit.Assert.*;

import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 36})
public class ScrapbookStackTest {
  private ScrapbookStack stack;
  private List<String> pages;

  @Before
  public void setup() {
    pages = new ArrayList<>();
    List<Observation> records = new ArrayList<>();
    for (int i = 0; i < 3; i++)
      records.add(
          new Observation(
              Observation.fresh("photo-" + i, Instant.now()),
              "guest",
              new File("photo"),
              false,
              ""));
    stack =
        new ScrapbookStack(
            RuntimeEnvironment.getApplication(),
            records,
            "photo-0",
            r -> {
              TextView card = new TextView(RuntimeEnvironment.getApplication());
              card.setText(r.id());
              card.setOnClickListener(v -> pages.add("opened"));
              return card;
            },
            pages::add);
    stack.measure(
        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY));
    stack.layout(0, 0, 1000, 1500);
    android.app.Activity activity =
        Robolectric.buildActivity(android.app.Activity.class).setup().get();
    activity.setContentView(stack);
  }

  private View top() {
    FrameLayout pile = (FrameLayout) stack.getChildAt(0);
    return pile.getChildAt(pile.getChildCount() - 1);
  }

  private void touch(View view, int action, float x, float y) {
    MotionEvent event = MotionEvent.obtain(0, 10, action, x, y, 0);
    view.dispatchTouchEvent(event);
    event.recycle();
  }

  private void finishTurn() {
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
  }

  @Test
  public void horizontalSwipeRevealsNextMemoryAndCyclesBothWays() {
    View card = top();
    touch(card, MotionEvent.ACTION_DOWN, 600, 200);
    touch(card, MotionEvent.ACTION_MOVE, 250, 210);
    touch(card, MotionEvent.ACTION_UP, 250, 210);
    finishTurn();
    assertEquals(1, stack.currentIndex());
    stack.turn(-1);
    finishTurn();
    assertEquals(0, stack.currentIndex());
    stack.turn(-1);
    finishTurn();
    assertEquals(2, stack.currentIndex());
    assertFalse(pages.contains("opened"));
  }

  @Test
  public void verticalMovementAndCancelledSwipeNeverTurnOrOpenAPhoto() {
    View card = top();
    touch(card, MotionEvent.ACTION_DOWN, 300, 100);
    touch(card, MotionEvent.ACTION_MOVE, 305, 400);
    touch(card, MotionEvent.ACTION_UP, 305, 400);
    finishTurn();
    assertEquals(0, stack.currentIndex());
    touch(card, MotionEvent.ACTION_DOWN, 600, 200);
    touch(card, MotionEvent.ACTION_MOVE, 250, 210);
    touch(card, MotionEvent.ACTION_CANCEL, 250, 210);
    finishTurn();
    assertEquals(0, stack.currentIndex());
    assertFalse(pages.contains("opened"));
  }

  @Test
  public void tapOpensPhotoAndNextButtonWorksWithoutSwipe() {
    View card = top();
    touch(card, MotionEvent.ACTION_DOWN, 300, 100);
    touch(card, MotionEvent.ACTION_UP, 300, 100);
    assertTrue(pages.contains("opened"));
    LinearLayout controls = (LinearLayout) stack.getChildAt(1);
    controls.getChildAt(2).performClick();
    finishTurn();
    assertEquals(1, stack.currentIndex());
  }
}
