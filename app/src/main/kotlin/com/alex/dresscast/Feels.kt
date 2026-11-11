package com.alex.dresscast

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * "Feels like", calculated the way most weather apps do it:
 * wind chill when it's cold, heat index when it's hot and humid, and otherwise just the real temperature.
 * (Open-Meteo's own apparent temperature takes several degrees off for wind and humidity even on mild days.)
 */
object Feels {

    fun of(t: Double, windKmh: Double?, humidity: Double?): Double {
        val v = windKmh ?: 0.0
        if (t <= 10.0 && v > 4.8) {
            // Wind chill (Environment Canada / US National Weather Service), °C and km/h.
            val p = v.pow(0.16)
            return min(t, 13.12 + 0.6215 * t - 11.37 * p + 0.3965 * t * p)
        }
        if (t >= 27.0 && humidity != null && humidity >= 40.0) {
            // Heat index (Rothfusz), worked out in °F.
            val f = t * 9 / 5 + 32
            val r = humidity
            val hi = -42.379 + 2.04901523 * f + 10.14333127 * r - 0.22475541 * f * r -
                0.00683783 * f * f - 0.05481717 * r * r + 0.00122874 * f * f * r +
                0.00085282 * f * r * r - 0.00000199 * f * f * r * r
            return max(t, (hi - 32) * 5 / 9)
        }
        return t
    }

    /** Feels-like for every hour. Falls back to Open-Meteo's value for forecasts saved by older versions. */
    fun hourly(h: JSONObject): JSONArray {
        val temp = h.optJSONArray("temperature_2m") ?: return h.optJSONArray("apparent_temperature") ?: JSONArray()
        val wind = h.optJSONArray("wind_speed_10m")
        val hum = h.optJSONArray("relative_humidity_2m")
        if (wind == null) return h.optJSONArray("apparent_temperature") ?: temp
        val out = JSONArray()
        for (i in 0 until temp.length()) {
            val t = temp.optDouble(i)
            if (t.isNaN()) out.put(JSONObject.NULL)
            else out.put(of(t, wind.optDouble(i).takeIf { !it.isNaN() }, hum?.optDouble(i)?.takeIf { !it.isNaN() }))
        }
        return out
    }
}
