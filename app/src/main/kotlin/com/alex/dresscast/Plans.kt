package com.alex.dresscast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** A plan you added yourself. days: 0 = Monday … 6 = Sunday (repeating); otherwise a single [date]. */
data class Plan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    val date: LocalDate?,
    val days: Set<Int> = emptySet(),
    /** "out", "in" or "walk" (commute). */
    val where: String = "out",
    val remind: Boolean = true,
    /** "run", "bike", "walk", "dog", "gym", "work" or "" (anything else). */
    val activity: String = "",
) {
    val startHour get() = start.hour
    val endHour get() = if (end.minute > 0) end.hour + 1 else end.hour
    fun occursOn(d: LocalDate) = if (days.isNotEmpty()) (d.dayOfWeek.value - 1) in days else date == d
}

object PlanStore {
    fun load(p: Prefs): List<Plan> = runCatching {
        val a = JSONArray(p.plans)
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            Plan(
                id = o.getString("id"), title = o.getString("title"),
                start = LocalTime.parse(o.getString("start")), end = LocalTime.parse(o.getString("end")),
                date = o.optString("date").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                days = o.optString("days").split(',').mapNotNull { it.toIntOrNull() }.toSet(),
                where = o.optString("where", "out"), remind = o.optBoolean("remind", true),
                activity = o.optString("activity", ""),
            )
        }
    }.getOrDefault(emptyList())

    fun save(p: Prefs, list: List<Plan>) {
        p.plans = JSONArray(list.map {
            JSONObject().put("id", it.id).put("title", it.title).put("start", it.start.toString()).put("end", it.end.toString())
                .put("date", it.date?.toString() ?: "").put("days", it.days.joinToString(",")).put("where", it.where).put("remind", it.remind).put("activity", it.activity)
        }).toString()
    }

    fun upsert(p: Prefs, plan: Plan) = save(p, load(p).filter { it.id != plan.id } + plan)
    fun delete(p: Prefs, id: String) = save(p, load(p).filter { it.id != id })
}

/** A calendar on the phone (Samsung Calendar, Google, Outlook… all live in Android's shared calendar store). */
/** account: where it's stored (Google account, Samsung account, this phone…); accountName: usually an email address. */
data class PhoneCalendar(val id: Long, val name: String, val account: String, val color: Int, val accountName: String = "")

/** Something on your schedule: one of your own plans or a calendar event. */
data class PlanItem(
    val key: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val where: String,
    val source: String,
    val color: Int,
    val plan: Plan? = null,
    val location: String = "",
)

