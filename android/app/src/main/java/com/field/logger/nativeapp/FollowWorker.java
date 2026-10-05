package com.field.logger.nativeapp;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.*;
import com.field.logger.CommunityActivity;
import com.field.logger.R;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

/**
 * Android schedules periodic checks; only currently accessible publications can generate an alert.
 */
public final class FollowWorker extends Worker {
  public FollowWorker(Context c, WorkerParameters p) {
    super(c, p);
  }

  public static void schedule(Context c) {
    Constraints net =
        new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    WorkManager.getInstance(c)
        .enqueueUniquePeriodicWork(
            "follow-notifications",
            ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(FollowWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(net)
                .build());
    WorkManager.getInstance(c)
        .enqueueUniqueWork(
            "follow-notifications-now",
            ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(FollowWorker.class).setConstraints(net).build());
  }

  @Override
  public Result doWork() {
    Repository repo = new Repository(getApplicationContext());
    JSONObject account = repo.session.get();
    if (account == null) return Result.success();
    String owner = repo.owner();
    SharedPreferences prefs = TrailPreferences.of(getApplicationContext(), owner);
    if (!prefs.getBoolean("notifications", false)) return Result.success();
    if (!NotificationManagerCompat.from(getApplicationContext()).areNotificationsEnabled())
      return Result.success();
    if (Build.VERSION.SDK_INT >= 33
        && ContextCompat.checkSelfPermission(
                getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return Result.success();
    try {
      String since = prefs.getString("noticeSince", Instant.now().toString()),
          at = "&lat=" + prefs.getFloat("mapLat", 54) + "&lon=" + prefs.getFloat("mapLon", -2);
      Set<String> seen = new HashSet<>(prefs.getStringSet("notifiedIds", Collections.emptySet()));
      String cutoff = Instant.now().toString();
      int offset = 0;
      do {
        JSONObject feed =
            repo.api.json(
                "/api/social/feed?scope=following&since="
                    + android.net.Uri.encode(since)
                    + at
                    + "&offset="
                    + offset,
                "GET",
                account.optString("cookie"),
                null);
        JSONArray photos = feed.getJSONArray("photos");
        for (int i = 0; i < photos.length(); i++) {
          if (isStopped() || !owner.equals(repo.owner())) return Result.failure();
          JSONObject p = photos.getJSONObject(i);
          String id = p.optString("id");
          if (p.optBoolean("own") || seen.contains(id)) continue;
          notifyPhoto(getApplicationContext(), p, at);
          seen.add(id);
        }
        if (!feed.optBoolean("hasMore")) break;
        offset = feed.optInt("nextOffset", offset + 100);
      } while (offset < 10000 && !isStopped());
      if (!isStopped()) {
        if (seen.size() > 2000) seen = new HashSet<>(seen.stream().limit(2000).toList());
        prefs.edit().putString("noticeSince", cutoff).putStringSet("notifiedIds", seen).apply();
      }
      return Result.success();
    } catch (Api.Failure e) {
      return e.status == 401 ? Result.failure() : Result.retry();
    } catch (Exception e) {
      return Result.retry();
    }
  }

  private void notifyPhoto(Context c, JSONObject p, String at) {
    NotificationManager manager =
        (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    if (Build.VERSION.SDK_INT >= 26)
      manager.createNotificationChannel(
          new NotificationChannel(
              "trail-following", "Followed explorers", NotificationManager.IMPORTANCE_DEFAULT));
    Intent intent =
        new Intent(c, CommunityActivity.class)
            .putExtra("mode", "notification")
            .putExtra("id", p.optString("id"))
            .putExtra("at", at);
    PendingIntent open =
        PendingIntent.getActivity(
            c,
            p.optString("id").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    JSONObject author = p.optJSONObject("author");
    String username = author == null ? "an explorer" : author.optString("username");
    NotificationCompat.Builder n =
        new NotificationCompat.Builder(c, "trail-following")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("A new discovery from @" + username)
            .setContentText(p.optString("name", "A moment outdoors"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("trail-following");
    try {
      NotificationManagerCompat.from(c).notify(p.optString("id").hashCode(), n.build());
    } catch (SecurityException ignored) {
    }
  }
}
