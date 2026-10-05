package com.field.logger.nativeapp;

import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONObject;

public final class AvatarPicker {
  public interface Selected {
    void accept(JSONObject avatar);
  }

  public static void show(Context c, JSONObject current, Selected selected) {
    JSONObject draft = current == null ? new JSONObject() : Observation.copy(current);
    LinearLayout content = new LinearLayout(c);
    content.setOrientation(LinearLayout.VERTICAL);
    int dp = Math.round(c.getResources().getDisplayMetrics().density);
    content.setPadding(20 * dp, 12 * dp, 20 * dp, 20 * dp);
    AvatarView preview = new AvatarView(c, draft);
    LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(132 * dp, 132 * dp);
    pl.gravity = Gravity.CENTER;
    content.addView(preview, pl);
    TextView label = new TextView(c);
    label.setText("Start with a character, then make it yours");
    label.setTextColor(0xff154e45);
    content.addView(label);
    HorizontalScrollView presets = new HorizontalScrollView(c);
    LinearLayout strip = new LinearLayout(c);
    for (int n = 0; n < 24; n++) {
      JSONObject a = new JSONObject();
      for (int i = 0; i < AvatarView.KEYS.length; i++)
        Observation.put(a, AvatarView.KEYS[i], (n * (i + 3) + i) % AvatarView.CHOICES[i].length);
      AvatarView tile = new AvatarView(c, a);
      tile.setContentDescription("Choose character " + (n + 1));
      strip.addView(tile, new LinearLayout.LayoutParams(64 * dp, 72 * dp));
    }
    presets.addView(strip);
    content.addView(presets);
    Spinner[] controls = new Spinner[AvatarView.KEYS.length];
    String[] labels = {
      "Skin tone",
      "Hair colour",
      "Hair style",
      "Eyes",
      "Expression",
      "Glasses",
      "Presentation",
      "Pose",
      "Outfit"
    };
    for (int i = 0; i < controls.length; i++) {
      final int index = i;
      TextView l = new TextView(c);
      l.setText(labels[i]);
      l.setTextColor(0xff154e45);
      content.addView(l);
      Spinner s = new Spinner(c);
      controls[i] = s;
      s.setAdapter(
          new ArrayAdapter<>(
              c, android.R.layout.simple_spinner_dropdown_item, AvatarView.CHOICES[i]));
      s.setSelection(
          Math.max(
              0, Math.min(AvatarView.CHOICES[i].length - 1, draft.optInt(AvatarView.KEYS[i]))));
      content.addView(s, new LinearLayout.LayoutParams(-1, 48 * dp));
      s.setOnItemSelectedListener(
          new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(
                android.widget.AdapterView<?> p, View v, int position, long id) {
              Observation.put(draft, AvatarView.KEYS[index], position);
              preview.update(draft);
            }

            public void onNothingSelected(android.widget.AdapterView<?> p) {}
          });
    }
    // Presets update the same editor, preserving an obvious Save action.
    for (int n = 0; n < strip.getChildCount(); n++) {
      final int preset = n;
      strip
          .getChildAt(n)
          .setOnClickListener(
              v -> {
                for (int i = 0; i < controls.length; i++)
                  controls[i].setSelection((preset * (i + 3) + i) % AvatarView.CHOICES[i].length);
              });
    }
    ScrollView scroll = new ScrollView(c);
    scroll.addView(content);
    new AlertDialog.Builder(c)
        .setTitle("Your explorer avatar")
        .setView(scroll)
        .setPositiveButton("Save avatar", (d, w) -> selected.accept(Observation.copy(draft)))
        .setNegativeButton("Cancel", null)
        .show();
  }
}
