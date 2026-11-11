package com.alex.dresscast

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Phone location helpers (no Google Play Services needed). */
object Loc {

    fun hasPerm(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastKnown(c: Context): Location? {
        if (!hasPerm(c)) return null
        val lm = c.getSystemService(LocationManager::class.java)
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Gets a fresh location. The callback runs on the main thread, with null on failure. */
    @SuppressLint("MissingPermission")
    fun current(c: Context, cb: (Location?) -> Unit) {
        val lm = c.getSystemService(LocationManager::class.java)
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null || !hasPerm(c)) {
            cb(lastKnown(c))
            return
        }
        val main = Handler(Looper.getMainLooper())
        val finished = AtomicBoolean(false)
        val timeout = Runnable { if (finished.compareAndSet(false, true)) cb(lastKnown(c)) }
        main.postDelayed(timeout, 20000)
        fun finish(loc: Location?) {
            if (finished.compareAndSet(false, true)) {
                main.removeCallbacks(timeout)
                cb(loc ?: lastKnown(c))
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, c.mainExecutor) { finish(it) }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: Location) = finish(location)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            finish(null)
        }
    }

    /** Turns coordinates into "Town, Country". Call from a background thread. */
    @Suppress("DEPRECATION")
    fun placeName(c: Context, lat: Double, lon: Double): String {
        try {
            if (Geocoder.isPresent()) {
                val a = Geocoder(c, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                val town = a?.locality ?: a?.subAdminArea ?: a?.adminArea
                val country = a?.countryName
                if (town != null) return if (country != null) "$town, $country" else town
            }
        } catch (_: Exception) {
        }
        return String.format(Locale.US, "%.2f, %.2f", lat, lon)
    }

    fun save(p: Prefs, lat: Double, lon: Double, name: String) {
        p.lat = lat.toFloat()
        p.lon = lon.toFloat()
        p.city = name
        p.useGps = true
    }

    /** Used before each forecast: if the phone has moved more than 3 km, update the place. */
    fun updateFromLastKnown(c: Context, p: Prefs) {
        val l = lastKnown(c) ?: return
        val dist = FloatArray(1)
        Location.distanceBetween(p.lat.toDouble(), p.lon.toDouble(), l.latitude, l.longitude, dist)
        if (dist[0] > 3000) save(p, l.latitude, l.longitude, placeName(c, l.latitude, l.longitude))
    }
}
