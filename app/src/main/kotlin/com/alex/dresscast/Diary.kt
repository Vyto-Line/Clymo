package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/** One day: how it felt outside, what you wore and how you found it (-1 cold, 0 just right, 1 warm). */
data class DiaryEntry(val date: LocalDate, val feel: Int, val sky: String, val kinds: List<String>, val answer: Int)

/** Outfit diary: filled in automatically when you answer "How was it?". Stays on the phone. */
object Diary {
    private val outer = listOf("coat", "winterjacket", "lightjacket", "cardigan", "windbreaker", "raincoat")

    fun load(p: Prefs): List<DiaryEntry> = runCatching {
        val a = JSONArray(p.diary)
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            DiaryEntry(LocalDate.parse(o.getString("date")), o.getInt("feel"), o.optString("sky"),
                o.optString("kinds").split(',').filter { it.isNotBlank() }, o.getInt("answer"))
        }.sortedByDescending { it.date }
    }.getOrDefault(emptyList())

    fun record(p: Prefs, d: DayAdvice, answer: Int) {
        val e = DiaryEntry(LocalDate.now(), ((d.feelMin + d.feelMax) / 2).roundToInt(), d.headline.substringBefore(','),
            d.items.map { ShareCard.kindOf(it) }.distinct(), answer)
        val list = (load(p).filter { it.date != e.date } + e).sortedByDescending { it.date }.take(180)
        p.diary = JSONArray(list.map {
            JSONObject().put("date", it.date.toString()).put("feel", it.feel).put("sky", it.sky)
                .put("kinds", it.kinds.joinToString(",")).put("answer", it.answer)
        }).toString()
    }

    private fun with(kind: String): String {
        val name = Wardrobe.all.firstOrNull { it.key == kind }?.name?.lowercase(L.locale) ?: kind
        return t("with a $name", "με $name")
    }

    /** What has worked for you, once there are a few answers. */
    fun insight(list: List<DiaryEntry>): Pair<String, String>? {
        if (list.size < 3) return null
        val bands = list.groupBy { (floor(it.feel / 3.0) * 3).toInt() }.filterValues { it.size >= 3 }
        val best = bands.entries.sortedByDescending { it.value.size }.firstNotNullOfOrNull { (band, days) ->
            val kind = days.flatMap { d -> d.kinds.filter { it in outer }.take(1) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                ?: days.flatMap { it.kinds.take(1) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                ?: return@firstNotNullOfOrNull null
            val withIt = days.filter { kind in it.kinds }
            val good = withIt.count { it.answer == 0 }
            if (withIt.size >= 3 && good * 10 >= withIt.size * 6) Triple(band, kind, good to withIt.size) else null
        } ?: return null
        val (band, kind, counts) = best
        val range = "$band–${band + 2}°"
        val head = t("At $range ${with(kind)} you felt just right ${counts.first} out of ${counts.second} times.",
            "Στους $range ${with(kind)} ένιωσες τέλεια ${counts.first} στις ${counts.second} φορές.")
        val sub = t("That's why on days like these we suggest it.", "Γι' αυτό σου το προτείνουμε τις μέρες με τέτοιο καιρό.")
        return head to sub
    }
}
