package com.field.logger.nativeapp;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import androidx.core.content.ContextCompat;
import java.util.function.Consumer;

/** Requests a short foreground fix per capture; the Android grant is reused automatically. */
public final class LocationCapture {
  private final Context context;
  private final LocationManager manager;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private LocationListener listener;
  private Runnable timeout;

  public LocationCapture(Context c) {
    context = c;
    manager = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
  }

  public boolean granted() {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
  }

  public void start(Consumer<Location> callback) {
    stop();
    if (!granted()) return;
    listener =
        new LocationListener() {
          @Override
          public void onLocationChanged(Location p) {
            if (System.currentTimeMillis() - p.getTime() > 30000) return;
            callback.accept(p);
            if (p.hasAccuracy() && p.getAccuracy() <= 30) stop();
          }

          @Override
          public void onStatusChanged(String p, int status, Bundle extras) {}

          @Override
          public void onProviderEnabled(String p) {}

          @Override
          public void onProviderDisabled(String p) {}
        };
    for (String provider :
        new String[] {LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER})
      try {
        if (manager.isProviderEnabled(provider)) {
          Location last = manager.getLastKnownLocation(provider);
          if (last != null && System.currentTimeMillis() - last.getTime() < 15000)
            callback.accept(last);
          manager.requestLocationUpdates(provider, 1000, 0, listener, Looper.getMainLooper());
        }
      } catch (SecurityException | IllegalArgumentException ignored) {
      }
    timeout = this::stop;
    handler.postDelayed(timeout, 20000);
  }

  public void stop() {
    if (timeout != null) handler.removeCallbacks(timeout);
    if (listener != null) {
      manager.removeUpdates(listener);
      listener = null;
    }
  }
}
