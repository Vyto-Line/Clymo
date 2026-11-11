package com.alex.dresscast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at the chosen time: fetch weather, notify, schedule tomorrow. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context ctx = context.getApplicationContext();
        final PendingResult pending = goAsync();
        Scheduler.scheduleNext(ctx);
        new Thread(() -> {
            try {
                Weather.Result r = null;
                Exception last = null;
                for (int i = 0; i < 3 && r == null; i++) {   // retry if network is still waking up
                    try { r = Weather.fetchToday(ctx); }
                    catch (Exception e) { last = e; Thread.sleep(5000); }
                }
                if (r != null) Scheduler.notify(ctx, r.title, r.outfit, r.body);
                else Scheduler.notify(ctx, "Weather unavailable",
                        "Couldn't get today's forecast",
                        "Couldn't get today's forecast: " + (last != null ? last.getMessage() : ""));
            } catch (InterruptedException ignored) {
            } finally {
                pending.finish();
            }
        }).start();
    }
}
