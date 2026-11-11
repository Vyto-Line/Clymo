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

    fun scheduleAll(ctx: Context) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        schedule(ctx, ACTION_MORNING, 0, nextTime(p.hour, p.minute))
        if (p.evening) schedule(ctx, ACTION_EVENING, 1, nextTime(21, 0))
        else am.cancel(pending(ctx, ACTION_EVENING, 1))
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

    fun show(ctx: Context, fc: Forecast, dayIndex: Int) {
        val d = fc.days.getOrNull(dayIndex) ?: return
        val f = Fmt(fc.fahrenheit)
        val whenWord = if (dayIndex == 0) t("today", "σήμερα") else t("tomorrow", "αύριο")
        val title = d.items.take(2).joinToString(" + ") { it.name } + " " + whenWord
        val start = hh(d.startHour)
        val text = t("${f.t(d.feelStart)} at $start, ${f.t(d.feelMax)} at its warmest.", "${f.t(d.feelStart)} στις $start, έως ${f.t(d.feelMax)}.") +
            (d.rainFrom?.let { t(" Rain from ${hh(it)} (${d.rainPct}%).", " Βροχή από τις ${hh(it)} (${d.rainPct}%).") } ?: "")
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

    fun error(ctx: Context, message: String) = post(
        ctx, 1,
        t("Weather unavailable", "Ο καιρός δεν είναι διαθέσιμος"),
        t("Couldn't get the forecast", "Δεν ήταν δυνατή η λήψη της πρόγνωσης"),
        t("Couldn't get the forecast: ", "Δεν ήταν δυνατή η λήψη της πρόγνωσης: ") + message,
    )

    private fun post(ctx: Context, id: Int, title: String, text: String, big: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t("Daily weather", "Καθημερινός καιρός"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
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
