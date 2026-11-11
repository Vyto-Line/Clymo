package com.alex.dresscast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Phone location helpers (no Google Play Services needed). */
public class Loc {

    public interface Callback { void done(Location loc); }

    public static boolean hasPerm(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public static Location lastKnown(Context c) {
        if (!hasPerm(c)) return null;
        LocationManager lm = c.getSystemService(LocationManager.class);
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) { }
        }
        return best;
    }

    /** Gets a fresh location. The callback runs on the main thread, with null on failure. */
    @SuppressLint("MissingPermission")
    public static void current(Context c, Callback cb) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        String provider = null;
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        if (provider == null || !hasPerm(c)) {
            cb.done(lastKnown(c));
            return;
        }

        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean finished = new AtomicBoolean(false);
        Runnable timeout = () -> {
            if (finished.compareAndSet(false, true)) cb.done(lastKnown(c));
        };
        main.postDelayed(timeout, 20000);

        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, c.getMainExecutor(), loc -> {
                    if (finished.compareAndSet(false, true)) {
                        main.removeCallbacks(timeout);
                        cb.done(loc != null ? loc : lastKnown(c));
                    }
                });
            } else {
                lm.requestSingleUpdate(provider, new LocationListener() {
                    @Override public void onLocationChanged(Location loc) {
                        if (finished.compareAndSet(false, true)) {
                            main.removeCallbacks(timeout);
                            cb.done(loc);
                        }
                    }
                    @Override public void onStatusChanged(String p, int s, Bundle e) { }
                    @Override public void onProviderEnabled(String p) { }
                    @Override public void onProviderDisabled(String p) { }
                }, Looper.getMainLooper());
            }
        } catch (Exception e) {
            if (finished.compareAndSet(false, true)) {
                main.removeCallbacks(timeout);
                cb.done(null);
            }
        }
    }

    /** Turns coordinates into "Town, Country". Call from a background thread. */
    @SuppressWarnings("deprecation")
    public static String placeName(Context c, double lat, double lon) {
        try {
            if (Geocoder.isPresent()) {
                List<Address> list = new Geocoder(c, Locale.getDefault()).getFromLocation(lat, lon, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    String town = a.getLocality();
                    if (town == null) town = a.getSubAdminArea();
                    if (town == null) town = a.getAdminArea();
                    if (town != null) {
                        return a.getCountryName() != null ? town + ", " + a.getCountryName() : town;
                    }
                }
            }
        } catch (Exception ignored) { }
        return String.format(Locale.US, "%.2f, %.2f", lat, lon);
    }

    public static void save(Context c, double lat, double lon, String name) {
        Weather.prefs(c).edit()
                .putFloat("lat", (float) lat)
                .putFloat("lon", (float) lon)
                .putString("city", name)
                .putBoolean("useGps", true)
                .apply();
    }

    /** Used by the morning alarm: if the phone has moved more than 3 km, update the place. */
    public static void updateFromLastKnown(Context c) {
        Location l = lastKnown(c);
        if (l == null) return;
        android.content.SharedPreferences p = Weather.prefs(c);
        float[] dist = new float[1];
        Location.distanceBetween(p.getFloat("lat", 0), p.getFloat("lon", 0),
                l.getLatitude(), l.getLongitude(), dist);
        if (!p.contains("lat") || dist[0] > 3000) {
            save(c, l.getLatitude(), l.getLongitude(), placeName(c, l.getLatitude(), l.getLongitude()));
        }
    }
}
