package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Mood { Mild, Hot, Rainy, Cold }
enum class Garment { Jacket, Shirt, Umbrella, Glasses, Sun, Shorts, Shoe, Scarf }

data class Item(val name: String, val why: String, val icon: Garment)
data class Period(val name: String, val feel: Double, val rain: Int, val code: Int)

data class DayAdvice(
    val date: LocalDate,
    val code: Int,
    val tMin: Double,
    val tMax: Double,
    val feelMin: Double,
    val feelMax: Double,
    /** The real temperature and feels-like right now (today only). */
    val now: Double?,
    val feelNow: Double?,
    /** Feels-like temperature when you head out (your "from" hour). */
    val feelStart: Double,
    val startHour: Int,
    /** When it feels warmest while you're out, and how it feels when you head home. */
    val peakHour: Int,
    val feelEnd: Double,
    val endHour: Int,
    val rainPct: Int,
    val rainFrom: Int?,
    /** Hours (while you're out) with a 50%+ chance of rain. */
    val rainHours: List<Int>,
    val wind: Double,
    val uv: Double,
    val periods: List<Period>,
    val mood: Mood,
    val headline: String,
    val sub: String,
    val items: List<Item>,
    val tag: String,
)

data class Forecast(val city: String, val fahrenheit: Boolean, val days: List<DayAdvice>)

/** Shows a Celsius value in the unit the user picked. */
class Fmt(private val fahrenheit: Boolean) {
    fun t(c: Double): String = (if (fahrenheit) c * 9 / 5 + 32 else c).roundToInt().toString() + "°"
}

fun hh(h: Int) = "%02d:00".format(h)

object Advice {

    fun build(json: JSONObject, p: Prefs): Forecast =
        build(json, p.city, p.shift, p.outFrom, p.outTo, p.fahrenheit, p.off)

    fun build(json: JSONObject, city: String, shift: Double, outFrom: Int, outTo: Int, fahrenheit: Boolean, off: Set<String> = emptySet()): Forecast {
        val h = json.getJSONObject("hourly")
        val d = json.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val from = minOf(outFrom, outTo).coerceIn(0, 23)
        val to = maxOf(outFrom, outTo).coerceIn(0, 23)
        val today = LocalDate.now()
        val feels = Feels.hourly(h)
        val temps = h.optJSONArray("temperature_2m") ?: JSONArray()
        val days = (0 until dates.length()).map { i ->
            day(i, LocalDate.parse(dates.getString(i)), d,
                feels, temps,
                h.getJSONArray("precipitation_probability"),
                h.getJSONArray("weather_code"),
                shift, from, to, Fmt(fahrenheit), off)
        }.filter { !it.date.isBefore(today) }   // an old saved forecast may start yesterday
        return Forecast(city, fahrenheit, days)
    }

    private fun JSONArray.num(i: Int): Double? = if (i < length()) optDouble(i).takeIf { !it.isNaN() } else null
    private fun JSONArray.int(i: Int): Int = if (i < length()) optInt(i, 0) else 0

