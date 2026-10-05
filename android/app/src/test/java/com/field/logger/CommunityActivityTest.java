package com.field.logger;

import static org.junit.Assert.*;

import android.content.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.field.logger.nativeapp.*;
import java.io.File;
import java.time.Instant;
import java.util.UUID;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {24, 36})
public class CommunityActivityTest {
  @Before
  public void setup() {
    new LauncherActivityTest().initializeBackgroundScheduler();
  }

  private View find(View v, String label) {
    if (label.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof TextView && ((TextView) v).getText().toString().equals(label)) return v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        View r = find(g.getChildAt(i), label);
        if (r != null) return r;
      }
    }
    return null;
  }

  @Test
  public void mapIsNativeWithAudienceFiltersAndCanExpand() {
    try (ActivityController<CommunityActivity> c =
        Robolectric.buildActivity(CommunityActivity.class).setup().visible()) {
      CommunityActivity a = c.get();
      View root = a.getWindow().getDecorView();
      assertNotNull(find(root, "My photos"));
      assertNotNull(find(root, "Following"));
      assertNotNull(find(root, "Nearby"));
      assertNotNull(find(root, "Everyone"));
      assertNotNull(find(root, "Later"));
      assertNotNull(find(root, "Invited"));
      View nav = find(root, "Camera");
      assertEquals(View.VISIBLE, ((View) nav.getParent()).getVisibility());
      find(root, "Toggle full screen map").performClick();
      assertEquals(View.GONE, ((View) nav.getParent()).getVisibility());
      find(root, "Toggle full screen map").performClick();
      assertEquals(View.VISIBLE, ((View) nav.getParent()).getVisibility());
      assertNull(Shadows.shadowOf(a).getNextStartedActivity());
    }
  }

  @Test
  public void communityRequiresAccountWithoutExposingCredentialEntry() {
    Intent i =
        new Intent(RuntimeEnvironment.getApplication(), CommunityActivity.class)
            .putExtra("mode", "settings");
    try (ActivityController<CommunityActivity> c =
        Robolectric.buildActivity(CommunityActivity.class, i).setup()) {
      View root = c.get().getWindow().getDecorView();
      assertNotNull(find(root, "Sign in or create account"));
      assertNull(find(root, "API key"));
      assertNull(Shadows.shadowOf(c.get()).getNextStartedActivity());
    }
  }

  @Test
  public void acornHasLargeAccessibleToggleAndPreservesOfflineCount() {
    Context c = RuntimeEnvironment.getApplication();
    String id = UUID.randomUUID().toString(), owner = UUID.randomUUID().toString();
    JournalDb db = JournalDb.get(c);
    JSONObject d = Observation.fresh(id, Instant.now());
    Observation.put(d, "acornCount", 6);
    Observation.put(d, "note", "Private note");
    db.save(new Observation(d, owner, new File(c.getFilesDir(), id + ".jpg"), false, ""));
    assertNull(db.toggleFlag(id, "another-user", "acorned"));
    Observation liked = db.toggleFlag(id, owner, "acorned");
    assertTrue(liked.pending);
    assertTrue(liked.data.optBoolean("acorned"));
    assertEquals(7, liked.data.optInt("acornCount"));
    assertEquals("Private note", liked.data.optString("note"));
    assertEquals(2, liked.revision());
    Observation later = db.toggleFlag(id, owner, "checkLater");
    assertTrue(later.data.optBoolean("checkLater"));
    assertEquals(7, later.data.optInt("acornCount"));
    Observation unliked = db.toggleFlag(id, owner, "acorned");
    assertEquals(6, unliked.data.optInt("acornCount"));
    assertTrue(unliked.data.optBoolean("checkLater"));
    AcornButton b = new AcornButton(c, true, 7);
    AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
    b.onInitializeAccessibilityNodeInfo(info);
    assertTrue(info.isCheckable());
    assertTrue(info.isChecked());
    assertTrue(b.getMinimumHeight() >= 48 * c.getResources().getDisplayMetrics().density);
  }

  @Test
  public void journalTabReturnsToExistingNativeJournal() {
    try (ActivityController<CommunityActivity> controller =
        Robolectric.buildActivity(CommunityActivity.class).setup()) {
      CommunityActivity activity = controller.get();
      find(activity.getWindow().getDecorView(), "Journal").performClick();
      Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
      assertEquals("journal", intent.getStringExtra("view"));
      assertEquals(LauncherActivity.class.getName(), intent.getComponent().getClassName());
      assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0);
      assertTrue(activity.isFinishing());
    }
  }
}
