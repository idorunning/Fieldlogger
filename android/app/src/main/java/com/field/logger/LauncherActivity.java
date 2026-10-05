package com.field.logger;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.*;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.*;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.work.WorkManager;
import com.bumptech.glide.Glide;
import com.field.logger.nativeapp.*;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.*;

/** Field Logger's native Android interface. No WebView, browser activity or remote UI. */
public final class LauncherActivity extends AppCompatActivity {
  private static final int FOREST = 0xff173f35,
      PAPER = 0xfff8f9f4,
      LIME = 0xffd7e9a2,
      MUTED = 0xff728070;
  private Repository repo;
  private LocationCapture locator;
  private FrameLayout root, body;
  private LinearLayout shell;
  private String screen = "journal",
      selectedId = "",
      query = "",
      category = "all",
      from = "",
      to = "";
  private List<Observation> records = new ArrayList<>();
  private Observation draft;
  private MapView map;
  private ProcessCameraProvider cameraProvider;
  private ImageCapture imageCapture;
  private androidx.camera.core.Camera camera;
  private volatile android.location.Location fix;
  private volatile String gpsPhotoId = "";
  private boolean busy = false;
  private ActivityResultLauncher<String> cameraPermission;
  private ActivityResultLauncher<String[]> locationPermission;
  private ActivityResultLauncher<String[]> gallery, backupImport;
  private ActivityResultLauncher<String> export;
  private Runnable afterLocation;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    repo = new Repository(this);
    locator = new LocationCapture(this);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(FOREST);
    getWindow().setNavigationBarColor(FOREST);
    if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(false);
    cameraPermission =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            ok -> {
              if (ok) openCamera();
              else showCameraDenied();
            });
    locationPermission =
        registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
              Runnable action = afterLocation;
              afterLocation = null;
              if (action != null) action.run();
            });
    gallery =
        registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
              if (uri != null) importPhoto(uri);
            });
    backupImport =
        registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
              if (uri != null) importJournal(uri);
            });
    export =
        registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            uri -> {
              if (uri != null) exportJournal(uri);
            });
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (screen.equals("camera") || screen.equals("review")) {
                  leaveCapture();
                  showJournal();
                } else if (!screen.equals("journal")) showJournal();
                else {
                  setEnabled(false);
                  getOnBackPressedDispatcher().onBackPressed();
                  setEnabled(true);
                }
              }
            });
    root = new FrameLayout(this);
    root.setBackgroundColor(FOREST);
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          Insets safe =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
          return insets;
        });
    if (state != null) {
      screen = state.getString("screen", "journal");
      selectedId = state.getString("selected", "");
    }
    WorkManager.getInstance(this)
        .getWorkInfosForUniqueWorkLiveData("journal-sync")
        .observe(
            this,
            work -> {
              if (screen.equals("journal")
                  || screen.equals("collection")
                  || screen.equals("milestones")) reload();
              else if (screen.equals("detail")) {
                Observation chosen = repo.db.find(selectedId);
                if (chosen != null) showDetail(chosen);
              }
            });
    String restoredScreen = screen;
    showJournal();
    if (state != null && restoredScreen.equals("detail") && !selectedId.isEmpty()) {
      Observation record = repo.db.find(selectedId);
      if (record != null && record.owner.equals(repo.owner())) showDetail(record);
    }
    Repository.IO.execute(
        () -> {
          try {
            repo.enqueue();
          } catch (Exception ignored) {
          }
        });
    handleLink(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    handleLink(intent);
  }

  private void handleLink(Intent intent) {
    Uri uri = intent.getData();
    if (uri != null
        && "fieldlogger.co.uk".equals(uri.getHost())
        && "https".equals(uri.getScheme())) {
      String view = uri.getQueryParameter("view");
      if ("camera".equals(view)) openCamera();
      else if ("map".equals(view)) showMap();
      else if ("collection".equals(view)) showCollection();
      else if ("achievements".equals(view)) showMilestones();
      if ("api-key".equals(uri.getFragment())) showKeySettings();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("screen", screen);
    state.putString("selected", selectedId);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (map != null) map.onResume();
    if (repo != null
        && (screen.equals("journal") || screen.equals("collection") || screen.equals("milestones")))
      reload();
  }

  @Override
  protected void onPause() {
    if (map != null) map.onPause();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (locator != null) locator.stop();
    leaveCapture();
    if (map != null) map.onDetach();
    super.onDestroy();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private GradientDrawable shape(int color, float radius) {
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(color);
    bg.setCornerRadius(dp(radius));
    return bg;
  }

  private android.graphics.drawable.Drawable ripple(int color, float radius) {
    return new RippleDrawable(
        android.content.res.ColorStateList.valueOf(0x30ffffff),
        shape(color, radius),
        shape(-1, radius));
  }

  private LinearLayout column() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    return layout;
  }

  private LinearLayout row() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.HORIZONTAL);
    layout.setGravity(Gravity.CENTER_VERTICAL);
    return layout;
  }

  private TextView text(String value, float size, int color, boolean bold) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    v.setLineSpacing(dp(3), 1);
    return v;
  }

  private TextView title(String value, float size, int color) {
    TextView v = text(value, size, color, false);
    v.setTypeface(Typeface.create("serif", Typeface.NORMAL));
    return v;
  }

  private void pad(View view, int n) {
    view.setPadding(dp(n), dp(n), dp(n), dp(n));
  }

  private View iconButton(String icon, String label, int color, int background, Runnable action) {
    FrameLayout button = new FrameLayout(this);
    button.setContentDescription(label);
    button.setFocusable(true);
    button.setClickable(true);
    button.setBackground(ripple(background, 30));
    IconView v = new IconView(this, icon, color);
    button.addView(v, new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
    button.setOnClickListener(x -> action.run());
    button.setMinimumWidth(dp(48));
    button.setMinimumHeight(dp(48));
    return button;
  }

  private TextView button(String label, boolean primary, Runnable action) {
    TextView v = text(label, 16, primary ? PAPER : FOREST, true);
    v.setGravity(Gravity.CENTER);
    v.setPadding(dp(16), dp(12), dp(16), dp(12));
    v.setMinHeight(dp(54));
    v.setBackground(ripple(primary ? FOREST : 0xffeaf0e1, 16));
    v.setFocusable(true);
    v.setOnClickListener(x -> action.run());
    return v;
  }

  private void space(LinearLayout parent, int height) {
    parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
  }

  private ScrollView scroll(LinearLayout contents) {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(false);
    scroll.setClipToPadding(false);
    scroll.addView(contents);
    return scroll;
  }

  private void message(String value) {
    Toast.makeText(this, value, Toast.LENGTH_LONG).show();
  }

  private void async(RunnableEx operation, Runnable success) {
    busy = true;
    Repository.IO.execute(
        () -> {
          try {
            operation.run();
            runOnUiThread(
                () -> {
                  busy = false;
                  if (!isFinishing()) success.run();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  busy = false;
                  message(
                      e instanceof Api.Failure
                          ? e.getMessage()
                          : "Could not finish. Your saved photos are safe. "
                              + (e.getMessage() == null ? "Please try again." : e.getMessage()));
                });
          }
        });
  }

  private interface RunnableEx {
    void run() throws Exception;
  }

  private void frame(boolean woodland, boolean bottom) {
    if (map != null) {
      map.onPause();
      map.onDetach();
      map = null;
    }
    if (cameraProvider != null && !screen.equals("camera")) cameraProvider.unbindAll();
    root.removeAllViews();
    if (woodland) {
      ImageView bg = new ImageView(this);
      bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
      root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
      Glide.with(this).load("file:///android_asset/woodland.jpg").into(bg);
      View shade = new View(this);
      shade.setBackground(
          new GradientDrawable(
              GradientDrawable.Orientation.TOP_BOTTOM,
              new int[] {0x99092620, 0x55153627, 0x99102f27}));
      root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    }
    shell = column();
    shell.setBackgroundColor(woodland ? android.graphics.Color.TRANSPARENT : PAPER);
    root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout header = row();
    header.setPadding(dp(18), dp(8), dp(18), dp(8));
    header.setMinimumHeight(dp(68));
    TextView brand = title("Field Logger", 25, woodland ? PAPER : FOREST);
    brand.setContentDescription("Journal home");
    brand.setOnClickListener(v -> showJournal());
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(
        iconButton(
            "key",
            "Identification settings",
            woodland ? PAPER : FOREST,
            woodland ? 0x30173f35 : 0xffeaf0e1,
            this::showKeySettings),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    View account =
        iconButton(
            "person",
            repo.owner().equals("guest") ? "Sign in or register" : "Your account",
            woodland ? PAPER : FOREST,
            woodland ? 0x30173f35 : 0xffeaf0e1,
            this::showAccount);
    LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(48), dp(48));
    ap.leftMargin = dp(6);
    header.addView(account, ap);
    shell.addView(header);
    body = new FrameLayout(this);
    shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    if (bottom) shell.addView(bottomBar());
  }

  private View bottomBar() {
    LinearLayout bar = row();
    bar.setPadding(dp(5), dp(5), dp(5), dp(5));
    bar.setBackgroundColor(PAPER);
    bar.setMinimumHeight(dp(86));
    String[] labels = {"Journal", "Map", "Camera", "Collection", "Milestones"};
    String[] icons = {"journal", "map", "camera", "collection", "milestones"};
    Runnable[] actions = {
      this::showJournal, this::showMap, this::openCamera, this::showCollection, this::showMilestones
    };
    for (int i = 0; i < 5; i++) {
      final Runnable action = actions[i];
      LinearLayout item = column();
      item.setGravity(Gravity.CENTER);
      item.setMinimumHeight(dp(72));
      item.setFocusable(true);
      item.setContentDescription(labels[i]);
      item.setOnClickListener(v -> action.run());
      boolean active = screen.equals(icons[i]);
      int color = active ? FOREST : MUTED;
      if (i == 2) {
        View capture = iconButton("camera", "Camera", LIME, FOREST, action);
        item.addView(capture, new LinearLayout.LayoutParams(dp(52), dp(52)));
      } else {
        item.addView(
            new IconView(this, icons[i], color), new LinearLayout.LayoutParams(dp(32), dp(32)));
      }
      TextView label = text(labels[i], 10.5f, i == 2 ? FOREST : color, true);
      label.setGravity(Gravity.CENTER);
      label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      item.addView(label);
      bar.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
    }
    return bar;
  }

  private void reload() {
    if (body == null) return;
    records = repo.db.list(repo.owner());
    if (screen.equals("journal")) renderJournal();
    else if (screen.equals("collection")) renderCollection();
    else if (screen.equals("milestones")) renderMilestones();
  }

  private void showJournal() {
    leaveCapture();
    screen = "journal";
    frame(true, true);
    reload();
  }

  private boolean matches(Observation r) {
    String all =
        r.name()
            + " "
            + r.data.optString("scientificName")
            + " "
            + r.place()
            + " "
            + r.data.optString("note");
    String day = r.data.optString("localDate");
    return (category.equals("all") || category.equals(r.category()))
        && all.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))
        && (from.isEmpty() || day.compareTo(from) >= 0)
        && (to.isEmpty() || day.compareTo(to) <= 0);
  }

  private void renderJournal() {
    int scrollY =
        body.getChildCount() > 0 && body.getChildAt(0) instanceof ScrollView
            ? body.getChildAt(0).getScrollY()
            : 0;
    body.removeAllViews();
    LinearLayout content = column();
    content.setPadding(dp(18), dp(6), dp(18), dp(24));
    LinearLayout heading = row();
    LinearLayout names = column();
    names.addView(title("Your field journal", 26, PAPER));
    names.addView(
        text(
            records.size() + " " + (records.size() == 1 ? "discovery" : "discoveries"),
            13,
            0xffe3ebdc,
            false));
    heading.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(
        iconButton("search", "Search journal", PAPER, 0x60173f35, this::showFilters),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    heading.addView(
        iconButton(
            "gallery",
            "Add from gallery",
            PAPER,
            0x60173f35,
            () -> gallery.launch(new String[] {"image/*"})),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    content.addView(heading);
    space(content, 16);
    if (!query.isEmpty() || !category.equals("all") || !from.isEmpty() || !to.isEmpty()) {
      TextView clear =
          button(
              "Clear filters",
              false,
              () -> {
                query = "";
                category = "all";
                from = "";
                to = "";
                renderJournal();
              });
      content.addView(clear);
      space(content, 12);
    }
    int found = 0;
    for (Observation r : records)
      if (matches(r)) {
        found++;
        content.addView(photoCard(r), new LinearLayout.LayoutParams(-1, dp(400)));
        space(content, 18);
      }
    if (found == 0) {
      LinearLayout empty = column();
      pad(empty, 25);
      empty.setBackground(shape(0xaa0f342b, 24));
      empty.addView(
          new IconView(this, "camera", LIME), new LinearLayout.LayoutParams(dp(45), dp(45)));
      space(empty, 20);
      empty.addView(
          title(
              records.isEmpty() ? "Your first discovery awaits" : "No discoveries here yet",
              27,
              PAPER));
      space(empty, 12);
      empty.addView(
          text(
              records.isEmpty()
                  ? "Tap Camera below. Your photos will make this space yours."
                  : "Try another name, place, date or kind.",
              16,
              PAPER,
              false));
      content.addView(empty);
    }
    ScrollView journalScroll = scroll(content);
    body.addView(journalScroll, new FrameLayout.LayoutParams(-1, -1));
    journalScroll.post(() -> journalScroll.scrollTo(0, scrollY));
  }

  private View photoCard(Observation r) {
    FrameLayout card = new FrameLayout(this);
    card.setBackground(shape(FOREST, 24));
    card.setClipToOutline(true);
    card.setContentDescription(
        r.name() + ", " + (r.place().isEmpty() ? "Place not named" : r.place()));
    card.setFocusable(true);
    card.setOnClickListener(v -> showDetail(r));
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    card.addView(image, new FrameLayout.LayoutParams(-1, -1));
    Glide.with(this).load(r.photo).into(image);
    LinearLayout caption = column();
    caption.setPadding(dp(20), dp(60), dp(20), dp(20));
    caption.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00173f35, 0xf006231c}));
    caption.addView(text(r.name(), 23, PAPER, true));
    space(caption, 7);
    caption.addView(
        text(
            "⌖ "
                + (r.place().isEmpty()
                    ? (r.hasGps() ? "Place name pending" : "A moment outdoors")
                    : r.place()),
            14,
            0xffe3ebdc,
            false));
    caption.addView(text(dateLabel(r.data.optString("capturedAt")), 12, 0xffd7e5d0, false));
    card.addView(caption, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    TextView kind =
        text(
            r.category().substring(0, 1).toUpperCase(Locale.ROOT) + r.category().substring(1),
            12,
            PAPER,
            true);
    kind.setPadding(dp(12), dp(7), dp(12), dp(7));
    kind.setBackground(shape(Observation.color(r.category()), 18));
    FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
    kp.setMargins(dp(14), dp(14), dp(14), 0);
    card.addView(kind, kp);
    if (r.pending) {
      TextView status = text("Saved locally", 11, PAPER, false);
      pad(status, 8);
      status.setBackground(shape(0xaa173f35, 20));
      FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
      sp.setMargins(0, dp(14), dp(14), 0);
      card.addView(status, sp);
    }
    return card;
  }

  private String dateLabel(String iso) {
    try {
      return Instant.parse(iso)
          .atZone(ZoneId.systemDefault())
          .format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.UK));
    } catch (Exception e) {
      return iso;
    }
  }

  private EditText input(String hint, String value, boolean multiline) {
    EditText field = new EditText(this);
    field.setHint(hint);
    field.setText(value);
    field.setTextColor(FOREST);
    field.setHintTextColor(MUTED);
    field.setTextSize(16);
    field.setSingleLine(!multiline);
    field.setMinHeight(dp(54));
    field.setPadding(dp(14), dp(10), dp(14), dp(10));
    field.setBackground(shape(0xffeef2e9, 12));
    field.setContentDescription(hint);
    return field;
  }

  private void labeled(LinearLayout layout, String label, View field) {
    space(layout, 14);
    layout.addView(text(label, 13, FOREST, true));
    space(layout, 6);
    layout.addView(field, new LinearLayout.LayoutParams(-1, -2));
  }

  private void showFilters() {
    LinearLayout form = column();
    pad(form, 20);
    EditText search = input("Name, species or place", query, false);
    labeled(form, "Find a discovery", search);
    Spinner kinds = new Spinner(this);
    String[] values = new String[9];
    values[0] = "All discoveries";
    System.arraycopy(Observation.CATEGORIES, 0, values, 1, 8);
    kinds.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
    for (int i = 1; i < values.length; i++) if (category.equals(values[i])) kinds.setSelection(i);
    labeled(form, "Kind", kinds);
    EditText start = input("YYYY-MM-DD", from, false), end = input("YYYY-MM-DD", to, false);
    labeled(form, "From date", start);
    labeled(form, "To date", end);
    new AlertDialog.Builder(this)
        .setTitle("Explore your journal")
        .setView(form)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Apply",
            (d, w) -> {
              query = search.getText().toString();
              category =
                  kinds.getSelectedItemPosition() == 0
                      ? "all"
                      : values[kinds.getSelectedItemPosition()];
              from = start.getText().toString().trim();
              to = end.getText().toString().trim();
              if (!validDate(from) || !validDate(to)) {
                message("Use dates such as 2026-10-05.");
                from = "";
                to = "";
              }
              renderJournal();
            })
        .show();
  }

  private boolean validDate(String s) {
    if (s.isEmpty()) return true;
    try {
      LocalDate.parse(s);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void showDetail(Observation record) {
    leaveCapture();
    screen = "detail";
    selectedId = record.id();
    frame(false, false);
    LinearLayout content = column();
    LinearLayout close = row();
    pad(close, 14);
    close.addView(
        text("From your field journal", 16, FOREST, true), new LinearLayout.LayoutParams(0, -2, 1));
    close.addView(
        iconButton("close", "Close discovery", FOREST, 0xffeaf0e1, this::showJournal),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    content.addView(close);
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    content.addView(image, new LinearLayout.LayoutParams(-1, dp(420)));
    Glide.with(this).load(record.photo).into(image);
    LinearLayout details = column();
    pad(details, 22);
    details.addView(
        text(
            record.category().toUpperCase(Locale.ROOT),
            12,
            Observation.color(record.category()),
            true));
    space(details, 8);
    details.addView(title(record.name(), 34, FOREST));
    String scientific = record.data.optString("scientificName");
    if (!scientific.isEmpty()) details.addView(text(scientific, 17, MUTED, false));
    space(details, 16);
    details.addView(text(dateLabel(record.data.optString("capturedAt")), 14, MUTED, false));
    details.addView(
        text(
            record.place().isEmpty()
                ? (record.hasGps() ? "GPS saved · nearby place name pending" : "Place not named")
                : record.place(),
            16,
            FOREST,
            true));
    if (record.hasGps())
      details.addView(
          text(
              String.format(
                  Locale.UK,
                  "%.5f, %.5f",
                  record.data.optDouble("latitude"),
                  record.data.optDouble("longitude")),
              12,
              MUTED,
              false));
    space(details, 20);
    LinearLayout shares = row();
    shares.addView(
        button("Share photo", true, () -> share(record, false, false)),
        new LinearLayout.LayoutParams(0, -2, 1));
    TextView story = button("Photo & story", false, () -> chooseShare(record));
    LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
    second.leftMargin = dp(8);
    shares.addView(story, second);
    details.addView(shares);
    space(details, 12);
    details.addView(button("Add a note or correct the name", false, () -> editRecord(record)));
    JSONObject ai = record.data.optJSONObject("identification");
    space(details, 26);
    if (ai == null) {
      details.addView(title("A little mystery", 26, FOREST));
      space(details, 10);
      String why =
          record.error.isEmpty()
              ? "Your photo is saved. Identification appears after upload when your account has an"
                  + " OpenAI key."
              : record.error;
      details.addView(text(why, 16, MUTED, false));
      space(details, 12);
      details.addView(button("Identification settings", false, this::showKeySettings));
    } else {
      details.addView(
          text(
              "SUGGESTED ID · "
                  + ai.optString("confidence", "unknown").toUpperCase(Locale.ROOT)
                  + " CONFIDENCE",
              12,
              MUTED,
              true));
      space(details, 12);
      details.addView(text(ai.optString("summary"), 17, FOREST, false));
      section(
          details, "What to look for", join(ai.optJSONArray("identifyingFeatures"), "• ", "\n"));
      section(details, "Here and now", ai.optString("seasonalContext"));
      section(details, "Look closer", ai.optString("lookCloser"));
      section(
          details, ai.optString("referenceTitle", "Field guide"), ai.optString("referenceText"));
      section(details, "Other possibilities", join(ai.optJSONArray("alternatives"), "• ", "\n"));
      JSONArray sources = ai.optJSONArray("sources");
      if (sources != null && sources.length() > 0) {
        section(details, "Sources", "");
        for (int i = 0; i < sources.length(); i++) {
          JSONObject s = sources.optJSONObject(i);
          if (s != null) {
            TextView link =
                button(
                    s.optString("name", "Reference"),
                    false,
                    () -> openReference(s.optString("url")));
            details.addView(link);
            space(details, 8);
          }
        }
      }
      details.addView(
          text(
              "Identification is a suggestion. Check the visible features before confirming a"
                  + " species.",
              13,
              MUTED,
              false));
    }
    section(details, "Your field note", record.data.optString("note"));
    content.addView(details);
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private String join(JSONArray values, String prefix, String separator) {
    if (values == null) return "";
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < values.length(); i++) parts.add(prefix + values.optString(i));
    return String.join(separator, parts);
  }

  private void section(LinearLayout parent, String title, String value) {
    if (value.isEmpty() && !title.equals("Sources")) return;
    space(parent, 22);
    parent.addView(title(title, 25, FOREST));
    space(parent, 9);
    if (!value.isEmpty()) parent.addView(text(value, 16, FOREST, false));
  }

  private void editRecord(Observation record) {
    LinearLayout form = column();
    pad(form, 20);
    EditText name = input("Name", record.data.optString("name"), false),
        scientific = input("Scientific name", record.data.optString("scientificName"), false),
        place = input("Place", record.place(), false),
        note = input("Your field note", record.data.optString("note"), true),
        date = input("YYYY-MM-DD HH:mm", localTimestamp(record), false);
    labeled(form, "Name", name);
    labeled(form, "Scientific name", scientific);
    Spinner kind = new Spinner(this);
    kind.setAdapter(
        new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, Observation.CATEGORIES));
    for (int i = 0; i < Observation.CATEGORIES.length; i++)
      if (record.category().equals(Observation.CATEGORIES[i])) kind.setSelection(i);
    labeled(form, "Kind", kind);
    labeled(form, "Date and time", date);
    labeled(form, "Place", place);
    labeled(form, "Field note", note);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Your field note")
            .setView(scroll(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    view -> {
                      try {
                        if (name.length() > 160
                            || scientific.length() > 180
                            || place.length() > 160
                            || note.length() > 3000)
                          throw new IllegalArgumentException("Please shorten the name or note.");
                        Observation current = repo.db.find(record.id());
                        if (current == null || !current.owner.equals(repo.owner()))
                          throw new IllegalStateException("Open this discovery in its account.");
                        JSONObject data = Observation.copy(current.data);
                        Observation.put(data, "name", name.getText().toString().trim());
                        Observation.put(
                            data, "scientificName", scientific.getText().toString().trim());
                        Observation.put(data, "place", place.getText().toString().trim());
                        Observation.put(data, "note", note.getText().toString());
                        Observation.put(
                            data,
                            "category",
                            Observation.CATEGORIES[kind.getSelectedItemPosition()]);
                        Observation.put(
                            data, "confirmed", !name.getText().toString().trim().isEmpty());
                        LocalDateTime captured =
                            LocalDateTime.parse(
                                date.getText().toString().trim(),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                        Observation.put(
                            data,
                            "capturedAt",
                            captured.atZone(ZoneId.systemDefault()).toInstant().toString());
                        Observation.put(data, "localDate", captured.toLocalDate().toString());
                        Observation.put(data, "localHour", captured.getHour());
                        Observation.put(data, "timezone", ZoneId.systemDefault().getId());
                        repo.db.edit(current, data);
                        repo.enqueue();
                        dialog.dismiss();
                        showDetail(repo.db.find(record.id()));
                      } catch (Exception e) {
                        message(
                            e.getMessage() == null
                                ? "Check the date and try again."
                                : e.getMessage());
                      }
                    }));
    dialog.show();
  }

  private String localTimestamp(Observation r) {
    try {
      return Instant.parse(r.data.optString("capturedAt"))
          .atZone(ZoneId.systemDefault())
          .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    } catch (Exception e) {
      return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
  }

  private void openReference(String url) {
    Uri uri = Uri.parse(url);
    if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
      message("This reference link is unavailable.");
      return;
    }
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, uri));
    } catch (ActivityNotFoundException e) {
      message("No app is available to open this reference.");
    }
  }

  private void chooseShare(Observation record) {
    CheckBox location = new CheckBox(this);
    location.setText("Include place name");
    location.setChecked(false);
    new AlertDialog.Builder(this)
        .setTitle("Share photo & story")
        .setView(location)
        .setMessage("Exact GPS coordinates are omitted.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Share", (d, w) -> share(record, true, location.isChecked()))
        .show();
  }

  private void share(Observation record, boolean story, boolean place) {
    async(
        () -> {
          File dir = new File(getCacheDir(), "shared");
          dir.mkdirs();
          File file = new File(dir, record.id() + (story ? "-story.jpg" : ".jpg"));
          if (!story) {
            try (InputStream in = new FileInputStream(record.photo);
                OutputStream out = new FileOutputStream(file)) {
              byte[] buffer = new byte[8192];
              int n;
              while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
          } else {
            Bitmap original = BitmapFactory.decodeFile(record.photo.getAbsolutePath());
            if (original == null) throw new IOException("Could not open this photo.");
            int width = 1080;
            int height =
                Math.min(
                    1200, Math.round(width * (float) original.getHeight() / original.getWidth()));
            String summary = "";
            JSONObject ai = record.data.optJSONObject("identification");
            if (ai != null) summary = ai.optString("summary");
            if (summary.length() > 600) summary = summary.substring(0, 600) + "…";
            String copy =
                record.name()
                    + "\n"
                    + record.data.optString("scientificName")
                    + "\n"
                    + dateLabel(record.data.optString("capturedAt"))
                    + (place && !record.place().isEmpty() ? "\n" + record.place() : "")
                    + "\n\n"
                    + summary
                    + "\n\nField Logger";
            TextPaint paint = new TextPaint(3);
            paint.setColor(FOREST);
            paint.setTextSize(40);
            android.text.StaticLayout layout =
                android.text.StaticLayout.Builder.obtain(copy, 0, copy.length(), paint, width - 100)
                    .setLineSpacing(10, 1)
                    .build();
            Bitmap card =
                Bitmap.createBitmap(
                    width, height + layout.getHeight() + 100, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(card);
            c.drawColor(PAPER);
            c.drawBitmap(original, null, new Rect(0, 0, width, height), null);
            c.save();
            c.translate(50, height + 40);
            layout.draw(c);
            c.restore();
            try (FileOutputStream out = new FileOutputStream(file)) {
              card.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }
            original.recycle();
            card.recycle();
          }
        },
        () -> {
          File file =
              new File(
                  new File(getCacheDir(), "shared"), record.id() + (story ? "-story.jpg" : ".jpg"));
          Uri uri = FileProvider.getUriForFile(this, "com.field.logger.files", file);
          Intent intent =
              new Intent(Intent.ACTION_SEND)
                  .setType("image/jpeg")
                  .putExtra(Intent.EXTRA_STREAM, uri)
                  .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          intent.setClipData(ClipData.newRawUri("Field Logger photo", uri));
          startActivity(Intent.createChooser(intent, "Share your discovery"));
        });
  }

  private void showCollection() {
    leaveCapture();
    screen = "collection";
    frame(false, true);
    reload();
  }

  private Map<String, Observation> species() {
    Map<String, Observation> result = new LinkedHashMap<>();
    for (Observation r : records)
      if (Observation.knownSpecies(r))
        result.putIfAbsent(r.data.optString("scientificName").trim().toLowerCase(Locale.ROOT), r);
    return result;
  }

  private void renderCollection() {
    body.removeAllViews();
    LinearLayout content = column();
    pad(content, 20);
    content.addView(title("Your collection", 32, FOREST));
    space(content, 8);
    content.addView(
        text(
            records.size() + " discoveries · " + species().size() + " different species",
            16,
            MUTED,
            false));
    space(content, 20);
    for (String kind : Observation.CATEGORIES) {
      long count = records.stream().filter(r -> r.category().equals(kind)).count();
      LinearLayout tile = row();
      pad(tile, 18);
      tile.setBackground(shape(0xffeaf0e1, 18));
      TextView label =
          text(
              kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1),
              18,
              Observation.color(kind),
              true);
      tile.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
      tile.addView(text(Long.toString(count), 26, FOREST, true));
      tile.setContentDescription(label.getText() + ", " + count + " discoveries");
      tile.setOnClickListener(
          v -> {
            category = kind;
            showJournal();
          });
      content.addView(tile);
      space(content, 10);
    }
    section(content, "Species you’ve met", "");
    for (Observation r : species().values()) {
      TextView item =
          button(r.name() + "\n" + r.data.optString("scientificName"), false, () -> showDetail(r));
      content.addView(item);
      space(content, 8);
    }
    if (species().isEmpty())
      content.addView(
          text("Confirmed species will appear here as you discover more.", 16, MUTED, false));
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void showMilestones() {
    leaveCapture();
    screen = "milestones";
    frame(false, true);
    reload();
  }

  private void renderMilestones() {
    body.removeAllViews();
    LinearLayout content = column();
    pad(content, 20);
    content.addView(title("Stay curious", 32, FOREST));
    space(content, 8);
    content.addView(text("Small rewards for noticing your world.", 16, MUTED, false));
    Set<String> kinds = new HashSet<>(),
        areas = new HashSet<>(),
        months = new HashSet<>(),
        days = new HashSet<>();
    int small = 0;
    boolean early = false, evening = false;
    for (Observation r : records) {
      kinds.add(r.category());
      if (r.hasGps())
        areas.add(
            String.format(
                Locale.US,
                "%.2f,%.2f",
                r.data.optDouble("latitude"),
                r.data.optDouble("longitude")));
      String day = r.data.optString("localDate");
      days.add(day);
      if (day.length() >= 7) months.add(day.substring(0, 7));
      if (r.category().equals("bugs") || r.category().equals("fungi")) small++;
      early |= r.data.optInt("localHour") < 9;
      evening |= r.data.optInt("localHour") >= 17;
    }
    long botanics =
        species().values().stream()
            .filter(r -> r.category().equals("plants") || r.category().equals("flowers"))
            .count();
    String[] names = {
      "First wonder",
      "A little of everything",
      "Botanical beginnings",
      "Small worlds",
      "New corners",
      "Through the seasons",
      "Early bird",
      "Curious collector"
    };
    String[] descriptions = {
      "Save your very first discovery.",
      "Notice four different kinds of things.",
      "Meet five plant or flower species.",
      "Photograph five bugs or fungi.",
      "Make discoveries in three different areas.",
      "Collect discoveries in four different months.",
      "Save a discovery before 9 am.",
      "Get to know twenty different species."
    };
    int[] goals = {1, 4, 5, 5, 3, 4, 1, 20},
        progress =
            {
              records.size(),
              kinds.size(),
              (int) botanics,
              small,
              areas.size(),
              months.size(),
              early ? 1 : 0,
              species().size()
            };
    space(content, 22);
    for (int i = 0; i < names.length; i++) {
      LinearLayout tile = column();
      pad(tile, 22);
      tile.setBackground(shape(progress[i] >= goals[i] ? 0xffe6efd5 : 0xffeef1e9, 20));
      tile.addView(text((progress[i] >= goals[i] ? "✓ " : "") + names[i], 20, FOREST, true));
      space(tile, 8);
      tile.addView(text(descriptions[i], 15, MUTED, false));
      space(tile, 12);
      ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      bar.setMax(goals[i]);
      bar.setProgress(Math.min(progress[i], goals[i]));
      bar.setProgressTintList(android.content.res.ColorStateList.valueOf(FOREST));
      tile.addView(bar);
      tile.addView(text(Math.min(progress[i], goals[i]) + " / " + goals[i], 12, MUTED, false));
      content.addView(tile);
      space(content, 14);
    }
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void showMap() {
    leaveCapture();
    screen = "map";
    frame(false, true);
    records = repo.db.list(repo.owner());
    Configuration.getInstance().setUserAgentValue("FieldLogger-Android/2.0.0 (fieldlogger.co.uk)");
    Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(), "map"));
    Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "map/tiles"));
    map = new MapView(this);
    map.setTileSource(TileSourceFactory.MAPNIK);
    map.setMultiTouchControls(true);
    map.setBuiltInZoomControls(false);
    map.setTilesScaledToDpi(true);
    body.addView(map, new FrameLayout.LayoutParams(-1, -1));
    GeoPoint center = new GeoPoint(54, -2);
    boolean found = false;
    for (Observation r : records)
      if (r.hasGps()) {
        GeoPoint point = new GeoPoint(r.data.optDouble("latitude"), r.data.optDouble("longitude"));
        if (!found) {
          center = point;
          found = true;
        }
        Marker marker = new Marker(map);
        marker.setPosition(point);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setTitle(r.name());
        GradientDrawable dot = shape(Observation.color(r.category()), 16);
        dot.setSize(dp(25), dp(25));
        dot.setStroke(dp(3), PAPER);
        marker.setIcon(dot);
        marker.setOnMarkerClickListener(
            (m, v) -> {
              showDetail(r);
              return true;
            });
        map.getOverlays().add(marker);
      }
    map.getController().setZoom(found ? 14d : 5d);
    map.getController().setCenter(center);
    LinearLayout top = row();
    pad(top, 12);
    top.setBackground(shape(0xeef8f9f4, 18));
    top.addView(
        text(found ? "Your discovery map" : "Photos with GPS appear here", 16, FOREST, true),
        new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        iconButton("plus", "Zoom in", FOREST, 0xffeaf0e1, () -> map.getController().zoomIn()),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView minus = button("−", false, () -> map.getController().zoomOut());
    top.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(48)));
    FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    tp.setMargins(dp(12), dp(10), dp(12), 0);
    body.addView(top, tp);
    TextView attribution = text("© OpenStreetMap contributors", 12, FOREST, false);
    pad(attribution, 8);
    attribution.setBackgroundColor(PAPER);
    attribution.setOnClickListener(v -> openReference("https://www.openstreetmap.org/copyright"));
    body.addView(
        attribution, new FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM | Gravity.START));
    map.onResume();
  }

  private void showAccount() {
    if (repo.owner().equals("guest")) {
      showLogin(false);
      return;
    }
    JSONObject user = repo.session.get();
    if (user == null) {
      showLogin(false);
      return;
    }
    String[] actions = {
      "Sync now",
      "Identification settings",
      "Export journal",
      "Import web journal backup",
      "Sources & privacy",
      "Sign out",
      "Delete account"
    };
    new AlertDialog.Builder(this)
        .setTitle(user.optString("name", "Your journal"))
        .setItems(
            actions,
            (d, which) -> {
              switch (which) {
                case 0:
                  repo.enqueue();
                  message("Uploads queued. Your journal syncs when connected.");
                  break;
                case 1:
                  showKeySettings();
                  break;
                case 2:
                  export.launch("fieldlogger-" + LocalDate.now() + ".json");
                  break;
                case 3:
                  backupImport.launch(new String[] {"application/json", "text/plain"});
                  break;
                case 4:
                  showSources();
                  break;
                case 5:
                  async(repo::signOut, this::showJournal);
                  break;
                case 6:
                  deleteAccount();
                  break;
              }
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private void showLogin(boolean register) {
    LinearLayout form = column();
    pad(form, 20);
    form.addView(
        text(
            "Use your existing Field Logger account to bring your uploaded photos into the native"
                + " app.",
            16,
            MUTED,
            false));
    EditText name = input("Your name", "", false),
        email = input("Email", "", false),
        password = input("Password", "", false);
    email.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    password.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    if (Build.VERSION.SDK_INT >= 26) {
      password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
      email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
    }
    if (register) labeled(form, "Your name", name);
    labeled(form, "Email", email);
    labeled(form, "Password", password);
    space(form, 14);
    form.addView(
        button(
            "Import web journal backup",
            false,
            () -> backupImport.launch(new String[] {"application/json", "text/plain"})));
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(register ? "Create your account" : "Welcome back")
            .setView(scroll(form))
            .setNegativeButton("Cancel", null)
            .setNeutralButton(
                register ? "Sign in" : "Create account", (d, w) -> showLogin(!register))
            .setPositiveButton(register ? "Create account" : "Sign in", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    view -> {
                      if (busy) return;
                      String pass = password.getText().toString(),
                          mail = email.getText().toString(),
                          userName = name.getText().toString();
                      if (pass.length() < 8
                          || !android.util.Patterns.EMAIL_ADDRESS.matcher(mail.trim()).matches()
                          || (register && userName.trim().isEmpty())) {
                        message("Enter your email and a password of at least 8 characters.");
                        return;
                      }
                      password.setText("");
                      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                      Repository.IO.execute(
                          () -> {
                            try {
                              busy = true;
                              repo.authenticate(mail, pass, userName, register);
                              runOnUiThread(
                                  () -> {
                                    busy = false;
                                    dialog.dismiss();
                                    showJournal();
                                    message(
                                        "Signed in. Your uploaded photos will appear as they"
                                            + " download.");
                                  });
                            } catch (Exception e) {
                              runOnUiThread(
                                  () -> {
                                    busy = false;
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    message(
                                        e.getMessage() == null
                                            ? "Could not sign in. Please try again."
                                            : e.getMessage());
                                  });
                            }
                          });
                    }));
    dialog.show();
  }

  private void showKeySettings() {
    if (repo.owner().equals("guest")) {
      showLogin(false);
      return;
    }
    JSONObject user = repo.session.get();
    if (user == null) return;
    String owner = user.optString("id"), cookie = user.optString("cookie");
    async(
        () -> {
          JSONObject status = repo.api.json("/api/settings/openai-key", "GET", cookie, null);
          runOnUiThread(
              () -> {
                if (!repo.owner().equals(owner)) return;
                LinearLayout form = column();
                pad(form, 20);
                form.addView(
                    text(
                        status.optBoolean("hasKey")
                            ? "Your account already has an OpenAI key. It also works in the native"
                                + " app."
                            : "Connect your OpenAI key for photo identification. It is encrypted on"
                                + " the server and never stored in the app.",
                        16,
                        FOREST,
                        false));
                EditText key = input("Paste API key", "", false);
                key.setInputType(
                    android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                if (Build.VERSION.SDK_INT >= 26)
                  key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
                labeled(form, "Replace or add key", key);
                space(form, 14);
                form.addView(
                    text(
                        "OpenAI charges your API account for identification and connection tests.",
                        13,
                        MUTED,
                        false));
                AlertDialog dialog =
                    new AlertDialog.Builder(this)
                        .setTitle("Photo identification")
                        .setView(scroll(form))
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Save key", null)
                        .create();
                if (status.optBoolean("hasKey")) {
                  space(form, 16);
                  form.addView(
                      button(
                          "Test connection",
                          false,
                          () ->
                              async(
                                  () ->
                                      repo.api.json(
                                          "/api/settings/openai-key/test",
                                          "POST",
                                          cookie,
                                          new JSONObject()),
                                  () -> message("Connected. Photo identification is ready."))));
                  space(form, 10);
                  form.addView(
                      button(
                          "Remove saved key",
                          false,
                          () ->
                              new AlertDialog.Builder(this)
                                  .setTitle("Remove identification key?")
                                  .setMessage("Your journal photos remain saved.")
                                  .setNegativeButton("Cancel", null)
                                  .setPositiveButton(
                                      "Remove",
                                      (d, w) ->
                                          async(
                                              () ->
                                                  repo.api.json(
                                                      "/api/settings/openai-key",
                                                      "DELETE",
                                                      cookie,
                                                      null),
                                              () -> {
                                                dialog.dismiss();
                                                message("Key removed.");
                                              }))
                                  .show()));
                }
                dialog.setOnShowListener(
                    v ->
                        dialog
                            .getButton(AlertDialog.BUTTON_POSITIVE)
                            .setOnClickListener(
                                view -> {
                                  String secret = key.getText().toString().trim();
                                  if (!secret.matches("sk-[A-Za-z0-9_-]{16,1000}")) {
                                    message("Enter an OpenAI API key beginning with sk-.");
                                    return;
                                  }
                                  key.setText("");
                                  JSONObject value = new JSONObject();
                                  Observation.put(value, "apiKey", secret);
                                  async(
                                      () ->
                                          repo.api.json(
                                              "/api/settings/openai-key", "PUT", cookie, value),
                                      () -> {
                                        dialog.dismiss();
                                        repo.enqueue();
                                        message(
                                            "Key saved. Pending photos will be identified when"
                                                + " connected.");
                                      });
                                }));
                dialog.show();
              });
        },
        () -> {});
  }

  private void deleteAccount() {
    EditText password = input("Confirm password", "", false);
    password.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    LinearLayout form = column();
    pad(form, 20);
    form.addView(
        text(
            "This removes your account, uploaded journal and this account’s native phone copies."
                + " Export first if you want to keep a backup.",
            16,
            FOREST,
            false));
    labeled(form, "Password", password);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Delete your account?")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete account", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    view -> {
                      JSONObject user = repo.session.get();
                      if (user == null) return;
                      String owner = user.optString("id"), cookie = user.optString("cookie");
                      JSONObject value = new JSONObject();
                      Observation.put(value, "password", password.getText().toString());
                      password.setText("");
                      async(
                          () -> {
                            repo.deleteAccount(value.optString("password"));
                          },
                          () -> {
                            dialog.dismiss();
                            showJournal();
                            message("Your account has been deleted.");
                          });
                    }));
    dialog.show();
  }

  private void showSources() {
    screen = "sources";
    frame(false, false);
    LinearLayout content = column();
    pad(content, 22);
    content.addView(button("Back to journal", false, this::showJournal));
    section(
        content,
        "Your photos. Your journal.",
        "Field Logger saves photos, dates, notes and optional GPS in its own private Android"
            + " database and files. Your session is encrypted using Android Keystore. Passwords and"
            + " API keys are not kept in the app. Location is requested during camera capture,"
            + " never as continuous walking history.");
    section(
        content,
        "Offline and backup",
        "Photos save on this phone first. Android schedules uploads when you’re signed in and"
            + " connected. Keep the app installed and export backups for discoveries that have not"
            + " uploaded. Other accounts cannot see this account’s local journal. The native app"
            + " and old browser storage are separate; uploaded photos come across through your"
            + " account, and device-only web photos can be imported from a journal export.");
    section(
        content,
        "Identification",
        "OpenAI receives the resized photo and capture context when you connect an account key."
            + " Suggestions can be mistaken. Wikipedia and GBIF provide named reference material."
            + " Optional specialist classifiers depend on server configuration. Avoid using"
            + " identification to decide whether a plant or fungus is safe to eat.");
    section(
        content,
        "Place names and maps",
        "Nearby place names use Photon’s OpenStreetMap data. Coordinates rounded to four decimal"
            + " places are sent through our server, with no photo or account identity. Names are"
            + " cached and manually entered names are kept. Map tiles are requested for the area"
            + " you view; maps need a connection. © OpenStreetMap contributors.");
    section(
        content,
        "Sharing",
        "Sharing opens Android’s chooser only when you ask. Photo files are resized and have EXIF"
            + " removed. Story cards omit exact GPS; place names are included only if selected."
            + " Journal exports contain original saved coordinates and should be kept private.");
    space(content, 20);
    content.addView(button("Wikipedia", false, () -> openReference("https://www.wikipedia.org")));
    space(content, 8);
    content.addView(button("GBIF", false, () -> openReference("https://www.gbif.org")));
    space(content, 8);
    content.addView(
        button(
            "Photon · OpenStreetMap geocoder",
            false,
            () -> openReference("https://github.com/komoot/photon")));
    space(content, 8);
    content.addView(
        button(
            "OpenStreetMap attribution",
            false,
            () -> openReference("https://www.openstreetmap.org/copyright")));
    space(content, 8);
    content.addView(
        button(
            "Support & project",
            false,
            () -> openReference("https://github.com/idorunning/Fieldlogger")));
    section(
        content,
        "Photo credits",
        "Woodland photograph by Rob Wingate · Unsplash. Your journal uses your own photographs.");
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void importJournal(Uri uri) {
    String owner = repo.owner();
    async(
        () -> {
          byte[] bytes = Api.read(getContentResolver().openInputStream(uri), 32 * 1024 * 1024);
          int count = repo.importBackup(bytes, owner);
          runOnUiThread(() -> message(count + " discoveries imported."));
        },
        this::showJournal);
  }

  private void exportJournal(Uri uri) {
    String owner = repo.owner();
    async(
        () -> {
          JSONObject backup = new JSONObject();
          Observation.put(backup, "format", "fieldnotes-backup-v1");
          Observation.put(backup, "exportedAt", Instant.now().toString());
          JSONArray rows = new JSONArray();
          for (Observation r : repo.db.list(owner)) {
            JSONObject item = Observation.copy(r.data);
            try (InputStream in = new FileInputStream(r.photo)) {
              Observation.put(
                  item,
                  "photo",
                  "data:image/jpeg;base64,"
                      + android.util.Base64.encodeToString(
                          Api.read(in, 4 * 1024 * 1024), android.util.Base64.NO_WRAP));
            }
            rows.put(item);
          }
          Observation.put(backup, "observations", rows);
          try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open the backup destination.");
            out.write(backup.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
          }
        },
        () -> message("Journal exported, including photos and GPS."));
  }

  private void importPhoto(Uri uri) {
    if (busy) return;
    locator.stop();
    fix = null;
    gpsPhotoId = "";
    String owner = repo.owner();
    async(
        () -> {
          File file = File.createTempFile("gallery-", ".image", getCacheDir());
          try {
            try (InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(file)) {
              out.write(Api.read(in, 35 * 1024 * 1024));
            }
            draft = Photos.prepare(file, owner, repo, true);
            persistDraft();
          } finally {
            file.delete();
          }
        },
        this::showReview);
  }

  private void persistDraft() {
    if (draft != null)
      repo.db.save(new Observation(draft.data, "draft:" + draft.owner, draft.photo, true, ""));
  }

  private void showCameraDenied() {
    new AlertDialog.Builder(this)
        .setTitle("Camera access")
        .setMessage(
            "Allow Camera in Field Logger’s Android settings to take photos. You can also choose a"
                + " photo from your gallery.")
        .setNegativeButton("Close", null)
        .setNeutralButton("Gallery", (d, w) -> gallery.launch(new String[] {"image/*"}))
        .setPositiveButton(
            "Settings",
            (d, w) ->
                startActivity(
                    new Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
        .show();
  }

  private void openCamera() {
    if (busy) return;
    List<Observation> interrupted = repo.db.list("draft:" + repo.owner());
    if (!interrupted.isEmpty()) {
      Observation row = interrupted.get(0);
      new AlertDialog.Builder(this)
          .setTitle("You have a photo ready to save")
          .setPositiveButton(
              "Resume",
              (d, w) -> {
                draft = new Observation(row.data, repo.owner(), row.photo, true, "");
                showReview();
              })
          .setNegativeButton(
              "Discard",
              (d, w) -> {
                repo.db.remove(row.id());
                row.photo.delete();
                draft = null;
                openCamera();
              })
          .show();
      return;
    }
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      cameraPermission.launch(Manifest.permission.CAMERA);
      return;
    }
    locator.stop();
    fix = null;
    gpsPhotoId = "";
    screen = "camera";
    frame(false, false);
    shell.setBackgroundColor(android.graphics.Color.BLACK);
    shell.removeViewAt(0);
    PreviewView preview = new PreviewView(this);
    preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
    body.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout controls = row();
    controls.setPadding(dp(14), dp(14), dp(14), dp(14));
    controls.addView(
        iconButton("close", "Close camera", PAPER, 0x99173f35, this::showJournal),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    TextView label = text("Camera", 17, PAPER, true);
    label.setGravity(Gravity.CENTER);
    controls.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    controls.addView(
        iconButton(
            "flash",
            "Toggle flash",
            PAPER,
            0x99173f35,
            () -> {
              if (imageCapture != null) {
                int mode =
                    imageCapture.getFlashMode() == ImageCapture.FLASH_MODE_OFF
                        ? ImageCapture.FLASH_MODE_AUTO
                        : ImageCapture.FLASH_MODE_OFF;
                imageCapture.setFlashMode(mode);
                message(mode == ImageCapture.FLASH_MODE_AUTO ? "Flash auto" : "Flash off");
              }
            }),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    body.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    LinearLayout shutter = column();
    shutter.setGravity(Gravity.CENTER);
    shutter.setPadding(dp(16), dp(10), dp(16), dp(25));
    View capture = iconButton("camera", "Take photo", FOREST, PAPER, this::takePhoto);
    shutter.addView(capture, new LinearLayout.LayoutParams(dp(80), dp(80)));
    space(shutter, 12);
    shutter.addView(text("Keep the moment", 14, PAPER, false));
    body.addView(shutter, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
    future.addListener(
        () -> {
          if (!screen.equals("camera")) return;
          try {
            cameraProvider = future.get();
            cameraProvider.unbindAll();
            Preview stream = new Preview.Builder().build();
            stream.setSurfaceProvider(preview.getSurfaceProvider());
            imageCapture =
                new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(90)
                    .setTargetResolution(new android.util.Size(1920, 1440))
                    .build();
            camera =
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, stream, imageCapture);
            ScaleGestureDetector scale =
                new ScaleGestureDetector(
                    this,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                      @Override
                      public boolean onScale(ScaleGestureDetector detector) {
                        if (camera == null) return false;
                        androidx.camera.core.ZoomState zoom =
                            camera.getCameraInfo().getZoomState().getValue();
                        if (zoom != null)
                          camera
                              .getCameraControl()
                              .setZoomRatio(
                                  Math.max(
                                      zoom.getMinZoomRatio(),
                                      Math.min(
                                          zoom.getMaxZoomRatio(),
                                          zoom.getZoomRatio() * detector.getScaleFactor())));
                        return true;
                      }
                    });
            preview.setOnTouchListener(
                (v, event) -> {
                  scale.onTouchEvent(event);
                  if (event.getAction() == MotionEvent.ACTION_UP
                      && event.getPointerCount() == 1
                      && camera != null) {
                    camera
                        .getCameraControl()
                        .startFocusAndMetering(
                            new FocusMeteringAction.Builder(
                                    preview
                                        .getMeteringPointFactory()
                                        .createPoint(event.getX(), event.getY()))
                                .build());
                    v.performClick();
                  }
                  return true;
                });
          } catch (Exception e) {
            message("Camera could not open. You can choose a gallery photo.");
            showJournal();
          }
        },
        ContextCompat.getMainExecutor(this));
    if (locator.granted()) startGps();
    else if (!getPreferences(0).getBoolean("locationAsked", false)) {
      getPreferences(0).edit().putBoolean("locationAsked", true).apply();
      afterLocation = this::startGps;
      locationPermission.launch(
          new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
          });
    }
  }

  private void startGps() {
    locator.start(
        location -> {
          fix = location;
          String id = gpsPhotoId;
          if (!id.isEmpty())
            Repository.IO.execute(
                () -> {
                  repo.db.enrichGps(id, location);
                  repo.enqueue();
                  runOnUiThread(
                      () -> {
                        if (screen.equals("review")) showReview();
                        else if (screen.equals("journal")) reload();
                      });
                });
        });
  }

  private void takePhoto() {
    if (imageCapture == null || busy) return;
    busy = true;
    startGps();
    String owner = repo.owner();
    File file;
    try {
      file = File.createTempFile("capture-", ".jpg", getCacheDir());
    } catch (IOException e) {
      busy = false;
      message("Could not open camera storage.");
      return;
    }
    imageCapture.takePicture(
        new ImageCapture.OutputFileOptions.Builder(file).build(),
        Repository.IO,
        new ImageCapture.OnImageSavedCallback() {
          @Override
          public void onImageSaved(ImageCapture.OutputFileResults result) {
            try {
              draft = Photos.prepare(file, owner, repo, false);
              if (fix != null && !draft.hasGps()) {
                Observation.put(draft.data, "latitude", fix.getLatitude());
                Observation.put(draft.data, "longitude", fix.getLongitude());
                Observation.put(draft.data, "accuracy", (double) fix.getAccuracy());
                Observation.put(draft.data, "locationSource", "gps");
              }
              persistDraft();
              gpsPhotoId = draft.id();
              runOnUiThread(
                  () -> {
                    busy = false;
                    showReview();
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    busy = false;
                    message("Could not prepare this photo. Please try again.");
                  });
            } finally {
              file.delete();
            }
          }

          @Override
          public void onError(ImageCaptureException e) {
            file.delete();
            runOnUiThread(
                () -> {
                  busy = false;
                  message("The camera could not save this photo. Please try again.");
                });
          }
        });
  }

  private void leaveCapture() {
    if (cameraProvider != null) cameraProvider.unbindAll();
    imageCapture = null;
    camera = null;
  }

  private void showReview() {
    if (draft == null) {
      showJournal();
      return;
    }
    screen = "review";
    frame(false, false);
    Observation stored = repo.db.find(draft.id());
    if (stored != null) draft = new Observation(stored.data, draft.owner, stored.photo, true, "");
    LinearLayout content = column();
    LinearLayout close = row();
    pad(close, 14);
    close.addView(
        text("A new discovery", 18, FOREST, true), new LinearLayout.LayoutParams(0, -2, 1));
    close.addView(
        iconButton("close", "Keep draft and close", FOREST, 0xffeaf0e1, this::showJournal),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    content.addView(close);
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    content.addView(image, new LinearLayout.LayoutParams(-1, dp(380)));
    Glide.with(this).load(draft.photo).into(image);
    LinearLayout details = column();
    pad(details, 20);
    details.addView(button("Save discovery", true, this::saveDraft));
    space(details, 12);
    details.addView(
        text(
            draft.hasGps()
                ? "GPS attached · place name fills when online"
                : "GPS is optional. You can save immediately.",
            14,
            MUTED,
            false));
    space(details, 18);
    details.addView(button("Add details · optional", false, () -> editDraft()));
    space(details, 12);
    details.addView(
        button(
            "Discard this photo",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Discard this photo?")
                    .setNegativeButton("Keep", null)
                    .setPositiveButton(
                        "Discard",
                        (d, w) -> {
                          if (draft != null) {
                            repo.db.remove(draft.id());
                            draft.photo.delete();
                            draft = null;
                          }
                          locator.stop();
                          gpsPhotoId = "";
                          showJournal();
                        })
                    .show()));
    content.addView(details);
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void editDraft() {
    if (draft == null) return;
    LinearLayout form = column();
    pad(form, 20);
    EditText name = input("Name, if you know it", draft.data.optString("name"), false),
        place = input("Place", draft.place(), false),
        note = input("Your field note", draft.data.optString("note"), true);
    labeled(form, "Name", name);
    Spinner kind = new Spinner(this);
    kind.setAdapter(
        new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, Observation.CATEGORIES));
    for (int i = 0; i < Observation.CATEGORIES.length; i++)
      if (draft.category().equals(Observation.CATEGORIES[i])) kind.setSelection(i);
    labeled(form, "Kind", kind);
    labeled(form, "Place", place);
    labeled(form, "Note", note);
    space(form, 14);
    form.addView(
        button(
            locator.granted() ? "Update GPS" : "Allow location for photos",
            false,
            () -> {
              if (locator.granted()) startGps();
              else {
                afterLocation = this::startGps;
                locationPermission.launch(
                    new String[] {
                      Manifest.permission.ACCESS_FINE_LOCATION,
                      Manifest.permission.ACCESS_COARSE_LOCATION
                    });
              }
            }));
    new AlertDialog.Builder(this)
        .setTitle("Add details")
        .setView(scroll(form))
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Keep details",
            (d, w) -> {
              if (draft == null) return;
              Observation latest = repo.db.find(draft.id());
              JSONObject value = Observation.copy(latest == null ? draft.data : latest.data);
              Observation.put(value, "name", name.getText().toString().trim());
              Observation.put(value, "confirmed", name.length() > 0);
              Observation.put(
                  value, "category", Observation.CATEGORIES[kind.getSelectedItemPosition()]);
              Observation.put(value, "place", place.getText().toString().trim());
              Observation.put(value, "note", note.getText().toString());
              draft = new Observation(value, draft.owner, draft.photo, true, "");
              persistDraft();
              showReview();
            })
        .show();
  }

  private void saveDraft() {
    if (draft == null || busy) return;
    if (!draft.owner.equals(repo.owner())) {
      message("Sign in to the account where this photo was taken before saving.");
      return;
    }
    Observation stored = repo.db.find(draft.id());
    Observation record =
        new Observation(
            stored == null ? draft.data : stored.data, draft.owner, draft.photo, true, "");
    repo.db.save(record);
    draft = null;
    repo.enqueue();
    query = "";
    category = "all";
    from = "";
    to = "";
    showJournal();
    message("Discovery saved. Uploads and identification resume when connected.");
  }
}