object Calendars {
    fun hasPerm(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Samsung Calendar is only the app: the calendars it shows live in your accounts, most often Google. */
    private fun accountLabel(type: String, name: String): String = when {
        type == "LOCAL" || type.contains("local", true) || name.equals("My calendar", true) -> t("On this phone", "Μόνο στη συσκευή")
        type.contains("osp") || type.contains("samsung", true) -> t("Samsung account", "Λογαριασμός Samsung")
        type == "com.google" -> t("Google account", "Λογαριασμός Google")
        type.contains("outlook", true) || type.contains("exchange", true) || type.contains("microsoft", true) -> t("Outlook account", "Λογαριασμός Outlook")
        else -> name
    }

    fun list(ctx: Context): List<PhoneCalendar> {
        if (!hasPerm(ctx)) return emptyList()
        val out = mutableListOf<PhoneCalendar>()
        runCatching {
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_COLOR),
                "${CalendarContract.Calendars.VISIBLE} = 1", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += PhoneCalendar(c.getLong(0), c.getString(1) ?: "", accountLabel(c.getString(3) ?: "", c.getString(2) ?: ""), c.getInt(4),
                        c.getString(2) ?: "")
                }
            }
        }
        return out
    }

    private val online = Regex("(?i)\\b(online|zoom|teams|meet\\.google|webex|skype)\\b")

    /** Today's and tomorrow's timed events from the calendars you've switched on. */
    fun events(ctx: Context, p: Prefs): List<PlanItem> {
        if (!p.calOn || !hasPerm(ctx)) return emptyList()
        val off = p.calOff.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        val cals = list(ctx).associateBy { it.id }
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.now().plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val out = mutableListOf<PlanItem>()
        runCatching {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, from)
                android.content.ContentUris.appendId(it, to)
            }.build()
            ctx.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.DISPLAY_COLOR),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(4) == 1) continue                     // skip all-day events
                    val calId = c.getLong(6)
                    if (calId in off) continue
                    val location = c.getString(5) ?: ""
                    val title = c.getString(1) ?: ""
                    val isOnline = online.containsMatchIn(location) || online.containsMatchIn(title)
                    if (p.calOnlyOut && (location.isBlank() || isOnline)) continue
                    val b = Instant.ofEpochMilli(c.getLong(2)).atZone(zone)
                    val e = Instant.ofEpochMilli(c.getLong(3)).atZone(zone)
                    out += PlanItem(
                        key = "cal-${c.getLong(0)}-${c.getLong(2)}", title = title.ifBlank { t("Event", "Εκδήλωση") },
                        date = b.toLocalDate(), start = b.toLocalTime(), end = if (e.toLocalDate() == b.toLocalDate()) e.toLocalTime() else LocalTime.of(23, 59),
                        where = if (location.isBlank() || isOnline) "in" else "out",
                        source = t("Calendar", "Ημερολόγιο"), color = c.getInt(7), location = location,
                    )
                }
            }
        }
        return out
    }

    /** Your own plans and calendar events for [date], in time order. */
    fun itemsFor(ctx: Context, p: Prefs, date: LocalDate): List<PlanItem> {
        val own = PlanStore.load(p).filter { it.occursOn(date) }.map {
            PlanItem("plan-${it.id}", it.title, date, it.start, it.end, it.where, t("Yours", "Δικό σου"), 0xFF1F5FC9.toInt(), it)
        }
        val cal = events(ctx, p).filter { it.date == date }
        return (own + cal).sortedBy { it.start }
    }
}

/** Hour-by-hour values from the saved forecast, looked up by date and hour. */
class Hourly(json: JSONObject) {
    private val dates: List<LocalDate>
    private val feel: JSONArray
    private val rain: JSONArray?
    init {
        val d = json.getJSONObject("daily").getJSONArray("time")
        dates = (0 until d.length()).map { LocalDate.parse(d.getString(it)) }
        val h = json.getJSONObject("hourly")
        feel = Feels.hourly(h)
        rain = h.optJSONArray("precipitation_probability")
    }
    private fun idx(date: LocalDate, hour: Int): Int? = dates.indexOf(date).takeIf { it >= 0 }?.let { it * 24 + hour.coerceIn(0, 23) }
    fun feel(date: LocalDate, hour: Int): Double? = idx(date, hour)?.let { i -> feel.optDouble(i).takeIf { !it.isNaN() } }
    fun rain(date: LocalDate, hour: Int): Int = idx(date, hour)?.let { rain?.optInt(it, 0) } ?: 0
}

/** feelsLike: for sports, how warm it will feel once you're moving (null otherwise). */
data class PlanTip(val weather: String, val tip: String, val icon: Garment, val feelsLike: String? = null)

