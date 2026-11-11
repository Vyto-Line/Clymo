package com.alex.dresscast

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val lat: Double, val lon: Double)
data class Cached(val json: JSONObject, val fetchedAt: Long)

/** Talks to Open-Meteo (free, no account or API key) and keeps the last forecast for offline use. */
object Repo {

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "forecast.json")

    /** Downloads a fresh forecast and saves it. Call from a background thread. */
    fun fetchJson(ctx: Context, p: Prefs = Prefs(ctx)): JSONObject {
        if (p.useGps) runCatching { Loc.updateFromLastKnown(ctx, p) }
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=apparent_temperature,precipitation_probability,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
                "apparent_temperature_max,apparent_temperature_min," +
                "precipitation_sum,wind_speed_10m_max,uv_index_max" +
                "&timezone=auto&forecast_days=7",
            p.lat, p.lon,
        )
        val json = JSONObject(get(url))
        runCatching {
            cacheFile(ctx).writeText(JSONObject().put("fetchedAt", System.currentTimeMillis()).put("data", json).toString())
        }
        return json
    }

    /** The last saved forecast, if any. */
    fun cached(ctx: Context): Cached? = runCatching {
        val o = JSONObject(cacheFile(ctx).readText())
        Cached(o.getJSONObject("data"), o.getLong("fetchedAt"))
    }.getOrNull()

    /** Fresh forecast if possible, otherwise the saved one. Null only if neither works. */
    fun load(ctx: Context, p: Prefs = Prefs(ctx)): Forecast? {
        L.use(p)
        val json = runCatching { fetchJson(ctx, p) }.getOrNull() ?: cached(ctx)?.json ?: return null
        return runCatching { Advice.build(json, p) }.getOrNull()
    }

    fun geocode(name: String): Place {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=" +
            (if (L.greek) "el" else "en") + "&name=" + URLEncoder.encode(name.trim(), "UTF-8")
        val results = JSONObject(get(url)).optJSONArray("results")
        if (results == null || results.length() == 0) throw Exception(L.t("City not found: $name", "Δεν βρέθηκε η πόλη: $name"))
        val r = results.getJSONObject(0)
        val country = r.optString("country", "")
        val label = r.getString("name") + if (country.isEmpty()) "" else ", $country"
        return Place(label, r.getDouble("latitude"), r.getDouble("longitude"))
    }

    private fun get(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 15000
        try {
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }
}
