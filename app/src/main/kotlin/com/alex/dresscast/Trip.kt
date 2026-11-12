package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

data class Trip(val city: String, val lat: Double, val lon: Double, val from: LocalDate, val to: LocalDate) {
    val days get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    val dates: String get() {
        val f = DateTimeFormatter.ofPattern("EEE d MMM", L.locale)
        return from.format(f).replaceFirstChar { it.uppercase(L.locale) } + " – " + to.format(f) +
            " · " + t("$days days", "$days μέρες")
    }
}

data class TripDay(val date: LocalDate, val tMax: Double, val tMin: Double, val feelMax: Double, val feelMin: Double,
                   val code: Int, val rainPct: Int, val rainMm: Double, val uv: Double)

data class PackItem(val key: String, val name: String, val qty: Int, val why: String)

/** A trip and its packing list, built from the destination's forecast and your wardrobe. */
object Trips {
    fun load(p: Prefs): Trip? = runCatching {
        val o = JSONObject(p.trip)
        Trip(o.getString("city"), o.getDouble("lat"), o.getDouble("lon"), LocalDate.parse(o.getString("from")), LocalDate.parse(o.getString("to")))
    }.getOrNull()?.takeIf { !it.to.isBefore(LocalDate.now()) }

    fun save(p: Prefs, trip: Trip?) {
        p.trip = trip?.let {
            JSONObject().put("city", it.city).put("lat", it.lat).put("lon", it.lon).put("from", it.from.toString()).put("to", it.to.toString()).toString()
        } ?: ""
        p.tripChecked = ""
        p.tripCache = ""
    }

