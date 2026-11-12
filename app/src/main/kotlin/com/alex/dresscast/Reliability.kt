package com.alex.dresscast

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.alex.dresscast.L.t

/** Things on the phone that can stop the morning reminder from arriving, each with a fix. */
data class Issue(val title: String, val text: String, val button: String, val intent: Intent)

object Reliability {

    @SuppressLint("BatteryLife")
    fun issues(ctx: Context): List<Issue> {
        val pkg = ctx.packageName
        val list = mutableListOf<Issue>()

        if (!ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            list += Issue(
                t("Notifications are off", "Οι ειδοποιήσεις είναι απενεργοποιημένες"),
                t("Clymo can't send your morning outfit.", "Το Clymo δεν μπορεί να σου στείλει το πρωινό ντύσιμο."),
                t("Turn on", "Ενεργοποίηση"),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
            )
        }
        if (!Scheduler.canExact(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Issue(
                t("Reminders may be late", "Οι υπενθυμίσεις μπορεί να αργούν"),
                t("Allow alarms so the reminder comes on time.", "Επίτρεψε τα ξυπνητήρια για να έρχεται στην ώρα της."),
                t("Allow", "Να επιτρέπεται"),
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")),
            )
        }
        if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)) {
            list += Issue(
                t("Battery saver may block reminders", "Η εξοικονόμηση μπαταρίας μπορεί να τις μπλοκάρει"),
                t("Let Clymo run in the background. It only wakes up a few times a day.", "Άφησε το Clymo να τρέχει στο παρασκήνιο. Ξυπνά μόνο λίγες φορές τη μέρα."),
                t("Allow", "Να επιτρέπεται"),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
            )
        }
        return list
    }
}
