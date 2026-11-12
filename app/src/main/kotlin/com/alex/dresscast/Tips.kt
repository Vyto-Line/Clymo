package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** A one-line "good day for…" suggestion. kind: laundry, beach or car. */
data class DayTip(val kind: String, val title: String, val body: String, val short: String)

/** Strong sun while you're outside: UV 6+ between [from] and [to]. */
data class SunInfo(val uv: Int, val from: Int, val to: Int, val remindAt: LocalTime) {
    val level get() = when {
        uv >= 11 -> t("extreme", "ακραίος")
        uv >= 8 -> t("very high", "πολύ υψηλός")
        else -> t("high", "υψηλός")
    }
    val title get() = t("Sunscreen · UV $uv, $level", "Αντηλιακό · UV $uv, $level")

    fun body(willRemind: Boolean): String {
        val window = t("Strong sun ${hh(from)}–${hh(to)}.", "Δυνατός ήλιος ${hh(from)}–${hh(to)}.")
        return if (willRemind) {
            val at = "%02d:%02d".format(remindAt.hour, remindAt.minute)
            window + t(" I'll remind you at $at, since you'll be out.", " Θα σου θυμίσω στις $at, αφού θα είσαι έξω.")
        } else window + t(" Reapply every two hours.", " Ξαναβάλε κάθε δύο ώρες.")
    }
}

object Tips {

    private class Day(json: JSONObject, val date: LocalDate) {
        val d: JSONObject = json.getJSONObject("daily")
        val h: JSONObject = json.getJSONObject("hourly")
        val i: Int = run {
            val a = d.getJSONArray("time")
            (0 until a.length()).firstOrNull { a.getString(it) == date.toString() } ?: -1
        }
        val ok get() = i >= 0
        val days get() = d.getJSONArray("time").length()
        fun hourly(name: String, day: Int, hour: Int): Double =
            h.optJSONArray(name)?.optDouble(day * 24 + hour.coerceIn(0, 23)) ?: Double.NaN
        fun daily(name: String, day: Int = i): Double = d.optJSONArray(name)?.optDouble(day) ?: Double.NaN
        fun sunsetHour(day: Int = i): Int = d.optJSONArray("sunset")?.optString(day)?.substringAfter('T')
            ?.let { runCatching { LocalTime.parse(it).hour }.getOrNull() } ?: 18
        /** Highest chance of rain between two hours of a day. */
        fun rainMax(day: Int, from: Int, to: Int): Int =
            (from..to).maxOfOrNull { hourly("precipitation_probability", day, it).let { v -> if (v.isNaN()) 0 else v.roundToInt() } } ?: 0
    }

    /** The day's tips, in order of usefulness. Only those you've switched on and that apply today. */
    fun today(json: JSONObject, p: Prefs): List<DayTip> {
        val day = runCatching { Day(json, LocalDate.now()) }.getOrNull()?.takeIf { it.ok } ?: return emptyList()
        return listOfNotNull(
            if (p.tipBeach) runCatching { beach(json, day, p) }.getOrNull() else null,
            if (p.tipLaundry) runCatching { laundry(day) }.getOrNull() else null,
            if (p.tipCar) runCatching { car(json, day) }.getOrNull() else null,
        )
    }

    private fun laundry(day: Day): DayTip? {
        val nowH = LocalTime.now().hour
        val start = maxOf(nowH, 8)
        val sunset = day.sunsetHour()
        if (sunset - start < 3) return null
        // Dry until the first likely-rain hour, or sunset.
        val end = (start..sunset).firstOrNull {
            day.hourly("precipitation_probability", day.i, it) >= 30 || day.hourly("weather_code", day.i, it) >= 51
        } ?: sunset
        if (end - start < 3) return null
        val hours = start until end
        val hum = hours.map { day.hourly("relative_humidity_2m", day.i, it) }.filter { !it.isNaN() }.average()
        val wind = hours.map { day.hourly("wind_speed_10m", day.i, it) }.filter { !it.isNaN() }.average()
        val sunny = hours.count { day.hourly("weather_code", day.i, it) <= 2 }
        val temp = hours.map { day.hourly("temperature_2m", day.i, it) }.filter { !it.isNaN() }.average()
        if (hum.isNaN() || hum >= 75 || (sunny < 2 && wind < 10)) return null
        val body = when {
            temp >= 28 && hum < 50 -> t("Dries in about an hour. Hang dark clothes in the shade.", "Στεγνώνουν σε περίπου μία ώρα. Άπλωσε στη σκιά τα σκούρα.")
            sunny >= 2 && wind >= 10 -> t("Sun and a light breeze until ${hh(end)}. Bring it in before the evening damp.",
                "Ήλιος και ελαφρύς αέρας ως τις ${hh(end)}. Μάζεψέ τα πριν πέσει η υγρασία το βράδυ.")
            sunny >= 2 -> t("Dry and sunny until ${hh(end)}.", "Στεγνά και ήλιος ως τις ${hh(end)}.")
            else -> t("Dry with a breeze until ${hh(end)}.", "Στεγνά με αεράκι ως τις ${hh(end)}.")
        }
        return DayTip("laundry", t("Hanging out the laundry", "Άπλωμα ρούχων"), body, t("Good day to hang laundry", "Καλή μέρα για άπλωμα"))
    }

