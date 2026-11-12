package com.alex.dresscast

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val lat: Double, val lon: Double, val region: String = "")
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
                "&hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code,relative_humidity_2m,wind_speed_10m,uv_index" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
                "apparent_temperature_max,apparent_temperature_min," +
                "precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant," +
                "uv_index_max,sunrise,sunset" +
                "&timezone=auto&forecast_days=7&past_days=1",
            p.lat, p.lon,
        )
        val json = JSONObject(get(url))
        // Air quality comes from Open-Meteo's companion service; skip it quietly if it fails.
        runCatching {
            val aq = JSONObject(get(String.format(Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=european_aqi", p.lat, p.lon)))
            json.put("aqi", aq.getJSONObject("current").getInt("european_aqi"))
        }
        // Air quality extras (Europe, Copernicus via Open-Meteo): pollen for the pollen card, dust for the car-wash tip.
        val aqVars = (if (p.pollenOn) Pollen.TYPES.map { it + "_pollen" } else emptyList()) + (if (p.tipCar) listOf("dust") else emptyList())
        if (aqVars.isNotEmpty()) runCatching {
            val aq = JSONObject(get(String.format(Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f" +
                    "&hourly=${aqVars.joinToString(",")}&forecast_days=3&timezone=auto", p.lat, p.lon)))
            json.put("pollen", aq.getJSONObject("hourly"))
        }
        // Sea conditions for the beach tip (no data inland).
        if (p.tipBeach) runCatching {
            val m = JSONObject(get(String.format(Locale.US,
                "https://marine-api.open-meteo.com/v1/marine?latitude=%.4f&longitude=%.4f" +
                    "&hourly=sea_surface_temperature&daily=wave_height_max&forecast_days=2&timezone=auto", p.lat, p.lon)))
            json.put("marine", m)
        }
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

    /** Up to five places matching what's typed, for the search suggestions. */
    fun suggest(query: String): List<Place> {
        if (query.trim().length < 2) return emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=5&language=" +
            (if (L.greek) "el" else "en") + "&name=" + URLEncoder.encode(query.trim(), "UTF-8")
        val results = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { results.getJSONObject(it) }.map { r ->
            val country = r.optString("country", "")
            val region = listOf(r.optString("admin1", ""), country).filter { it.isNotBlank() }.distinct().joinToString(", ")
            Place(r.getString("name") + if (country.isEmpty()) "" else ", $country", r.getDouble("latitude"), r.getDouble("longitude"), region)
        }
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

    internal fun get(url: String): String {
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
