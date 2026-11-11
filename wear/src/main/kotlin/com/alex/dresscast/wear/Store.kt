package com.alex.dresscast.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataMap

/** The latest summary from the phone, kept on the watch. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("summary", Context.MODE_PRIVATE)

    fun s(key: String) = sp.getString(key, "") ?: ""
    val has get() = sp.getLong("at", 0L) > 0L
    val icons: List<String> get() = s("icons").split(',').filter { it.isNotBlank() }

    fun save(m: DataMap) {
        val e = sp.edit()
        for (k in listOf("city", "temp", "feel", "rain", "sky", "outfit", "extra", "mainIcon", "today",
            "planTime", "planTitle", "planWeather", "planTip", "planIcon", "wet", "labelNext", "labelOpen")) {
            e.putString(k, m.getString(k, ""))
        }
        e.putString("icons", (m.getStringArray("icons") ?: emptyArray()).joinToString(","))
        e.putLong("at", m.getLong("at", System.currentTimeMillis()))
        e.apply()
    }

    companion object {
        /** Redraw the tiles and the complication after new data. */
        fun refreshAll(ctx: Context) {
            runCatching { TileService.getUpdater(ctx).requestUpdate(TodayTile::class.java) }
            runCatching { TileService.getUpdater(ctx).requestUpdate(PlanTile::class.java) }
            runCatching {
                ComplicationDataSourceUpdateRequester.create(ctx, ComponentName(ctx, OutfitComplication::class.java)).requestUpdateAll()
            }
        }

        fun iconRes(name: String): Int = when (name) {
            "Jacket" -> R.drawable.ic_g_jacket
            "Umbrella" -> R.drawable.ic_g_umbrella
            "Glasses" -> R.drawable.ic_g_glasses
            "Sun" -> R.drawable.ic_g_sun
            "Shorts" -> R.drawable.ic_g_shorts
            "Shoe" -> R.drawable.ic_g_shoe
            "Scarf" -> R.drawable.ic_g_scarf
            else -> R.drawable.ic_g_shirt
        }
    }
}