    private fun beach(json: JSONObject, day: Day, p: Prefs): DayTip? {
        if (LocalDate.now().monthValue !in 5..10) return null
        val m = json.optJSONObject("marine") ?: return null
        val tMax = day.daily("temperature_2m_max")
        val wind = day.daily("wind_speed_10m_max")
        if (tMax.isNaN() || tMax < 26 || wind >= 30) return null
        if (day.rainMax(day.i, 10, 18) >= 30) return null
        val wave = m.optJSONObject("daily")?.optJSONArray("wave_height_max")?.optDouble(0) ?: Double.NaN
        val seaArr: JSONArray = m.optJSONObject("hourly")?.optJSONArray("sea_surface_temperature") ?: return null
        val sea = seaArr.optDouble(12)
        if (wave.isNaN() || sea.isNaN() || wave > 0.8 || sea < 21) return null
        val f = Fmt(p.fahrenheit)
        val breeze = when {
            wind < 12 -> t("calm", "άπνοια")
            wind < 20 -> t("light breeze", "ελαφρύ αεράκι")
            else -> t("some wind", "λίγος αέρας")
        }
        val waves = String.format(L.locale, "%.1f", wave)
        val uv = day.daily("uv_index_max")
        val best = if (!uv.isNaN() && uv >= 8) t(" Best before 12:00 or after 17:00.", " Καλύτερα πριν τις 12:00 ή μετά τις 17:00.") else ""
        return DayTip("beach", t("Beach", "Θάλασσα"),
            t("Water ${f.t(sea)}, waves $waves m, $breeze.", "Νερό ${f.t(sea)}, κύμα $waves m, $breeze.") + best,
            t("Good day for the beach", "Καλή μέρα για θάλασσα"))
    }

    private val elDays = listOf("τη Δευτέρα", "την Τρίτη", "την Τετάρτη", "την Πέμπτη", "την Παρασκευή", "το Σάββατο", "την Κυριακή")
    private val enDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    private fun car(json: JSONObject, day: Day): DayTip? {
        val nowH = LocalTime.now().hour
        if (day.rainMax(day.i, nowH, 22) >= 30) return null              // it's wet today anyway
        fun wet(di: Int) = di < day.days && (day.daily("precipitation_sum", di).let { !it.isNaN() && it >= 1.0 } || day.rainMax(di, 6, 22) >= 60)
        val rainDay = listOf(day.i + 1, day.i + 2).firstOrNull { wet(it) }
        val title = t("Skip the car wash", "Άσε το πλύσιμο αυτοκινήτου")
        if (rainDay != null) {
            val from = (6..22).firstOrNull { day.hourly("precipitation_probability", rainDay, it) >= 50 }
            val whenText = if (rainDay == day.i + 1) t("tomorrow", "αύριο") else t("the day after tomorrow", "μεθαύριο")
            val first = t("Rain $whenText", "Βροχή $whenText") + (from?.let { t(" from ${hh(it)}", " από τις ${hh(it)}") } ?: "") + "."
            val good = ((rainDay + 1) until day.days).firstOrNull { !wet(it) }
            val after = good?.let {
                val dow = LocalDate.now().plusDays((it - day.i).toLong()).dayOfWeek.value - 1
                t(" Better on ${enDays[dow]}.", " Καλύτερα ${elDays[dow]}.")
            } ?: ""
            return DayTip("car", title, first + after, title)
        }
        // Saharan dust in the next two days (dust in μg/m³ from the air-quality forecast).
        val dust = json.optJSONObject("pollen")?.optJSONArray("dust") ?: return null
        val times = json.optJSONObject("pollen")?.optJSONArray("time") ?: return null
        val now = LocalDateTime.now()
        var peak = 0.0
        for (k in 0 until minOf(dust.length(), times.length())) {
            val at = runCatching { LocalDateTime.parse(times.getString(k)) }.getOrNull() ?: continue
            if (at.isAfter(now) && at.isBefore(now.plusHours(48))) peak = maxOf(peak, dust.optDouble(k).takeIf { !it.isNaN() } ?: 0.0)
        }
        if (peak < 100) return null
        return DayTip("car", title, t("Saharan dust on the way in the next two days.", "Αφρικανική σκόνη τις επόμενες δύο μέρες."), title)
    }

    /** Strong sun today while you're out, or null. */
    fun sun(json: JSONObject, p: Prefs): SunInfo? {
        val day = runCatching { Day(json, LocalDate.now()) }.getOrNull()?.takeIf { it.ok } ?: return null
        val (from, to) = p.hoursFor(LocalDate.now())
        val uvHours = (0..23).filter { day.hourly("uv_index", day.i, it).let { v -> !v.isNaN() && v >= 6 } }
        val maxUv = day.daily("uv_index_max")
        val (uvFrom, uvTo) = when {
            uvHours.isNotEmpty() -> uvHours.first() to uvHours.last() + 1
            !maxUv.isNaN() && maxUv >= 6 -> 11 to 16                       // older saved forecasts have no hourly UV
            else -> return null
        }
        val start = maxOf(uvFrom, from)
        val end = minOf(uvTo, to + 1)
        if (start >= end) return null                                      // not outside while the sun is strong
        val uv = (if (maxUv.isNaN()) 6.0 else maxUv).roundToInt()
        return SunInfo(uv, uvFrom, uvTo, LocalTime.of(start, 0).minusMinutes(15))
    }
}
