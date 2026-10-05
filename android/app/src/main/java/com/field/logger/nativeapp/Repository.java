package com.field.logger.nativeapp;

import android.content.Context;
import androidx.work.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class Repository {
  public final Context context;
  public final JournalDb db;
  public final Session session;
  public final Api api = new Api();
  public static final ExecutorService IO = Executors.newFixedThreadPool(3);
  private static final Object SYNC_LOCK = new Object();

  public Repository(Context c) {
    context = c.getApplicationContext();
    db = JournalDb.get(c);
    session = new Session(c);
  }

  public String owner() {
    return session.owner();
  }

  public File photoFile(String id) {
    File dir = new File(context.getFilesDir(), "photos");
    dir.mkdirs();
    return new File(dir, id + ".jpg");
  }

  public void enqueue() {
    Constraints net =
        new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    WorkManager wm = WorkManager.getInstance(context);
    wm.enqueueUniqueWork(
        "journal-sync",
        ExistingWorkPolicy.KEEP,
        new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setConstraints(net)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build());
    wm.enqueueUniquePeriodicWork(
        "journal-sync-periodic",
        ExistingPeriodicWorkPolicy.KEEP,
        new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(net)
            .build());
  }

  public void authenticate(String email, String password, String name, boolean register)
      throws Exception {
    synchronized (SYNC_LOCK) {
      JSONObject form = new JSONObject();
      Observation.put(form, "email", email.trim());
      Observation.put(form, "password", password);
      if (register) Observation.put(form, "name", name.trim());
      Api.Response result =
          api.request(
              "/api/auth/" + (register ? "register" : "login"),
              "POST",
              null,
              form.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
              "application/json");
      JSONObject user = result.json().getJSONObject("user");
      if (!result.cookie.startsWith("fieldnotes_session="))
        throw new IOException("The server did not return a sign-in session.");
      session.save(user, result.cookie);
      db.adoptGuest(user.getString("id"));
      enqueue();
    }
  }

  public void signOut() throws Exception {
    synchronized (SYNC_LOCK) {
      JSONObject user = session.get();
      if (user != null)
        try {
          api.json("/api/auth/logout", "POST", user.optString("cookie"), new JSONObject());
        } catch (IOException ignored) {
        }
      session.clear();
    }
  }

  public void deleteAccount(String password) throws Exception {
    synchronized (SYNC_LOCK) {
      JSONObject user = session.get();
      if (user == null) throw new IOException("Sign in before deleting your account.");
      JSONObject form = new JSONObject();
      Observation.put(form, "password", password);
      api.json("/api/account", "DELETE", user.optString("cookie"), form);
      String id = user.optString("id");
      session.clear();
      db.deleteOwner(id);
      db.deleteOwner("draft:" + id);
    }
  }

  public boolean sync() throws Exception {
    synchronized (SYNC_LOCK) {
      String owner = owner();
      boolean retry = false;
      for (Observation record : db.list(owner))
        if (record.hasGps() && record.place().trim().isEmpty())
          try {
            String place =
                place(record.data.optDouble("latitude"), record.data.optDouble("longitude"));
            if (!place.isEmpty())
              db.enrichPlace(
                  record.id(),
                  record.data.optDouble("latitude"),
                  record.data.optDouble("longitude"),
                  place);
            else retry = true;
          } catch (IOException e) {
            retry = true;
          }
      JSONObject account = session.get();
      if (account == null) return !retry;
      String cookie = account.getString("cookie");
      JSONObject me = api.json("/api/auth/me", "GET", cookie, null).optJSONObject("user");
      if (me == null || !me.optString("id").equals(owner))
        throw new Api.Failure(
            401, "Sign in again to resume uploads. Your photos are safe on this phone.");
      for (Observation record : db.list(owner)) {
        try {
          if (record.pending) {
            api.upload(record, cookie);
            if (!db.markUploaded(record.id(), record.revision(), owner)) {
              retry = true;
              continue;
            }
          }
          Observation latest = db.find(record.id());
          if (latest != null
              && !latest.archived()
              && latest.data.optString("analysisState").equals("pending")) {
            JSONObject ai =
                api.json(
                        "/api/observations/" + record.id() + "/identify",
                        "POST",
                        cookie,
                        new JSONObject())
                    .getJSONObject("identification");
            db.mergeAnalysis(record.id(), owner, ai);
          }
        } catch (Api.Failure e) {
          db.error(record.id(), owner, identificationMessage(e));
          if (e.status == 401) throw e;
          if (e.status != 503 && e.status != 400 && e.status != 409) retry = true;
        } catch (IOException e) {
          db.error(record.id(), owner, "Waiting for a connection. Your photo is saved.");
          retry = true;
        }
      }
      JSONArray remote =
          api.json("/api/observations", "GET", cookie, null).getJSONArray("observations");
      for (int i = 0; i < remote.length(); i++) {
        JSONObject data = remote.getJSONObject(i);
        String id = data.getString("id");
        try {
          UUID.fromString(id);
        } catch (IllegalArgumentException e) {
          continue;
        }
        Observation old = db.find(id);
        if (old != null
            && (!old.owner.equals(owner)
                || old.pending
                || old.revision() > data.optInt("revision"))) continue;
        File photo = old == null ? photoFile(id) : old.photo;
        if (!photo.isFile()) {
          byte[] bytes =
              api.request("/api/observations/" + id + "/photo", "GET", cookie, null, null).bytes;
          File temp = new File(photo.getPath() + ".tmp");
          try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(bytes);
            out.getFD().sync();
          }
          if (!temp.renameTo(photo)) throw new IOException("Could not save a downloaded photo.");
        }
        db.mergeRemote(data, owner, photo);
      }
      return !retry;
    }
  }

  public String place(double lat, double lon) throws Exception {
    String cell = String.format(Locale.US, "%.4f,%.4f", lat, lon);
    String cached = db.place(cell);
    if (cached != null) return cached;
    JSONObject result = api.json("/api/place?lat=" + lat + "&lon=" + lon, "GET", null, null);
    String place = result.optString("place");
    db.cachePlace(
        cell, place, System.currentTimeMillis() + (place.isEmpty() ? 60000 : 30L * 86400000));
    Thread.sleep(1100);
    return place;
  }

  public static String identificationMessage(Api.Failure failure) {
    String message = failure.getMessage() == null ? "" : failure.getMessage();
    if (failure.status == 503
        || message.toLowerCase(Locale.ROOT).matches(".*(api|key|quota|billing).*"))
      return "Your photo is saved. Identification will be added when the service is available.";
    return message;
  }

  public int importBackup(byte[] bytes, String owner) throws Exception {
    JSONObject backup = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    if (!"fieldnotes-backup-v1".equals(backup.optString("format")))
      throw new IOException("Choose a My Trail Log journal export.");
    JSONArray rows = backup.getJSONArray("observations");
    int count = 0;
    for (int i = 0; i < rows.length(); i++) {
      JSONObject item = rows.getJSONObject(i);
      String id = item.getString("id");
      UUID.fromString(id);
      Observation old = db.find(id);
      if (old != null && !old.owner.equals(owner)) continue;
      if (old != null && old.revision() >= item.optInt("revision", 1)) continue;
      String photo = item.getString("photo");
      if (!photo.startsWith("data:image/jpeg;base64,"))
        throw new IOException("A backup photo is not a JPEG.");
      byte[] image =
          android.util.Base64.decode(
              photo.substring(photo.indexOf(',') + 1), android.util.Base64.DEFAULT);
      if (image.length > 4 * 1024 * 1024
          || image.length < 10
          || image[0] != (byte) 255
          || image[1] != (byte) 216) throw new IOException("A backup photo is invalid.");
      File file = photoFile(id);
      try (FileOutputStream out = new FileOutputStream(file)) {
        out.write(image);
        out.getFD().sync();
      }
      for (String k : new String[] {"photo", "owner", "syncState", "error"}) item.remove(k);
      db.save(new Observation(item, owner, file, true, ""));
      count++;
    }
    enqueue();
    return count;
  }
}
