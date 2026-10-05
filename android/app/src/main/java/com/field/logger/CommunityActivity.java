package com.field.logger;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.*;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.*;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.field.logger.nativeapp.*;
import java.io.File;
import java.util.*;
import org.json.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.*;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.*;

/** Native discovery map and opt-in community. Private journal data never enters the public feed. */
public final class CommunityActivity extends AppCompatActivity {
  private static final int GREEN = 0xff154e45,
      PAPER = 0xfffffdf7,
      MUTED = 0xff68796f,
      GOLD = 0xfff0dc7a;
  private Repository repo;
  private FrameLayout root, body, mapFrame;
  private LinearLayout shell, nav, preview;
  private MapView map;
  private String mode = "map", scope = "own", category = "all", author = "", photoId = "";
  private boolean topAcorns = false, expanded = false;
  private double latitude = 54, longitude = -2, radius = 10;
  private int epoch = 0, mapRequest = 0;
  private final List<JSONObject> visible = new ArrayList<>();
  private final List<CustomTarget<Bitmap>> targets = new ArrayList<>();
  private TextView mapStatus;
  private EditText contactDestination;
  private ActivityResultLauncher<Intent> contactPicker;
  private JSONObject profile;
  private String activeOwner;
  private TextView actionStatus;

  private interface Task {
    JSONObject run() throws Exception;
  }

