package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

data class HourInfo(val hour: Int, val temp: Double, val feel: Double, val rain: Int, val code: Int)
data class Reason(val item: Item, val text: String)

/** Everything on the "Today in detail" screen. */
data class Details(
    val hours: List<HourInfo>,
    val humidityNow: Int?,
    val humidityLater: Int?,
    val wind: Double,
    val windDir: Int?,
    val gusts: Double,
    val uv: Double,
    val sunrise: String?,
    val sunset: String?,
    val daylightMin: Int?,
    val rainMm: Double,
    val rainWindow: Pair<Int, Int>?,
    val aqi: Int?,
    val vsYesterday: Double?,
    val windVsYesterday: Double?,
    val reasons: List<Reason>,
)

object DetailsBuilder {

    private fun JSONArray?.num(i: Int): Double? =
        if (this != null && i in 0 until length()) optDouble(i).takeIf { !it.isNaN() } else null

    fun build(json: JSONObject, p: Prefs, today: DayAdvice): Details? = runCatching {
        val d = json.getJSONObject("daily")
        val h = json.getJSONObject("hourly")
        val dates = d.getJSONArray("time")
        val i = (0 until dates.length()).firstOrNull { dates.getString(it) == today.date.toString() } ?: return null
        val base = i * 24
        val temp = h.optJSONArray("temperature_2m")
        val feel = h.optJSONArray("apparent_temperature")
        val rain = h.optJSONArray("precipitation_probability")
        val code = h.optJSONArray("weather_code")
        val hum = h.optJSONArray("relative_humidity_2m")
        fun dv(key: String, at: Int = i) = d.optJSONArray(key).num(at)

        val from = minOf(p.outFrom, p.outTo).coerceIn(0, 23)
        val to = maxOf(p.outFrom, p.outTo).coerceIn(0, 23)
        val range = if (to - from >= 4) from..to else 7..22
        val hours = range.mapNotNull { hr ->
            val f = feel.num(base + hr) ?: return@mapNotNull null
            HourInfo(hr, temp.num(base + hr) ?: f, f, rain.num(base + hr)?.roundToInt() ?: 0, code.num(base + hr)?.toInt() ?: 0)
        }

        val nowHour = if (today.date == LocalDate.now()) LocalTime.now().hour else 8
        val humNow = hum.num(base + nowHour)?.roundToInt()
        val humLater = (nowHour..23).mapNotNull { hum.num(base + it) }.maxOrNull()?.roundToInt()

        val sunrise = d.optJSONArray("sunrise")?.optString(i)?.substringAfter('T')?.takeIf { it.isNotBlank() }
        val sunset = d.optJSONArray("sunset")?.optString(i)?.substringAfter('T')?.takeIf { it.isNotBlank() }
        val daylight = if (sunrise != null && sunset != null) runCatching {
            Duration.between(LocalTime.parse(sunrise), LocalTime.parse(sunset)).toMinutes().toInt()
        }.getOrNull() else null

        val wetHours = (0..23).filter { (rain.num(base + it) ?: 0.0) >= 50 }
        val window = if (wetHours.isEmpty()) null else wetHours.first() to (wetHours.last() + 1).coerceAtMost(24)

        val wind = dv("wind_speed_10m_max") ?: today.wind
        val vsY = if (i > 0) dv("temperature_2m_max", i - 1)?.let { today.tMax - it } else null
        val windVsY = if (i > 0) dv("wind_speed_10m_max", i - 1)?.let { wind - it } else null

        Details(
            hours = hours,
            humidityNow = humNow,
            humidityLater = humLater,
            wind = wind,
            windDir = dv("wind_direction_10m_dominant")?.roundToInt(),
            gusts = dv("wind_gusts_10m_max") ?: wind,
            uv = today.uv,
            sunrise = sunrise,
            sunset = sunset,
            daylightMin = daylight,
            rainMm = dv("precipitation_sum") ?: 0.0,
            rainWindow = window,
            aqi = if (json.has("aqi")) json.optInt("aqi") else null,
            vsYesterday = vsY,
            windVsYesterday = windVsY,
            reasons = reasons(today, hours, temp.num(base + today.startHour), Fmt(p.fahrenheit)),
        )
    }.getOrNull()

