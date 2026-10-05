package com.field.logger.nativeapp;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;

public final class SyncWorker extends Worker {
  public SyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
    super(context, parameters);
  }

  @NonNull
  @Override
  public Result doWork() {
    try {
      return new Repository(getApplicationContext()).sync() ? Result.success() : Result.retry();
    } catch (Api.Failure e) {
      return e.status == 401 ? Result.failure() : Result.retry();
    } catch (Exception e) {
      return Result.retry();
    }
  }
}
