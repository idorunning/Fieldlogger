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
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.*;

/** My Trail Log's native Android interface. No WebView, browser activity or remote UI. */
public final class LauncherActivity extends AppCompatActivity {
  private static final int FOREST = 0xff154e45,
      PAPER = 0xfffffdf7,
      LIME = 0xfff0dc7a,
      MUTED = 0xff728070;
  private Repository repo;
  private AvatarPhotoController avatarStudio;
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
  private final Map<String, Long> pendingLocations = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<String, String> scrapbookPages = new HashMap<>();
  private static final Set<String> CLOSER_REVIEWS =
      java.util.concurrent.ConcurrentHashMap.newKeySet();
  private boolean busy = false;
  private ActivityResultLauncher<String> cameraPermission;
  private ActivityResultLauncher<String[]> locationPermission;
  private ActivityResultLauncher<String[]> gallery, backupImport;
  private ActivityResultLauncher<String> export, zipExport;
  private String zipExportOwner = "";
  private boolean zipDownloading;
  private Runnable afterLocation;
  private ActivityResultLauncher<String> notificationPermission;
  private int achievementLayer = -1;
  private boolean earnedOnly = false;
  private TextView syncStatus;
  private WeatherScene weatherScene;
  private boolean weatherLoading = false;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    repo = new Repository(this);
    avatarStudio =
        new AvatarPhotoController(
            this,
            value -> {
              TrailPreferences.avatar(this, repo.owner(), value);
              if (!"generated".equals(value.optString("kind"))) repo.enqueue();
              if (screen.equals("account")) showAccount();
              else if (screen.equals("journal")) reload();
            });
    locator = new LocationCapture(this);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(FOREST);
    getWindow().setNavigationBarColor(FOREST);
    if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(false);
    notificationPermission =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            ok -> {
              TrailPreferences.of(this, repo.owner())
                  .edit()
                  .putBoolean("notifications", ok)
                  .apply();
              if (ok) FollowWorker.schedule(this);
              if (screen.equals("account")) showAccount();
            });
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
    zipExport =
        registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"),
            uri -> {
              if (uri != null) exportCloudZip(uri);
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
                } else if (screen.equals("archive") || screen.equals("sources")) showAccount();
                else if (!screen.equals("journal")) showJournal();
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
      zipExportOwner = state.getString("zipExportOwner", "");
    }
    WorkManager.getInstance(this)
        .getWorkInfosForUniqueWorkLiveData("journal-sync")
        .observe(
            this,
            work -> {
              if (screen.equals("journal")
                  || screen.equals("collection")
                  || screen.equals("milestones")) reload();
              else if (screen.equals("archive")) showArchive();
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
    String nativeView = intent.getStringExtra("view");
    String observationId = intent.getStringExtra("observation_id");
    if ("journal".equals(nativeView)) showJournal();
    else if ("camera".equals(nativeView)) openCamera();
    else if ("login".equals(nativeView)) showLogin(false);
    else if ("collection".equals(nativeView)) showCollection();
    else if ("achievements".equals(nativeView)) showMilestones();
    else if ("account".equals(nativeView)) showAccount();
    if (observationId != null) {
      Observation selected = repo.db.find(observationId);
      if (selected != null && selected.owner.equals(repo.owner())) showDetail(selected);
    }
    if (!"login".equals(nativeView)) intent.removeExtra("view");
    intent.removeExtra("observation_id");
    Uri uri = intent.getData();
    if (uri != null
        && "fieldlogger.co.uk".equals(uri.getHost())
        && "https".equals(uri.getScheme())) {
      if ("/invite".equals(uri.getPath())) {
        String token = uri.getQueryParameter("token");
        if (token != null && token.matches("[a-f0-9]{64}")) {
          getSharedPreferences("pending-invite", 0).edit().putString("token", token).apply();
          community("invite", "");
        }
        return;
      }
      String view = uri.getQueryParameter("view");
      if ("camera".equals(view)) openCamera();
      else if ("map".equals(view)) showMap();
      else if ("collection".equals(view)) showCollection();
      else if ("achievements".equals(view)) showMilestones();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("screen", screen);
    state.putString("selected", selectedId);
    state.putString("zipExportOwner", zipExportOwner);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (map != null) map.onResume();
    if (repo != null) FollowWorker.schedule(this);
    if (repo != null && repo.session.get() != null) BillingManager.restore(this);
    if (screen.equals("archive")) showArchive();
    if (screen.equals("detail") && repo != null) {
      Observation current = repo.db.find(selectedId);
      if (current == null || !current.owner.equals(repo.owner())) showJournal();
      else showDetail(current);
    } else if (screen.equals("account") && repo != null) showAccount();
    if (locator != null && locator.granted() && repo != null) {
      List<Observation> recent = new ArrayList<>(repo.db.list(repo.owner()));
      recent.addAll(repo.db.list("draft:" + repo.owner()));
      for (Observation r : recent)
        if (!r.hasGps() && r.data.optBoolean("cameraCapture")) {
          try {
            long time = Instant.parse(r.data.optString("capturedAt")).toEpochMilli();
            if (System.currentTimeMillis() - time >= 0 && System.currentTimeMillis() - time < 45000)
              pendingLocations.put(r.id(), time);
          } catch (Exception ignored) {
          }
        }
      if (!pendingLocations.isEmpty()) startGps();
    }
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
      android.content.SharedPreferences preferences = TrailPreferences.of(this, repo.owner());
      boolean dynamic = preferences.getBoolean("weatherBackground", true);
      JSONObject weather = new JSONObject();
      if (dynamic)
        try {
          weather = new JSONObject(preferences.getString("weather", "{}"));
        } catch (Exception ignored) {
        }
      if (!dynamic) Observation.put(weather, "fixed", true);
      boolean fresh =
          System.currentTimeMillis() - preferences.getLong("weatherAt", 0) < 3 * 3600000L;
      if (!fresh) weather.remove("condition");
      boolean effects =
          dynamic
              && fresh
              && preferences.getBoolean("weatherEffects", true)
              && System.currentTimeMillis() - preferences.getLong("effectAt", 0) > 20 * 60000L;
      if (Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled())
        effects = false;
      weatherScene = new WeatherScene(this, weather, effects);
      root.addView(weatherScene, new FrameLayout.LayoutParams(-1, -1));
      if (effects) preferences.edit().putLong("effectAt", System.currentTimeMillis()).apply();
    }
    shell = column();
    shell.setBackgroundColor(woodland ? android.graphics.Color.TRANSPARENT : PAPER);
    root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout header = row();
    header.setPadding(dp(18), dp(8), dp(18), dp(8));
    header.setMinimumHeight(dp(68));
    TextView brand = title("My Trail Log", 27, woodland ? PAPER : FOREST);
    brand.setTypeface(
        androidx.core.content.res.ResourcesCompat.getFont(this, R.font.trail_log_wordmark));
    brand.setMaxLines(1);
    brand.setEllipsize(android.text.TextUtils.TruncateAt.END);
    brand.setContentDescription("Journal home");
    brand.setOnClickListener(v -> showJournal());
    header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
    View account = new AvatarView(this, TrailPreferences.avatar(this, repo.owner()));
    account.setContentDescription("Your profile and settings");
    account.setFocusable(true);
    account.setOnClickListener(v -> showAccount());
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
    records = repo.db.listActive(repo.owner());
    if (screen.equals("journal")) renderJournal();
    else if (screen.equals("collection")) renderCollection();
    else if (screen.equals("milestones")) renderMilestones();
  }

  private void showJournal() {
    leaveCapture();
    screen = "journal";
    frame(true, true);
    reload();
    refreshWeather();
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
    names.addView(title("Your field journal", 23, PAPER));
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
    Map<String, List<Observation>> days = new LinkedHashMap<>();
    int found = 0;
    for (Observation r : records)
      if (matches(r)) {
        found++;
        days.computeIfAbsent(r.day(), day -> new ArrayList<>()).add(r);
      }
    for (Map.Entry<String, List<Observation>> entry : days.entrySet()) {
      String day = entry.getKey();
      LinearLayout dateStrip = row();
      dateStrip.setPadding(dp(12), dp(9), dp(12), dp(9));
      dateStrip.setBackground(shape(0xeefefbf2, 12));
      TextView date = title(diaryDate(day), 22, FOREST);
      date.setTypeface(
          androidx.core.content.res.ResourcesCompat.getFont(this, R.font.trail_log_wordmark));
      dateStrip.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
      dateStrip.addView(text(entry.getValue().size() + " memories", 12, MUTED, false));
      content.addView(dateStrip);
      space(content, 10);
      content.addView(
          new ScrapbookStack(
              this,
              entry.getValue(),
              scrapbookPages.get(day),
              this::photoCard,
              id -> scrapbookPages.put(day, id)),
          new LinearLayout.LayoutParams(-1, -2));
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

  private String diaryDate(String value) {
    try {
      LocalDate date = LocalDate.parse(value);
      return date.format(
          DateTimeFormatter.ofPattern(
              date.getYear() == LocalDate.now().getYear() ? "EEEE, d MMMM" : "d MMMM yyyy",
              Locale.UK));
    } catch (Exception ignored) {
      return value;
    }
  }

  private View photoCard(Observation r) {
    LinearLayout paper = column();
    paper.setBackground(shape(PAPER, 8));
    paper.setPadding(dp(9), dp(9), dp(9), dp(10));
    paper.setContentDescription(
        r.name() + ", " + (r.place().isEmpty() ? "Place name pending" : r.place()));
    paper.setFocusable(true);
    paper.setOnClickListener(v -> showDetail(r));
    FrameLayout photo = new FrameLayout(this);
    photo.setClipToOutline(true);
    photo.setBackground(shape(FOREST, 4));
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    photo.addView(image, new FrameLayout.LayoutParams(-1, -1));
    Glide.with(this).load(r.photo).into(image);
    TextView kind = text(r.category().toUpperCase(Locale.UK), 10, PAPER, true);
    kind.setPadding(dp(10), dp(6), dp(10), dp(6));
    kind.setBackground(shape(Observation.color(r.category()), 16));
    FrameLayout.LayoutParams badge =
        new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
    badge.setMargins(dp(10), dp(10), 0, 0);
    photo.addView(kind, badge);
    FrameLayout.LayoutParams acornPosition =
        new FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM | Gravity.END);
    acornPosition.setMargins(0, 0, dp(8), dp(8));
    photo.addView(ownAcorn(r), acornPosition);
    if (r.pending) {
      TextView local = text("Saved locally", 10, PAPER, false);
      pad(local, 7);
      local.setBackground(shape(0xbb154e45, 15));
      FrameLayout.LayoutParams status =
          new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
      status.setMargins(dp(10), 0, 0, dp(10));
      photo.addView(local, status);
    }
    paper.addView(photo, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout caption = column();
    caption.setPadding(dp(6), dp(9), dp(6), 0);
    TextView name = title(r.name(), 23, FOREST);
    name.setTypeface(
        androidx.core.content.res.ResourcesCompat.getFont(this, R.font.trail_log_wordmark));
    name.setMaxLines(1);
    name.setEllipsize(TextUtils.TruncateAt.END);
    caption.addView(name);
    TextView place =
        text(
            "⌖ "
                + (r.place().isEmpty()
                    ? (r.hasGps() ? "Adding place name…" : "Location unavailable")
                    : r.place()),
            12,
            MUTED,
            false);
    place.setMaxLines(1);
    place.setEllipsize(TextUtils.TruncateAt.END);
    caption.addView(place);
    caption.addView(text(StoryCard.when(r), 11, MUTED, false));
    JSONObject info = r.data.optJSONObject("identification");
    if (info != null && !info.optString("interestingFact").isBlank()) {
      TextView fact = text("✦ " + info.optString("interestingFact"), 12, FOREST, false);
      fact.setMaxLines(2);
      fact.setEllipsize(TextUtils.TruncateAt.END);
      caption.addView(fact);
    }
    paper.addView(caption);
    return paper;
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
    FrameLayout discoveryPhoto = new FrameLayout(this);
    discoveryPhoto.addView(image, new FrameLayout.LayoutParams(-1, -1));
    FrameLayout.LayoutParams acornPlace =
        new FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM | Gravity.END);
    acornPlace.setMargins(0, 0, dp(12), dp(12));
    discoveryPhoto.addView(ownAcorn(record), acornPlace);
    content.addView(discoveryPhoto, new LinearLayout.LayoutParams(-1, dp(420)));
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
    LinearLayout socialActions = row();
    TextView later =
        button(
            record.data.optBoolean("checkLater") ? "Saved for later" : "Check out later",
            false,
            () -> {});
    later.setOnClickListener(
        v -> {
          Observation updated = repo.db.toggleFlag(record.id(), repo.owner(), "checkLater");
          if (updated != null) {
            Observation.put(record.data, "checkLater", updated.data.optBoolean("checkLater"));
            later.setText(
                updated.data.optBoolean("checkLater") ? "Saved for later" : "Check out later");
            repo.enqueue();
          }
        });
    socialActions.addView(later, new LinearLayout.LayoutParams(0, -2, 1));
    details.addView(socialActions);
    if (!record.archived()) {
      space(details, 12);
      JSONObject publication = record.data.optJSONObject("publication");
      details.addView(
          button(
              publication != null && publication.optString("status").equals("published")
                  ? "Manage published photo"
                  : "Publish discovery",
              false,
              () ->
                  community(
                      publication != null && publication.optString("status").equals("published")
                          ? "published"
                          : "publish",
                      record.id())));
    }
    String scientific = record.data.optString("scientificName");
    if (!scientific.isEmpty()) details.addView(text(scientific, 17, MUTED, false));
    space(details, 16);
    details.addView(text(StoryCard.when(record), 14, MUTED, false));
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
    space(details, 12);
    details.addView(
        button(
            record.archived() ? "Restore to journal" : "Move to archive",
            false,
            () -> archiveRecord(record, !record.archived())));
    JSONObject ai = record.data.optJSONObject("identification");
    space(details, 26);
    if (ai == null) {
      details.addView(title("A little mystery", 26, FOREST));
      space(details, 10);
      String why =
          record.error.isEmpty()
              ? ("error".equals(record.data.optString("analysisState"))
                  ? "Your photo is saved. Identification could not finish. You can try again."
                  : "Your photo is saved. Its identification will appear after upload.")
              : Repository.identificationMessage(new Api.Failure(0, record.error));
      details.addView(text(why, 16, MUTED, false));
      space(details, 12);
      if (!record.pending
          && (!record.error.isEmpty() || "error".equals(record.data.optString("analysisState")))) {
        boolean reviewing = CLOSER_REVIEWS.contains(repo.owner() + ":" + record.id());
        TextView retry =
            button(reviewing ? "Identifying this photo…" : "Try again", false, () -> {});
        retry.setEnabled(!reviewing);
        retry.setOnClickListener(v -> tryCloserLook(record, retry));
        details.addView(retry);
        space(details, 12);
      }
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
      JSONObject recognition = ai.optJSONObject("recognition");
      if (recognition != null && "tentative".equals(recognition.optString("mode"))) {
        details.addView(
            text(
                "A closer review was not available. Treat this suggested identity as tentative "
                    + "and check the visible features and other possibilities below.",
                15,
                FOREST,
                true));
        space(details, 10);
        boolean reviewing = CLOSER_REVIEWS.contains(repo.owner() + ":" + record.id());
        TextView retry =
            button(reviewing ? "Reviewing the details…" : "Try a closer look", false, () -> {});
        retry.setEnabled(!reviewing);
        retry.setOnClickListener(v -> tryCloserLook(record, retry));
        details.addView(retry);
        space(details, 12);
      }
      details.addView(text(ai.optString("summary"), 17, FOREST, false));
      section(details, "A little wonder", ai.optString("interestingFact"));
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
    LinearLayout preview = column();
    pad(preview, 12);
    ImageView image = new ImageView(this);
    image.setAdjustViewBounds(true);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setContentDescription("Preview of your branded photo story");
    preview.addView(image, new LinearLayout.LayoutParams(-1, dp(335)));
    CheckBox location = new CheckBox(this);
    location.setText("Include place name");
    location.setChecked(true);
    location.setMinHeight(dp(48));
    preview.addView(location);
    preview.addView(text("Exact GPS coordinates stay private.", 12, MUTED, false));
    final int[] generation = {0};
    final Bitmap[] current = {null};
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Your photo & story")
            .setView(scroll(preview))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Share image", (d, w) -> share(record, true, location.isChecked()))
            .create();
    Runnable render =
        () -> {
          int request = ++generation[0];
          boolean place = location.isChecked();
          Repository.IO.execute(
              () -> {
                try {
                  Bitmap card = StoryCard.render(this, record, place);
                  runOnUiThread(
                      () -> {
                        if (!dialog.isShowing() || request != generation[0]) {
                          card.recycle();
                          return;
                        }
                        Bitmap old = current[0];
                        current[0] = card;
                        image.setImageBitmap(card);
                        if (old != null) old.recycle();
                      });
                } catch (Exception e) {
                  runOnUiThread(() -> message("Could not preview this photo."));
                }
              });
        };
    location.setOnCheckedChangeListener((button, checked) -> render.run());
    dialog.setOnDismissListener(
        d -> {
          ++generation[0];
          image.setImageDrawable(null);
          if (current[0] != null) current[0].recycle();
        });
    dialog.show();
    render.run();
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
            Bitmap card = StoryCard.render(this, record, place);
            try (FileOutputStream out = new FileOutputStream(file)) {
              if (!card.compress(Bitmap.CompressFormat.JPEG, 92, out))
                throw new IOException("Could not create story image.");
            } finally {
              card.recycle();
            }
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
          intent.setClipData(ClipData.newRawUri("My Trail Log photo", uri));
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
    List<Achievements.Badge> badges = repo.db.evaluateAchievements(repo.owner());
    Map<String, String> earned = repo.db.earned(repo.owner());
    int level = Achievements.level(earned.size());
    if (achievementLayer < 0) achievementLayer = level;
    achievementLayer = Math.min(level, achievementLayer);
    content.addView(title(Achievements.LEVELS[level] + " explorer", 32, Achievements.ink(level)));
    space(content, 8);
    content.addView(
        text(
            earned.size() + " permanent achievements · " + badges.size() + " little challenges",
            15,
            MUTED,
            false));
    space(content, 10);
    LinearLayout material = column();
    pad(material, 15);
    material.setBackground(shape(Achievements.background(level), 18));
    material.addView(text("NATURE’S MATERIALS · COMMON TO RARE", 11, FOREST, true));
    material.addView(text("Soil → Clay → Flint → Quartz → Amber → Gold", 14, FOREST, true));
    space(material, 6);
    material.addView(text(Achievements.LEVEL_DESCRIPTION[level], 14, FOREST, false));
    content.addView(material);
    content.addView(
        text(
            "Explore, notice and keep memories. Earned badges stay yours when photos are archived.",
            14,
            MUTED,
            false));
    space(content, 12);
    if (level < 5) {
      int next = Achievements.THRESHOLDS[level + 1];
      content.addView(
          text(
              (next - earned.size())
                  + " more achievements reveal "
                  + Achievements.LEVELS[level + 1]
                  + ".",
              15,
              Achievements.ink(level + 1),
              true));
      ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      progress.setMax(next);
      progress.setProgress(earned.size());
      progress.setProgressTintList(
          android.content.res.ColorStateList.valueOf(Achievements.colour(level + 1)));
      content.addView(progress);
    }
    HorizontalScrollView tiers = new HorizontalScrollView(this);
    tiers.setHorizontalScrollBarEnabled(false);
    LinearLayout tierRow = row();
    for (int i = 0; i < 6; i++) {
      final int n = i;
      TextView chip =
          button(
              (i <= level ? "" : "🔒 ") + Achievements.LEVELS[i],
              i == achievementLayer,
              () -> {
                if (n > level)
                  message(
                      "This layer opens after " + Achievements.THRESHOLDS[n] + " achievements.");
                else {
                  achievementLayer = n;
                  earnedOnly = false;
                  renderMilestones();
                }
              });
      chip.setBackground(
          ripple(i == achievementLayer ? Achievements.colour(i) : Achievements.background(i), 16));
      chip.setTextColor(i == achievementLayer ? Achievements.onColour(i) : Achievements.ink(i));
      chip.setContentDescription(
          Achievements.LEVELS[i]
              + ". "
              + Achievements.LEVEL_DESCRIPTION[i]
              + (i > level ? " Locked." : " Unlocked."));
      tierRow.addView(chip, new LinearLayout.LayoutParams(-2, dp(52)));
    }
    tiers.addView(tierRow);
    space(content, 16);
    content.addView(tiers);
    space(content, 12);
    content.addView(
        button(
            earnedOnly ? "Show this layer’s challenges" : "View all earned badges",
            false,
            () -> {
              earnedOnly = !earnedOnly;
              renderMilestones();
            }));
    space(content, 16);
    content.addView(
        title(
            earnedOnly ? "Yours to keep" : Achievements.LEVELS[achievementLayer] + " discoveries",
            25,
            Achievements.ink(achievementLayer)));
    space(content, 12);
    for (Achievements.Badge b : badges) {
      boolean won = earned.containsKey(b.id);
      if (earnedOnly ? !won : b.layer != achievementLayer) continue;
      LinearLayout tile = column();
      pad(tile, 17);
      GradientDrawable badgeBackground = shape(Achievements.background(b.layer), 18);
      badgeBackground.setStroke(dp(won ? 2 : 1), Achievements.colour(b.layer));
      tile.setBackground(badgeBackground);
      LinearLayout line = row();
      line.addView(
          new IconView(this, won ? "milestones" : "leaf", Achievements.ink(b.layer)),
          new LinearLayout.LayoutParams(dp(32), dp(32)));
      TextView name = text(b.name, 18, FOREST, true);
      name.setPadding(dp(10), 0, 0, 0);
      line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
      tile.addView(line);
      space(tile, 5);
      tile.addView(
          text(
              Achievements.LEVELS[b.layer] + " · layer " + (b.layer + 1),
              12,
              Achievements.ink(b.layer),
              true));
      space(tile, 8);
      tile.addView(text(b.description, 14, MUTED, false));
      if (won) {
        space(tile, 8);
        tile.addView(
            text(
                "✓ Earned · " + dateLabel(earned.get(b.id)) + " · Yours to keep",
                12,
                FOREST,
                true));
      } else {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(b.goal);
        bar.setProgress(b.progress);
        bar.setProgressTintList(
            android.content.res.ColorStateList.valueOf(Achievements.colour(b.layer)));
        tile.addView(bar);
        tile.addView(text(b.progress + " / " + b.goal, 12, MUTED, false));
      }
      content.addView(tile);
      space(content, 12);
    }
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void community(String mode, String id) {
    leaveCapture();
    startActivity(
        new Intent(this, CommunityActivity.class)
            .putExtra("mode", mode)
            .putExtra("id", id)
            .putExtra("returnToAccount", screen.equals("account")));
  }

  private void showMap() {
    community("map", "");
  }

  private AcornButton ownAcorn(Observation record) {
    AcornButton button =
        new AcornButton(this, record.data.optBoolean("acorned"), record.data.optInt("acornCount"));
    button.setOnClickListener(
        v -> {
          Observation changed = repo.db.toggleFlag(record.id(), repo.owner(), "acorned");
          if (changed == null) return;
          Observation.put(record.data, "acorned", changed.data.optBoolean("acorned"));
          Observation.put(record.data, "acornCount", changed.data.optInt("acornCount"));
          button.update(changed.data.optBoolean("acorned"), changed.data.optInt("acornCount"));
          button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
          repo.enqueue();
        });
    return button;
  }

  private void showAccount() {
    leaveCapture();
    screen = "account";
    frame(false, true);
    JSONObject user = repo.session.get();
    LinearLayout content = column();
    pad(content, 20);
    LinearLayout identity = row();
    AvatarView avatar = new AvatarView(this, TrailPreferences.avatar(this, repo.owner()));
    avatar.setOnClickListener(v -> editAvatar());
    avatar.setContentDescription("Change your avatar");
    identity.addView(avatar, new LinearLayout.LayoutParams(dp(82), dp(82)));
    LinearLayout who = column();
    who.setPadding(dp(14), 0, 0, 0);
    who.addView(
        title(
            user == null ? "Your trail space" : user.optString("name", "Your trail space"),
            26,
            FOREST));
    who.addView(text(user == null ? "On this phone" : user.optString("email"), 13, MUTED, false));
    identity.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
    content.addView(identity);
    space(content, 12);
    accountRow(
        content,
        "person",
        "Make my avatar",
        "A woodland cartoon from your photo, or a character made by you",
        this::editAvatar);
    if (user == null)
      accountRow(
          content,
          "person",
          "Sign in or register",
          "Sync your journal and join your trail circle",
          () -> showLogin(false));
    accountHeading(content, "Your membership");
    accountRow(
        content,
        "leaf",
        "Photo allowance & subscription",
        "Free and paid plans, trial, restore or manage in Google Play",
        () -> startActivity(new Intent(this, SubscriptionActivity.class)));
    accountHeading(content, "Your community");
    accountRow(
        content,
        "person",
        "Profile & privacy",
        "Username, avatar and how friends find you",
        () -> community("settings", ""));
    accountRow(
        content,
        "person",
        "Friends & contacts",
        "Find members and view followed journals",
        () -> community("people", ""));
    accountRow(
        content,
        "share",
        "Published photos",
        "Manage sharing, publish all or unpublish all",
        () -> community("published", ""));
    accountRow(
        content,
        "bookmark",
        "Places for later",
        "Saved discoveries to visit",
        () -> community("saved", ""));
    accountHeading(content, "Your journal");
    syncStatus = text(syncDescription(), 13, MUTED, false);
    content.addView(syncStatus);
    accountRow(
        content, "check", "Sync now", "Upload saved photos and refresh this phone", this::syncNow);
    accountRow(
        content,
        "journal",
        "Archive",
        repo.db.listArchive(repo.owner()).size() + " photos tucked away",
        this::showArchive);
    accountRow(
        content,
        "gallery",
        "Add an existing photo",
        "Choose a photo from your phone",
        () -> gallery.launch(new String[] {"image/*"}));
    accountRow(
        content,
        "share",
        "Download all photos & data (ZIP)",
        "Your cloud journal and archive · always free",
        this::chooseCloudZip);
    accountRow(
        content,
        "journal",
        "Device backup (JSON)",
        "Includes photos waiting to upload · for smaller journals",
        () -> export.launch("my-trail-log-" + LocalDate.now() + ".json"));
    accountRow(
        content,
        "journal",
        "Import journal backup",
        "Bring a saved journal onto this phone",
        () -> backupImport.launch(new String[] {"application/json", "text/plain"}));
    accountHeading(content, "App preferences");
    android.content.SharedPreferences prefs = TrailPreferences.of(this, repo.owner());
    settingToggle(
        content,
        "Weather & time backgrounds",
        "Light and colours follow the local weather and time of day",
        prefs,
        "weatherBackground",
        true);
    settingToggle(
        content,
        "Subtle weather effects",
        "A few seconds of rain, snow or wind. Respects reduced motion",
        prefs,
        "weatherEffects",
        true);
    Switch alerts = new Switch(this);
    alerts.setText("New photos from people I follow");
    alerts.setTextColor(FOREST);
    alerts.setMinHeight(dp(56));
    alerts.setChecked(prefs.getBoolean("notifications", false));
    content.addView(alerts);
    content.addView(
        text(
            "Android checks periodically, usually about every 15 minutes. Private photos never"
                + " trigger an alert.",
            12,
            MUTED,
            false));
    alerts.setOnCheckedChangeListener(
        (v, on) -> {
          prefs
              .edit()
              .putBoolean("notifications", on)
              .putString("noticeSince", Instant.now().toString())
              .apply();
          if (on) {
            if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
              notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            else FollowWorker.schedule(this);
          }
        });
    accountRow(
        content,
        "pin",
        "Location permission",
        locator.granted()
            ? "Granted · automatically used for new camera photos"
            : "Allow location to save where each photo was taken",
        () -> {
          if (locator.granted()) {
            message("Location is already enabled for camera photos.");
            return;
          }
          afterLocation =
              () -> {
                showAccount();
                refreshWeather();
              };
          locationPermission.launch(
              new String[] {
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
              });
        });
    accountHeading(content, "Help & account");
    accountRow(
        content,
        "leaf",
        "Sources & privacy",
        "Field guides, map and weather credits",
        this::showSources);
    if (user != null) {
      accountRow(
          content,
          "person",
          "Sign out",
          "Your saved journal stays on this phone",
          () ->
              new AlertDialog.Builder(this)
                  .setTitle("Sign out?")
                  .setMessage("Saved photos stay safe. Sign in again to sync.")
                  .setPositiveButton("Sign out", (d, w) -> async(repo::signOut, this::showJournal))
                  .setNegativeButton("Cancel", null)
                  .show());
      accountRow(
          content,
          "close",
          "Delete account",
          "Permanently delete this account and its stored photos",
          this::deleteAccount);
    }
    space(content, 20);
    content.addView(text("My Trail Log · " + BuildConfig.VERSION_NAME, 12, MUTED, false));
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void accountHeading(LinearLayout content, String label) {
    space(content, 24);
    content.addView(title(label, 23, FOREST));
    space(content, 10);
  }

  private void accountRow(
      LinearLayout parent, String icon, String name, String subtitle, Runnable action) {
    LinearLayout tile = row();
    tile.setPadding(dp(14), dp(12), dp(14), dp(12));
    tile.setBackground(ripple(0xffeef1e6, 16));
    tile.setMinimumHeight(dp(72));
    tile.setFocusable(true);
    tile.setContentDescription(name + ". " + subtitle);
    tile.addView(new IconView(this, icon, FOREST), new LinearLayout.LayoutParams(dp(28), dp(28)));
    LinearLayout words = column();
    words.setPadding(dp(14), 0, dp(8), 0);
    words.addView(text(name, 16, FOREST, true));
    words.addView(text(subtitle, 12, MUTED, false));
    tile.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    tile.addView(text("›", 24, MUTED, false));
    tile.setOnClickListener(v -> action.run());
    parent.addView(tile);
    space(parent, 8);
  }

  private void settingToggle(
      LinearLayout content,
      String name,
      String explanation,
      android.content.SharedPreferences prefs,
      String key,
      boolean initial) {
    Switch toggle = new Switch(this);
    toggle.setText(name);
    toggle.setTextColor(FOREST);
    toggle.setMinHeight(dp(56));
    toggle.setChecked(prefs.getBoolean(key, initial));
    toggle.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
    content.addView(toggle);
    content.addView(text(explanation, 12, MUTED, false));
  }

  private boolean manualSyncing = false;

  private void syncNow() {
    if (manualSyncing) return;
    manualSyncing = true;
    String owner = repo.owner();
    if (syncStatus != null) syncStatus.setText("Syncing your journal…");
    Repository.IO.execute(
        () -> {
          String result;
          try {
            if (!repo.sync()) repo.enqueue();
            String allowance =
                TrailPreferences.of(this, owner).getString("photoAllowanceMessage", "");
            result =
                allowance.isEmpty()
                    ? "Journal refreshed. Remaining uploads retry when connected."
                    : allowance;
          } catch (Exception e) {
            repo.enqueue();
            result = "Waiting for a connection. Your saved photos are safe.";
          }
          manualSyncing = false;
          String message = result;
          runOnUiThread(
              () -> {
                if (owner.equals(repo.owner()) && screen.equals("account")) {
                  if (syncStatus != null) syncStatus.setText(syncDescription());
                  message(message);
                }
              });
        });
  }

  private String syncDescription() {
    List<Observation> all = repo.db.list(repo.owner());
    long pending =
        all.stream()
            .filter(r -> r.pending || r.data.optString("analysisState").equals("pending"))
            .count();
    return repo.session.get() == null
        ? "Saved on this phone · sign in to sync"
        : !TrailPreferences.of(this, repo.owner()).getString("photoAllowanceMessage", "").isEmpty()
            ? TrailPreferences.of(this, repo.owner()).getString("photoAllowanceMessage", "")
            : pending == 0
                ? "Journal up to date"
                : pending + " discoveries waiting to sync or identify";
  }

  private void editAvatar() {
    avatarStudio.show(TrailPreferences.avatar(this, repo.owner()));
  }

  private void refreshWeather() {
    if (repo == null
        || weatherLoading
        || repo.session.get() == null
        || !TrailPreferences.of(this, repo.owner()).getBoolean("weatherBackground", true)) return;
    if (System.currentTimeMillis() - TrailPreferences.of(this, repo.owner()).getLong("weatherAt", 0)
        < 15 * 60000L) return;
    if (locator.granted()) {
      locator.start(
          location -> {
            fix = location;
            fetchWeather(location.getLatitude(), location.getLongitude());
          });
      return;
    }
    for (Observation r : repo.db.listActive(repo.owner()))
      if (r.hasGps()) {
        fetchWeather(r.data.optDouble("latitude"), r.data.optDouble("longitude"));
        break;
      }
  }

  private void fetchWeather(double lat, double lon) {
    if (weatherLoading
        || System.currentTimeMillis()
                - TrailPreferences.of(this, repo.owner()).getLong("weatherAt", 0)
            < 15 * 60000L) return;
    weatherLoading = true;
    String owner = repo.owner();
    JSONObject user = repo.session.get();
    if (user == null) {
      weatherLoading = false;
      return;
    }
    Repository.IO.execute(
        () -> {
          try {
            JSONObject weather =
                repo.api.json(
                    "/api/weather?lat=" + lat + "&lon=" + lon,
                    "GET",
                    user.optString("cookie"),
                    null);
            TrailPreferences.of(this, owner)
                .edit()
                .putString("weather", weather.toString())
                .putLong("weatherAt", System.currentTimeMillis())
                .apply();
            runOnUiThread(
                () -> {
                  if (owner.equals(repo.owner()) && screen.equals("journal")) {
                    frame(true, true);
                    renderJournal();
                  }
                });
          } catch (Exception ignored) {
          } finally {
            weatherLoading = false;
          }
        });
  }

  private void archiveRecord(Observation record, boolean archive) {
    if (!repo.db.archive(record.id(), repo.owner(), archive)) {
      message("This photo is unavailable.");
      return;
    }
    repo.enqueue();
    if (archive) showJournal();
    else showArchive();
    JSONObject shared = record.data.optJSONObject("publication");
    message(
        archive
            ? (shared != null && shared.optString("status").equals("published")
                ? "Moved to Archive. The shared copy will be unpublished when this phone syncs."
                : "Moved to Archive in your account settings.")
            : "Restored to your journal.");
  }

  private void showArchive() {
    leaveCapture();
    screen = "archive";
    frame(false, true);
    LinearLayout content = column();
    pad(content, 20);
    content.addView(title("Your archive", 32, FOREST));
    space(content, 8);
    content.addView(
        text("Kept safely. Tap a memory to restore it to your journal.", 15, MUTED, false));
    space(content, 16);
    List<Observation> archived = repo.db.listArchive(repo.owner());
    if (archived.isEmpty()) content.addView(text("No archived memories yet.", 17, FOREST, false));
    for (Observation r : archived) {
      content.addView(photoCard(r), new LinearLayout.LayoutParams(-1, dp(345)));
      space(content, 12);
      content.addView(button("Restore " + r.name(), false, () -> archiveRecord(r, false)));
      space(content, 22);
    }
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void showLogin(boolean register) {
    LinearLayout form = column();
    pad(form, 20);
    form.addView(
        text(
            "Use your existing My Trail Log account to bring your uploaded photos into the native"
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
                                    if ("login".equals(getIntent().getStringExtra("view"))) {
                                      finish();
                                      return;
                                    }
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
    space(form, 12);
    form.addView(
        text(
            "Deleting My Trail Log does not cancel a Google Play subscription. Cancel it in Google"
                + " Play to prevent future renewal charges. You can still delete your account now.",
            15,
            FOREST,
            true));
    space(form, 8);
    form.addView(
        button(
            "Manage or cancel in Google Play",
            false,
            () ->
                openReference(
                    "https://play.google.com/store/account/subscriptions?package=com.field.logger")));
    space(form, 12);
    labeled(form, "Password", password);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Delete your account?")
            .setView(scroll(form))
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
        "My Trail Log saves photos, dates, notes and automatic camera GPS in its own private"
            + " Android database and files. Your session is encrypted using Android Keystore."
            + " Passwords are not kept in the app. Location is requested during camera capture,"
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
        "OpenAI receives the resized photo and capture context for identification."
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
            + " removed. Branded story images include the place name by default, with a preview to"
            + " hide it. Exact GPS stays private. Journal exports contain original saved"
            + " coordinates and should be kept private.");
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
        "Weather & postcode search",
        "Local weather uses coarse Open-Meteo or MET Norway forecasts (CC BY 4.0). Sunrise and"
            + " sunset guide the background light. UK postcodes use Postcodes.io; other place"
            + " searches use Photon. Forecasts are approximate and no continuous background"
            + " location is requested.");
    content.addView(
        button("Open-Meteo attribution", false, () -> openReference("https://open-meteo.com/")));
    space(content, 10);
    content.addView(button("Postcodes.io", false, () -> openReference("https://postcodes.io/")));
    space(content, 16);
    section(
        content,
        "Photo credits",
        "Woodland photograph by Rob Wingate · Unsplash. Your journal uses your own photographs.");
    body.addView(scroll(content), new FrameLayout.LayoutParams(-1, -1));
  }

  private void tryCloserLook(Observation record, TextView action) {
    boolean firstIdentification = record.data.optJSONObject("identification") == null;
    String retryLabel = firstIdentification ? "Try again" : "Try a closer look";
    JSONObject account = repo.session.get();
    if (account == null) {
      message("Sign in before retrying identification. Your photo stays saved on this phone.");
      return;
    }
    String owner = repo.owner(),
        cookie = account.optString("cookie"),
        key = owner + ":" + record.id();
    if (!record.owner.equals(owner) || !CLOSER_REVIEWS.add(key)) return;
    action.setEnabled(false);
    action.setText(firstIdentification ? "Identifying this photo…" : "Reviewing the details…");
    Repository.IO.execute(
        () -> {
          String notice;
          try {
            JSONObject identification = repo.api.identifyCloser(record.id(), cookie);
            if (!owner.equals(repo.owner())) return;
            repo.db.mergeAnalysis(record.id(), owner, identification);
            JSONObject recognition = identification.optJSONObject("recognition");
            notice =
                recognition != null && "tentative".equals(recognition.optString("mode"))
                    ? "A closer review is unavailable right now. The suggested identity remains"
                        + " tentative."
                    : recognition != null && "closer".equals(recognition.optString("mode"))
                        ? "Closer review finished. Check the visible features before confirming a"
                            + " species."
                        : firstIdentification
                            ? "Your suggested identity is ready. Check the visible features before"
                                + " confirming a species."
                            : "Your current suggestion is refreshed. Check the visible features"
                                + " before confirming a species.";
          } catch (Api.Failure e) {
            notice =
                e.status == 404
                    ? "This photo is not in your cloud journal yet. Use Sync now before retrying"
                        + " identification."
                    : Repository.identificationMessage(e);
          } catch (Exception e) {
            notice =
                "Identification could not finish. Your saved photo and any current suggestion"
                    + " are kept.";
          } finally {
            CLOSER_REVIEWS.remove(key);
          }
          String result = notice;
          runOnUiThread(
              () -> {
                if (isFinishing() || isDestroyed() || !owner.equals(repo.owner())) return;
                action.setEnabled(true);
                action.setText(retryLabel);
                if (screen.equals("detail") && selectedId.equals(record.id())) {
                  Observation updated = repo.db.find(record.id());
                  if (updated != null) showDetail(updated);
                  message(result);
                } else Toast.makeText(this, result, Toast.LENGTH_LONG).show();
              });
        });
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
          try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open the backup destination.");
            JournalBackup.write(
                out, Instant.now().toString(), repo.db.list(owner), repo.db.earned(owner));
          } catch (Exception e) {
            try {
              android.provider.DocumentsContract.deleteDocument(getContentResolver(), uri);
            } catch (Exception ignored) {
            }
            throw e;
          }
        },
        () -> message("Journal exported, including photos and GPS."));
  }

  private void chooseCloudZip() {
    if (zipDownloading) {
      message("Your ZIP is downloading. You can keep using your journal while it finishes.");
      return;
    }
    JSONObject account = repo.session.get();
    if (account == null) {
      message(
          "Sign in to download your cloud journal. Device backup includes photos saved only on this"
              + " phone.");
      return;
    }
    long pending = repo.db.list(repo.owner()).stream().filter(r -> r.pending).count();
    String description =
        "Your uploaded photos, archive, exact saved GPS, notes, achievements and account data "
            + "are included. This is always free, on every plan."
            + (pending > 0
                ? "\n\n"
                    + pending
                    + " photos or edits are waiting to sync. Use Sync now first, or save a Device"
                    + " backup (JSON) to include data held only on this phone."
                : "");
    new AlertDialog.Builder(this)
        .setTitle("Download photos & data")
        .setMessage(description)
        .setPositiveButton(
            "Choose where to save",
            (d, w) -> {
              zipExportOwner = repo.owner();
              zipExport.launch("my-trail-log-" + LocalDate.now() + ".zip");
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void exportCloudZip(Uri uri) {
    JSONObject account = repo.session.get();
    if (account == null || !repo.owner().equals(zipExportOwner)) {
      try {
        android.provider.DocumentsContract.deleteDocument(getContentResolver(), uri);
      } catch (Exception ignored) {
      }
      message("Sign in to the same account before downloading its journal.");
      return;
    }
    zipDownloading = true;
    String owner = repo.owner(), cookie = account.optString("cookie");
    Toast.makeText(this, "Downloading your free ZIP. You can keep exploring.", Toast.LENGTH_LONG)
        .show();
    Repository.IO.execute(
        () -> {
          String result;
          try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open the chosen destination.");
            long bytes = repo.api.exportZip(cookie, out);
            if (bytes == 0) throw new IOException("The export was empty. Please try again.");
            result = "Your photos and cloud journal data are saved as ZIP, including the archive.";
          } catch (Exception e) {
            try {
              android.provider.DocumentsContract.deleteDocument(getContentResolver(), uri);
            } catch (Exception ignored) {
            }
            result =
                e instanceof Api.Failure
                    ? e.getMessage()
                    : "Your ZIP download did not finish. Check your connection and available"
                        + " storage, then try again. Your journal is safe.";
          }
          zipDownloading = false;
          String report = result;
          runOnUiThread(
              () -> {
                if (!isFinishing() && !isDestroyed() && owner.equals(repo.owner())) message(report);
              });
        });
  }

  private void importPhoto(Uri uri) {
    if (busy) return;
    locator.stop();
    fix = null;
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
            resolvePlace(draft.id());
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
            "Allow Camera in My Trail Log’s Android settings to take photos. You can also choose a"
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
          long now = System.currentTimeMillis();
          for (Map.Entry<String, Long> pending : pendingLocations.entrySet()) {
            if (now - pending.getValue() > 45000) {
              pendingLocations.remove(pending.getKey());
              continue;
            }
            if (Math.abs(location.getTime() - pending.getValue()) > 45000) continue;
            String id = pending.getKey();
            Repository.IO.execute(
                () -> {
                  repo.db.enrichGps(id, location);
                  resolvePlace(id);
                  repo.enqueue();
                  runOnUiThread(
                      () -> {
                        if (screen.equals("review") && draft != null && draft.id().equals(id))
                          showReview();
                        else if (screen.equals("journal")) reload();
                      });
                });
          }
        });
  }

  private void resolvePlace(String id) {
    Repository.IO.execute(
        () -> {
          Observation r = repo.db.find(id);
          if (r == null || !r.hasGps() || !r.place().isBlank()) return;
          try {
            String place = repo.place(r.data.optDouble("latitude"), r.data.optDouble("longitude"));
            if (!place.isBlank())
              repo.db.enrichPlace(
                  id, r.data.optDouble("latitude"), r.data.optDouble("longitude"), place);
            else repo.enqueue();
            runOnUiThread(
                () -> {
                  if (screen.equals("review") && draft != null && draft.id().equals(id))
                    showReview();
                  else if (screen.equals("journal")) reload();
                });
          } catch (Exception ignored) {
            repo.enqueue();
          }
        });
  }

  private void takePhoto() {
    if (imageCapture == null || busy) return;
    busy = true;
    final long capturedMillis = System.currentTimeMillis();
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
              Observation.put(draft.data, "cameraCapture", true);
              ZonedDateTime taken =
                  Instant.ofEpochMilli(capturedMillis).atZone(ZoneId.systemDefault());
              Observation.put(draft.data, "capturedAt", taken.toInstant().toString());
              Observation.put(draft.data, "localDate", taken.toLocalDate().toString());
              Observation.put(draft.data, "localHour", taken.getHour());
              android.location.Location captureFix = fix;
              if (captureFix != null
                  && Math.abs(captureFix.getTime() - capturedMillis) <= 30000
                  && !draft.hasGps()) {
                Observation.put(draft.data, "latitude", captureFix.getLatitude());
                Observation.put(draft.data, "longitude", captureFix.getLongitude());
                Observation.put(draft.data, "accuracy", (double) captureFix.getAccuracy());
                Observation.put(draft.data, "locationSource", "gps");
              }
              persistDraft();
              pendingLocations.put(draft.id(), capturedMillis);
              android.location.Location lateFix = fix;
              if (lateFix != null && Math.abs(lateFix.getTime() - capturedMillis) <= 30000)
                repo.db.enrichGps(draft.id(), lateFix);
              resolvePlace(draft.id());
              runOnUiThread(
                  () -> {
                    busy = false;
                    showReview();
                    String capturedId = draft.id();
                    root.postDelayed(
                        () -> {
                          if (screen.equals("review")
                              && draft != null
                              && draft.id().equals(capturedId)) showReview();
                        },
                        45000);
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

  private boolean recentCapture(Observation record) {
    try {
      return Math.abs(
              System.currentTimeMillis()
                  - Instant.parse(record.data.optString("capturedAt")).toEpochMilli())
          < 45000;
    } catch (Exception ignored) {
      return false;
    }
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
                ? (draft.place().isBlank()
                    ? "Location saved · adding place name automatically"
                    : "⌖ " + draft.place())
                : (draft.data.optBoolean("imported")
                    ? "This gallery photo has no saved location."
                    : locator.granted() && recentCapture(draft)
                        ? "Finding location automatically. You can save now."
                        : "Location unavailable · your photo is kept safely."),
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
                            pendingLocations.remove(draft.id());
                            repo.db.remove(draft.id());
                            draft.photo.delete();
                            draft = null;
                          }
                          locator.stop();
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
