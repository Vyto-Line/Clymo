package com.alex.dresscast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

/** Fires for the morning reminder, the 21:00 preview and the rain warning. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val pending = goAsync()
        val action = intent.action
        if (action == Scheduler.ACTION_MORNING || action == Scheduler.ACTION_EVENING || action == null) Scheduler.scheduleAll(ctx)
        if (action == Scheduler.ACTION_PLAN || action == Scheduler.ACTION_WARN || action == Scheduler.ACTION_SUN || action == Scheduler.ACTION_TRIP) {
            Thread {
                try {
                    val p = Prefs(ctx)
                    L.use(p)
                    when (action) {
                        Scheduler.ACTION_PLAN -> planReminder(ctx, p)
                        Scheduler.ACTION_SUN -> sunReminder(ctx, p)
                        Scheduler.ACTION_TRIP -> Trips.load(p)?.let { trip ->
                            val days = runCatching { Trips.fetch(p, trip) }.getOrNull() ?: Trips.cached(p)
                            val items = Trips.pack(days, p)
                            Notifier.trip(ctx, trip, items.count { it.key in Trips.checked(p) }, items.size)
                        }
                        else -> warnings(ctx, p)
                    }
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }.start()
            return
        }
        Thread {
            try {
                val p = Prefs(ctx)
                L.use(p)
                // Try to download a few times while the network wakes up, then fall back to the saved forecast.
                var json: org.json.JSONObject? = null
                for (attempt in 0 until 3) {
                    json = runCatching { Repo.fetchJson(ctx, p) }.getOrNull()
                    if (json != null) break
                    Thread.sleep(5000)
                }
                val source = json ?: Repo.cached(ctx)?.json
                val result = source?.let { runCatching { Advice.build(it, p) }.getOrNull() }
                when {
                    result == null -> if (action != Scheduler.ACTION_RAIN && action != Scheduler.ACTION_EVENING) Notifier.error(ctx, "no internet")
                    action == Scheduler.ACTION_RAIN -> {
                        val today = result.days.firstOrNull()
                        val now = LocalTime.now().hour
                        val soon = today?.rainHours?.firstOrNull { it in (now + 1)..(now + 2) }
                        if (today != null && soon != null) Notifier.rain(ctx, today, soon)
                    }
                    action == Scheduler.ACTION_EVENING -> Notifier.show(ctx, result, 1)
                    else -> Notifier.show(ctx, result, 0,
                        source?.let { j -> runCatching { Tips.today(j, p).firstOrNull()?.short }.getOrNull() })
                }
                if (result != null && source != null) {
                    Scheduler.scheduleSun(ctx, source)
                    Scheduler.scheduleRain(ctx, result)
                    Widget.updateAll(ctx, result)
                    // High pollen for the types you're allergic to: once, with the morning reminder.
                    if (action == Scheduler.ACTION_MORNING && p.pollenNotifiedDay != java.time.LocalDate.now().toString()) {
                        Pollen.days(source, p).firstOrNull { it.date == java.time.LocalDate.now() }
                            ?.let { Pollen.alert(it, Pollen.selected(p)) }
                            ?.let { (title, text) ->
                                Notifier.pollen(ctx, title, text)
                                p.pollenNotifiedDay = java.time.LocalDate.now().toString()
                            }
                    }
                }
                if (action != Scheduler.ACTION_RAIN) warnings(ctx, p)
                // Once a morning, mention a new version (only once per version).
                if (action == null || action == Scheduler.ACTION_MORNING) {
                    runCatching { Updater.newer(ctx) }.getOrNull()?.let { r ->
                        if (p.updateNotified != r.version) {
                            Notifier.update(ctx, r)
                            p.updateNotified = r.version
                        }
                    }
                }
            } catch (_: InterruptedException) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Sunscreen: once a day, only if the sun is still strong while you're out. */
private fun sunReminder(ctx: Context, p: Prefs) {
    val today = java.time.LocalDate.now().toString()
    if (!p.sunOn || p.sunNotifiedDay == today) return
    val json = Repo.cached(ctx)?.json ?: return
    val info = Tips.sun(json, p) ?: return
    if (java.time.LocalTime.now().hour >= info.to) return
    Notifier.sun(ctx, info)
    p.sunNotifiedDay = today
}

/** Downloads warnings and notifies the new ones. */
private fun warnings(ctx: Context, p: Prefs) {
    if (!p.warnOn) return
    val list = runCatching { Warnings.fetch(ctx, p) }.getOrNull() ?: return
    Notifier.warnings(ctx, Warnings.fresh(p, list))
}

/** 30 minutes before a plan: what the weather does and what to take. Then set the next reminder. */
private fun planReminder(ctx: Context, p: Prefs) {
    val now = java.time.LocalDateTime.now()
    val item = Calendars.itemsFor(ctx, p, now.toLocalDate()).filter { it.plan?.remind == true }
        .firstOrNull { val m = java.time.Duration.between(now.toLocalTime(), it.start).toMinutes(); m in 0..45 }
    if (item != null) {
        val json = runCatching { Repo.fetchJson(ctx, p) }.getOrNull() ?: Repo.cached(ctx)?.json
        val tip = json?.let { runCatching { PlanAdvice.of(item, Hourly(it), p) }.getOrNull() }
        Notifier.plan(ctx, item, tip)
    }
    Scheduler.schedulePlans(ctx)
}

/** Alarms are cleared on reboot and on app updates, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleAll(context.applicationContext)
    }
}
