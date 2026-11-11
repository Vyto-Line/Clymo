package com.alex.dresscast

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
    val feelAt8: Double,
    val rainPct: Int,
    val rainFrom: Int?,
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

object Advice {

    fun build(json: JSONObject, city: String, comfort: Int, fahrenheit: Boolean): Forecast {
        val h = json.getJSONObject("hourly")
        val d = json.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            day(i, LocalDate.parse(dates.getString(i)), d,
                h.getJSONArray("apparent_temperature"),
                h.getJSONArray("precipitation_probability"),
                h.getJSONArray("weather_code"),
                comfort, Fmt(fahrenheit))
        }
        return Forecast(city, fahrenheit, days)
    }

    private fun JSONArray.num(i: Int): Double? = if (i < length()) optDouble(i).takeIf { !it.isNaN() } else null
    private fun JSONArray.int(i: Int): Int = if (i < length()) optInt(i, 0) else 0

    private fun day(
        i: Int, date: LocalDate, d: JSONObject,
        hFeel: JSONArray, hRain: JSONArray, hCode: JSONArray,
        comfort: Int, f: Fmt,
    ): DayAdvice {
        fun dv(key: String, def: Double = 0.0) = d.getJSONArray(key).optDouble(i, def).let { if (it.isNaN()) def else it }
        val code = d.getJSONArray("weather_code").optInt(i, 0)
        val tMax = dv("temperature_2m_max")
        val tMin = dv("temperature_2m_min")
        val wind = dv("wind_speed_10m_max")
        val uv = dv("uv_index_max")
        val rainMm = dv("precipitation_sum")

        // Daytime hours (07:00–22:00) decide the outfit, not the night.
        val base = i * 24
        val hours = (7..22).map { base + it }
        val feels = hours.mapNotNull { hFeel.num(it) }
        val feelMin = feels.minOrNull() ?: dv("apparent_temperature_min", tMin)
        val feelMax = feels.maxOrNull() ?: dv("apparent_temperature_max", tMax)
        val feelAt8 = hFeel.num(base + 8) ?: feelMin
        val rainPct = hours.maxOfOrNull { hRain.int(it) } ?: 0
        val worst = hours.maxOfOrNull { hCode.int(it) } ?: code
        val rainFrom = (7..22).firstOrNull { hRain.int(base + it) >= 50 }

        val periods = listOf("Morning" to 7..11, "Afternoon" to 12..17, "Evening" to 18..22).mapNotNull { (name, range) ->
            val idx = range.map { base + it }
            val fs = idx.mapNotNull { hFeel.num(it) }
            if (fs.isEmpty()) null
            else Period(name, fs.average(), idx.maxOf { hRain.int(it) }, idx.maxOf { hCode.int(it) })
        }

        // "I run cold" makes the day count as colder, so warmer clothes come sooner.
        val shift = comfort * 1.5
        val aMin = feelMin + shift
        val aMax = feelMax + shift
        val avg = (aMin + aMax) / 2

        val clothes = mutableListOf<Item>()
        when {
            avg < 0 -> {
                clothes += Item("Warm coat", "Feels ${f.t(feelAt8)} at 8:00", Garment.Jacket)
                clothes += Item("Sweater", "Stays under ${f.t(feelMax)}", Garment.Shirt)
                clothes += Item("Scarf + gloves", "Freezing cold", Garment.Scarf)
            }
            avg < 7 -> {
                clothes += Item("Warm coat", "Feels ${f.t(feelAt8)} at 8:00", Garment.Jacket)
                clothes += Item("Sweater", "Up to ${f.t(feelMax)}", Garment.Shirt)
            }
            avg < 13 -> {
                clothes += Item("Jacket", "${f.t(feelAt8)} this morning", Garment.Jacket)
                clothes += Item("Long sleeves", "Up to ${f.t(feelMax)}", Garment.Shirt)
            }
            avg < 18 -> {
                clothes += Item("Light jacket", "${f.t(feelAt8)} this morning", Garment.Jacket)
                clothes += if (aMax >= 18) Item("T-shirt", "${f.t(feelMax)} at its warmest", Garment.Shirt)
                else Item("Long sleeves", "Up to ${f.t(feelMax)}", Garment.Shirt)
            }
            avg < 24 -> {
                if (aMin < 16) clothes += Item("Light jacket", "${f.t(feelAt8)} this morning", Garment.Jacket)
                clothes += Item("T-shirt", "${f.t(feelMax)} at its warmest", Garment.Shirt)
            }
            avg < 29 -> {
                clothes += Item("T-shirt", "Up to ${f.t(feelMax)}", Garment.Shirt)
                clothes += Item("Shorts", "Warm all day", Garment.Shorts)
            }
            else -> {
                clothes += Item("Linen shirt", "${f.t(feelMax)} at its hottest", Garment.Shirt)
                clothes += Item("Shorts", "Hot all day", Garment.Shorts)
            }
        }

        val extras = mutableListOf<Item>()
        val snow = isSnow(worst)
        val wetPeriods = periods.count { it.rain >= 50 }
        if (snow) {
            extras += Item("Boots", "Snow expected", Garment.Shoe)
        } else if (rainFrom != null) {
            if (periods.isNotEmpty() && wetPeriods == periods.size) {
                extras += Item("Umbrella", "$rainPct% rain all day", Garment.Umbrella)
                if (rainMm >= 3) extras += Item("Waterproof shoes", "${rainMm.roundToInt()} mm of rain", Garment.Shoe)
            } else {
                extras += Item("Umbrella", "$rainPct% rain from " + "%02d:00".format(rainFrom), Garment.Umbrella)
            }
        } else if (rainPct >= 30) {
            extras += Item("Small umbrella", "$rainPct% chance of rain", Garment.Umbrella)
        }
        if (wind >= 35 && clothes.none { it.icon == Garment.Jacket }) {
            extras += Item("Windproof layer", "Wind ${wind.roundToInt()} km/h", Garment.Jacket)
        }
        if (uv >= 6) extras += Item("Cap + sunscreen", "UV ${uv.roundToInt()} at midday", Garment.Sun)
        else if (uv >= 3 && avg >= 16) extras += Item("Sunglasses", "UV ${uv.roundToInt()} at midday", Garment.Glasses)

        val keep = (4 - extras.size).coerceAtLeast(2)
        val items = (clothes.take(keep) + extras).take(4)

        val mood = when {
            snow || avg < 6 -> Mood.Cold
            wetPeriods >= 2 || rainMm >= 5 -> Mood.Rainy
            aMax >= 28 -> Mood.Hot
            else -> Mood.Mild
        }

        val rainWhen = when {
            rainFrom == null -> null
            rainFrom < 12 -> "in the morning"
            rainFrom < 18 -> "in the afternoon"
            else -> "in the evening"
        }
        val sky = describe(code)
        val headline = when (mood) {
            Mood.Rainy -> "Rain most of the day"
            Mood.Cold -> if (snow) "Cold, with snow" else "Cold, ${sky.lowercase()}"
            Mood.Hot -> "Hot, ${sky.lowercase()}"
            Mood.Mild -> sky
        } + if (mood != Mood.Rainy && !snow && rainWhen != null) ", rain $rainWhen" else ""

        val sub = listOfNotNull(
            "${f.t(feelAt8)} at 8:00",
            "${f.t(feelMax)} at its warmest",
            rainFrom?.let { "rain from %02d:00".format(it) },
            if (wind >= 30) "windy" else null,
        ).joinToString(" · ")

        val tag = if (mood == Mood.Rainy) "Rain jacket" else clothes.first().name

        return DayAdvice(date, code, tMin, tMax, feelMin, feelMax, feelAt8, rainPct, rainFrom, wind, uv,
            periods, mood, headline, sub, items, tag)
    }

    fun isSnow(c: Int) = c in 71..77 || c == 85 || c == 86

    fun describe(c: Int): String = when {
        c == 0 -> "Clear"
        c <= 2 -> "Partly cloudy"
        c == 3 -> "Overcast"
        c <= 48 -> "Foggy"
        c <= 57 -> "Drizzle"
        c <= 67 -> "Rain"
        c <= 77 -> "Snow"
        c <= 82 -> "Showers"
        c <= 86 -> "Snow showers"
        else -> "Thunderstorms"
    }
}
