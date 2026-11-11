package com.alex.dresscast

import android.content.Context
import com.alex.dresscast.L.t
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Sends a short summary to the DressCast watch app (Wear OS tiles and complication).
 * Texts are prepared here, in your language and units, so the watch only displays them.
 */
object WatchSync {
    const val PATH = "/dresscast"

    fun send(ctx: Context, fc: Forecast, json: JSONObject, p: Prefs) {
        runCatching {
            val d = fc.days.firstOrNull { it.date == LocalDate.now() } ?: fc.days.firstOrNull() ?: return
            val f = Fmt(fc.fahrenheit)
            val now = d.now ?: d.tMax
            val feel = d.feelNow ?: d.feelStart
            val rain = when {
                d.rainFrom != null && d.rainFrom > LocalTime.now().hour -> t("Rain from ${hh(d.rainFrom)}", "Βροχή από ${hh(d.rainFrom)}")
                d.rainHours.any { it >= LocalTime.now().hour } -> t("Rain now", "Βρέχει τώρα")
                else -> t("Dry until late", "Στεγνά ως αργά")
            }
            val req = PutDataMapRequest.create(PATH)
            val m = req.dataMap
            m.putString("city", fc.city.substringBefore(','))
            m.putString("temp", f.t(now))
            m.putString("feel", t("Feels ", "Αίσθηση ") + f.t(feel) + t(" · High ", " · Μέγ. ") + f.t(d.tMax))
            m.putString("rain", rain)
            m.putString("wet", if (d.rainHours.any { it >= LocalTime.now().hour }) "1" else "")
            m.putString("sky", d.headline)
            m.putString("outfit", d.items.take(2).joinToString(" + ") { it.name })
            m.putString("extra", d.items.drop(1).firstOrNull()?.let { "+ " + it.name.lowercase(L.locale) } ?: "")
            m.putStringArray("icons", d.items.take(3).map { it.icon.name }.toTypedArray())
            m.putString("mainIcon", (d.items.firstOrNull()?.icon ?: Garment.Shirt).name)
            m.putString("today", LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE d", L.locale)))
            // The next of today's plans that hasn't finished yet.
            val h = runCatching { Hourly(json) }.getOrNull()
            val next = Calendars.itemsFor(ctx, p, LocalDate.now()).firstOrNull { it.end.isAfter(LocalTime.now()) }
            if (next != null) {
                val tip = h?.let { PlanAdvice.of(next, it, p) }
                m.putString("planTime", "%02d:%02d".format(next.start.hour, next.start.minute))
                m.putString("planTitle", next.title)
                m.putString("planWeather", tip?.weather ?: "")
                m.putString("planTip", tip?.tip ?: PlanAdvice.whereLabel(next.where))
                m.putString("planIcon", (tip?.icon ?: Garment.Shirt).name)
            } else {
                m.putString("planTime", "")
                m.putString("planTitle", t("No more plans today", "Κανένα άλλο σχέδιο σήμερα"))
                m.putString("planWeather", "")
                m.putString("planTip", "")
                m.putString("planIcon", "")
            }
            m.putString("labelNext", t("Next plan", "Επόμενο σχέδιο"))
            m.putString("labelOpen", t("Open DressCast on your phone", "Άνοιξε το DressCast στο κινητό"))
            m.putLong("at", System.currentTimeMillis())
            Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent())
        }
    }
}
