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
import java.util.Calendar

/** Morning reminder at the chosen time, and the optional 21:00 preview of tomorrow. */
object Scheduler {
    const val ACTION_MORNING = "com.alex.dresscast.MORNING"
    const val ACTION_EVENING = "com.alex.dresscast.EVENING"

    fun scheduleAll(ctx: Context) {
        val p = Prefs(ctx)
        schedule(ctx, ACTION_MORNING, 0, p.hour, p.minute)
        if (p.evening) schedule(ctx, ACTION_EVENING, 1, 21, 0)
        else ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, ACTION_EVENING, 1))
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

    private fun pending(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(ctx: Context, action: String, code: Int, hour: Int, minute: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = nextTime(hour, minute)
        val pi = pending(ctx, action, code)
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

object Notifier {
    private const val CHANNEL = "daily_weather"

    fun show(ctx: Context, fc: Forecast, dayIndex: Int) {
        val d = fc.days.getOrNull(dayIndex) ?: return
        val f = Fmt(fc.fahrenheit)
        val whenWord = if (dayIndex == 0) "today" else "tomorrow"
        val title = d.items.take(2).joinToString(" + ") { it.name } + " " + whenWord
        val text = "${f.t(d.feelAt8)} at 8:00, ${f.t(d.feelMax)} at its warmest." +
            (d.rainFrom?.let { " Rain from %02d:00 (%d%%).".format(it, d.rainPct) } ?: "")
        val periods = d.periods.joinToString("  ·  ") { "${it.name} ${f.t(it.feel)}" }
        val wear = d.items.joinToString(", ") { it.name.lowercase() }
        val big = "$text\n$periods\n\nWear: $wear.\n${fc.city}"
        post(ctx, if (dayIndex == 0) 1 else 2, title, text, big)
    }

    fun error(ctx: Context, message: String) =
        post(ctx, 1, "Weather unavailable", "Couldn't get the forecast", "Couldn't get the forecast: $message")

    private fun post(ctx: Context, id: Int, title: String, text: String, big: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily weather", NotificationManager.IMPORTANCE_HIGH))
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
