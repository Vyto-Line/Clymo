package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/** One pollen type on one day: the peak in grains/m³ and a 0–4 level. */
data class PollenInfo(val type: String, val peak: Double, val level: Int, val calmFrom: Int?) {
    val name get() = Pollen.name(type)
    val levelName get() = Pollen.levelName(level)
}

data class PollenDay(val date: LocalDate, val items: List<PollenInfo>) {
    /** The worst of the types you're allergic to. */
    fun worst(mine: Set<String>) = items.filter { it.type in mine }.maxByOrNull { it.level }
}

object Pollen {
    val TYPES = listOf("olive", "grass", "birch", "ragweed", "mugwort", "alder")

    fun name(type: String) = when (type) {
        "olive" -> t("Olive", "Ελιά")
        "grass" -> t("Grass", "Γρασίδι")
        "birch" -> t("Birch", "Σημύδα")
        "ragweed" -> t("Ragweed", "Αμβροσία")
        "mugwort" -> t("Mugwort", "Αρτεμισία")
        "alder" -> t("Alder", "Σκλήθρα")
        else -> type
    }

    /** Genitive for sentences like "High olive pollen" / "Υψηλή γύρη ελιάς". */
    private fun genitive(type: String) = when (type) {
        "olive" -> t("olive", "ελιάς"); "grass" -> t("grass", "γρασιδιού"); "birch" -> t("birch", "σημύδας")
        "ragweed" -> t("ragweed", "αμβροσίας"); "mugwort" -> t("mugwort", "αρτεμισίας"); "alder" -> t("alder", "σκλήθρας")
        else -> type
    }

    fun levelName(level: Int) = when (level) {
        0 -> t("None", "Καμία"); 1 -> t("Low", "Χαμηλή"); 2 -> t("Moderate", "Μέτρια"); 3 -> t("High", "Υψηλή")
        else -> t("Very high", "Πολύ υψηλή")
    }

    fun levelColor(level: Int): Long = when (level) {
        0 -> 0xFF9AA3AE; 1 -> 0xFF2E9E6B; 2 -> 0xFFE0A100; 3 -> 0xFFE8701A; else -> 0xFFD32F2F
    }

    /** grains/m³ thresholds for low / moderate / high / very high (common European bands). */
    private fun bands(type: String) = when (type) {
        "grass" -> doubleArrayOf(1.0, 10.0, 50.0, 150.0)
        "birch" -> doubleArrayOf(1.0, 10.0, 100.0, 500.0)
        "alder" -> doubleArrayOf(1.0, 10.0, 50.0, 200.0)
        "olive" -> doubleArrayOf(1.0, 10.0, 50.0, 200.0)
        "ragweed", "mugwort" -> doubleArrayOf(1.0, 5.0, 20.0, 50.0)
        else -> doubleArrayOf(1.0, 10.0, 50.0, 200.0)
    }

    fun level(type: String, v: Double): Int = bands(type).count { v >= it }

    fun selected(p: Prefs): Set<String> = p.pollenTypes.split(',').filter { it in TYPES }.toSet()

    /** Today's and tomorrow's pollen from the saved forecast; empty outside Europe or if switched off. */
    fun days(json: JSONObject, p: Prefs): List<PollenDay> {
        if (!p.pollenOn) return emptyList()
        val h = json.optJSONObject("pollen") ?: return emptyList()
        val times = h.optJSONArray("time") ?: return emptyList()
        val stamps = (0 until times.length()).map { LocalDateTime.parse(times.getString(it)) }
        val now = LocalDateTime.now()
        return stamps.map { it.toLocalDate() }.distinct().filter { !it.isBefore(LocalDate.now()) }.map { date ->
            val items = TYPES.mapNotNull { type ->
                val a = h.optJSONArray(type + "_pollen") ?: return@mapNotNull null
                val idx = stamps.indices.filter { stamps[it].toLocalDate() == date }
                val raw = idx.map { i -> a.optDouble(i) }
                if (raw.all { it.isNaN() }) return@mapNotNull null      // no data here (outside Europe)
                val vals = raw.map { if (it.isNaN()) 0.0 else it }
                val peak = vals.max()
                val lvl = level(type, peak)
                // From which hour it stays at most "low" for the rest of the day.
                val calm = if (lvl < 3) null else idx.indices.lastOrNull { level(type, vals[it]) >= 2 }
                    ?.let { last -> stamps[idx[last]].hour + 1 }?.takeIf { it in 12..23 && (date != now.toLocalDate() || it > now.hour) }
                PollenInfo(type, peak, lvl, calm)
            }
            PollenDay(date, items)
        }.filter { it.items.isNotEmpty() }
    }

    /** "Good day for allergies" etc. for the card subtitle. */
    fun summary(day: PollenDay, mine: Set<String>): String {
        val w = day.worst(mine) ?: day.items.maxByOrNull { it.level }
        return when (w?.level ?: 0) {
            0, 1 -> t("A good day if you have allergies", "Καλή μέρα για όσους έχουν αλλεργίες")
            2 -> t("Some sneezing possible", "Πιθανά λίγα φτερνίσματα")
            else -> t("Sunglasses help; air out early or late", "Γυαλιά ηλίου βοηθούν· αέρισε νωρίς ή αργά")
        }
    }

    /** Morning alert text when one of your types is high: title and body. */
    fun alert(day: PollenDay, mine: Set<String>): Pair<String, String>? {
        val w = day.worst(mine)?.takeIf { it.level >= 3 } ?: return null
        val title = t("${levelName(w.level)} ${genitive(w.type)} pollen today", "${levelName(w.level)} γύρη ${genitive(w.type)} σήμερα")
        val body = t("Sunglasses help.", "Γυαλιά ηλίου βοηθούν.") +
            (w.calmFrom?.let { t(" Less pollen after ${hh(it)}.", " Λιγότερη γύρη μετά τις ${hh(it)}.") } ?: "")
        return title to body
    }
}