    private fun day(
        i: Int, date: LocalDate, d: JSONObject,
        hFeel: JSONArray, hTemp: JSONArray, hRain: JSONArray, hCode: JSONArray,
        shift: Double, from: Int, to: Int, f: Fmt, off: Set<String>,
    ): DayAdvice {
        fun dv(key: String, def: Double = 0.0) = d.getJSONArray(key).optDouble(i, def).let { if (it.isNaN()) def else it }
        val code = d.getJSONArray("weather_code").optInt(i, 0)
        val tMax = dv("temperature_2m_max")
        val tMin = dv("temperature_2m_min")
        val wind = dv("wind_speed_10m_max")
        val uv = dv("uv_index_max")
        val rainMm = dv("precipitation_sum")

        // Only the hours you're outside count.
        val base = i * 24
        val outHours = (from..to).toList()
        val feels = outHours.mapNotNull { hFeel.num(base + it) }
        val feelMin = feels.minOrNull() ?: dv("apparent_temperature_min", tMin)
        val feelMax = feels.maxOrNull() ?: dv("apparent_temperature_max", tMax)
        val feelStart = hFeel.num(base + from) ?: feelMin
        val isToday = date == LocalDate.now()
        val nowHour = java.time.LocalTime.now().hour
        val now = if (isToday) hTemp.num(base + nowHour) else null
        val feelNow = if (isToday) hFeel.num(base + nowHour) else null
        val codeNow = if (isToday) hCode.int(base + nowHour) else code
        val rainAhead = isToday && rainHours.any { it > nowHour }
        val peakHour = outHours.maxByOrNull { hFeel.num(base + it) ?: -999.0 } ?: from
        val feelEnd = hFeel.num(base + to) ?: feelMin
        val peak = hh(peakHour)
        val rainPct = outHours.maxOfOrNull { hRain.int(base + it) } ?: 0
        val worst = outHours.maxOfOrNull { hCode.int(base + it) } ?: code
        val rainHours = outHours.filter { hRain.int(base + it) >= 50 }
        val rainFrom = rainHours.firstOrNull()

        val ranges = listOf(7..11, 12..17, 18..22)
        val names = listOf(t("Morning", "Πρωί"), t("Afternoon", "Απόγευμα"), t("Evening", "Βράδυ"))
        val all = ranges.indices.mapNotNull { k ->
            val idx = ranges[k].map { base + it }
            val fs = idx.mapNotNull { hFeel.num(it) }
            if (fs.isEmpty()) null
            else ranges[k] to Period(names[k], fs.average(), idx.maxOf { hRain.int(it) }, idx.maxOf { hCode.int(it) })
        }
        val periods = all.map { it.second }
        // Parts of the day you're actually out in.
        val outPeriods = all.filter { (r, _) -> r.first <= to && r.last >= from }.map { it.second }

        val aMin = feelMin + shift
        val aMax = feelMax + shift
        val avg = (aMin + aMax) / 2
        val start = hh(from)

        val clothes = mutableListOf<Item>()
        when {
            avg < 0 -> {
                clothes += Item(t("Warm coat", "Ζεστό παλτό"), t("Feels ${f.t(feelStart)} at $start", "Αίσθηση ${f.t(feelStart)} στις $start"), Garment.Jacket)
                clothes += Item(t("Sweater", "Πουλόβερ"), t("Stays under ${f.t(feelMax)}", "Μένει κάτω από ${f.t(feelMax)}"), Garment.Shirt)
                if ("scarf" !in off) clothes += Item(t("Scarf + gloves", "Κασκόλ + γάντια"), t("Freezing cold", "Παγωνιά"), Garment.Scarf)
            }
            avg < 7 -> {
                clothes += Item(t("Warm coat", "Ζεστό παλτό"), t("Feels ${f.t(feelStart)} at $start", "Αίσθηση ${f.t(feelStart)} στις $start"), Garment.Jacket)
                clothes += Item(t("Sweater", "Πουλόβερ"), t("Up to ${f.t(feelMax)}", "Έως ${f.t(feelMax)}"), Garment.Shirt)
            }
            avg < 13 -> {
                clothes += Item(t("Jacket", "Μπουφάν"), t("${f.t(feelStart)} at $start", "${f.t(feelStart)} στις $start"), Garment.Jacket)
                clothes += Item(t("Long sleeves", "Μακρυμάνικο"), t("Up to ${f.t(feelMax)}", "Έως ${f.t(feelMax)}"), Garment.Shirt)
            }
            avg < 18 -> {
                clothes += Item(t("Light jacket", "Ελαφρύ μπουφάν"), t("${f.t(feelStart)} at $start", "${f.t(feelStart)} στις $start"), Garment.Jacket)
                clothes += if (aMax >= 18) Item(t("T-shirt", "Κοντομάνικο"), t("${f.t(feelMax)} at $peak", "${f.t(feelMax)} στις $peak"), Garment.Shirt)
                else Item(t("Long sleeves", "Μακρυμάνικο"), t("Up to ${f.t(feelMax)}", "Έως ${f.t(feelMax)}"), Garment.Shirt)
            }
            avg < 24 -> {
                if (aMin < 16) clothes += Item(t("Light jacket", "Ελαφρύ μπουφάν"), t("${f.t(feelStart)} at $start", "${f.t(feelStart)} στις $start"), Garment.Jacket)
                clothes += Item(t("T-shirt", "Κοντομάνικο"), t("${f.t(feelMax)} at $peak", "${f.t(feelMax)} στις $peak"), Garment.Shirt)
            }
            avg < 29 -> {
                clothes += Item(t("T-shirt", "Κοντομάνικο"), t("Up to ${f.t(feelMax)}", "Έως ${f.t(feelMax)}"), Garment.Shirt)
                clothes += Item(t("Shorts", "Σορτς"), t("Warm all day", "Ζέστη όλη μέρα"), Garment.Shorts)
            }
            else -> {
                clothes += Item(t("Linen shirt", "Λινό πουκάμισο"), t("${f.t(feelMax)} at $peak", "${f.t(feelMax)} στις $peak"), Garment.Shirt)
                clothes += Item(t("Shorts", "Σορτς"), t("Hot all day", "Πολλή ζέστη όλη μέρα"), Garment.Shorts)
            }
        }

        val extras = mutableListOf<Item>()
        val snow = isSnow(worst)
        val wetOut = outPeriods.count { it.rain >= 50 }
        // Accessories respect what the user turned off in Settings, with a sensible alternative where there is one.
        fun rainCover(why: String) =
            if ("umbrella" !in off) Item(t("Umbrella", "Ομπρέλα"), why, Garment.Umbrella)
            else Item(t("Hooded rain jacket", "Αδιάβροχο με κουκούλα"), why, Garment.Jacket)
        if (snow) {
            if ("boots" !in off) extras += Item(t("Boots", "Μπότες"), t("Snow expected", "Αναμένεται χιόνι"), Garment.Shoe)
        } else if (rainFrom != null) {
            if (outPeriods.isNotEmpty() && wetOut == outPeriods.size) {
                extras += rainCover(t("$rainPct% rain all day", "$rainPct% βροχή όλη μέρα"))
                if (rainMm >= 3 && "boots" !in off) extras += Item(t("Waterproof shoes", "Αδιάβροχα παπούτσια"), t("${rainMm.roundToInt()} mm of rain", "${rainMm.roundToInt()} mm βροχής"), Garment.Shoe)
            } else {
                extras += rainCover(t("$rainPct% rain from ${hh(rainFrom)}", "$rainPct% βροχή από τις ${hh(rainFrom)}"))
            }
        } else if (rainPct >= 30 && "umbrella" !in off) {
            extras += Item(t("Small umbrella", "Μικρή ομπρέλα"), t("$rainPct% chance of rain", "$rainPct% πιθανότητα βροχής"), Garment.Umbrella)
        }
        if (wind >= 35 && clothes.none { it.icon == Garment.Jacket } && extras.none { it.icon == Garment.Jacket }) {
            extras += Item(t("Windproof layer", "Αντιανεμικό"), t("Wind ${wind.roundToInt()} km/h", "Άνεμος ${wind.roundToInt()} km/h"), Garment.Jacket)
        }
        val uvWhy = t("UV ${uv.roundToInt()} at midday", "UV ${uv.roundToInt()} το μεσημέρι")
        if (uv >= 6) {
            extras += if ("cap" !in off) Item(t("Cap + sunscreen", "Καπέλο + αντηλιακό"), uvWhy, Garment.Sun)
            else Item(t("Sunscreen", "Αντηλιακό"), uvWhy, Garment.Sun)
            if ("glasses" !in off && avg >= 16) extras += Item(t("Sunglasses", "Γυαλιά ηλίου"), uvWhy, Garment.Glasses)
        } else if (uv >= 3 && avg >= 16) {
            if ("glasses" !in off) extras += Item(t("Sunglasses", "Γυαλιά ηλίου"), uvWhy, Garment.Glasses)
            else if ("cap" !in off) extras += Item(t("Cap", "Καπέλο"), t("Shades your eyes · UV ${uv.roundToInt()}", "Σκιάζει τα μάτια · UV ${uv.roundToInt()}"), Garment.Sun)
        }

        val keep = (4 - extras.size).coerceAtLeast(2)
        val items = (clothes.take(keep) + extras).take(4)

        val mood = when {
            snow || avg < 6 -> Mood.Cold
            wetOut >= 2 || rainMm >= 5 -> Mood.Rainy
            aMax >= 28 -> Mood.Hot
            else -> Mood.Mild
        }

        val rainWhen = when {
            rainFrom == null -> null
            rainFrom < 12 -> t("in the morning", "το πρωί")
            rainFrom < 18 -> t("in the afternoon", "το απόγευμα")
            else -> t("in the evening", "το βράδυ")
        }
        // Today: describe the sky right now, and only mention rain if it's still to come.
        val sky = describe(if (isToday) codeNow else code)
        val rainLater = if (isToday) rainAhead else rainWhen != null
        val headline = when (mood) {
            Mood.Rainy -> if (isToday && !rainAhead) sky else t("Rain most of the day", "Βροχή σχεδόν όλη μέρα")
            Mood.Cold -> if (snow) t("Cold, with snow", "Κρύο με χιόνι") else t("Cold", "Κρύο") + ", " + sky.lowercase(L.locale)
            Mood.Hot -> t("Hot", "Ζέστη") + ", " + sky.lowercase(L.locale)
            Mood.Mild -> sky
        } + if (mood != Mood.Rainy && !snow && rainLater) t(", rain later", ", βροχή αργότερα") else ""

        // The summary line is about right now.
        val sub = listOfNotNull(
            feelNow?.let { t("Feels like ${f.t(it)} now", "Αίσθηση ${f.t(it)} τώρα") }
                ?: t("Feels like ${f.t(feelStart)} in the morning", "Αίσθηση ${f.t(feelStart)} το πρωί"),
            if (wind >= 30) t("windy", "αέρας") else null,
        ).joinToString(" · ")

        val tag = if (mood == Mood.Rainy) t("Rain jacket", "Αδιάβροχο") else clothes.first().name

        return DayAdvice(date, code, tMin, tMax, feelMin, feelMax, now, feelNow, feelStart, from, peakHour, feelEnd, to, rainPct, rainFrom, rainHours,
            wind, uv, periods, mood, headline, sub, items, tag)
    }

    fun isSnow(c: Int) = c in 71..77 || c == 85 || c == 86

    fun describe(c: Int): String = when {
        c == 0 -> t("Clear", "Αίθριος")
        c <= 2 -> t("Partly cloudy", "Λίγα σύννεφα")
        c == 3 -> t("Overcast", "Συννεφιά")
        c <= 48 -> t("Foggy", "Ομίχλη")
        c <= 57 -> t("Drizzle", "Ψιχάλα")
        c <= 67 -> t("Rain", "Βροχή")
        c <= 77 -> t("Snow", "Χιόνι")
        c <= 82 -> t("Showers", "Μπόρες")
        c <= 86 -> t("Snow showers", "Χιονοπτώσεις")
        else -> t("Thunderstorms", "Καταιγίδες")
    }
}
