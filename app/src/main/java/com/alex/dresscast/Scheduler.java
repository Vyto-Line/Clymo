package com.alex.dresscast;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** Schedules the daily alarm (at the time you picked) and shows the notification. */
public class Scheduler {

    static final String CHANNEL = "daily_weather";

    @SuppressLint("ScheduleExactAlarm")
    public static long scheduleNext(Context ctx) {
        SharedPreferences p = Weather.prefs(ctx);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, p.getInt("hour", 8));
        c.set(Calendar.MINUTE, p.getInt("minute", 0));
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exactOk) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pi);
        }
        return c.getTimeInMillis();
    }

    public static void notify(Context ctx, String title, String shortText, String body) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Daily weather", NotificationManager.IMPORTANCE_HIGH));

        PendingIntent open = PendingIntent.getActivity(ctx, 1,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setColor(0xFF3B7DDD)
                .setContentTitle(title)
                .setContentText(shortText)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(1, n);
    }
}
