package com.field.logger.nativeapp;

import android.content.*;
import org.json.JSONObject;

public final class TrailPreferences {
  public static SharedPreferences of(Context c, String owner) {
    return c.getSharedPreferences("trail-preferences-" + owner, 0);
  }

  public static JSONObject avatar(Context c, String owner) {
    try {
      return new JSONObject(of(c, owner).getString("avatar", "{}"));
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public static void avatar(Context c, String owner, JSONObject value) {
    of(c, owner)
        .edit()
        .putString("avatar", value.toString())
        // The avatar generation endpoint has already activated a generated cartoon server-side.
        .putBoolean("avatarPending", !"generated".equals(value.optString("kind")))
        .apply();
  }
}
