package com.alex.dresscast

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import com.alex.dresscast.L.t
import com.alex.dresscast.ui.palette
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Ticked-off outfit items, shared by the app and the checklist widget (today only). */
object Checks {
    fun get(p: Prefs): Set<Int> =
        if (p.checkedDay == LocalDate.now().toString()) p.checked.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        else emptySet()

    fun toggle(p: Prefs, i: Int) {
        val now = get(p).toMutableSet()
        if (!now.add(i)) now.remove(i)
        p.checkedDay = LocalDate.now().toString()
        p.checked = now.joinToString(",")
    }
}

/** Runs [work] off the main thread with the saved (or a fresh) forecast, then updates every widget. */
private fun refreshInBackground(context: Context, receiver: android.content.BroadcastReceiver, work: (Prefs) -> Unit = {}) {
    val ctx = context.applicationContext
    val pending = receiver.goAsync()
    Thread {
        try {
            val p = Prefs(ctx)
            L.use(p)
            work(p)
            val c = Repo.cached(ctx)
            // The saved forecast has every hour, so "now" stays right without a download for up to 12 hours.
            val fc = if (c != null && System.currentTimeMillis() - c.fetchedAt < 12 * 3_600_000L)
                runCatching { Advice.build(c.json, p) }.getOrNull()
            else Repo.load(ctx, p)
            Widget.updateAll(ctx, fc)
        } finally {
            pending.finish()
        }
    }.start()
}

/** The main Clymo widget: changes layout as it's resized, from a 1×1 tile to the 4×3 big picture. */
class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshInBackground(context, this)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refreshInBackground(context, this)
}

/** Today's outfit as a checklist; ticks sync with the app. */
class ChecklistWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshInBackground(context, this)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val i = intent.getIntExtra("i", -1)
            refreshInBackground(context, this) { p -> if (i >= 0) Checks.toggle(p, i) }
        } else super.onReceive(context, intent)
    }

    companion object { const val ACTION_TOGGLE = "com.alex.dresscast.TOGGLE" }
}

/** The next five days and what to wear. */
class WeekWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refreshInBackground(context, this)
}

object Widget {

    private class WPal(
        val bg: Int, val card: Int, val ink: Int, val muted: Int, val accent: Int, val onAccent: Int,
        val tile: Int, val sceneInk: Int, val mood: Mood, val dark: Boolean,
    )