object PlanAdvice {
    /** What the weather does during a plan and what to bring. Inside plans only count the trip there and back. */
    fun of(item: PlanItem, h: Hourly, p: Prefs): PlanTip? {
        val f = Fmt(p.fahrenheit)
        val sh = item.start.hour
        val eh = (if (item.end.minute > 0) item.end.hour + 1 else item.end.hour).coerceIn(sh, 23)
        val hours = if (item.where == "out") (sh..eh).toList() else listOf(sh, eh).distinct()
        val feels = hours.mapNotNull { h.feel(item.date, it) }
        if (feels.isEmpty()) return null
        val start = h.feel(item.date, sh) ?: feels.first()
        val end = h.feel(item.date, eh) ?: feels.last()
        val rain = hours.maxOf { h.rain(item.date, it) }
        val act = item.plan?.activity ?: ""
        if (act == "run" || act == "bike") return sport(act, start, feels.min() + p.shift, rain, f, p)
        val a = feels.min() + p.shift + when (act) { "walk" -> 3; "dog" -> 2; else -> 0 }
        val cover = when {
            a >= 21 -> null
            a >= 18 -> t("Something light at hand", "Κάτι ελαφρύ πρόχειρο")
            a >= 13 -> t("Light jacket", "Ελαφρύ μπουφάν")
            a >= 7 -> t("Jacket", "Μπουφάν")
            else -> t("Warm coat", "Ζεστό παλτό")
        }
        val umbrella = rain >= 40 && "umbrella" !in p.off
        val parts = mutableListOf<String>()
        if (cover != null) parts += cover + when {
            item.where == "in" -> t(" for the trip", " για τη διαδρομή")
            end < start - 1.5 -> t(" for the way back", " για την επιστροφή")
            else -> ""
        }
        if (umbrella) parts += if (parts.isEmpty()) t("Small umbrella", "Μικρή ομπρέλα") else t("small umbrella", "μικρή ομπρέλα")
        val tip = if (parts.isEmpty()) {
            if (item.where == "in") t("Nothing extra needed", "Δεν χρειάζεσαι κάτι επιπλέον") else t("A T-shirt is fine", "Κοντομάνικο αρκεί")
        } else parts.joinToString(t(" and ", " και "))
        val weather = f.t(start) + if (rain >= 20) t(" · rain $rain%", " · βροχή $rain%") else ""
        val icon = if (cover != null) Garment.Jacket else if (umbrella) Garment.Umbrella else Garment.Shirt
        return PlanTip(weather, tip, icon)
    }

    /** Running or cycling: you warm up, so dress for a warmer day than it is. */
    private fun sport(act: String, start: Double, a: Double, rain: Int, f: Fmt, p: Prefs): PlanTip {
        val boost = if (act == "run") 10 else 4
        val eff = a + boost
        val clothes = if (act == "run") when {
            eff >= 18 -> t("T-shirt and shorts", "Κοντομάνικο και σορτς")
            eff >= 10 -> t("Thin long sleeves and shorts", "Λεπτό μακρυμάνικο και σορτς")
            eff >= 3 -> t("Long sleeves and long trousers", "Μακρυμάνικο και μακρύ παντελόνι")
            else -> t("Thermal layer, long trousers and gloves", "Ισοθερμικό, μακρύ παντελόνι και γάντια")
        } else when {
            eff >= 20 -> t("T-shirt", "Κοντομάνικο")
            eff >= 12 -> t("Long sleeves and a windbreaker", "Μακρυμάνικο και αντιανεμικό")
            eff >= 5 -> t("Jacket and gloves", "Μπουφάν και γάντια")
            else -> t("Warm jacket, gloves and a beanie", "Ζεστό μπουφάν, γάντια και σκούφο")
        }
        val extra = when {
            rain >= 40 -> t(" · rain jacket", " · αδιάβροχο")
            act == "run" && a < 15 -> t(" · cardigan for the warm-up", " · ζακέτα για το ζέσταμα")
            else -> ""
        }
        val weather = f.t(start) + if (rain >= 20) t(" · rain $rain%", " · βροχή $rain%") else ""
        val feels = t("You'll feel like ${f.t(eff)}, not ${f.t(a)}", "Θα νιώσεις σαν ${f.t(eff)}, όχι ${f.t(a)}")
        val icon = if (eff >= 18) Garment.Shirt else Garment.Jacket
        return PlanTip(weather, clothes + extra, icon, feels)
    }

    fun activityLabel(a: String) = when (a) {
        "run" -> t("Running", "Τρέξιμο"); "bike" -> t("Cycling", "Ποδήλατο"); "walk" -> t("Walking", "Περπάτημα")
        "dog" -> t("Dog walk", "Βόλτα σκύλου"); "gym" -> t("Gym", "Γυμναστήριο"); "work" -> t("Work", "Δουλειά")
        else -> ""
    }

    fun whereLabel(where: String) = when (where) {
        "in" -> t("Inside", "Μέσα")
        "walk" -> t("Commute", "Διαδρομή")
        else -> t("Outside", "Έξω")
    }

    fun minutesLabel(m: Int) = if (m >= 60) t("${m / 60} h ${m % 60} min", "${m / 60} ώρ. ${m % 60} λεπ.") else t("$m min", "$m′")
}
