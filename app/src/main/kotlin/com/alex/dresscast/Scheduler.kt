package com.alex.dresscast

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.alex.dresscast.L.t
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

/** Morning reminder, the optional 21:00 preview, and the warning an hour before rain. */
object Scheduler {
    const val ACTION_MORNING = "com.alex.dresscast.MORNING"
    const val ACTION_EVENING = "com.alex.dresscast.EVENING"
    const val ACTION_RAIN = "com.alex.dresscast.RAIN"
    const val ACTION_PLAN = "com.alex.dresscast.PLAN"
    const val ACTION_WARN = "com.alex.dresscast.WARN"
    const val ACTION_SUN = "com.alex.dresscast.SUN"
    const val ACTION_TRIP = "com.alex.dresscast.TRIP"

    fun scheduleAll(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        schedule(ctx, ACTION_MORNING, 0, nextMorning(p))
        if (p.evening) schedule(ctx, ACTION_EVENING, 1, nextTime(21, 0))
        else am.cancel(pending(ctx, ACTION_EVENING, 1))
        // Warnings are checked every few hours; Android may batch these to save battery.
        // RTC (not _WAKEUP): runs the next time the phone is awake anyway, so it never wakes it just for this.
        val exists = PendingIntent.getBroadcast(ctx, 6, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_WARN),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
        val warn = pending(ctx, ACTION_WARN, 6)
        if (!p.warnOn) { am.cancel(warn); warn.cancel() }
        else if (!exists || p.warnWakeV < 1) {
            am.cancel(warn)
            am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR, 3 * AlarmManager.INTERVAL_HOUR, warn)
            p.warnWakeV = 1
        }
        schedulePlans(ctx)
        scheduleTrip(ctx)
        scheduleProfiles(ctx)
    }

    /** Each family profile gets its own morning reminder. */
    fun scheduleProfiles(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val list = Profiles.load(p)
        for (i in 0 until 8) {
            val pr = list.getOrNull(i)
            val action = Profiles.ACTION + (pr?.id ?: "none$i")
            if (pr != null && pr.notify) schedule(ctx, action, 100 + i, nextTime(pr.hour, pr.minute))
            else am.cancel(pending(ctx, action, 100 + i))
        }
    }

    /** The evening before a trip, at 20:00: check your suitcase. */
    fun scheduleTrip(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, ACTION_TRIP, 8)
        val trip = Trips.load(p)
        val at = trip?.takeIf { p.tripRemind }?.let {
            LocalDateTime.of(it.from.minusDays(1), java.time.LocalTime.of(20, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        if (at == null || at < System.currentTimeMillis() + 30_000) am.cancel(pi) else schedule(ctx, ACTION_TRIP, 8, at)
    }

    /** Sunscreen reminder: only on UV 6+ days, shortly before you're out in the strong sun. */
    fun scheduleSun(ctx: Context, json: org.json.JSONObject) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, ACTION_SUN, 7)
        val info = if (p.sunOn && p.sunNotifiedDay != LocalDate.now().toString()) Tips.sun(json, p) else null
        val at = info?.let { LocalDateTime.of(LocalDate.now(), it.remindAt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        if (at == null || at < System.currentTimeMillis() + 30_000) am.cancel(pi) else schedule(ctx, ACTION_SUN, 7, at)
    }

    /** A reminder 30 minutes before the next plan of yours that asks for one. */
    fun schedulePlans(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, ACTION_PLAN, 5)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val next = PlanStore.load(p).filter { it.remind }.flatMap { pl ->
            (0..7).map { LocalDate.now().plusDays(it.toLong()) }.filter { pl.occursOn(it) }
                .map { d -> LocalDateTime.of(d, pl.start).minusMinutes(30).atZone(zone).toInstant().toEpochMilli() }
        }.filter { it > now + 30_000 }.minOrNull()
        if (next == null) am.cancel(pi) else schedule(ctx, ACTION_PLAN, 5, next)
    }

    /** After each new forecast: set a reminder one hour before the next rain while you're out. */
    fun scheduleRain(ctx: Context, fc: Forecast) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, ACTION_RAIN, 2)
        val today = fc.days.firstOrNull()
        if (!p.rainAlert || today == null || today.date != LocalDate.now()) {
            am.cancel(pi)
            return
        }
        val now = System.currentTimeMillis()
        val at = today.rainHours
            .map { LocalDateTime.of(today.date, java.time.LocalTime.of(it, 0)).minusHours(1) }
            .map { it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .firstOrNull { it > now + 60_000 }
        if (at == null) am.cancel(pi) else schedule(ctx, ACTION_RAIN, 2, at)
    }

    /** The next morning reminder, using the weekend time on Saturdays and Sundays if set. */
    fun nextMorning(p: Prefs): Long {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        for (d in 0..7) {
            val date = LocalDate.now().plusDays(d.toLong())
            val weekend = date.dayOfWeek.value >= 6 && p.wkTime
            val at = LocalDateTime.of(date, java.time.LocalTime.of(if (weekend) p.wkHour else p.hour, if (weekend) p.wkMinute else p.minute))
                .atZone(zone).toInstant().toEpochMilli()
            if (at > now) return at
        }
        return nextTime(p.hour, p.minute)
    }

    fun nextTime(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun pending(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(ctx: Context, action: String, code: Int, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, action, code)
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

object Notifier {
    private const val CHANNEL = "daily_weather"

    fun show(ctx: Context, fc: Forecast, dayIndex: Int, tip: String? = null) {
        val d = fc.days.getOrNull(dayIndex) ?: return
        val f = Fmt(fc.fahrenheit)
        val whenWord = if (dayIndex == 0) t("today", "σήμερα") else t("tomorrow", "αύριο")
        val title = d.items.take(2).joinToString(" + ") { it.name } + " " + whenWord
        val start = hh(d.startHour)
        val text = (if (dayIndex == 0 && d.now != null && d.feelNow != null)
                t("${f.t(d.now)} now, feels like ${f.t(d.feelNow)}. High ${f.t(d.tMax)}.", "${f.t(d.now)} τώρα, αίσθηση ${f.t(d.feelNow)}. Μέγιστη ${f.t(d.tMax)}.")
            else t("High ${f.t(d.tMax)}, low ${f.t(d.tMin)}.", "Μέγιστη ${f.t(d.tMax)}, ελάχιστη ${f.t(d.tMin)}.")) +
            (d.rainFrom?.let { t(" Rain from ${hh(it)} (${d.rainPct}%).", " Βροχή από τις ${hh(it)} (${d.rainPct}%).") } ?: "") +
            (tip?.let { " $it." } ?: "")
        val periods = d.periods.joinToString("  ·  ") { "${it.name} ${f.t(it.feel)}" }
        val wear = d.items.joinToString(", ") { it.name.lowercase(L.locale) }
        val big = "$text\n$periods\n\n" + t("Wear: ", "Φόρεσε: ") + "$wear.\n${fc.city}"
        post(ctx, if (dayIndex == 0) 1 else 2, title, text, big)
    }

    fun rain(ctx: Context, d: DayAdvice, hour: Int) {
        val title = t("Rain in about an hour", "Βροχή σε περίπου μία ώρα")
        val text = t("${d.rainPct}% chance from ${hh(hour)}. Take your umbrella.", "${d.rainPct}% πιθανότητα από τις ${hh(hour)}. Πάρε ομπρέλα.")
        post(ctx, 3, title, text, text)
    }

    /** New official warnings: one notification each. */
    fun warnings(ctx: Context, list: List<Warning>) {
        list.take(3).forEachIndexed { i, w ->
            val day = when (w.onset.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()) {
                LocalDate.now() -> if (w.active) t(" now", " τώρα") else t(" today", " για σήμερα")
                LocalDate.now().plusDays(1) -> t(" for tomorrow", " για αύριο")
                else -> ""
            }
            val title = t("${w.levelName} warning$day", "${w.levelName} προειδοποίηση$day")
            val what = w.typeName.replaceFirstChar { it.uppercase(L.locale) }
            val text = "$what" + (if (w.area.isNotBlank()) t(" in ${w.area}", " · ${w.area}") else "") + ", " + w.whenText.lowercase(L.locale) + ". " + w.tip + "."
            post(ctx, 10 + i, title, text, text + if (w.description.isNotBlank()) "\n\n" + w.description else "", "warnings")
        }
    }

    fun plan(ctx: Context, item: PlanItem, tip: PlanTip?) {
        val title = t("In 30 min: ${item.title}", "Σε 30′: ${item.title}")
        val text = if (tip == null) PlanAdvice.whereLabel(item.where) else "${tip.weather}. ${tip.tip}."
        post(ctx, 5, title, text, text, "plans")
    }

    fun pollen(ctx: Context, title: String, text: String) = post(ctx, 6, title, text, text)

    fun profile(ctx: Context, pr: Profile, d: DayAdvice, fahrenheit: Boolean, index: Int) {
        val f = Fmt(fahrenheit)
        val title = pr.name + ": " + d.items.take(2).joinToString(" + ") { it.name.lowercase(L.locale) }
        val text = t("Feels like ${f.t(d.feelStart)} at ${hh(d.startHour)}, high ${f.t(d.tMax)}.", "Αίσθηση ${f.t(d.feelStart)} στις ${hh(d.startHour)}, μέγιστη ${f.t(d.tMax)}.") +
            (d.rainFrom?.let { t(" Rain from ${hh(it)}.", " Βροχή από τις ${hh(it)}.") } ?: "")
        val big = text + "\n" + t("Wear: ", "Φόρεσε: ") + d.items.joinToString(", ") { it.name.lowercase(L.locale) } + "."
        post(ctx, 20 + index, title, text, big)
    }

    fun trip(ctx: Context, trip: Trip, ready: Int, total: Int) {
        val title = t("Tomorrow: ${trip.city}", "Αύριο: ${trip.city}")
        val text = if (total > 0) t("Check your suitcase: $ready of $total packed.", "Έλεγξε τη βαλίτσα: $ready από $total έτοιμα.")
            else t("Check your suitcase.", "Έλεγξε τη βαλίτσα.")
        post(ctx, 8, title, text, text)
    }

    fun sun(ctx: Context, info: SunInfo) {
        val text = t("UV ${info.uv} until ${hh(info.to)} and you're outside. Reapply every two hours.",
            "UV ${info.uv} ως τις ${hh(info.to)} και είσαι έξω. Ξαναβάλε κάθε δύο ώρες.")
        post(ctx, 7, t("Time for sunscreen", "Ώρα για αντηλιακό"), text, text)
    }

    fun update(ctx: Context, r: Release) = post(
        ctx, 4,
        t("Clymo ${r.version} is available", "Διαθέσιμο το Clymo ${r.version}"),
        t("Open the app to update.", "Άνοιξε την εφαρμογή για ενημέρωση."),
        t("Open the app and tap Update.", "Άνοιξε την εφαρμογή και πάτησε Ενημέρωση."),
    )

    fun error(ctx: Context, message: String) = post(
        ctx, 1,
        t("Weather unavailable", "Ο καιρός δεν είναι διαθέσιμος"),
        t("Couldn't get the forecast", "Δεν ήταν δυνατή η λήψη της πρόγνωσης"),
        t("Couldn't get the forecast: ", "Δεν ήταν δυνατή η λήψη της πρόγνωσης: ") + message,
    )

    private fun post(ctx: Context, id: Int, title: String, text: String, big: String, channel: String = CHANNEL) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Daily weather", "Καθημερινός καιρός"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("warnings", t("Weather warnings", "Προειδοποιήσεις καιρού"), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("plans", t("Plan reminders", "Υπενθυμίσεις σχεδίων"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setColor(0xFF3B7DDD.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(big))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