  private interface Done {
    void accept(JSONObject result) throws Exception;
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    repo = new Repository(this);
    activeOwner = repo.owner();
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    getWindow().setStatusBarColor(GREEN);
    getWindow().setNavigationBarColor(GREEN);
    new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
        .setAppearanceLightStatusBars(false);
    root = new FrameLayout(this);
    root.setBackgroundColor(GREEN);
    setContentView(root);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          Insets s =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(s.left, s.top, s.right, s.bottom);
          return insets;
        });
    contactPicker =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() != RESULT_OK
                  || result.getData() == null
                  || result.getData().getData() == null
                  || contactDestination == null) return;
              try (Cursor c =
                  getContentResolver()
                      .query(
                          result.getData().getData(),
                          new String[] {ContactsContract.CommonDataKinds.Email.ADDRESS},
                          null,
                          null,
                          null)) {
                if (c != null && c.moveToFirst()) {
                  String email = c.getString(0);
                  contactDestination.append((contactDestination.length() == 0 ? "" : ", ") + email);
                }
              } catch (Exception e) {
                toast("Choose an email address, or type it below.");
              }
            });
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              public void handleOnBackPressed() {
                if (mode.equals("map")) finish();
                else showMap();
              }
            });
    List<Observation> own = repo.db.listActive(repo.owner());
    for (Observation r : own)
      if (r.hasGps()) {
        latitude = r.data.optDouble("latitude");
        longitude = r.data.optDouble("longitude");
        break;
      }
    latitude = getPreferences(0).getFloat("lat", (float) latitude);
    longitude = getPreferences(0).getFloat("lon", (float) longitude);
    String requested = getIntent().getStringExtra("mode");
    photoId = getIntent().getStringExtra("id");
    if (photoId == null) photoId = "";
    if (saved != null) {
      latitude = saved.getDouble("lat", latitude);
      longitude = saved.getDouble("lon", longitude);
      scope = saved.getString("scope", "own");
      radius = saved.getDouble("radius", 10);
      topAcorns = saved.getBoolean("top");
    }
    if ("publish".equals(requested)) showPublish(photoId);
    else if ("settings".equals(requested)) showSettings();
    else if ("people".equals(requested)) showPeople();
    else if ("published".equals(requested)) showPublished();
    else if ("invite".equals(requested)) showInvite();
    else {
      if ("saved".equals(requested)) scope = "saved";
      showMap();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (map != null) map.onResume();
    if (repo != null && !repo.owner().equals(activeOwner)) {
      activeOwner = repo.owner();
      visible.clear();
      if (mode.equals("invite")) showInvite();
      else if (mode.equals("publish")) showPublish(photoId);
      else showMap();
    }
  }

  @Override
  protected void onPause() {
    if (map != null) map.onPause();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    disposeMap();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    rememberCentre();
    out.putDouble("lat", latitude);
    out.putDouble("lon", longitude);
    out.putString("scope", scope);
    out.putDouble("radius", radius);
    out.putBoolean("top", topAcorns);
    super.onSaveInstanceState(out);
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private TextView text(String s, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
  }

  private TextView title(String s) {
    TextView t = text(s, 30, GREEN, false);
    t.setTypeface(
        androidx.core.content.res.ResourcesCompat.getFont(this, R.font.trail_log_wordmark));
    return t;
  }

  private void gap(LinearLayout l, int height) {
    l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
  }

  private TextView button(String s, boolean primary, Runnable action) {
    TextView b = text(s, 15, primary ? PAPER : GREEN, true);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(50));
    b.setPadding(dp(14), dp(10), dp(14), dp(10));
    b.setBackground(shape(primary ? GREEN : 0xffe9efe4, 18));
    b.setFocusable(true);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private View icon(String name, String label, Runnable action) {
    FrameLayout box = new FrameLayout(this);
    box.setBackground(shape(PAPER, 25));
    box.setContentDescription(label);
    box.setFocusable(true);
    box.setOnClickListener(v -> action.run());
    IconView i = new IconView(this, name, GREEN);
    box.addView(i, new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER));
    return box;
  }

  private EditText input(String hint) {
    EditText e = new EditText(this);
    e.setHint(hint);
    e.setTextColor(GREEN);
    e.setTextSize(16);
    e.setMinHeight(dp(52));
    e.setPadding(dp(12), dp(8), dp(12), dp(8));
    e.setBackground(shape(0xffeef1e8, 12));
    return e;
  }

  private void rememberCentre() {
    if (map != null) {
      latitude = map.getMapCenter().getLatitude();
      longitude = map.getMapCenter().getLongitude();
      getPreferences(0)
          .edit()
          .putFloat("lat", (float) latitude)
          .putFloat("lon", (float) longitude)
          .apply();
    }
  }

  private void disposeMap() {
    for (CustomTarget<Bitmap> t : targets) Glide.with(getApplicationContext()).clear(t);
    targets.clear();
    if (map != null) {
      map.onPause();
      map.onDetach();
      map = null;
    }
  }

  private void frame(String next) {
    rememberCentre();
    disposeMap();
    epoch++;
    mode = next;
    actionStatus = null;
    root.removeAllViews();
    shell = column();
    shell.setBackgroundColor(PAPER);
    root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
    body = new FrameLayout(this);
    shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = row();
    nav.setBackgroundColor(PAPER);
    String[] labels = {"Journal", "Map", "Camera", "Collection", "Milestones"};
    String[] icons = {"journal", "map", "camera", "collection", "milestones"};
    for (int i = 0; i < 5; i++) {
      final int n = i;
      LinearLayout tab = column();
      tab.setGravity(Gravity.CENTER);
      tab.setPadding(0, dp(6), 0, dp(5));
      tab.setContentDescription(labels[i]);
      IconView im = new IconView(this, icons[i], i == 2 ? PAPER : GREEN);
      if (i == 2) {
        im.setBackground(shape(GREEN, 24));
        im.setPadding(dp(9), dp(9), dp(9), dp(9));
      }
      tab.addView(im, new LinearLayout.LayoutParams(dp(i == 2 ? 48 : 28), dp(i == 2 ? 48 : 28)));
      TextView label = text(labels[i], 11, GREEN, i == 2);
      label.setGravity(Gravity.CENTER);
      label.setMaxLines(1);
      androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
          label, 9, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
      tab.addView(label, new LinearLayout.LayoutParams(-1, -2));
      tab.setFocusable(true);
      tab.setOnClickListener(
          v -> {
            if (n == 0) journalPage("journal");
            if (n == 1) showMap();
            if (n == 2) {
              journalPage("camera");
            }
            if (n == 3 || n == 4) {
              journalPage(n == 3 ? "collection" : "achievements");
            }
          });
      nav.addView(tab, new LinearLayout.LayoutParams(0, dp(76), 1));
    }
    shell.addView(nav);
  }

  private void journalPage(String destination) {
    startActivity(
        new Intent(this, LauncherActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("view", destination));
    finish();
  }

  private LinearLayout page(String next, String heading, String subtitle) {
    frame(next);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    LinearLayout content = column();
    content.setPadding(dp(20), dp(18), dp(20), dp(26));
    LinearLayout h = row();
    h.addView(title(heading), new LinearLayout.LayoutParams(0, -2, 1));
    h.addView(
        icon("close", "Back to map", this::showMap), new LinearLayout.LayoutParams(dp(48), dp(48)));
    content.addView(h);
    if (!subtitle.isEmpty()) {
      gap(content, 6);
      content.addView(text(subtitle, 15, MUTED, false));
    }
    gap(content, 20);
    scroll.addView(content);
    body.addView(scroll);
    return content;
  }

  private boolean signedIn(LinearLayout content, Runnable retry) {
    if (repo.session.get() != null) return true;
    content.addView(
        text(
            "Sign in to share discoveries and meet other explorers. Your private journal stays on"
                + " this phone.",
            17,
            MUTED,
            false));
    gap(content, 16);
    content.addView(
        button(
            "Sign in or create account",
            true,
            () ->
                startActivity(new Intent(this, LauncherActivity.class).putExtra("view", "login"))));
    gap(content, 12);
    content.addView(button("I’m signed in — continue", false, retry));
    return false;
  }

  private String cookie() throws Exception {
    JSONObject u = repo.session.get();
    if (u == null) throw new Exception("Sign in to use shared discoveries.");
    return u.getString("cookie");
  }

  private JSONObject call(String path, String method, JSONObject payload) throws Exception {
    return repo.api.json("/api/social/" + path, method, cookie(), payload);
  }

  private JSONObject data(String key, Object value) {
    JSONObject d = new JSONObject();
    Observation.put(d, key, value);
    return d;
  }

  private void work(View trigger, Task task, Done done) {
    int generation = epoch;
    final String requestOwner = repo.owner();
    if (trigger != null) trigger.setEnabled(false);
    Repository.IO.execute(
        () -> {
          try {
            JSONObject result = task.run();
            runOnUiThread(
                () -> {
                  if (isFinishing()
                      || isDestroyed()
                      || generation != epoch
                      || !requestOwner.equals(repo.owner())) return;
                  if (trigger != null) trigger.setEnabled(true);
                  try {
                    done.accept(result);
                  } catch (Exception e) {
                    toast("Could not show this discovery. Please try again.");
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (isFinishing()
                      || isDestroyed()
                      || generation != epoch
                      || !requestOwner.equals(repo.owner())) return;
                  if (trigger != null) trigger.setEnabled(true);
                  String m = e.getMessage();
                  if (m == null
                      || m.toLowerCase(Locale.ROOT)
                          .matches(".*(api.?key|billing|quota|sql|database|encrypted).*"))
                    m = "The discovery service is temporarily unavailable. Your journal is safe.";
                  toast(m);
                  if (actionStatus != null) actionStatus.setText(m);
                  if (mapStatus != null && mode.equals("map"))
                    mapStatus.setText("Could not load · tap Search this area to retry");
                });
          }
        });
  }

  private Object imageModel(JSONObject p) throws Exception {
    Observation own = repo.db.find(p.optString("id"));
    if (p.optBoolean("own") && own != null && own.owner.equals(repo.owner())) return own.photo;
    String path = p.optString("photoUrl");
    if (!path.startsWith("/api/social/")) throw new Exception("Photo unavailable.");
    return new GlideUrl(
        Api.ORIGIN + path, new LazyHeaders.Builder().addHeader("Cookie", cookie()).build());
  }

  private void image(JSONObject p, ImageView view) {
    try {
      Glide.with(this)
          .load(imageModel(p))
          .diskCacheStrategy(DiskCacheStrategy.NONE)
          .skipMemoryCache(true)
          .into(view);
    } catch (Exception ignored) {
      view.setBackgroundColor(0xffe0e9dc);
    }
  }

  private String at() {
    return "lat=" + latitude + "&lon=" + longitude;
  }

  private String photoPath(JSONObject p, String action) {
    String url = p.optString("photoUrl");
    int q = url.indexOf('?');
    return "photos/"
        + p.optString("id")
        + (action.isEmpty() ? "" : "/" + action)
        + (q < 0 ? "" : "?" + url.substring(q + 1));
  }

  private void showMap() {
    frame("map");
    mapStatus = null;
    mapFrame = new FrameLayout(this);
    body.addView(mapFrame, new FrameLayout.LayoutParams(-1, -1));
    Configuration.getInstance().setUserAgentValue("MyTrailLog/2.2.0 (fieldlogger.co.uk)");
    Configuration.getInstance().setOsmdroidBasePath(new File(getCacheDir(), "map"));
    Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(), "map/tiles"));
    map = new MapView(this);
    map.setTileSource(TileSourceFactory.MAPNIK);
    map.setTilesScaledToDpi(true);
    map.setMultiTouchControls(true);
    map.setBuiltInZoomControls(false);
    mapFrame.addView(map, new FrameLayout.LayoutParams(-1, -1));
    map.getController().setZoom(latitude == 54 && longitude == -2 ? 6d : 13d);
    map.getController().setCenter(new GeoPoint(latitude, longitude));
    LinearLayout controls = column();
    controls.setPadding(dp(10), dp(8), dp(10), dp(8));
    controls.setBackground(shape(0xfafffdf7, 24));
    controls.setElevation(dp(5));
    LinearLayout heading = row();
    heading.addView(title("Out there"), new LinearLayout.LayoutParams(0, -2, 1));
    heading.addView(
        icon(
            "expand",
            "Toggle full screen map",
            () -> {
              expanded = !expanded;
              nav.setVisibility(expanded ? View.GONE : View.VISIBLE);
            }),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    heading.addView(
        icon("person", "Find people", this::showPeople),
        new LinearLayout.LayoutParams(dp(48), dp(48)));
    controls.addView(heading);
    HorizontalScrollView filterScroll = new HorizontalScrollView(this);
    filterScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout filters = row();
    String[] names = {"My photos", "Following", "Nearby", "Everyone", "Later", "Invited"};
    String[] values = {"own", "following", "nearby", "everyone", "saved", "invited"};
    for (int i = 0; i < names.length; i++) {
      final String selected = values[i];
      TextView chip =
          button(
              names[i],
              scope.equals(selected),
              () -> {
                rememberCentre();
                scope = selected;
                showMap();
              });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
      lp.rightMargin = dp(6);
      filters.addView(chip, lp);
    }
    filterScroll.addView(filters);
    controls.addView(filterScroll);
    LinearLayout options = row();
    TextView search =
        button(
            "Search this area",
            false,
            () -> {
              rememberCentre();
              loadMap();
            });
    options.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
    options.addView(
        button(topAcorns ? "Acorns ↓" : "Filters", false, this::mapFilters),
        new LinearLayout.LayoutParams(-2, dp(48)));
    controls.addView(options);
    mapStatus = text("Finding discoveries…", 12, MUTED, false);
    mapStatus.setPadding(dp(6), dp(4), 0, 0);
    mapStatus.setOnClickListener(v -> showMapList());
    controls.addView(mapStatus);
    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    cp.setMargins(dp(12), dp(12), dp(12), 0);
    mapFrame.addView(controls, cp);
    preview = column();
    preview.setPadding(dp(8), dp(8), dp(8), dp(8));
    FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    pp.setMargins(dp(12), 0, dp(12), dp(30));
    mapFrame.addView(preview, pp);
    TextView credit = text("© OpenStreetMap contributors", 11, GREEN, false);
    credit.setPadding(dp(10), dp(6), dp(10), dp(6));
    credit.setBackground(shape(PAPER, 6));
    credit.setOnClickListener(v -> open("https://www.openstreetmap.org/copyright"));
    mapFrame.addView(credit, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
    nav.setVisibility(expanded ? View.GONE : View.VISIBLE);
    map.onResume();
    loadMap();
  }

  private JSONObject ownPhoto(Observation r) {
    JSONObject p = Observation.copy(r.data);
    Observation.put(p, "own", true);
    Observation.put(p, "acorns", r.data.optInt("acornCount"));
    return p;
  }

  private void loadMap() {
    final int request = ++mapRequest;
    preview.removeAllViews();
    visible.clear();
    if (scope.equals("own") || scope.equals("saved")) {
      for (Observation r : repo.db.listActive(repo.owner()))
        if (r.hasGps()
            && (scope.equals("own") || r.data.optBoolean("checkLater"))
            && (category.equals("all") || category.equals(r.category()))) visible.add(ownPhoto(r));
    }
    if (scope.equals("own")) {
      renderMarkers();
      return;
    }
    if (repo.session.get() == null) {
      renderMarkers();
      mapStatus.setText("Sign in to see shared discoveries · tap here");
      mapStatus.setOnClickListener(v -> showSettings());
      return;
    }
    mapStatus.setText("Finding discoveries…");
    String path =
        "feed?scope="
            + scope
            + "&"
            + at()
            + "&radius="
            + radius
            + "&category="
            + category
            + (topAcorns ? "&sort=acorns" : "");
    work(
        null,
        () -> call(path, "GET", null),
        result -> {
          if (request != mapRequest || map == null) return;
          JSONArray photos = result.getJSONArray("photos");
          HashSet<String> ids = new HashSet<>();
          for (JSONObject p : visible) ids.add(p.optString("id"));
          for (int i = 0; i < photos.length(); i++) {
            JSONObject p = photos.getJSONObject(i);
            if (!ids.contains(p.optString("id"))) visible.add(p);
          }
          renderMarkers();
          if (result.optBoolean("hasMore")) mapStatus.append(" · first 100 — narrow filters");
        });
  }

  private void renderMarkers() {
    if (map == null) return;
    map.getOverlays().clear();
    for (CustomTarget<Bitmap> t : targets) Glide.with(this).clear(t);
    targets.clear();
    if (topAcorns) visible.sort((a, b) -> Integer.compare(b.optInt("acorns"), a.optInt("acorns")));
    Map<String, List<JSONObject>> groups = new LinkedHashMap<>();
    int noGps = 0;
    for (JSONObject p : visible) {
      if (!Observation.coords(p.opt("latitude"), p.opt("longitude"))) {
        noGps++;
        continue;
      }
      String key =
          String.format(Locale.US, "%.5f,%.5f", p.optDouble("latitude"), p.optDouble("longitude"));
      groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
    }
    List<List<JSONObject>> ordered = new ArrayList<>(groups.values());
    Collections.reverse(ordered);
    final MapView current = map;
    for (List<JSONObject> group : ordered) {
      JSONObject p = group.get(0);
      Marker marker = new Marker(map);
      marker.setPosition(new GeoPoint(p.optDouble("latitude"), p.optDouble("longitude")));
      marker.setAnchor(.5f, .5f);
      marker.setTitle(p.optString("name", "Discovery"));
      marker.setIcon(markerImage(null, p, group.size()));
      marker.setOnMarkerClickListener(
          (m, v) -> {
            if (group.size() == 1) showPreview(p);
            else {
              String[] choices = new String[group.size()];
              for (int i = 0; i < choices.length; i++)
                choices[i] =
                    group.get(i).optString("name", "Discovery")
                        + " · "
                        + group.get(i).optInt("acorns")
                        + " acorns";
              new AlertDialog.Builder(this)
                  .setTitle("Discoveries here")
                  .setItems(choices, (d, n) -> showPreview(group.get(n)))
                  .show();
            }
            return true;
          });
      map.getOverlays().add(marker);
      try {
        CustomTarget<Bitmap> target =
            new CustomTarget<Bitmap>(dp(64), dp(64)) {
              public void onResourceReady(Bitmap b, Transition<? super Bitmap> t) {
                if (map == current) {
                  marker.setIcon(markerImage(b, p, group.size()));
                  map.invalidate();
                }
              }

              public void onLoadCleared(Drawable d) {}
            };
        targets.add(target);
        Glide.with(this)
            .asBitmap()
            .load(imageModel(p))
            .circleCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(target);
      } catch (Exception ignored) {
      }
    }
    map.invalidate();
    mapStatus.setText(
        visible.size()
            + " discoveries"
            + (topAcorns ? " · most acorns first" : "")
            + (noGps > 0 ? " · " + noGps + " without GPS" : "")
            + " · View list");
    mapStatus.setOnClickListener(v -> showMapList());
    if (visible.isEmpty()) {
      TextView empty =
          button(
              scope.equals("own")
                  ? "Your geotagged photos will appear here"
                  : "No discoveries here yet · try a wider area",
              false,
              this::mapFilters);
      preview.addView(empty);
    }
  }

  private Drawable markerImage(Bitmap photo, JSONObject p, int group) {
    int size = dp(70);
    Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float mid = size / 2f;
    paint.setColor(0x35000000);
    c.drawCircle(mid, mid + dp(2), dp(32), paint);
    paint.setColor(PAPER);
    c.drawCircle(mid, mid, dp(31), paint);
    paint.setColor(Observation.color(p.optString("category")));
    c.drawCircle(mid, mid, dp(28), paint);
    if (photo != null) {
      int save = c.save();
      Path clip = new Path();
      clip.addCircle(mid, mid, dp(24), Path.Direction.CW);
      c.clipPath(clip);
      c.drawBitmap(
          photo, null, new RectF(mid - dp(24), mid - dp(24), mid + dp(24), mid + dp(24)), paint);
      c.restoreToCount(save);
    } else {
      paint.setColor(GOLD);
      c.drawCircle(mid, mid, dp(17), paint);
    }
    String badge = group > 1 ? "+" + group : Integer.toString(p.optInt("acorns"));
    paint.setColor(GREEN);
    c.drawRoundRect(dp(35), dp(47), dp(69), dp(68), dp(10), dp(10), paint);
    paint.setColor(PAPER);
    paint.setTypeface(Typeface.DEFAULT_BOLD);
    paint.setTextSize(dp(11));
    paint.setTextAlign(Paint.Align.CENTER);
    c.drawText(badge, dp(52), dp(62), paint);
    return new BitmapDrawable(getResources(), b);
  }

  private void mapFilters() {
    LinearLayout panel = column();
    panel.setPadding(dp(20), dp(8), dp(20), dp(12));
    CheckBox acorns = new CheckBox(this);
    acorns.setText("Most acorns first");
    acorns.setChecked(topAcorns);
    panel.addView(acorns);
    Spinner types = new Spinner(this);
    String[] choices = new String[Observation.CATEGORIES.length + 1];
    choices[0] = "all";
    System.arraycopy(Observation.CATEGORIES, 0, choices, 1, Observation.CATEGORIES.length);
    types.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
    types.setSelection(Arrays.asList(choices).indexOf(category));
    panel.addView(types, new LinearLayout.LayoutParams(-1, dp(50)));
    TextView value = text("Nearby radius: " + (int) radius + " km", 16, GREEN, true);
    panel.addView(value);
    SeekBar range = new SeekBar(this);
    range.setMax(99);
    range.setProgress((int) radius - 1);
    panel.addView(range, new LinearLayout.LayoutParams(-1, dp(48)));
    range.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int n, boolean u) {
            value.setText("Nearby radius: " + (n + 1) + " km");
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    new AlertDialog.Builder(this)
        .setTitle("Explore your way")
        .setView(panel)
        .setPositiveButton(
            "Apply",
            (d, w) -> {
              category = choices[types.getSelectedItemPosition()];
              topAcorns = acorns.isChecked();
              radius = range.getProgress() + 1;
              rememberCentre();
              showMap();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showPreview(JSONObject p) {
    preview.removeAllViews();
    LinearLayout tile = row();
    tile.setPadding(dp(8), dp(8), dp(8), dp(8));
    tile.setBackground(shape(PAPER, 20));
    tile.setElevation(dp(8));
    ImageView im = new ImageView(this);
    im.setScaleType(ImageView.ScaleType.CENTER_CROP);
    tile.addView(im, new LinearLayout.LayoutParams(dp(74), dp(85)));
    image(p, im);
    LinearLayout caption = column();
    caption.setPadding(dp(12), 0, dp(5), 0);
    caption.addView(text(p.optString("name", "A discovery"), 17, GREEN, true));
    caption.addView(text(p.optString("place", ""), 12, MUTED, false));
    caption.addView(
        text(
            p.optBoolean("own")
                ? "Your journal"
                : "@" + p.optJSONObject("author").optString("username"),
            12,
            MUTED,
            false));
    tile.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
    tile.addView(acorn(p), new LinearLayout.LayoutParams(-2, dp(48)));
    tile.setOnClickListener(v -> openPhoto(p));
    preview.addView(tile);
  }

  private AcornButton acorn(JSONObject p) {
    AcornButton b = new AcornButton(this, p.optBoolean("acorned"), p.optInt("acorns"));
    b.setOnClickListener(
        v -> {
          if (p.optBoolean("own")) {
            Observation r = repo.db.toggleFlag(p.optString("id"), repo.owner(), "acorned");
            if (r != null) {
              Observation.put(p, "acorned", r.data.optBoolean("acorned"));
              Observation.put(p, "acorns", r.data.optInt("acornCount"));
              b.update(p.optBoolean("acorned"), p.optInt("acorns"));
              repo.enqueue();
              b.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
          } else {
            boolean active = !p.optBoolean("acorned");
            work(
                b,
                () -> call(photoPath(p, "acorn"), "PUT", data("active", active)),
                r -> {
                  Observation.put(p, "acorned", r.optBoolean("active"));
                  Observation.put(p, "acorns", r.optInt("acorns"));
                  b.update(p.optBoolean("acorned"), p.optInt("acorns"));
                  b.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                });
          }
        });
    return b;
  }

  private View photoCard(JSONObject p) {
    LinearLayout card = column();
    card.setBackground(shape(PAPER, 12));
    card.setPadding(dp(9), dp(9), dp(9), dp(10));
    card.setElevation(dp(3));
    FrameLayout photo = new FrameLayout(this);
    ImageView im = new ImageView(this);
    im.setScaleType(ImageView.ScaleType.CENTER_CROP);
    photo.addView(im, new FrameLayout.LayoutParams(-1, -1));
    image(p, im);
    FrameLayout.LayoutParams ap =
        new FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP | Gravity.END);
    ap.setMargins(0, dp(8), dp(8), 0);
    photo.addView(acorn(p), ap);
    card.addView(photo, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView name = title(p.optString("name", "A discovery"));
    name.setTextSize(24);
    name.setMaxLines(1);
    card.addView(name);
    card.addView(text(p.optString("place"), 12, MUTED, false));
    card.setOnClickListener(v -> openPhoto(p));
    return card;
  }

  private void showMapList() {
    List<JSONObject> copy = new ArrayList<>(visible);
    LinearLayout content =
        page(
            "list",
            topAcorns ? "Loved discoveries" : "Discoveries",
            "Tap a photo to open it. Acorns celebrate a good find.");
    for (JSONObject p : copy) {
      content.addView(photoCard(p), new LinearLayout.LayoutParams(-1, dp(360)));
      gap(content, 18);
    }
    if (copy.isEmpty())
      content.addView(
          text("Nothing here yet. Try Nearby, Following or a wider radius.", 17, MUTED, false));
  }

  private void openPhoto(JSONObject p) {
    if (p.optBoolean("own")) {
      startActivity(
          new Intent(this, LauncherActivity.class).putExtra("observation_id", p.optString("id")));
      return;
    }
    showPublicPhoto(p);
  }

  private void showPublicPhoto(JSONObject p) {
    LinearLayout content =
        page("photo", p.optString("name", "A discovery"), p.optString("scientificName"));
    ImageView im = new ImageView(this);
    im.setScaleType(ImageView.ScaleType.CENTER_CROP);
    content.addView(im, new LinearLayout.LayoutParams(-1, dp(360)));
    image(p, im);
    gap(content, 12);
    JSONObject person = p.optJSONObject("author");
    LinearLayout actions = row();
    actions.addView(acorn(p), new LinearLayout.LayoutParams(-2, dp(48)));
    TextView later =
        button(p.optBoolean("checkLater") ? "Saved for later" : "Check out later", false, () -> {});
    later.setOnClickListener(
        v ->
            work(
                later,
                () ->
                    call(photoPath(p, "save"), "PUT", data("active", !p.optBoolean("checkLater"))),
                r -> {
                  Observation.put(p, "checkLater", r.optBoolean("active"));
                  later.setText(p.optBoolean("checkLater") ? "Saved for later" : "Check out later");
                }));
    actions.addView(later, new LinearLayout.LayoutParams(0, dp(50), 1));
    content.addView(actions);
    gap(content, 14);
    content.addView(
        button(
            "@" + person.optString("username") + " · View journal", false, () -> showUser(person)));
    gap(content, 12);
    TextView follow =
        button(
            p.optBoolean("following") ? "Following · tap to unfollow" : "Follow this explorer",
            false,
            () -> {});
    follow.setOnClickListener(
        v ->
            work(
                follow,
                () ->
                    call(
                        "users/" + person.optString("id") + "/follow",
                        "PUT",
                        data("following", !p.optBoolean("following"))),
                r -> {
                  Observation.put(p, "following", r.optBoolean("following"));
                  follow.setText(
                      p.optBoolean("following")
                          ? "Following · tap to unfollow"
                          : "Follow this explorer");
                }));
    content.addView(follow);
    gap(content, 18);
    content.addView(text(p.optString("place"), 18, GREEN, true));
    content.addView(
        text(p.optString("localDate") + " · " + p.optString("timezone"), 13, MUTED, false));
    gap(content, 14);
    content.addView(text(p.optString("summary"), 17, GREEN, false));
    gap(content, 12);
    content.addView(text(p.optString("interestingInfo"), 16, MUTED, false));
    if (Observation.coords(p.opt("latitude"), p.opt("longitude"))) {
      gap(content, 18);
      content.addView(
          button(
              "Visit this place",
              true,
              () ->
                  open(
                      "geo:"
                          + p.optDouble("latitude")
                          + ","
                          + p.optDouble("longitude")
                          + "?q="
                          + p.optDouble("latitude")
                          + ","
                          + p.optDouble("longitude")
                          + "("
                          + Uri.encode(p.optString("name"))
                          + ")")));
      content.addView(
          text(
              "Respect wildlife and access rights. A pin is not a public footpath.",
              12,
              MUTED,
              false));
    }
    gap(content, 20);
    content.addView(button("Report photo", false, () -> report(p)));
    gap(content, 8);
    content.addView(
        button(
            "Block @" + person.optString("username"),
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Block this explorer?")
                    .setMessage(
                        "Their discoveries will be hidden from you, and yours from them. You can"
                            + " unblock in your community settings.")
                    .setPositiveButton(
                        "Block",
                        (d, w) ->
                            work(
                                null,
                                () ->
                                    call(
                                        "users/" + person.optString("id") + "/block",
                                        "PUT",
                                        data("blocked", true)),
                                r -> showMap()))
                    .setNegativeButton("Cancel", null)
                    .show()));
  }

  private void report(JSONObject p) {
    String[] labels = {
      "Contains people",
      "Unsafe or inappropriate",
      "Personal information",
      "Spam",
      "Wrong location",
      "Something else"
    };
    String[] reasons = {
      "people", "unsafe", "personal_information", "spam", "wrong_location", "other"
    };
    new AlertDialog.Builder(this)
        .setTitle("Why are you reporting it?")
        .setItems(
            labels,
            (d, n) -> {
              EditText detail = input("Optional details (up to 500 characters)");
              detail.setFilters(
                  new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(500)});
              new AlertDialog.Builder(this)
                  .setTitle(labels[n])
                  .setView(detail)
                  .setPositiveButton(
                      "Send report",
                      (dialog, w) -> {
                        JSONObject payload = data("reason", reasons[n]);
                        Observation.put(payload, "detail", detail.getText().toString());
                        work(
                            null,
                            () -> call(photoPath(p, "report"), "POST", payload),
                            r -> {
                              toast("Reported. This photo is hidden while it is reviewed.");
                              showMap();
                            });
                      })
                  .setNegativeButton("Cancel", null)
                  .show();
            })
        .show();
  }

  private void showPeople() {
    LinearLayout content =
        page(
            "people",
            "Find your people",
            "Follow a username, or find a friend using an email they have made discoverable.");
    if (!signedIn(content, this::showPeople)) return;
    content.addView(button("My community profile", false, this::showSettings));
    gap(content, 12);
    EditText search = input("Search usernames");
    search.setSingleLine(true);
    content.addView(search);
    gap(content, 8);
    LinearLayout result = column();
    content.addView(
        button(
            "Search explorers",
            true,
            () ->
                work(
                    null,
                    () ->
                        call("users?query=" + Uri.encode(search.getText().toString()), "GET", null),
                    r -> userRows(result, r.getJSONArray("users")))));
    gap(content, 8);
    content.addView(
        button(
            "People I follow",
            false,
            () ->
                work(
                    null,
                    () -> call("users?following=true", "GET", null),
                    r -> userRows(result, r.getJSONArray("users")))));
    gap(content, 18);
    EditText emails = emailField(content, "Friend’s email address");
    content.addView(
        text(
            "Only the addresses you select are searched. Your address book is never uploaded.",
            12,
            MUTED,
            false));
    gap(content, 8);
    content.addView(
        button(
            "Find selected contacts",
            false,
            () -> {
              JSONArray chosen = emails(emails);
              if (chosen.length() == 0) {
                toast("Add at least one email address.");
                return;
              }
              work(
                  null,
                  () -> call("contacts", "POST", data("emails", chosen)),
                  r -> userRows(result, r.getJSONArray("users")));
            }));
    gap(content, 18);
    content.addView(result);
    work(
        null,
        () -> call("users?following=true", "GET", null),
        r -> userRows(result, r.getJSONArray("users")));
  }

  private void userRows(LinearLayout list, JSONArray users) throws Exception {
    list.removeAllViews();
    if (users.length() == 0)
      list.addView(
          text(
              "No explorers found. Email search works when your friend opts in under You →"
                  + " Community profile.",
              16,
              MUTED,
              false));
    for (int i = 0; i < users.length(); i++) {
      JSONObject p = users.getJSONObject(i);
      list.addView(
          button(
              "@" + p.optString("username") + (p.optInt("following") == 1 ? " · Following" : ""),
              false,
              () -> showUser(p)));
      gap(list, 10);
    }
  }

  private EditText emailField(LinearLayout content, String hint) {
    EditText emails = input(hint);
    emails.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    content.addView(emails);
    gap(content, 8);
    content.addView(
        button(
            "Choose a contact’s email",
            false,
            () -> {
              contactDestination = emails;
              try {
                contactPicker.launch(
                    new Intent(
                        Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI));
              } catch (ActivityNotFoundException e) {
                toast("No contacts picker installed. Type the email address instead.");
              }
            }));
    return emails;
  }

  private JSONArray emails(EditText input) {
    JSONArray a = new JSONArray();
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String s : input.getText().toString().split("[,;\\s]+")) {
      s = s.trim().toLowerCase(Locale.ROOT);
      if (!s.isEmpty()) unique.add(s);
    }
    for (String s : unique) a.put(s);
    return a;
  }

  private void showUser(JSONObject person) {
    author = person.optString("id");
    LinearLayout content =
        page(
            "person",
            "@" + person.optString("username"),
            "A shared scrapbook. Private entries stay private.");
    TextView follow = button("Follow / unfollow", false, () -> {});
    content.addView(follow);
    follow.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("@" + person.optString("username"))
                .setItems(
                    new String[] {"Follow", "Unfollow"},
                    (d, n) ->
                        work(
                            follow,
                            () ->
                                call(
                                    "users/" + person.optString("id") + "/follow",
                                    "PUT",
                                    data("following", n == 0)),
                            r ->
                                follow.setText(r.optBoolean("following") ? "Following" : "Follow")))
                .show());
    gap(content, 20);
    LinearLayout diary = column();
    content.addView(diary);
    String path = "feed?scope=everyone&author=" + author + "&" + at();
    work(
        null,
        () -> call(path, "GET", null),
        r -> {
          JSONArray photos = r.getJSONArray("photos");
          Map<String, List<JSONObject>> days = new TreeMap<>(Collections.reverseOrder());
          for (int i = 0; i < photos.length(); i++) {
            JSONObject p = photos.getJSONObject(i);
            days.computeIfAbsent(p.optString("localDate", "Undated"), k -> new ArrayList<>())
                .add(p);
          }
          if (days.isEmpty())
            diary.addView(text("No shared entries for you here yet.", 17, MUTED, false));
          for (Map.Entry<String, List<JSONObject>> day : days.entrySet()) {
            diary.addView(title(day.getKey()));
            gap(diary, 10);
            List<Observation> stack = new ArrayList<>();
            Map<String, JSONObject> dto = new HashMap<>();
            for (JSONObject p : day.getValue()) {
              stack.add(new Observation(p, "shared", new File(getCacheDir(), "unused"), false, ""));
              dto.put(p.optString("id"), p);
            }
            ScrapbookStack cards =
                new ScrapbookStack(
                    this, stack, "", r0 -> photoCard(dto.get(r0.id())), selected -> {});
            cards.setBackground(shape(GREEN, 20));
            diary.addView(cards, new LinearLayout.LayoutParams(-1, -2));
            gap(diary, 22);
          }
          if (r.optBoolean("hasMore"))
            diary.addView(text("Showing the latest 100 shared photos.", 13, MUTED, false));
        });
  }

  private void showSettings() {
    LinearLayout content =
        page(
            "settings",
            "Your trail circle",
            "Your username is your public identity. Your account name and email stay private.");
    if (!signedIn(content, this::showSettings)) return;
    EditText username = input("Username");
    content.addView(username);
    CheckBox searchable = new CheckBox(this);
    searchable.setText("Let friends find me using my email address");
    searchable.setTextColor(GREEN);
    content.addView(searchable);
    content.addView(
        text(
            "Off by default. Friends only receive your username; your email is never shown on your"
                + " profile.",
            13,
            MUTED,
            false));
    gap(content, 14);
    TextView save = button("Save community profile", true, () -> {});
    save.setEnabled(false);
    save.setOnClickListener(
        v -> {
          JSONObject p = data("username", username.getText().toString().trim());
          Observation.put(p, "discoverable", searchable.isChecked());
          work(save, () -> call("me", "PUT", p), r -> toast("Community profile saved."));
        });
    content.addView(save);
    gap(content, 20);
    content.addView(button("My published photos", false, this::showPublished));
    gap(content, 10);
    content.addView(
        button(
            "Places to check out later",
            false,
            () -> {
              scope = "saved";
              showMap();
            }));
    gap(content, 10);
    content.addView(button("People I follow", false, this::showPeople));
    gap(content, 10);
    content.addView(button("Blocked explorers", false, this::showBlocked));
    gap(content, 10);
    content.addView(
        button("Sharing guidelines", false, () -> open(Api.ORIGIN + "/community-rules")));
    work(
        null,
        () -> call("me", "GET", null),
        r -> {
          profile = r.getJSONObject("profile");
          username.setText(profile.optString("username"));
          searchable.setChecked(profile.optBoolean("discoverable"));
          save.setEnabled(true);
          if (profile.optBoolean("isModerator")) {
            gap(content, 18);
            content.addView(button("Review reported photos", true, this::showReports));
          }
        });
  }

  private void showBlocked() {
    LinearLayout content =
        page(
            "blocked",
            "Blocked explorers",
            "Unblocking lets you see each other’s shared discoveries again.");
    work(
        null,
        () -> call("blocked", "GET", null),
        r -> {
          JSONArray users = r.getJSONArray("users");
          if (users.length() == 0) content.addView(text("No blocked explorers.", 16, MUTED, false));
          for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.getJSONObject(i);
            content.addView(
                button(
                    "Unblock @" + u.optString("username"),
                    false,
                    () ->
                        work(
                            null,
                            () ->
                                call(
                                    "users/" + u.optString("id") + "/block",
                                    "PUT",
                                    data("blocked", false)),
                            done -> showBlocked())));
            gap(content, 10);
          }
        });
  }

  private void showPublished() {
    LinearLayout content =
        page(
            "published",
            "Shared by you",
            "Unpublish at any time. Your original photo stays in your private journal.");
    if (!signedIn(content, this::showPublished)) return;
    work(
        null,
        () -> call("published", "GET", null),
        r -> {
          JSONArray photos = r.getJSONArray("photos");
          if (photos.length() == 0)
            content.addView(
                text(
                    "Open one of your journal photos and choose Publish discovery to share it.",
                    17,
                    MUTED,
                    false));
          for (int i = 0; i < photos.length(); i++) {
            JSONObject p = photos.getJSONObject(i);
            content.addView(photoCard(p), new LinearLayout.LayoutParams(-1, dp(310)));
            content.addView(
                text(
                    p.optString("status")
                        + " · "
                        + p.optString("audience")
                        + " · "
                        + p.optInt("acorns")
                        + " acorns",
                    14,
                    GREEN,
                    true));
            if (!p.optString("reason").isEmpty())
              content.addView(text(p.optString("reason"), 14, MUTED, false));
            gap(content, 8);
            if (p.optString("status").equals("published")) {
              content.addView(
                  button(
                      "Unpublish", true, () -> unpublish(p.optString("id"), this::showPublished)));
              if (p.optString("audience").equals("people")) {
                gap(content, 8);
                content.addView(
                    button("Send or manage invitations", false, () -> manageInvites(p)));
              }
            } else if (!p.optString("status").equals("removed"))
              content.addView(
                  button("Review sharing", false, () -> showPublish(p.optString("id"))));
            gap(content, 24);
          }
        });
  }

  private void unpublish(String id, Runnable done) {
    work(
        null,
        () -> call("photos/" + id + "/publish", "DELETE", null),
        r -> {
          repo.enqueue();
          toast("Unpublished. Your photo remains in your journal.");
          done.run();
        });
  }

  private void showPublish(String id) {
    photoId = id;
    Observation record = repo.db.find(id);
    LinearLayout content =
        page(
            "publish",
            "Share a discovery",
            "Choose who can see this photo. Nothing is published until you tap Publish.");
    if (!signedIn(content, () -> showPublish(id))) return;
    if (record == null || !record.owner.equals(repo.owner()) || record.archived()) {
      content.addView(text("Open an active photo from your journal first.", 17, MUTED, false));
      return;
    }
    ImageView im = new ImageView(this);
    im.setScaleType(ImageView.ScaleType.CENTER_CROP);
    content.addView(im, new LinearLayout.LayoutParams(-1, dp(230)));
    Glide.with(this).load(record.photo).into(im);
    gap(content, 12);
    content.addView(title(record.name()));
    content.addView(text(record.place() + " · " + record.day(), 14, MUTED, false));
    TextView identity = text("Loading your public username…", 15, GREEN, true);
    content.addView(identity);
    content.addView(button("Change public username", false, this::showSettings));
    gap(content, 16);
    RadioGroup audience = new RadioGroup(this);
    final int everyoneId = View.generateViewId(),
        localId = View.generateViewId(),
        peopleId = View.generateViewId();
    int[] audienceIds = {everyoneId, localId, peopleId};
    String[] labels = {
      "Everyone signed in",
      "Local — people browsing my chosen area",
      "Specific people — private email invitations"
    };
    for (int i = 0; i < 3; i++) {
      RadioButton b = new RadioButton(this);
      b.setId(audienceIds[i]);
      b.setText(labels[i]);
      b.setTextColor(GREEN);
      b.setMinHeight(dp(52));
      audience.addView(b);
    }
    // A fresh publication always requires an explicit audience choice.
    audience.clearCheck();
    content.addView(audience);
    LinearLayout local = column(), people = column();
    local.setVisibility(View.GONE);
    people.setVisibility(View.GONE);
    content.addView(local);
    content.addView(people);
    double[] circle = {
      record.hasGps() ? record.data.optDouble("latitude") : latitude,
      record.hasGps() ? record.data.optDouble("longitude") : longitude,
      10
    };
    TextView circleText = text("Local sharing radius: 10 km", 15, GREEN, true);
    local.addView(circleText);
    local.addView(
        text(
            "Choose a centre on the map. People browsing inside this circle can see your photo.",
            13,
            MUTED,
            false));
    local.addView(
        button(
            "Choose area on map",
            false,
            () ->
                chooseCircle(
                    circle,
                    () ->
                        circleText.setText(
                            "Local radius: "
                                + (int) circle[2]
                                + " km · "
                                + String.format(Locale.UK, "%.3f, %.3f", circle[0], circle[1])))));
    EditText recipients = emailField(people, "Email addresses, separated by commas");
    people.addView(
        text(
            "After publishing, send each person their own invite link. Only their signed-in account"
                + " can accept it.",
            13,
            MUTED,
            false));
    audience.setOnCheckedChangeListener(
        (g, checked) -> {
          local.setVisibility(checked == localId ? View.VISIBLE : View.GONE);
          people.setVisibility(checked == peopleId ? View.VISIBLE : View.GONE);
        });
    gap(content, 16);
    CheckBox consent = new CheckBox(this);
    consent.setText(
        "I agree to the sharing guidelines and to sharing this photo, its location, date and story"
            + " with my chosen audience.");
    consent.setTextColor(GREEN);
    content.addView(consent);
    content.addView(
        button("Read sharing guidelines", false, () -> open(Api.ORIGIN + "/community-rules")));
    gap(content, 8);
    content.addView(
        text(
            "Photos containing people or unsafe content cannot be published. The automatic check"
                + " can make mistakes; your private journal is unaffected.",
            13,
            MUTED,
            false));
    gap(content, 18);
    TextView status = text("", 14, MUTED, false);
    actionStatus = status;
    content.addView(status);
    TextView publish = button("Publish discovery", true, () -> {});
    publish.setEnabled(false);
    content.addView(publish);
    publish.setOnClickListener(
        v -> {
          if (!consent.isChecked()) {
            toast("Read and accept the sharing guidelines first.");
            return;
          }
          if (audience.getCheckedRadioButtonId() == -1) {
            toast("Choose who can see this discovery first.");
            return;
          }
          String selected =
              audience.getCheckedRadioButtonId() == localId
                  ? "local"
                  : audience.getCheckedRadioButtonId() == peopleId ? "people" : "everyone";
          JSONArray chosen = emails(recipients);
          if (selected.equals("people") && chosen.length() == 0) {
            toast("Add at least one email address.");
            return;
          }
          JSONObject payload = data("audience", selected);
          Observation.put(payload, "agree", true);
          Observation.put(payload, "emails", chosen);
          Observation.put(payload, "latitude", circle[0]);
          Observation.put(payload, "longitude", circle[1]);
          Observation.put(payload, "radiusKm", circle[2]);
          status.setText("Uploading and checking this photo… Please keep this screen open.");
          work(
              publish,
              () -> {
                Observation latest = repo.db.find(id);
                if (latest == null || latest.archived())
                  throw new Exception("This photo is unavailable.");
                if (latest.pending) {
                  repo.api.upload(latest, cookie());
                  if (!repo.db.markUploaded(id, latest.revision(), repo.owner()))
                    throw new Exception("Your photo changed. Please try again.");
                }
                Observation.put(payload, "revision", latest.revision());
                return call("photos/" + id + "/publish", "POST", payload);
              },
              r -> {
                repo.enqueue();
                if (r.optBoolean("published")) {
                  toast("Your discovery is shared.");
                  JSONArray invites = r.optJSONArray("invitations");
                  if (invites != null && invites.length() > 0) showInvitations(invites);
                  else showPublished();
                } else status.setText(r.optString("reason", "This photo stays private."));
              });
        });
    gap(content, 12);
    content.addView(
        button(
            "Keep private / unpublish",
            false,
            () ->
                work(
                    null,
                    () -> call("published", "GET", null),
                    r -> {
                      JSONArray all = r.getJSONArray("photos");
                      for (int i = 0; i < all.length(); i++)
                        if (all.getJSONObject(i).optString("id").equals(id)) {
                          unpublish(id, this::showPublished);
                          return;
                        }
                      finish();
                    })));
    work(
        null,
        () -> call("me", "GET", null),
        r -> {
          identity.setText("Published as @" + r.getJSONObject("profile").optString("username"));
          publish.setEnabled(true);
        });
  }

  private void chooseCircle(double[] circle, Runnable done) {
    MapView picker = new MapView(this);
    picker.setTileSource(TileSourceFactory.MAPNIK);
    picker.setMultiTouchControls(true);
    picker.setBuiltInZoomControls(false);
    picker.getController().setZoom(11d);
    picker.getController().setCenter(new GeoPoint(circle[0], circle[1]));
    LinearLayout panel = column();
    panel.setPadding(dp(12), 0, dp(12), 0);
    panel.addView(text("Pan the map to move the centre of the circle.", 14, MUTED, false));
    panel.addView(picker, new LinearLayout.LayoutParams(-1, dp(300)));
    TextView amount = text("Radius: " + (int) circle[2] + " km", 16, GREEN, true);
    panel.addView(amount);
    SeekBar range = new SeekBar(this);
    range.setMax(99);
    range.setProgress((int) circle[2] - 1);
    panel.addView(range, new LinearLayout.LayoutParams(-1, dp(48)));
    Polygon boundary = new Polygon(picker);
    boundary.getFillPaint().setColor(0x25154e45);
    boundary.getOutlinePaint().setColor(GREEN);
    boundary.getOutlinePaint().setStrokeWidth(dp(2));
    picker.getOverlays().add(boundary);
    Runnable redraw =
        () -> {
          boundary.setPoints(
              Polygon.pointsAsCircle(
                  new GeoPoint(
                      picker.getMapCenter().getLatitude(), picker.getMapCenter().getLongitude()),
                  (range.getProgress() + 1) * 1000d));
          picker.invalidate();
        };
    range.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int n, boolean user) {
            amount.setText("Radius: " + (n + 1) + " km");
            redraw.run();
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {}
        });
    picker.addMapListener(
        new MapListener() {
          public boolean onScroll(ScrollEvent e) {
            redraw.run();
            return false;
          }

          public boolean onZoom(ZoomEvent e) {
            return false;
          }
        });
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Your local sharing area")
            .setView(panel)
            .setPositiveButton(
                "Use this area",
                (d, w) -> {
                  circle[0] = picker.getMapCenter().getLatitude();
                  circle[1] = picker.getMapCenter().getLongitude();
                  circle[2] = range.getProgress() + 1;
                  done.run();
                })
            .setNegativeButton("Cancel", null)
            .create();
    dialog.setOnDismissListener(
        d -> {
          picker.onPause();
          picker.onDetach();
        });
    dialog.show();
    picker.onResume();
    picker.post(redraw);
  }

  private void showInvitations(JSONArray invites) {
    LinearLayout content =
        page(
            "invitations",
            "Invite your people",
            "Send each person their own link. The invitation only opens for the email address"
                + " shown.");
    for (int i = 0; i < invites.length(); i++) {
      JSONObject invite = invites.optJSONObject(i);
      content.addView(
          button(
              "Email " + invite.optString("email"),
              true,
              () -> {
                String url =
                    "mailto:"
                        + Uri.encode(invite.optString("email"))
                        + "?subject="
                        + Uri.encode("A discovery for you — My Trail Log")
                        + "&body="
                        + Uri.encode(
                            "I thought you’d like this discovery from My Trail Log. Sign in with"
                                + " this email address to open your private invitation:\n\n"
                                + invite.optString("url"));
                open(url);
              }));
      gap(content, 12);
    }
    content.addView(button("Done", false, this::showPublished));
  }

  private void manageInvites(JSONObject p) {
    LinearLayout content =
        page(
            "invitations",
            "Private invitations",
            "Invite or resend a link to the people you choose. To remove everyone’s access,"
                + " unpublish this photo.");
    EditText recipients = emailField(content, "Email addresses");
    gap(content, 12);
    content.addView(
        button(
            "Create invitation links",
            true,
            () -> {
              JSONArray chosen = emails(recipients);
              work(
                  null,
                  () ->
                      call(
                          "photos/" + p.optString("id") + "/invitations",
                          "POST",
                          data("emails", chosen)),
                  r -> showInvitations(r.getJSONArray("invitations")));
            }));
    gap(content, 18);
    work(
        null,
        () -> call("photos/" + p.optString("id") + "/invitations", "GET", null),
        r -> {
          JSONArray rows = r.getJSONArray("recipients");
          for (int i = 0; i < rows.length(); i++) {
            JSONObject recipient = rows.getJSONObject(i);
            content.addView(
                text(
                    recipient.optString("email")
                        + " · "
                        + (recipient.optInt("accepted") == 1 ? "accepted" : "awaiting invitation"),
                    14,
                    MUTED,
                    false));
          }
        });
  }

  private void showInvite() {
    LinearLayout content =
        page(
            "invite",
            "A discovery for you",
            "Sign in with the email address this private invitation was sent to.");
    if (!signedIn(content, this::showInvite)) return;
    String token = getSharedPreferences("pending-invite", 0).getString("token", "");
    if (token.isEmpty()) {
      content.addView(
          text("Open the invitation link from your email to continue.", 17, MUTED, false));
      return;
    }
    content.addView(
        button(
            "Accept invitation",
            true,
            () ->
                work(
                    null,
                    () -> call("invite", "POST", data("token", token)),
                    r -> {
                      getSharedPreferences("pending-invite", 0).edit().clear().apply();
                      work(
                          null,
                          () -> call("photos/" + r.getString("id"), "GET", null),
                          photo -> showPublicPhoto(photo.getJSONObject("photo")));
                    })));
  }

  private void showReports() {
    LinearLayout content =
        page(
            "moderation",
            "Review reports",
            "Reported photos stay hidden. Restore runs the automatic publishing check again.");
    work(
        null,
        () -> call("moderation", "GET", null),
        r -> {
          JSONArray reports = r.getJSONArray("reports");
          if (reports.length() == 0) content.addView(text("No pending reports.", 17, MUTED, false));
          Set<String> seen = new HashSet<>();
          for (int i = 0; i < reports.length(); i++) {
            JSONObject report = reports.getJSONObject(i);
            String id = report.optString("observation_id");
            if (!seen.add(id)) continue;
            JSONObject p = new JSONObject(report.optString("snapshot"));
            Observation.put(p, "photoUrl", "/api/social/moderation/" + id + "/photo");
            ImageView im = new ImageView(this);
            im.setScaleType(ImageView.ScaleType.CENTER_CROP);
            content.addView(im, new LinearLayout.LayoutParams(-1, dp(250)));
            image(p, im);
            content.addView(
                text(
                    "@" + report.optString("username") + " · " + report.optString("reason"),
                    16,
                    GREEN,
                    true));
            content.addView(text(report.optString("detail"), 14, MUTED, false));
            content.addView(
                button(
                    "Remove publication",
                    true,
                    () ->
                        work(
                            null,
                            () -> call("moderation/" + id, "POST", data("action", "remove")),
                            done -> showReports())));
            gap(content, 8);
            content.addView(
                button(
                    "Recheck and restore if allowed",
                    false,
                    () ->
                        work(
                            null,
                            () -> call("moderation/" + id, "POST", data("action", "review")),
                            done -> {
                              toast(done.optString("status") + " · " + done.optString("reason"));
                              showReports();
                            })));
            gap(content, 24);
          }
        });
  }

  private void open(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (ActivityNotFoundException e) {
      toast("No app is available to open this link.");
    }
  }
}
