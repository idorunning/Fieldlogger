package com.field.logger.nativeapp;

import android.content.*;
import androidx.work.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** An explicit one-off batch. New photos are never automatically opted into sharing. */
public final class BulkPublishWorker extends Worker {
  public BulkPublishWorker(Context c, WorkerParameters p) {
    super(c, p);
  }

  public static void enqueue(Context c, String owner) {
    WorkManager.getInstance(c)
        .enqueueUniqueWork(
            "bulk-publish-" + owner,
            ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(BulkPublishWorker.class)
                .setInputData(new Data.Builder().putString("owner", owner).build())
                .setConstraints(
                    new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build());
  }

  @Override
  public Result doWork() {
    Repository repo = new Repository(getApplicationContext());
    String owner = getInputData().getString("owner");
    if (owner == null || !owner.equals(repo.owner())) return Result.failure();
    SharedPreferences prefs = TrailPreferences.of(getApplicationContext(), owner);
    try {
      JSONObject job = new JSONObject(prefs.getString("bulkJob", "{}"));
      if (!job.optString("state").equals("running")) return Result.success();
      JSONArray ids = job.getJSONArray("ids");
      JSONObject account = repo.session.get();
      String cookie = account.optString("cookie");
      for (int i = job.optInt("next"); i < ids.length(); i++) {
        if (isStopped()
            || !owner.equals(repo.owner())
            || !new JSONObject(prefs.getString("bulkJob", "{}"))
                .optString("token")
                .equals(job.optString("token"))) return Result.failure();
        Observation record = repo.db.find(ids.getString(i));
        String outcome = "skipped";
        if (record != null && record.owner.equals(owner) && !record.archived()) {
          try {
            if (record.pending) {
              repo.api.upload(record, cookie);
              if (!repo.db.markUploaded(record.id(), record.revision(), owner))
                throw new Api.Failure(409, "Photo changed while uploading.");
            }
            if (isStopped()
                || !new JSONObject(prefs.getString("bulkJob", "{}"))
                    .optString("state")
                    .equals("running")) return Result.failure();
            if (record.data.optString("analysisState").equals("pending")) {
              JSONObject ai =
                  repo.api
                      .json(
                          "/api/observations/" + record.id() + "/identify",
                          "POST",
                          cookie,
                          new JSONObject())
                      .getJSONObject("identification");
              repo.db.mergeAnalysis(record.id(), owner, ai);
            }
            Observation latest = repo.db.find(record.id());
            if (latest == null || latest.archived() || latest.revision() != record.revision())
              throw new Api.Failure(409, "Photo changed while checking.");
            if (isStopped()) return Result.failure();
            JSONObject payload = Observation.copy(job.getJSONObject("payload"));
            Observation.put(payload, "revision", record.revision());
            JSONObject result =
                repo.api.json(
                    "/api/social/photos/" + record.id() + "/publish", "POST", cookie, payload);
            outcome = result.optBoolean("published") ? "published" : "blocked";
            JSONArray invites = result.optJSONArray("invitations");
            if (invites != null) {
              JSONArray all = job.optJSONArray("invitations");
              if (all == null) all = new JSONArray();
              for (int j = 0; j < invites.length(); j++) all.put(invites.getJSONObject(j));
              Observation.put(job, "invitations", all);
            }
          } catch (Api.Failure e) {
            if (e.status == 401) return Result.failure();
            if (e.photoLimit()) {
              repo.db.error(record.id(), owner, e.getMessage());
              Observation.put(job, "state", "paused");
              Observation.put(
                  job,
                  "message",
                  e.getMessage()
                      + " Choose a plan or wait for renewal, then start publishing again.");
              synchronized (BulkPublishWorker.class) {
                JSONObject latest = new JSONObject(prefs.getString("bulkJob", "{}"));
                if (latest.optString("token").equals(job.optString("token"))
                    && latest.optString("state").equals("running"))
                  prefs
                      .edit()
                      .putString("bulkJob", job.toString())
                      .putString("photoAllowanceMessage", e.getMessage())
                      .commit();
              }
              return Result.success();
            }
            if (e.status == 429 || e.status >= 500) return Result.retry();
            outcome = "skipped";
          }
        }
        Observation.put(job, outcome, job.optInt(outcome) + 1);
        Observation.put(job, "next", i + 1);
        Observation.put(job, "updatedAt", Instant.now().toString());
        synchronized (BulkPublishWorker.class) {
          JSONObject latest = new JSONObject(prefs.getString("bulkJob", "{}"));
          if (!latest.optString("token").equals(job.optString("token"))
              || !latest.optString("state").equals("running")) return Result.failure();
          prefs.edit().putString("bulkJob", job.toString()).commit();
        }
        setProgressAsync(
            new Data.Builder().putInt("done", i + 1).putInt("total", ids.length()).build());
      }
      synchronized (BulkPublishWorker.class) {
        JSONObject latest = new JSONObject(prefs.getString("bulkJob", "{}"));
        if (latest.optString("token").equals(job.optString("token"))
            && latest.optString("state").equals("running")) {
          Observation.put(job, "state", "finished");
          prefs.edit().putString("bulkJob", job.toString()).commit();
        }
      }
      repo.enqueue();
      return Result.success();
    } catch (Exception e) {
      return Result.retry();
    }
  }

  public static void cancel(Context c, String owner) {
    synchronized (BulkPublishWorker.class) {
      try {
        SharedPreferences p = TrailPreferences.of(c, owner);
        JSONObject job = new JSONObject(p.getString("bulkJob", "{}"));
        Observation.put(job, "state", "cancelled");
        Observation.put(job, "token", java.util.UUID.randomUUID().toString());
        p.edit().putString("bulkJob", job.toString()).commit();
      } catch (Exception ignored) {
      }
    }
    WorkManager.getInstance(c).cancelUniqueWork("bulk-publish-" + owner);
  }
}
