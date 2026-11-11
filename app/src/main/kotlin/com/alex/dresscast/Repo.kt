package com.alex.dresscast

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val lat: Double, val lon: Double)

/** Talks to Open-Meteo (free, no account or API key). Call from a background thread. */
object Repo {

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
        return JSONObject(get(url))
    }

    fun load(ctx: Context, p: Prefs = Prefs(ctx)): Forecast {
        val json = fetchJson(ctx, p)
        return Advice.build(json, p.city, p.comfort, p.fahrenheit)
    }

    fun geocode(name: String): Place {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" +
            URLEncoder.encode(name.trim(), "UTF-8")
        val results = JSONObject(get(url)).optJSONArray("results")
        if (results == null || results.length() == 0) throw Exception("City not found: $name")
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