    private fun isDark(ctx: Context, p: Prefs) = when (p.theme) {
        "Dark" -> true
        "Light" -> false
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun wpal(ctx: Context, p: Prefs, mood: Mood): WPal {
        val dark = isDark(ctx, p)
        val c = palette(mood, dark)
        return WPal(
            bg = c.bg.toArgb(), card = c.card.toArgb(), ink = c.ink.toArgb(), muted = c.cardMuted.toArgb(),
            accent = c.accent.toArgb(), onAccent = c.onAccent.toArgb(),
            tile = if (dark) c.card.toArgb() else 0xFFFFFFFF.toInt(),
            sceneInk = c.scene.ink.toArgb(), mood = mood, dark = dark,
        )
    }

    fun updateAll(ctx: Context, fc: Forecast?) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val p = Prefs(ctx)
        L.use(p)
        mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java)).forEach { id ->
            runCatching { mgr.updateAppWidget(id, main(ctx, p, fc, mgr.getAppWidgetOptions(id))) }
        }
        mgr.getAppWidgetIds(ComponentName(ctx, ChecklistWidget::class.java)).forEach { id ->
            runCatching { mgr.updateAppWidget(id, checklist(ctx, p, fc)) }
        }
        mgr.getAppWidgetIds(ComponentName(ctx, WeekWidget::class.java)).forEach { id ->
            runCatching { mgr.updateAppWidget(id, week(ctx, p, fc)) }
        }
    }

    // ------------------------------------------------------------ main, resizable

    private val layouts = listOf(
        SizeF(40f, 40f) to R.layout.w_1x1,
        SizeF(40f, 110f) to R.layout.w_1x2,
        SizeF(110f, 40f) to R.layout.w_2x1,
        SizeF(110f, 110f) to R.layout.w_2x2,
        SizeF(250f, 40f) to R.layout.w_4x1,
        SizeF(250f, 110f) to R.layout.w_4x2,
        SizeF(250f, 180f) to R.layout.w_4x3,
    )

    /** Before Android 12 we pick the layout ourselves from the widget's size. */
    private fun pickLayout(options: Bundle?): Int {
        val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
        val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110) ?: 110
        return layouts.lastOrNull { (s, _) -> s.width <= w && s.height <= h }?.second ?: R.layout.w_1x1
    }

    private fun main(ctx: Context, p: Prefs, fc: Forecast?, options: Bundle?): RemoteViews =
        if (Build.VERSION.SDK_INT >= 31) RemoteViews(layouts.associate { (size, id) -> size to fill(ctx, p, fc, id) })
        else fill(ctx, p, fc, pickLayout(options))

    private fun openApp(ctx: Context) =
        PendingIntent.getActivity(ctx, 5, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun RemoteViews.tint(id: Int, color: Int) = setInt(id, "setColorFilter", color)
    private fun RemoteViews.show(id: Int, on: Boolean) = setViewVisibility(id, if (on) View.VISIBLE else View.GONE)
    private fun RemoteViews.text(id: Int, s: CharSequence, color: Int) {
        setTextViewText(id, s)
        setTextColor(id, color)
    }

    private fun garmentIcon(g: Garment) = when (g) {
        Garment.Jacket -> R.drawable.ic_g_jacket
        Garment.Shirt -> R.drawable.ic_g_shirt
        Garment.Umbrella -> R.drawable.ic_g_umbrella
        Garment.Glasses -> R.drawable.ic_g_glasses
        Garment.Sun -> R.drawable.ic_g_sun
        Garment.Shorts -> R.drawable.ic_g_shorts
        Garment.Pants -> R.drawable.ic_g_pants
        Garment.Shoe -> R.drawable.ic_g_shoe
        Garment.Scarf -> R.drawable.ic_g_scarf
    }

    private fun weatherIcon(code: Int) = when {
        code == 0 -> R.drawable.ic_g_sun
        code <= 3 || code in 45..48 -> R.drawable.ic_w_cloudsun
        Advice.isSnow(code) -> R.drawable.ic_w_snow
        else -> R.drawable.ic_w_rain
    }

    private fun fill(ctx: Context, p: Prefs, fc: Forecast?, layout: Int): RemoteViews {
        val v = RemoteViews(ctx.packageName, layout)
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        val d = fc?.days?.firstOrNull()
        val w = wpal(ctx, p, d?.mood ?: Mood.Mild)
        val big = layout == R.layout.w_4x3
        v.tint(R.id.w_bg, if (big) w.card else w.bg)
        if (fc == null || d == null) {
            v.text(R.id.w_temp, "–", w.ink)
            v.text(R.id.w_headline, t("Open Clymo to load the weather", "Άνοιξε το Clymo για τον καιρό"), w.ink)
            v.text(R.id.w_item1, "Clymo", w.ink)
            return v
        }
        val f = Fmt(fc.fahrenheit)
        val umbrella = d.items.firstOrNull { it.icon == Garment.Umbrella }
        val first = d.items.first()

        v.text(R.id.w_temp, f.t(d.now ?: d.tMax), if (big) w.sceneInk else w.ink)
        v.text(R.id.w_headline, d.headline, if (big) w.sceneInk else w.ink)
        v.text(R.id.w_city, fc.city.substringBefore(",") + " · " + (d.feelNow?.let { t("Feels like ${f.t(it)}", "Αίσθηση ${f.t(it)}") }
            ?: t("High ${f.t(d.tMax)}", "Μέγ. ${f.t(d.tMax)}")), w.muted)
        v.setImageViewResource(R.id.w_icon, weatherIcon(d.code))
        v.tint(R.id.w_icon, w.ink)

        // 1×1 / 2×1 / 2×2
        v.setImageViewResource(R.id.w_item1_icon, garmentIcon(first.icon))
        v.tint(R.id.w_item1_icon, w.accent)
        v.text(R.id.w_item1, if (layout == R.layout.w_2x1) d.items.take(2).joinToString(" + ") { it.name } else first.name, w.ink)
        v.text(R.id.w_note,
            if (layout == R.layout.w_1x2) (d.feelNow?.let { t("feels ${f.t(it)}", "αίσθ. ${f.t(it)}") } ?: "")
            else d.rainFrom?.let { t("Rain from ${hh(it)} · ${d.rainPct}%", "Βροχή από ${hh(it)} · ${d.rainPct}%") } ?: d.items.drop(1).firstOrNull()?.name ?: "",
            w.muted)

        // Item tiles (1×2, 4×1, 4×3)
        val tileItems = if (layout == R.layout.w_1x2) listOf(first, umbrella ?: d.items.getOrNull(1)) else d.items.take(4)
        val ids = listOf(
            Triple(R.id.w_i1_box, R.id.w_i1, R.id.w_i1_bg), Triple(R.id.w_i2_box, R.id.w_i2, R.id.w_i2_bg),
            Triple(R.id.w_i3_box, R.id.w_i3, R.id.w_i3_bg), Triple(R.id.w_i4_box, R.id.w_i4, R.id.w_i4_bg),
        )
        val labels = listOf(R.id.w_l1, R.id.w_l2, R.id.w_l3, R.id.w_l4)
        ids.forEachIndexed { i, (box, icon, bg) ->
            val item = tileItems.getOrNull(i)
            v.show(box, item != null)
            if (big) v.show(labels[i], item != null)
            if (item != null) {
                val hot = item.icon == Garment.Umbrella && d.rainFrom != null
                v.setImageViewResource(icon, garmentIcon(item.icon))
                v.tint(icon, if (hot) w.onAccent else w.accent)
                v.tint(bg, if (hot) w.accent else if (big) blend(w.accent, w.card) else w.tile)
                if (big) v.text(labels[i], item.name, w.ink)
            }
        }
        if (layout == R.layout.w_1x2) {
            v.text(R.id.w_l2, if (umbrella != null && d.rainFrom != null) hh(d.rainFrom) else "", w.accent)
        }

        // Chips + day tiles (4×2)
        val chipIds = listOf(Triple(R.id.w_chip1_box, R.id.w_chip1, R.id.w_chip1_bg), Triple(R.id.w_chip2_box, R.id.w_chip2, R.id.w_chip2_bg), Triple(R.id.w_chip3_box, R.id.w_chip3, R.id.w_chip3_bg))
        val chips = d.items.take(3)
        chipIds.forEachIndexed { i, (box, tv, bg) ->
            val item = chips.getOrNull(i)
            v.show(box, item != null)
            if (item != null) {
                val hot = item.icon == Garment.Umbrella && d.rainFrom != null
                v.text(tv, if (hot) "${item.name} · ${hh(d.rainFrom!!)}" else item.name, if (hot) w.onAccent else w.ink)
                v.tint(bg, if (hot) w.accent else w.tile)
            }
        }
        val tiles = listOf(
            listOf(R.id.w_t1_bg, R.id.w_t1_icon, R.id.w_t1_name, R.id.w_t1_temp),
            listOf(R.id.w_t2_bg, R.id.w_t2_icon, R.id.w_t2_name, R.id.w_t2_temp),
            listOf(R.id.w_t3_bg, R.id.w_t3_icon, R.id.w_t3_name, R.id.w_t3_temp),
        )
        tiles.forEachIndexed { i, (bg, icon, name, temp) ->
            val pe = d.periods.getOrNull(i) ?: return@forEachIndexed
            val wet = pe.rain >= 50
            val fg = if (wet) w.onAccent else w.ink
            v.tint(bg, if (wet) w.accent else w.tile)
            v.setImageViewResource(icon, weatherIcon(pe.code))
            v.tint(icon, fg)
            v.text(name, pe.name, if (wet) w.onAccent else w.muted)
            v.text(temp, "${f.t(pe.feel)}  ${pe.rain}%", fg)
        }

        // Big picture (4×3)
        if (big) {
            // Kept small on purpose: widget updates must stay under Android's ~1 MB limit.
            val k = minOf(ctx.resources.displayMetrics.density, 1.25f)
            v.setImageViewBitmap(R.id.w_scene, scene(ctx, p, d.mood, (330 * k).roundToInt(), (150 * k).roundToInt(), 26 * k))
            val strip = if (d.rainFrom != null) t("Rain from ${hh(d.rainFrom)}", "Βροχή από ${hh(d.rainFrom)}") + " · " else ""
            v.text(R.id.w_strip, strip + d.periods.joinToString(", ") { "${it.name.lowercase(L.locale)} ${f.t(it.feel)}" }, w.onAccent)
            v.tint(R.id.w_strip_bg, w.accent)
            v.tint(R.id.w_strip_icon, w.onAccent)
            v.setImageViewResource(R.id.w_strip_icon, if (d.rainFrom != null) R.drawable.ic_g_umbrella else weatherIcon(d.code))
        }
        return v
    }

    private fun blend(a: Int, b: Int): Int {
        // ~18% of a over b, like the app's tinted icon tiles.
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        fun mix(s: Int) = ((ch(a, s) * 0.18f) + (ch(b, s) * 0.82f)).roundToInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    // ------------------------------------------------------------ checklist

    private fun checklist(ctx: Context, p: Prefs, fc: Forecast?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.w_check)
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        val d = fc?.days?.firstOrNull()
        val w = wpal(ctx, p, d?.mood ?: Mood.Mild)
        v.tint(R.id.w_bg, w.card)
        v.text(R.id.w_title, t("Today, wear", "Σήμερα φόρεσε"), w.ink)
        val items = d?.items ?: emptyList()
        val checked = Checks.get(p)
        v.text(R.id.w_count, if (items.isEmpty()) "" else t("${checked.count { it < items.size }} of ${items.size}", "${checked.count { it < items.size }} από ${items.size}"), w.muted)
        val rows = listOf(
            listOf(R.id.w_r1, R.id.w_c1_ring, R.id.w_c1_dot, R.id.w_c1_tick, R.id.w_n1),
            listOf(R.id.w_r2, R.id.w_c2_ring, R.id.w_c2_dot, R.id.w_c2_tick, R.id.w_n2),
            listOf(R.id.w_r3, R.id.w_c3_ring, R.id.w_c3_dot, R.id.w_c3_tick, R.id.w_n3),
            listOf(R.id.w_r4, R.id.w_c4_ring, R.id.w_c4_dot, R.id.w_c4_tick, R.id.w_n4),
        )
        rows.forEachIndexed { i, (row, ring, dot, tick, name) ->
            val item = items.getOrNull(i)
            v.show(row, item != null)
            if (item != null) {
                val on = i in checked
                v.show(ring, !on); v.show(dot, on); v.show(tick, on)
                v.tint(ring, w.muted); v.tint(dot, w.accent); v.tint(tick, w.onAccent)
                v.text(name, item.name, w.ink)
                val toggle = PendingIntent.getBroadcast(
                    ctx, 100 + i,
                    Intent(ctx, ChecklistWidget::class.java).setAction(ChecklistWidget.ACTION_TOGGLE).putExtra("i", i),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                v.setOnClickPendingIntent(row, toggle)
            }
        }
        val rain = d?.rainFrom
        v.show(R.id.w_strip_box, rain != null || d == null)
        v.tint(R.id.w_strip_bg, blend(w.accent, w.card))
        v.tint(R.id.w_strip_icon, w.accent)
        v.text(R.id.w_strip, if (rain != null) t("Rain from ${hh(rain)}", "Βροχή από ${hh(rain)}") else t("Open Clymo to load", "Άνοιξε το Clymo"), w.accent)
        return v
    }

    // ------------------------------------------------------------ next five days

    private fun week(ctx: Context, p: Prefs, fc: Forecast?): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.w_week)
        v.setOnClickPendingIntent(R.id.w_root, openApp(ctx))
        val days = fc?.days?.drop(1)?.take(5) ?: emptyList()
        val w = wpal(ctx, p, fc?.days?.firstOrNull()?.mood ?: Mood.Mild)
        v.tint(R.id.w_bg, w.card)
        val f = Fmt(fc?.fahrenheit ?: false)
        val lo = days.minOfOrNull { it.tMin } ?: 0.0
        val hi = days.maxOfOrNull { it.tMax } ?: 1.0
        val cols = listOf(
            listOf(R.id.w_col1, R.id.w_d1, R.id.w_d1_bg, R.id.w_d1_icon, R.id.w_h1, R.id.w_bar1_track, R.id.w_bar1_trackbg, R.id.w_bar1, R.id.w_lo1, R.id.w_tag1),
            listOf(R.id.w_col2, R.id.w_d2, R.id.w_d2_bg, R.id.w_d2_icon, R.id.w_h2, R.id.w_bar2_track, R.id.w_bar2_trackbg, R.id.w_bar2, R.id.w_lo2, R.id.w_tag2),
            listOf(R.id.w_col3, R.id.w_d3, R.id.w_d3_bg, R.id.w_d3_icon, R.id.w_h3, R.id.w_bar3_track, R.id.w_bar3_trackbg, R.id.w_bar3, R.id.w_lo3, R.id.w_tag3),
            listOf(R.id.w_col4, R.id.w_d4, R.id.w_d4_bg, R.id.w_d4_icon, R.id.w_h4, R.id.w_bar4_track, R.id.w_bar4_trackbg, R.id.w_bar4, R.id.w_lo4, R.id.w_tag4),
            listOf(R.id.w_col5, R.id.w_d5, R.id.w_d5_bg, R.id.w_d5_icon, R.id.w_h5, R.id.w_bar5_track, R.id.w_bar5_trackbg, R.id.w_bar5, R.id.w_lo5, R.id.w_tag5),
        )
        cols.forEachIndexed { i, ids ->
            val (col, dayId, bg, icon, hiId) = ids
            val track = ids[5]; val trackBg = ids[6]; val bar = ids[7]; val loId = ids[8]; val tag = ids[9]
            val d = days.getOrNull(i)
            v.show(col, d != null)
            if (d == null) return@forEachIndexed
            val dayPal = palette(d.mood, w.dark)
            v.text(dayId, d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, L.locale), w.ink)
            v.tint(bg, if (w.dark) dayPal.card.toArgb() else dayPal.bg.toArgb())
            v.setImageViewResource(icon, weatherIcon(d.code))
            v.tint(icon, dayPal.accent.toArgb())
            v.text(hiId, f.t(d.tMax), w.ink)
            v.text(loId, f.t(d.tMin), w.muted)
            v.text(tag, d.tag, w.muted)
            v.tint(trackBg, blend(w.muted, w.card))
            v.tint(bar, if (d.tMax >= 22) 0xFFE08A2E.toInt() else w.accent)
            // Range bar: only Android 12+ lets widgets size it; older phones show the plain track.
            if (Build.VERSION.SDK_INT >= 31 && hi > lo) {
                val trackDp = 30f
                val top = ((hi - d.tMax) / (hi - lo) * trackDp).toFloat()
                val h = ((d.tMax - d.tMin) / (hi - lo) * trackDp).toFloat().coerceAtLeast(4f)
                v.setViewLayoutHeight(bar, h, TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutMargin(bar, RemoteViews.MARGIN_TOP, top, TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutHeight(track, trackDp, TypedValue.COMPLEX_UNIT_DIP)
            } else {
                v.show(bar, false)
            }
        }
        return v
    }

    // ------------------------------------------------------------ scene picture for the big widget

    private fun scene(ctx: Context, p: Prefs, mood: Mood, wPx: Int, hPx: Int, radius: Float): Bitmap {
        val c = palette(mood, isDark(ctx, p)).scene
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        // Round the top corners to match the widget.
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, wPx.toFloat(), hPx.toFloat()), floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
        }
        cv.clipPath(clip)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color: androidx.compose.ui.graphics.Color) = paint.apply { style = Paint.Style.FILL; this.color = color.toArgb() }
        cv.drawColor(c.sky.toArgb())
        val s = wPx / 350f
        cv.translate(0f, hPx - 210f * s)
        cv.scale(s, s)
        fun circle(x: Float, y: Float, r: Float, color: androidx.compose.ui.graphics.Color) = cv.drawCircle(x, y, r, fill(color))
        fun cloud(color: androidx.compose.ui.graphics.Color, puffs: List<Triple<Float, Float, Float>>, l: Float, t: Float, r: Float, b: Float) {
            puffs.forEach { (x, y, rr) -> circle(x, y, rr, color) }
            cv.drawRect(l, t, r, b, fill(color))
        }
        when (mood) {
            Mood.Mild -> {
                circle(282f, 60f, 26f, c.sun)
                cloud(c.cloud, listOf(Triple(262f, 84f, 16f), Triple(286f, 76f, 20f), Triple(310f, 86f, 15f)), 262f, 84f, 310f, 101f)
                cloud(c.cloud, listOf(Triple(196f, 44f, 9f), Triple(208f, 38f, 12f), Triple(222f, 45f, 8f)), 196f, 44f, 222f, 53f)
            }
            Mood.Hot -> {
                circle(282f, 62f, 58f, c.sun.copy(alpha = 0.22f))
                circle(282f, 62f, 44f, c.sun.copy(alpha = 0.45f))
                circle(282f, 62f, 32f, c.sun)
            }
            Mood.Rainy -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.strokeCap = Paint.Cap.ROUND; paint.color = c.rain.toArgb()
                listOf(240f to 72f, 262f to 84f, 284f to 70f, 306f to 86f, 328f to 72f, 250f to 108f, 272f to 120f, 294f to 106f, 316f to 122f, 338f to 108f)
                    .forEach { (x, y) -> cv.drawLine(x, y, x - 8f, y + 18f, paint) }
                cloud(c.cloud, listOf(Triple(230f, 40f, 26f), Triple(268f, 30f, 32f), Triple(306f, 42f, 26f), Triple(336f, 36f, 22f)), 230f, 40f, 336f, 66f)
                cloud(c.cloud2, listOf(Triple(250f, 66f, 14f), Triple(274f, 60f, 18f), Triple(298f, 68f, 12f)), 250f, 66f, 298f, 80f)
            }
            Mood.Cold -> {
                circle(290f, 74f, 22f, c.sun)
                listOf(210f to 40f, 236f to 28f, 322f to 34f, 258f to 112f, 330f to 120f).forEach { (x, y) -> circle(x, y, 2f, c.flake) }
            }
        }
        val far = PathParser.createPathFromPathData("M0 192 C70 178 140 182 200 172 S300 150 350 158 L350 210 L0 210 Z")
        val near = PathParser.createPathFromPathData("M0 202 C90 192 170 200 250 190 S330 182 350 186 L350 210 L0 210 Z")
        cv.drawPath(far, fill(c.far))
        cv.drawPath(near, fill(c.near))
        return bmp
    }
}