    /** One sentence per outfit item explaining the choice. */
    private fun reasons(day: DayAdvice, hours: List<HourInfo>, tempStart: Double?, f: Fmt): List<Reason> {
        val start = hh(day.startHour)
        val warmest = hours.maxByOrNull { it.feel }
        val hasJacket = day.items.any { it.icon == Garment.Jacket }
        return day.items.map { item ->
            val text = when {
                item.icon == Garment.Jacket && tempStart != null && day.wind >= 15 && tempStart - day.feelStart >= 1 ->
                    t("It's ${f.t(tempStart)} at $start, but the ${day.wind.roundToInt()} km/h wind makes it feel like ${f.t(day.feelStart)}.",
                        "Είναι ${f.t(tempStart)} στις $start, αλλά ο αέρας των ${day.wind.roundToInt()} km/h το κάνει να μοιάζει με ${f.t(day.feelStart)}.")
                item.icon == Garment.Jacket ->
                    t("It feels like ${f.t(day.feelStart)} when you head out at $start.", "Θα μοιάζει με ${f.t(day.feelStart)} όταν βγεις στις $start.")
                item.icon == Garment.Shirt && hasJacket && warmest != null ->
                    t("It warms to ${f.t(warmest.feel)} by ${hh(warmest.hour)}, so you can take the jacket off.",
                        "Ζεσταίνει στους ${f.t(warmest.feel)} μέχρι τις ${hh(warmest.hour)}, οπότε μπορείς να βγάλεις το μπουφάν.")
                item.icon == Garment.Umbrella && day.rainFrom != null ->
                    t("${day.rainPct}% chance of rain from ${hh(day.rainFrom)}, while you're usually out.",
                        "${day.rainPct}% πιθανότητα βροχής από τις ${hh(day.rainFrom)}, όσο είσαι συνήθως έξω.")
                else -> item.why + "."
            }
            Reason(item, text)
        }
    }

    // ---- Words for the stat cards

    fun windFrom(deg: Int?): String {
        if (deg == null) return ""
        val k = (((deg % 360) + 22.5) / 45).toInt() % 8
        val en = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        val el = listOf("βόρειος", "βορειοανατολικός", "ανατολικός", "νοτιοανατολικός", "νότιος", "νοτιοδυτικός", "δυτικός", "βορειοδυτικός")
        return t("From the ${en[k]}", el[k].replaceFirstChar { it.uppercase(L.locale) })
    }

    fun uvLevel(uv: Double): String = when {
        uv < 3 -> t("Low", "Χαμηλός")
        uv < 6 -> t("Moderate", "Μέτριος")
        uv < 8 -> t("High", "Υψηλός")
        uv < 11 -> t("Very high", "Πολύ υψηλός")
        else -> t("Extreme", "Ακραίος")
    }

    fun aqiLevel(aqi: Int): Pair<String, String> = when {
        aqi <= 20 -> t("Good", "Καλή") to t("Fine for time outside", "Εντάξει για βόλτα")
        aqi <= 40 -> t("Fair", "Αρκετά καλή") to t("Fine for time outside", "Εντάξει για βόλτα")
        aqi <= 60 -> t("Moderate", "Μέτρια") to t("Sensitive people take care", "Προσοχή για ευαίσθητους")
        aqi <= 80 -> t("Poor", "Κακή") to t("Limit time outside", "Περιόρισε τον χρόνο έξω")
        else -> t("Very poor", "Πολύ κακή") to t("Stay inside if you can", "Μείνε μέσα αν μπορείς")
    }

    fun yesterdayText(d: Details, p: Prefs): String? {
        val diff = d.vsYesterday ?: return null
        val deg = abs(diff).roundToInt()
        val main = when {
            deg == 0 -> t("About as warm as yesterday", "Περίπου όπως χθες")
            diff > 0 -> t("$deg° warmer than yesterday", "$deg° πιο ζεστά από χθες")
            else -> t("$deg° colder than yesterday", "$deg° πιο κρύα από χθες")
        }
        val wind = d.windVsYesterday?.let {
            when {
                it <= -5 -> t(", with less wind", ", με λιγότερο αέρα")
                it >= 5 -> t(", with more wind", ", με περισσότερο αέρα")
                else -> ""
            }
        } ?: ""
        val yesterday = LocalDate.now().minusDays(1).toString()
        val said = if (p.feedbackDay == yesterday) when (p.feedbackLast) {
            -1 -> t(" Yesterday you said you were too cold.", " Χθες είπες ότι κρύωσες.")
            1 -> t(" Yesterday you said you were too warm.", " Χθες είπες ότι ζεστάθηκες.")
            else -> t(" Yesterday you said the outfit was just right.", " Χθες είπες ότι το ντύσιμο ήταν τέλειο.")
        } else ""
        return "$main$wind.$said"
    }
}
