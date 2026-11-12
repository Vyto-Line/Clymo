package com.alex.dresscast

import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Someone else you dress in the morning (a child, a partner): their own feel for the cold,
 * hours outside, wardrobe and reminder time. Same place and weather as you.
 */
data class Profile(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val color: Int = 0,
    /** -1 runs cold, 0 normal, 1 runs warm. */
    val comfort: Int = 0,
    val from: Int = 8,
    val to: Int = 14,
    /** Wardrobe keys they don't have, comma-separated (like Prefs.noItems). */
    val noItems: String = "",
    val notify: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 15,
) {
    val off: Set<String> get() = noItems.split(',').filter { it.isNotBlank() }.toSet()
    /** Lower = dress warmer. About 2° per step. */
    val shift: Double get() = comfort * 2.0
    val initial: String get() = name.trim().take(1).uppercase(L.locale).ifBlank { "?" }
}

object Profiles {
    val colors = listOf(0xFFE2574C.toInt(), 0xFF0F766E.toInt(), 0xFF7C3AED.toInt(), 0xFFB7791F.toInt())
    const val ACTION = "com.alex.dresscast.PROFILE:"

    fun load(p: Prefs): List<Profile> = runCatching {
        val a = JSONArray(p.profiles)
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            Profile(o.getString("id"), o.getString("name"), o.optInt("color"), o.optInt("comfort"), o.optInt("from", 8), o.optInt("to", 14),
                o.optString("off"), o.optBoolean("notify", true), o.optInt("hour", 7), o.optInt("minute", 15))
        }
    }.getOrDefault(emptyList())

    private fun save(p: Prefs, list: List<Profile>) {
        p.profiles = JSONArray(list.map {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("comfort", it.comfort).put("from", it.from)
                .put("to", it.to).put("off", it.noItems).put("notify", it.notify).put("hour", it.hour).put("minute", it.minute)
        }).toString()
    }

    fun upsert(p: Prefs, pr: Profile) {
        val list = load(p)
        save(p, if (list.any { it.id == pr.id }) list.map { if (it.id == pr.id) pr else it } else list + pr)
    }

    fun delete(p: Prefs, id: String) = save(p, load(p).filter { it.id != id })

    /** Their outfit for the forecast you already have. */
    fun forecast(json: JSONObject, p: Prefs, pr: Profile): Forecast =
        Advice.build(json, p.city, pr.shift, { pr.from.coerceIn(0, 23) to pr.to.coerceIn(pr.from.coerceIn(0, 23), 23) }, p.fahrenheit, pr.off)

    fun hours(pr: Profile) = "%02d:00–%02d:00".format(pr.from, pr.to)

    fun comfortLabel(c: Int) = when (c) {
        -1 -> t("Runs cold", "Κρυώνει")
        1 -> t("Runs warm", "Ζεσταίνεται")
        else -> t("Normal", "Κανονικά")
    }
}