    /** Forecast for the trip's days (Open-Meteo reaches about 16 days ahead). Background thread. */
    fun fetch(p: Prefs, trip: Trip): List<TripDay> {
        val last = LocalDate.now().plusDays(15)
        if (trip.from.isAfter(last)) return emptyList()
        val end = if (trip.to.isAfter(last)) last else trip.to
        val start = if (trip.from.isBefore(LocalDate.now())) LocalDate.now() else trip.from
        val json = Repo.get(String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&timezone=auto" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
                "precipitation_probability_max,precipitation_sum,uv_index_max&start_date=%s&end_date=%s",
            trip.lat, trip.lon, start, end))
        p.tripCache = json
        return parse(json)
    }

    fun cached(p: Prefs): List<TripDay> = runCatching { parse(p.tripCache) }.getOrDefault(emptyList())

    private fun parse(json: String): List<TripDay> {
        val d = JSONObject(json).getJSONObject("daily")
        val time = d.getJSONArray("time")
        fun a(n: String) = d.optJSONArray(n) ?: JSONArray()
        return (0 until time.length()).map { i ->
            TripDay(LocalDate.parse(time.getString(i)), a("temperature_2m_max").optDouble(i), a("temperature_2m_min").optDouble(i),
                a("apparent_temperature_max").optDouble(i), a("apparent_temperature_min").optDouble(i),
                a("weather_code").optInt(i), a("precipitation_probability_max").optInt(i), a("precipitation_sum").optDouble(i, 0.0),
                a("uv_index_max").optDouble(i, 0.0))
        }
    }

    fun dayName(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.SHORT, L.locale).replaceFirstChar { it.uppercase(L.locale) }

    fun summary(days: List<TripDay>, p: Prefs): String {
        if (days.isEmpty()) return ""
        val avg = days.map { (it.feelMax + it.feelMin) / 2 + p.shift }.average()
        val wet = days.count { it.rainPct >= 50 || it.rainMm >= 1 }
        val swing = days.maxOf { it.tMax } - days.minOf { it.tMin }
        val feel = when {
            avg < 5 -> t("Cold", "Κρύο"); avg < 13 -> t("Chilly", "Δροσερό προς κρύο"); avg < 20 -> t("Mild", "Ήπιο")
            avg < 26 -> t("Warm", "Ζεστό"); else -> t("Hot", "Πολύ ζεστό")
        }
        val rain = when (wet) {
            0 -> t(", dry.", ", χωρίς βροχή.")
            1 -> t(", with one rainy day.", ", με μία βροχερή μέρα.")
            else -> t(", with $wet rainy days.", ", με $wet βροχερές μέρες.")
        }
        val layers = if (swing >= 10) t(" Dress in layers.", " Ντύσου σε στρώσεις.") else ""
        return feel + rain + layers
    }

    fun pack(days: List<TripDay>, p: Prefs): List<PackItem> {
        if (days.isEmpty()) return emptyList()
        val off = p.off
        fun has(k: String) = k !in off
        fun name(k: String) = Wardrobe.all.firstOrNull { it.key == k }?.name ?: k
        val out = mutableListOf<PackItem>()
        fun add(k: String, qty: Int, why: String) { if (qty > 0 && has(k)) out += PackItem(k, name(k), qty, why) }
        fun first(vararg ks: String) = ks.firstOrNull { has(it) }
        fun names(list: List<TripDay>) = list.joinToString(", ") { dayName(it.date) }

        val n = days.size
        val avg = days.map { (it.feelMax + it.feelMin) / 2 + p.shift }
        val mins = days.map { it.feelMin + p.shift }
        val coldest = avg.min()

        // Tops: one for each day.
        val warm = avg.count { it >= 20 }
        val cool = n - warm
        val warmTop = first("tshirt", "longsleeve")
        val coolTop = first("longsleeve", "hoodie", "tshirt")
        val tops = mutableMapOf<String, Int>()
        if (warm > 0 && warmTop != null) tops[warmTop] = (tops[warmTop] ?: 0) + warm
        if (cool > 0 && coolTop != null) tops[coolTop] = (tops[coolTop] ?: 0) + cool
        tops.forEach { (k, q) -> add(k, q.coerceAtMost(7), t("One for each day", "Μία για κάθε μέρα")) }

        // Something warm for the evenings.
        val coolEvenings = mins.count { it < 12 }
        when {
            coolEvenings > 0 -> first("sweater", "hoodie", "cardigan")?.let { add(it, minOf(2, coolEvenings), t("For the evenings, ${mins.min().roundToInt()}°", "Για τα βράδια, ${mins.min().roundToInt()}°")) }
            mins.any { it < 18 } -> first("cardigan", "hoodie", "longsleeve")?.let { if (it !in tops) add(it, 1, t("Cool evenings", "Δροσερά βράδια")) }
        }

        // Bottoms.
        val hot = avg.count { it >= 24 }
        if (hot > 0) first("shorts", "linen")?.let { add(it, minOf(hot, 3), t("Warm days", "Για τις ζεστές μέρες")) }
        if (n - hot > 0) first("jeans", "trousers", "linen")?.let { add(it, ceil((n - hot) / 3.0).toInt().coerceIn(1, 2), "") }

        // One coat or jacket, worn on the journey.
        val outer = when {
            coldest < 7 -> first("coat", "winterjacket", "lightjacket")
            coldest < 13 -> first("winterjacket", "coat", "lightjacket")
            coldest < 18 -> first("lightjacket", "cardigan", "windbreaker")
            else -> null
        }
        if (outer != null) add(outer, 1, t("Wear it on the journey", "Φόρεσέ το στο ταξίδι"))

        // Rain.
        val wet = days.filter { it.rainPct >= 50 || it.rainMm >= 1 }
        if (wet.isNotEmpty()) first("umbrella", "raincoat")?.let { add(it, 1, t("Rain: ${names(wet)}", "Βροχή: ${names(wet)}")) }
        val heavy = days.filter { it.rainMm >= 5 }
        if (heavy.isNotEmpty()) first("waterproof", "boots")?.let { add(it, 1, t("Heavy rain: ${names(heavy)}", "Δυνατή βροχή: ${names(heavy)}")) }

        // Shoes.
        if (avg.any { it >= 26 } && has("sandals")) add("sandals", 1, t("Hot days", "Για τις ζεστές μέρες"))
        add("sneakers", 1, t("Every day", "Για κάθε μέρα"))

        // Cold extras.
        if (mins.min() <= 3) {
            val why = t("${mins.min().roundToInt()}° at the coldest", "${mins.min().roundToInt()}° το πιο κρύο")
            add("beanie", 1, why); add("gloves", 1, why); add("scarf", 1, why)
        }
        if (coldest < 0) add("thermal", 1, t("Below zero", "Κάτω από το μηδέν"))

        // Sun.
        val sunny = days.filter { it.uv >= 6 }
        if (sunny.isNotEmpty()) {
            add("sunscreen", 1, t("UV ${sunny.maxOf { it.uv }.roundToInt()}", "UV ${sunny.maxOf { it.uv }.roundToInt()}"))
            add("cap", 1, t("Strong sun", "Δυνατός ήλιος"))
        }
        if (days.any { it.uv >= 3 } && avg.any { it >= 16 }) add("glasses", 1, "")
        return out
    }

    fun checked(p: Prefs): Set<String> = p.tripChecked.split(',').filter { it.isNotBlank() }.toSet()

    fun toggle(p: Prefs, key: String) {
        val c = checked(p)
        p.tripChecked = (if (key in c) c - key else c + key).joinToString(",")
    }

    fun shareText(trip: Trip, items: List<PackItem>): String =
        t("Packing for ${trip.city} (${trip.dates}):\n", "Βαλίτσα για ${trip.city} (${trip.dates}):\n") +
            items.joinToString("\n") { "• ${it.name} ×${it.qty}" + if (it.why.isNotBlank()) " (${it.why})" else "" } +
            "\n\nClymo"

}
