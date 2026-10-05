package com.field.logger.nativeapp;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;

/**
 * One-off, explicitly consented selfie-to-cartoon workflow. Register during Activity.onCreate,
 * before STARTED. Source files stay in private cache, never in the journal or community upload.
 */
public final class AvatarPhotoController {
  private static final String STATE = "my-trail-log-avatar-photo";
  private final AppCompatActivity activity;
  private final Session session;
  private final Api api = new Api();
  private final ActivityResultLauncher<Uri> camera;
  private final ActivityResultLauncher<String> cameraPermission;
  private final ActivityResultLauncher<String[]> gallery;
  private AvatarPicker.Selected selected;
  private File source;
  private String sourceOwner = "";
  private boolean waitingCapture, previewPending, generating;
  private volatile boolean destroyed;
  private AlertDialog processing;
  private AlertDialog statusDialog;

  public AvatarPhotoController(AppCompatActivity a) {
    this(a, null);
  }

  public AvatarPhotoController(AppCompatActivity a, AvatarPicker.Selected onSelected) {
    activity = a;
    selected = onSelected;
    session = new Session(a);
    Bundle restored = a.getSavedStateRegistry().consumeRestoredStateForKey(STATE);
    if (restored != null) {
      String filename = restored.getString("source", "");
      if (filename.matches("avatar-source-[0-9a-f-]+\\.jpg")) {
        File candidate = new File(shared(), filename);
        if (candidate.isFile()) source = candidate;
      }
      sourceOwner = restored.getString("owner", "");
      waitingCapture = restored.getBoolean("camera", false);
      previewPending =
          source != null && !waitingCapture && !restored.getBoolean("generating", false);
      if (restored.getBoolean("generating", false)) {
        // The in-flight operation owns its in-memory bytes; a recreated screen must not resend it.
        if (source != null) source.delete();
        source = null;
      }
    }
    // Interrupted files from an earlier launch are removed; preserve only a restored capture.
    File[] stale = shared().listFiles();
    if (stale != null)
      for (File file : stale)
        if (file.getName().startsWith("avatar-source-") && !file.equals(source)) file.delete();
    a.getSavedStateRegistry()
        .registerSavedStateProvider(
            STATE,
            () -> {
              Bundle state = new Bundle();
              state.putString("source", source == null ? "" : source.getName());
              state.putString("owner", sourceOwner);
              state.putBoolean("camera", waitingCapture);
              state.putBoolean("generating", generating);
              return state;
            });
    camera =
        a.registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            ok -> {
              waitingCapture = false;
              if (ok && source != null && source.length() > 0) preparePreview();
              else cleanup();
            });
    cameraPermission =
        a.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            ok -> {
              if (ok) launchCamera();
              else {
                cleanup();
                message(
                    "Camera access was declined. You can choose a photo or build a character"
                        + " instead.");
              }
            });
    gallery =
        a.registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
              if (uri == null) {
                cleanup();
                return;
              }
              try {
                newSource();
              } catch (IOException e) {
                cleanup();
                message("The temporary photo could not be opened.");
                return;
              }
              final File target = source;
              Repository.IO.execute(
                  () -> {
                    try (InputStream in = activity.getContentResolver().openInputStream(uri);
                        FileOutputStream out = new FileOutputStream(target)) {
                      out.write(Api.read(in, 20 * 1024 * 1024));
                      if (!usable()) {
                        target.delete();
                        return;
                      }
                      activity.runOnUiThread(
                          () -> {
                            if (!usable() || source != target) target.delete();
                            else preparePreview();
                          });
                    } catch (Exception e) {
                      target.delete();
                      activity.runOnUiThread(
                          () -> {
                            if (source == target) cleanup();
                            message("Choose a photo under 20 MB in a supported image format.");
                          });
                    }
                  });
            });
    a.getLifecycle()
        .addObserver(
            new DefaultLifecycleObserver() {
              @Override
              public void onStart(LifecycleOwner owner) {
                if (previewPending && source != null && !generating) {
                  previewPending = false;
                  preparePreview();
                }
              }

              @Override
              public void onDestroy(LifecycleOwner owner) {
                destroyed = true;
                if (processing != null) processing.dismiss();
                if (statusDialog != null) statusDialog.dismiss();
                if (!a.isChangingConfigurations() && !generating) cleanup();
              }
            });
  }

  public void show(JSONObject current) {
    show(current, selected);
  }

  public void show(JSONObject current, AvatarPicker.Selected callback) {
    if (generating) {
      message("Your trail avatar is being illustrated. You can keep exploring while it finishes.");
      return;
    }
    selected = callback;
    JSONObject user = session.get();
    if (user == null) {
      new AlertDialog.Builder(activity)
          .setTitle("Make my avatar")
          .setMessage(
              "Sign in to make a cartoon from your photo, or create an illustrated character now.")
          .setPositiveButton(
              "Build a character", (d, w) -> AvatarPicker.show(activity, current, callback))
          .setNegativeButton("Close", null)
          .show();
      return;
    }
    String owner = user.optString("id"), cookie = user.optString("cookie");
    AlertDialog checking =
        new AlertDialog.Builder(activity)
            .setTitle("Make my avatar")
            .setMessage("Opening your avatar studio…")
            .setNegativeButton("Cancel", null)
            .create();
    final boolean[] cancelled = {false};
    checking.setOnDismissListener(d -> cancelled[0] = true);
    statusDialog = checking;
    checking.show();
    Repository.IO.execute(
        () -> {
          JSONObject status = null;
          try {
            status = api.json("/api/avatar", "GET", cookie, null);
          } catch (Exception ignored) {
          }
          final JSONObject result = status;
          activity.runOnUiThread(
              () -> {
                if (!usable() || cancelled[0] || !owner.equals(session.owner())) return;
                checking.dismiss();
                JSONObject shown = current;
                JSONObject serverAvatar = result == null ? null : result.optJSONObject("avatar");
                if (AvatarImages.allowed(serverAvatar)
                    && !TrailPreferences.of(activity, owner).getBoolean("avatarPending", false)) {
                  // Recover a completed creation whose network response was interrupted.
                  TrailPreferences.of(activity, owner)
                      .edit()
                      .putString("avatar", serverAvatar.toString())
                      .putBoolean("avatarPending", false)
                      .apply();
                  shown = serverAvatar;
                  if (callback != null) callback.accept(serverAvatar);
                }
                studio(shown, callback, result);
              });
        });
  }

  private void studio(JSONObject current, AvatarPicker.Selected callback, JSONObject status) {
    // Fail closed: no source-photo upload when generation eligibility could not be checked.
    boolean enabled =
        status != null
            && status.optBoolean("generationEnabled", false)
            && status.optInt("remainingGenerations", 0) > 0;
    String detail =
        enabled
            ? "A photo of you becomes a warm woodland cartoon. Only the illustration is shown to"
                  + " members. "
                + status.optInt("remainingGenerations", 0)
                + " photo-avatar attempts available. "
                + status.optString("allowanceDescription", status.optString("allowance", ""))
            : "Photo avatars are unavailable right now or your photo-avatar allowance has been"
                  + " used. You can always build an illustrated character without sending a photo.";
    AlertDialog.Builder studio =
        new AlertDialog.Builder(activity).setTitle("Make my avatar").setMessage(detail);
    if (enabled) {
      studio.setPositiveButton(
          "Take a photo",
          (d, w) -> {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) launchCamera();
            else cameraPermission.launch(Manifest.permission.CAMERA);
          });
      studio.setNeutralButton(
          "Choose a photo",
          (d, w) -> {
            sourceOwner = session.owner();
            gallery.launch(new String[] {"image/*"});
          });
      studio.setNegativeButton(
          "Build a character", (d, w) -> AvatarPicker.show(activity, current, callback));
    } else {
      studio.setPositiveButton(
          "Build a character", (d, w) -> AvatarPicker.show(activity, current, callback));
      studio.setNegativeButton("Cancel", null);
    }
    studio.show();
  }

  private File shared() {
    File directory = new File(activity.getCacheDir(), "shared");
    directory.mkdirs();
    return directory;
  }

  private void newSource() throws IOException {
    cleanup();
    sourceOwner = session.owner();
    source = new File(shared(), "avatar-source-" + UUID.randomUUID() + ".jpg");
    if (!source.createNewFile()) throw new IOException("Could not create temporary photo.");
  }

  private void launchCamera() {
    try {
      newSource();
      waitingCapture = true;
      Uri uri = FileProvider.getUriForFile(activity, "com.field.logger.files", source);
      camera.launch(uri);
    } catch (Exception e) {
      cleanup();
      message("A camera could not be opened. Choose a photo instead.");
    }
  }

  private void preparePreview() {
    if (!usable() || source == null || generating) return;
    if (!sourceOwner.equals(session.owner()) || "guest".equals(sourceOwner)) {
      cleanup();
      message("Sign in again before making an avatar.");
      return;
    }
    final File input = source;
    Repository.IO.execute(
        () -> {
          try {
            byte[] jpeg = sanitise(input);
            if (!usable() || source != input) {
              input.delete();
              java.util.Arrays.fill(jpeg, (byte) 0);
              return;
            }
            // Replace the source with a metadata-free preview while the user reviews consent.
            try (FileOutputStream out = new FileOutputStream(input)) {
              out.write(jpeg);
            }
            activity.runOnUiThread(
                () -> {
                  if (!usable() || source != input) {
                    input.delete();
                    java.util.Arrays.fill(jpeg, (byte) 0);
                  } else confirm(jpeg);
                });
          } catch (Exception e) {
            input.delete();
            activity.runOnUiThread(
                () -> {
                  if (source == input) cleanup();
                  message(
                      "This photo could not be opened. Choose another supported image under 20"
                          + " MB.");
                });
          }
        });
  }

  private void confirm(byte[] jpeg) {
    if (!usable() || !sourceOwner.equals(session.owner())) {
      cleanup();
      return;
    }
    LinearLayout content = new LinearLayout(activity);
    content.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(20);
    content.setPadding(pad, dp(8), pad, pad);
    ImageView photo = new ImageView(activity);
    photo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    photo.setContentDescription("Private source photo for your avatar");
    photo.setImageBitmap(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length));
    content.addView(photo, new LinearLayout.LayoutParams(-1, dp(200)));
    TextView privacy = new TextView(activity);
    privacy.setTextColor(0xff154e45);
    privacy.setTextSize(15);
    privacy.setText(
        "Make a woodland-style cartoon from this photo. The photo is sent privately to OpenAI "
            + "for processing. It is never added to your journal or shared with members. "
            + "The app removes its temporary source after processing; OpenAI may retain API data "
            + "under its privacy terms. Only your cartoon becomes your member avatar.");
    content.addView(privacy);
    CheckBox consent = new CheckBox(activity);
    consent.setText("I have permission to use this photo and agree to AI processing.");
    consent.setTextColor(0xff154e45);
    consent.setMinHeight(dp(56));
    content.addView(consent);
    TextView allowance = new TextView(activity);
    allowance.setText(
        "Once AI processing starts, the attempt uses one photo-avatar allowance, including if the"
            + " AI service cannot finish. Your journal photo allowance is separate.");
    allowance.setTextColor(0xff647363);
    allowance.setTextSize(13);
    content.addView(allowance);
    ScrollView scroll = new ScrollView(activity);
    scroll.addView(content);
    final boolean[] submitted = {false};
    AlertDialog dialog =
        new AlertDialog.Builder(activity)
            .setTitle("Make my avatar")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(
                "Create my avatar",
                (d, w) -> {
                  submitted[0] = true;
                  generate(jpeg);
                })
            .create();
    dialog.setOnDismissListener(
        d -> {
          photo.setImageDrawable(null);
          if (!submitted[0]) {
            java.util.Arrays.fill(jpeg, (byte) 0);
            if (!activity.isChangingConfigurations()) cleanup();
          }
        });
    dialog.show();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    consent.setOnCheckedChangeListener(
        (button, checked) -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
  }

  private void generate(byte[] jpeg) {
    JSONObject user = session.get();
    if (user == null || !sourceOwner.equals(user.optString("id"))) {
      cleanup();
      message("Sign in again before making an avatar.");
      return;
    }
    generating = true;
    String owner = user.optString("id"), cookie = user.optString("cookie");
    processing =
        new AlertDialog.Builder(activity)
            .setTitle("Illustrating your trail avatar")
            .setMessage(
                "This can take a minute or two. You can keep exploring while your cartoon is made.")
            .setPositiveButton("Keep exploring", null)
            .create();
    processing.setCanceledOnTouchOutside(false);
    processing.show();
    Repository.IO.execute(
        () -> {
          JSONObject result = null;
          String error =
              "The avatar could not be made. Your journal is unchanged. Please try again later.";
          try {
            String boundary = "TrailAvatar" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream form = new ByteArrayOutputStream();
            String requestId = UUID.randomUUID().toString();
            form.write(
                ("--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"
                        + requestId
                        + "\r\n--"
                        + boundary
                        + "\r\nContent-Disposition: form-data; name=\"consent\"\r\n\r\ntrue\r\n--"
                        + boundary
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\"photo\";"
                        + " filename=\"avatar-source.jpg\"\r\n"
                        + "Content-Type: image/jpeg\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            form.write(jpeg);
            form.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            result =
                api.request(
                        "/api/avatar/photo",
                        "POST",
                        cookie,
                        form.toByteArray(),
                        "multipart/form-data; boundary=" + boundary)
                    .json()
                    .getJSONObject("avatar");
            if (!AvatarImages.allowed(result)) throw new IOException("Unexpected avatar result.");
            // The service has activated this cartoon. Do not queue another avatar PUT.
            if (owner.equals(session.owner()))
              TrailPreferences.of(activity, owner)
                  .edit()
                  .putString("avatar", result.toString())
                  .putBoolean("avatarPending", false)
                  .apply();
          } catch (Api.Failure e) {
            error = e.getMessage();
          } catch (Exception ignored) {
          } finally {
            java.util.Arrays.fill(jpeg, (byte) 0);
            cleanup();
          }
          final JSONObject avatar = result;
          final String failure = error;
          activity.runOnUiThread(
              () -> {
                generating = false;
                if (processing != null) processing.dismiss();
                processing = null;
                if (!usable() || !owner.equals(session.owner())) return;
                if (avatar == null || !AvatarImages.allowed(avatar)) {
                  message(failure);
                  return;
                }
                if (selected != null) selected.accept(avatar);
                AvatarView preview = new AvatarView(activity, avatar);
                FrameLayout frame = new FrameLayout(activity);
                FrameLayout.LayoutParams size =
                    new FrameLayout.LayoutParams(dp(220), dp(220), Gravity.CENTER);
                frame.addView(preview, size);
                new AlertDialog.Builder(activity)
                    .setTitle("Your trail avatar is ready")
                    .setView(frame)
                    .setPositiveButton("Lovely", null)
                    .show();
              });
        });
  }

  /** Reads pixels and creates a fresh JPEG: source EXIF, GPS and device metadata are not copied. */
  public static byte[] sanitise(File source) throws Exception {
    if (source.length() <= 0 || source.length() > 20 * 1024 * 1024)
      throw new IOException("Choose a photo under 20 MB.");
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(source.getAbsolutePath(), options);
    if (options.outWidth <= 0
        || options.outHeight <= 0
        || options.outWidth > 24000
        || options.outHeight > 24000) throw new IOException("Unsupported source photo.");
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap image = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
    if (image == null) throw new IOException("Unsupported source photo.");
    Bitmap adjusted = null;
    try {
      ExifInterface exif = new ExifInterface(source);
      Matrix matrix = new Matrix();
      switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
        case 2:
          matrix.setScale(-1, 1);
          break;
        case 3:
          matrix.setRotate(180);
          break;
        case 4:
          matrix.setScale(1, -1);
          break;
        case 5:
          matrix.setRotate(90);
          matrix.postScale(-1, 1);
          break;
        case 6:
          matrix.setRotate(90);
          break;
        case 7:
          matrix.setRotate(-90);
          matrix.postScale(-1, 1);
          break;
        case 8:
          matrix.setRotate(-90);
          break;
      }
      float scale = Math.min(1f, 1024f / Math.max(image.getWidth(), image.getHeight()));
      matrix.postScale(scale, scale);
      adjusted =
          Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), matrix, true);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!adjusted.compress(Bitmap.CompressFormat.JPEG, 88, out))
        throw new IOException("Could not prepare avatar photo.");
      byte[] data = out.toByteArray();
      if (data.length > 2 * 1024 * 1024) throw new IOException("Choose a smaller photo.");
      return data;
    } finally {
      if (adjusted != null && adjusted != image) adjusted.recycle();
      image.recycle();
    }
  }

  private boolean usable() {
    return !destroyed && !activity.isFinishing() && !activity.isDestroyed();
  }

  private int dp(int value) {
    return Math.round(value * activity.getResources().getDisplayMetrics().density);
  }

  private void message(String text) {
    if (!usable()) return;
    new AlertDialog.Builder(activity)
        .setTitle("Make my avatar")
        .setMessage(text)
        .setPositiveButton("OK", null)
        .show();
  }

  private synchronized void cleanup() {
    if (source != null) source.delete();
    source = null;
    sourceOwner = "";
    waitingCapture = false;
    previewPending = false;
  }
}
