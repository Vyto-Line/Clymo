package com.alex.dresscast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at the reminder time (or 21:00 for the preview): fetch, notify, schedule the next one. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val pending = goAsync()
        Scheduler.scheduleAll(ctx)
        val dayIndex = if (intent.action == Scheduler.ACTION_EVENING) 1 else 0
        Thread {
            try {
                var fc: Forecast? = null
                var last: Exception? = null
                repeat(3) {   // retry while the network wakes up
                    if (fc == null) {
                        try {
                            fc = Repo.load(ctx)
                        } catch (e: Exception) {
                            last = e
                            Thread.sleep(5000)
                        }
                    }
                }
                val result = fc
                if (result != null) Notifier.show(ctx, result, dayIndex)
                else if (dayIndex == 0) Notifier.error(ctx, last?.message ?: "network error")
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
