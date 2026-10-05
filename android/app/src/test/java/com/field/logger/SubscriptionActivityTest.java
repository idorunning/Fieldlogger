package com.field.logger;

import static org.junit.Assert.*;
import android.view.*;
import android.widget.TextView;
import com.field.logger.nativeapp.Session;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {24, 36})
public class SubscriptionActivityTest {
  private View find(View v, String label) {
    if (v instanceof TextView && ((TextView) v).getText().toString().contains(label)) return v;
    if (v instanceof ViewGroup) for (int n = 0; n < ((ViewGroup) v).getChildCount(); n++) {
      View result = find(((ViewGroup) v).getChildAt(n), label); if (result != null) return result;
    }
    return null;
  }
  @Test public void unsignedGuestCannotCheckoutAndFreeExportsRemainDescribed() {
    new Session(RuntimeEnvironment.getApplication()).clear();
    try (ActivityController<SubscriptionActivity> controller = Robolectric.buildActivity(SubscriptionActivity.class).setup()) {
      View root = controller.get().getWindow().getDecorView();
      assertNotNull(find(root, "Sign in from your journal"));
      assertFalse(find(root, "Awaiting Google Play setup").isEnabled());
      assertNotNull(find(root, "all journal data as ZIP remain free"));
      assertNotNull(find(root, "Manage or cancel in Google Play"));
      assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
    }
  }
}
