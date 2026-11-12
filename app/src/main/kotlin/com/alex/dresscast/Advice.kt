package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

enum class Mood { Mild, Hot, Rainy, Cold }
enum class Garment { Jacket, Shirt, Umbrella, Glasses, Sun, Shorts, Shoe, Scarf, Pants }

/** kind: the wardrobe key (see Wardrobe), used for illustrations and alternatives. */
data class Item(val name: String, val why: String, val icon: Garment, val kind: String = "")
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
        build(json, p.city, p.shift, { p.hoursFor(it) }, p.fahrenheit, p.off)

    fun build(
        json: JSONObject, city: String, shift: Double, hoursFor: (LocalDate) -> Pair<Int, Int>,
        fahrenheit: Boolean, off: Set<String> = emptySet(),
    ): Forecast {
        val h = json.getJSONObject("hourly")
        val d = json.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val today = LocalDate.now()
        val feels = Feels.hourly(h)
        val temps = h.optJSONArray("temperature_2m") ?: JSONArray()
        val days = (0 until dates.length()).map { i ->
            val date = LocalDate.parse(dates.getString(i))
            val (from, to) = hoursFor(date)
            day(i, date, d,
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

        val isToday = date == LocalDate.now()
        val nowHour = java.time.LocalTime.now().hour
        val now = if (isToday) hTemp.num(base + nowHour) else null
        val feelNow = if (isToday) hFeel.num(base + nowHour) else null
        val codeNow = if (isToday) hCode.int(base + nowHour) else code

        // Card texts talk about the present, not other hours.
        val nowText = feelNow?.let { t("Feels like ${f.t(it)} now", "Αίσθηση ${f.t(it)} τώρα") }
        val warm = t("Up to ${f.t(feelMax)} today", "Έως ${f.t(feelMax)} σήμερα")
        val coolLater = isToday && feelEnd <= (feelNow ?: feelMax) - 2 && nowHour < to
        val jacketWhy = when {
            coolLater -> t("Cools down in the evening", "Δροσίζει το βράδυ")
            nowText != null -> nowText
            else -> t("${f.t(feelStart)} in the morning", "${f.t(feelStart)} το πρωί")
        }
        val shirtWhy = nowText ?: warm

        // Only clothes you own (Settings → My wardrobe); each list is in order of preference.
        fun has(k: String) = k !in off
        fun pick(why: String, g: Garment, vararg chain: Pair<String, String>): Item? =
            chain.firstOrNull { has(it.first) }?.let { Item(it.second, why, g, it.first) }
        val coat = "coat" to t("Warm coat", "Ζεστό παλτό")
        val winter = "winterjacket" to t("Winter jacket", "Χειμερινό μπουφάν")
        val jacket = "winterjacket" to t("Jacket", "Μπουφάν")
        val light = "lightjacket" to t("Light jacket", "Ελαφρύ μπουφάν")
        val cardigan = "cardigan" to t("Cardigan", "Ζακέτα")
        val windbreaker = "windbreaker" to t("Windbreaker", "Αντιανεμικό")
        val sweater = "sweater" to t("Sweater", "Πουλόβερ")
        val hoodie = "hoodie" to t("Hoodie", "Φούτερ")
        val longs = "longsleeve" to t("Long sleeves", "Μακρυμάνικο")
        val tee = "tshirt" to t("T-shirt", "Κοντομάνικο")
        val jeans = "jeans" to t("Jeans", "Τζιν")
        val trousers = "trousers" to t("Trousers", "Παντελόνι")
        val linen = "linen" to t("Light trousers", "Λινό παντελόνι")
        val shorts = "shorts" to t("Shorts", "Σορτς")
        val coldLegs = t("Too cold for shorts", "Κρύο για κοντά")
        val coolLegs = t("Cool for shorts", "Δροσερά για κοντά")

        val clothes = mutableListOf<Item>()
        fun add(item: Item?) { if (item != null) clothes += item }
        when {
            avg < 0 -> {
                add(pick(jacketWhy, Garment.Jacket, coat, winter))
                add(pick(t("Stays under ${f.t(feelMax)} today", "Κάτω από ${f.t(feelMax)} όλη μέρα"), Garment.Shirt, sweater, hoodie, longs))
                val warmBits = listOf("scarf" to t("Scarf", "Κασκόλ"), "gloves" to t("gloves", "γάντια"), "beanie" to t("beanie", "σκούφος"))
                    .filter { has(it.first) }
                if (warmBits.isNotEmpty()) clothes += Item(
                    warmBits.take(2).joinToString(" + ") { it.second }.replaceFirstChar { it.uppercase(L.locale) },
                    t("Freezing cold", "Παγωνιά"), Garment.Scarf, warmBits.first().first,
                )
                add(pick(coldLegs, Garment.Pants, jeans, trousers))
                if (has("thermal")) clothes += Item(t("Thermal layer", "Ισοθερμικό"), t("Below zero", "Κάτω από το μηδέν"), Garment.Shirt, "thermal")
            }
            avg < 7 -> {
                add(pick(jacketWhy, Garment.Jacket, coat, winter))
                add(pick(warm, Garment.Shirt, sweater, hoodie, longs))
                add(pick(coldLegs, Garment.Pants, jeans, trousers))
            }
            avg < 13 -> {
                add(pick(jacketWhy, Garment.Jacket, jacket, coat, light))
                add(pick(warm, Garment.Shirt, longs, hoodie, sweater))
                add(pick(coldLegs, Garment.Pants, jeans, trousers))
            }
            avg < 18 -> {
                add(pick(jacketWhy, Garment.Jacket, light, cardigan, windbreaker, jacket))
                add(if (aMax >= 18) pick(shirtWhy, Garment.Shirt, tee, longs) else pick(warm, Garment.Shirt, longs, hoodie, tee))
                add(pick(coolLegs, Garment.Pants, jeans, trousers, linen))
            }
            avg < 24 -> {
                if (aMin < 16) add(pick(jacketWhy, Garment.Jacket, light, cardigan, windbreaker))
                add(pick(shirtWhy, Garment.Shirt, tee, longs))
                add(pick(coolLegs, Garment.Pants, trousers, jeans, linen))
            }
            avg < 29 -> {
                add(pick(warm, Garment.Shirt, tee))
                add(pick(t("Warm all day", "Ζέστη όλη μέρα"), Garment.Shorts, shorts) ?: pick(t("Warm all day", "Ζέστη όλη μέρα"), Garment.Pants, linen, trousers))
            }
            else -> {
                add(pick(shirtWhy, Garment.Shirt, "tshirt" to t("Linen shirt", "Λινό πουκάμισο")))
                add(pick(t("Hot all day", "Πολλή ζέστη όλη μέρα"), Garment.Shorts, shorts) ?: pick(t("Hot all day", "Πολλή ζέστη όλη μέρα"), Garment.Pants, linen))
            }
        }

        val extras = mutableListOf<Item>()
        val snow = isSnow(worst)
        val wetOut = outPeriods.count { it.rain >= 50 }
        fun extra(item: Item?) { if (item != null) extras += item }
        fun rainCover(why: String) = pick(why, Garment.Umbrella, "umbrella" to t("Umbrella", "Ομπρέλα"))
            ?: pick(why, Garment.Jacket, "raincoat" to t("Hooded rain jacket", "Αδιάβροχο με κουκούλα"))
        if (snow) {
            extra(pick(t("Snow expected", "Αναμένεται χιόνι"), Garment.Shoe, "boots" to t("Boots", "Μπότες"), "waterproof" to t("Waterproof shoes", "Αδιάβροχα παπούτσια")))
        } else if (rainFrom != null) {
            if (outPeriods.isNotEmpty() && wetOut == outPeriods.size) {
                extra(rainCover(t("$rainPct% rain all day", "$rainPct% βροχή όλη μέρα")))
                if (rainMm >= 3) extra(pick(t("${rainMm.roundToInt()} mm of rain", "${rainMm.roundToInt()} mm βροχής"), Garment.Shoe,
                    "waterproof" to t("Waterproof shoes", "Αδιάβροχα παπούτσια"), "boots" to t("Boots", "Μποτάκια")))
            } else {
                extra(rainCover(t("$rainPct% rain from ${hh(rainFrom)}", "$rainPct% βροχή από τις ${hh(rainFrom)}")))
            }
        } else if (rainPct >= 30) {
            extra(pick(t("$rainPct% chance of rain", "$rainPct% πιθανότητα βροχής"), Garment.Umbrella, "umbrella" to t("Small umbrella", "Μικρή ομπρέλα")))
        }
        if (wind >= 35 && clothes.none { it.icon == Garment.Jacket } && extras.none { it.icon == Garment.Jacket }) {
            extra(pick(t("Wind ${wind.roundToInt()} km/h", "Άνεμος ${wind.roundToInt()} km/h"), Garment.Jacket,
                "windbreaker" to t("Windproof layer", "Αντιανεμικό"), "raincoat" to t("Rain jacket against the wind", "Αδιάβροχο για τον αέρα")))
        }
        val uvWhy = t("UV ${uv.roundToInt()} at midday", "UV ${uv.roundToInt()} το μεσημέρι")
        if (uv >= 6) {
            when {
                has("cap") && has("sunscreen") -> extras += Item(t("Cap + sunscreen", "Καπέλο + αντηλιακό"), uvWhy, Garment.Sun, "cap")
                has("cap") -> extras += Item(t("Cap", "Καπέλο"), uvWhy, Garment.Sun, "cap")
                has("sunscreen") -> extras += Item(t("Sunscreen", "Αντηλιακό"), uvWhy, Garment.Sun, "sunscreen")
            }
            if (has("glasses") && avg >= 16) extras += Item(t("Sunglasses", "Γυαλιά ηλίου"), uvWhy, Garment.Glasses, "glasses")
        } else if (uv >= 3 && avg >= 16) {
            if (has("glasses")) extras += Item(t("Sunglasses", "Γυαλιά ηλίου"), uvWhy, Garment.Glasses, "glasses")
            else if (has("cap")) extras += Item(t("Cap", "Καπέλο"), t("Shades your eyes · UV ${uv.roundToInt()}", "Σκιάζει τα μάτια · UV ${uv.roundToInt()}"), Garment.Sun, "cap")
        }

        val keep = (4 - extras.size).coerceAtLeast(2)
        val items = (clothes.take(keep) + extras).take(4)
            .ifEmpty { listOf(Item(t("Your usual clothes", "Τα συνηθισμένα σου ρούχα"), warm, Garment.Shirt)) }

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
        val rainAhead = isToday && rainHours.any { it > nowHour }
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

        val tag = if (mood == Mood.Rainy) t("Rain jacket", "Αδιάβροχο") else (clothes.firstOrNull() ?: items.firstOrNull())?.name ?: t("Your usual clothes", "Τα συνηθισμένα σου")

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
