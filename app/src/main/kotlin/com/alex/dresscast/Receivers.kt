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
        if (action != Scheduler.ACTION_RAIN) Scheduler.scheduleAll(ctx)
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
                    else -> Notifier.show(ctx, result, 0)
                }
                if (result != null) {
                    Scheduler.scheduleRain(ctx, result)
                    Widget.updateAll(ctx, result)
                }
            } catch (_: InterruptedException) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Alarms are cleared on reboot and on app updates, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleAll(context.applicationContext)
    }
}
