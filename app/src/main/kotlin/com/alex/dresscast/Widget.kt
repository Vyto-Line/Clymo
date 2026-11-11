package com.alex.dresscast

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.alex.dresscast.L.t

/** Home screen widget: today's temperature and what to wear, in the day's mood colour. */
class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val ctx = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                val p = Prefs(ctx)
                L.use(p)
                // Use the saved forecast if it's fresh (under 2 hours), otherwise download.
                val c = Repo.cached(ctx)
                val fc = if (c != null && System.currentTimeMillis() - c.fetchedAt < 7_200_000)
                    runCatching { Advice.build(c.json, p) }.getOrNull()
                else Repo.load(ctx, p)
                Widget.updateAll(ctx, fc)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object Widget {
    fun updateAll(ctx: Context, fc: Forecast?) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = build(ctx, fc)
        ids.forEach { mgr.updateAppWidget(it, views) }
    }

    private fun build(ctx: Context, fc: Forecast?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget)
        val open = PendingIntent.getActivity(ctx, 5, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        v.setOnClickPendingIntent(R.id.widget_root, open)
        val d = fc?.days?.firstOrNull()
        if (fc == null || d == null) {
            v.setTextViewText(R.id.widget_city, "DressCast")
            v.setTextViewText(R.id.widget_temp, "–")
            v.setTextViewText(R.id.widget_headline, "")
            v.setTextViewText(R.id.widget_outfit, t("Open the app to load the weather", "Άνοιξε την εφαρμογή για τον καιρό"))
            v.setTextViewText(R.id.widget_detail, "")
            return v
        }
        val f = Fmt(fc.fahrenheit)
        val (bg, ink) = when (d.mood) {
            Mood.Mild -> R.drawable.widget_bg_mild to 0xFF0E1A2B.toInt()
            Mood.Hot -> R.drawable.widget_bg_hot to 0xFF2A1606.toInt()
            Mood.Rainy -> R.drawable.widget_bg_rainy to 0xFFFFFFFF.toInt()
            Mood.Cold -> R.drawable.widget_bg_cold to 0xFF0E1A2B.toInt()
        }
        v.setInt(R.id.widget_root, "setBackgroundResource", bg)
        listOf(R.id.widget_city, R.id.widget_temp, R.id.widget_headline, R.id.widget_outfit, R.id.widget_detail)
            .forEach { v.setTextColor(it, ink) }
        v.setTextViewText(R.id.widget_city, fc.city.substringBefore(","))
        v.setTextViewText(R.id.widget_temp, f.t(d.tMax))
        v.setTextViewText(R.id.widget_headline, d.headline)
        v.setTextViewText(R.id.widget_outfit, d.items.joinToString(" · ") { it.name })
        v.setTextViewText(R.id.widget_detail, d.sub)
        return v
    }
}
