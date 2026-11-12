package com.alex.dresscast

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.alex.dresscast.L.t
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Draws today's outfit as a 4:5 picture to share: weather on top, the clothes as little illustrations below. */
object ShareCard {
    const val W = 1080
    const val H = 1350

    /** 0 = follows the weather, 1 = light, 2 = dark. */
    private class Look(val top1: Int, val top2: Int, val body: Int, val tile: Int, val ink: Int, val headInk: Int, val muted: Int, val label: Int, val scene: Boolean)

    private fun look(style: Int, mood: Mood): Look = when (style) {
        1 -> Look(0xFFFFFFFF.toInt(), 0xFFEEF3FB.toInt(), 0xFFF3F5F8.toInt(), 0xFFFFFFFF.toInt(), 0xFF14181F.toInt(), 0xFF14181F.toInt(), 0xFF5B6472.toInt(), 0xFF5B6472.toInt(), false)
        2 -> Look(0xFF1B2433.toInt(), 0xFF22304A.toInt(), 0xFF273244.toInt(), 0xFF33405A.toInt(), 0xFFF1F4F8.toInt(), 0xFFF1F4F8.toInt(), 0xFFB3BDCA.toInt(), 0xFFB3BDCA.toInt(), false)
        else -> when (mood) {
            Mood.Hot -> Look(0xFFF6B26B.toInt(), 0xFFFCE3C2.toInt(), 0xFFFBF1E4.toInt(), 0xFFFFFFFF.toInt(), 0xFF14181F.toInt(), 0xFF2B1A0A.toInt(), 0xFF6B5E4A.toInt(), 0xFF7A5A33.toInt(), true)
            Mood.Rainy -> Look(0xFF7E93AD.toInt(), 0xFFB9C7D8.toInt(), 0xFFEEF1F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF14181F.toInt(), 0xFF0E1A2B.toInt(), 0xFF5B6472.toInt(), 0xFF4A5A70.toInt(), true)
            Mood.Cold -> Look(0xFFA9C7E6.toInt(), 0xFFE3EEF9.toInt(), 0xFFF1F5FA.toInt(), 0xFFFFFFFF.toInt(), 0xFF14181F.toInt(), 0xFF0E1A2B.toInt(), 0xFF5B6472.toInt(), 0xFF4A5A70.toInt(), true)
            else -> Look(0xFF7DB6EC.toInt(), 0xFFB9DAF5.toInt(), 0xFFF6F1E8.toInt(), 0xFFFFFFFF.toInt(), 0xFF14181F.toInt(), 0xFF0E1A2B.toInt(), 0xFF6B5E4A.toInt(), 0xFF6B5E4A.toInt(), true)
        }
    }

    /** The wardrobe key of an item, or a sensible one for older items. */
    fun kindOf(item: Item): String = item.kind.ifBlank {
        when (item.icon) {
            Garment.Jacket -> "lightjacket"; Garment.Shirt -> "tshirt"; Garment.Umbrella -> "umbrella"; Garment.Glasses -> "glasses"
            Garment.Sun -> "cap"; Garment.Shorts -> "shorts"; Garment.Shoe -> "sneakers"; Garment.Scarf -> "scarf"; Garment.Pants -> "jeans"
        }
    }

    fun picture(item: Item): Int = drawable(kindOf(item))

    fun drawable(kind: String): Int = when (kind) {
        "tshirt" -> R.drawable.il_tshirt; "longsleeve" -> R.drawable.il_longsleeve; "thermal" -> R.drawable.il_thermal
        "hoodie" -> R.drawable.il_hoodie; "sweater" -> R.drawable.il_sweater; "shorts" -> R.drawable.il_shorts
        "jeans" -> R.drawable.il_jeans; "trousers" -> R.drawable.il_trousers; "linen" -> R.drawable.il_linen
        "cardigan" -> R.drawable.il_cardigan; "lightjacket" -> R.drawable.il_lightjacket; "raincoat" -> R.drawable.il_raincoat
        "windbreaker" -> R.drawable.il_windbreaker; "winterjacket" -> R.drawable.il_winterjacket; "coat" -> R.drawable.il_coat
        "sneakers" -> R.drawable.il_sneakers; "sandals" -> R.drawable.il_sandals; "boots" -> R.drawable.il_boots
        "waterproof" -> R.drawable.il_waterproof; "umbrella" -> R.drawable.il_umbrella; "cap" -> R.drawable.il_cap
        "glasses" -> R.drawable.il_glasses; "scarf" -> R.drawable.il_scarf; "beanie" -> R.drawable.il_beanie
        "gloves" -> R.drawable.il_gloves; "sunscreen" -> R.drawable.il_sunscreen
        else -> R.drawable.il_tshirt
    }

    /** The day's outfit, plus shoes when the advice didn't need to mention any. */
    fun outfit(d: DayAdvice, p: Prefs): List<Item> {
        val items = d.items.take(3).toMutableList()
        if (items.none { it.icon == Garment.Shoe }) {
            val dry = if (d.rainFrom == null) t("Dry all day", "Χωρίς βροχή") else ""
            when {
                d.feelMax >= 26 && "sandals" !in p.off -> items += Item(t("Sandals", "Σανδάλια"), dry, Garment.Shoe, "sandals")
                "sneakers" !in p.off -> items += Item(t("Sneakers", "Αθλητικά"), dry, Garment.Shoe, "sneakers")
            }
        }
        return items.take(4)
    }

    private fun font(ctx: Context, bold: Boolean): Typeface {
        val base = runCatching { ResourcesCompat.getFont(ctx, R.font.inter) }.getOrNull() ?: Typeface.SANS_SERIF
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, if (bold) 700 else 500, false)
        else Typeface.create(base, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    fun render(ctx: Context, fc: Forecast, p: Prefs, style: Int, showCity: Boolean): Bitmap {
        val d = fc.days.first()
        val f = Fmt(fc.fahrenheit)
        val lk = look(style, d.mood)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = font(ctx, true)
        val regular = font(ctx, false)
        val headH = 470f

        // Background: body colour, then the header.
        c.drawColor(lk.body)
        paint.shader = LinearGradient(0f, 0f, 0f, headH, lk.top1, lk.top2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), headH, paint)
        paint.shader = null

        if (lk.scene) {
            // Sun (or a grey cloud on rainy days) and two hills, like the app's picture.
            if (d.mood == Mood.Rainy) {
                paint.color = 0xFFDDE4EC.toInt()
                c.drawCircle(820f, 150f, 60f, paint); c.drawCircle(890f, 125f, 75f, paint); c.drawCircle(960f, 160f, 55f, paint)
                c.drawRect(820f, 150f, 960f, 215f, paint)
            } else {
                paint.color = if (d.mood == Mood.Hot) 0xFFFFF3B0.toInt() else 0xFFFFD54F.toInt()
                c.drawCircle(900f, 140f, 72f, paint)
                if (d.mood != Mood.Hot) {
                    paint.color = Color.WHITE
                    c.drawCircle(842f, 205f, 42f, paint); c.drawCircle(905f, 185f, 54f, paint); c.drawCircle(968f, 212f, 40f, paint)
                    c.drawRect(842f, 205f, 968f, 246f, paint)
                }
            }
            val hill = Path().apply {
                moveTo(0f, 405f); cubicTo(220f, 368f, 430f, 380f, 620f, 352f); cubicTo(800f, 330f, 960f, 318f, W.toFloat(), 330f)
                lineTo(W.toFloat(), headH); lineTo(0f, headH); close()
            }
            paint.color = when (d.mood) { Mood.Hot -> 0xFFE8A15A.toInt(); Mood.Cold -> 0xFFC9D8E6.toInt(); Mood.Rainy -> 0xFF6F8F86.toInt(); else -> 0xFF7FB0A8.toInt() }
            c.drawPath(hill, paint)
            val hill2 = Path().apply {
                moveTo(0f, 440f); cubicTo(280f, 410f, 520f, 432f, 770f, 405f); cubicTo(920f, 392f, 1010f, 396f, W.toFloat(), 400f)
                lineTo(W.toFloat(), headH); lineTo(0f, headH); close()
            }
            paint.color = when (d.mood) { Mood.Hot -> 0xFFD98944.toInt(); Mood.Cold -> 0xFFB3C6D9.toInt(); Mood.Rainy -> 0xFF557A70.toInt(); else -> 0xFF5E9488.toInt() }
            c.drawPath(hill2, paint)
        }

        // Header text.
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMM", L.locale)).replaceFirstChar { it.uppercase(L.locale) }
        tp.color = lk.headInk; tp.typeface = bold; tp.textSize = 38f
        c.drawText(if (showCity) fc.city.substringBefore(',') + " · " + date else date, 60f, 100f, tp)
        tp.textSize = 190f; tp.letterSpacing = -0.04f
        c.drawText(f.t(d.now ?: d.tMax), 52f, 285f, tp)
        tp.letterSpacing = 0f; tp.textSize = 44f
        val sky = d.headline.substringBefore(',') + " · " + t("High ", "Μέγ. ") + f.t(d.tMax)
        c.drawText(TextUtils.ellipsize(sky, tp, 700f, TextUtils.TruncateAt.END).toString(), 60f, 355f, tp)

        // "Today I'm wearing" and the tiles.
        val pad = 48f
        tp.color = lk.label; tp.textSize = 30f; tp.letterSpacing = 0.08f
        c.drawText(t("TODAY I'M WEARING", "ΣΗΜΕΡΑ ΦΟΡΑΩ"), pad + 8f, 545f, tp)
        tp.letterSpacing = 0f

        val items = outfit(d, p)
        val n = items.size.coerceAtLeast(1)
        val gap = 24f
        val tileW = (W - 2 * pad - (n - 1) * gap) / n
        val top = 585f
        val tileH = 520f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lk.tile
            setShadowLayer(18f, 0f, 6f, if (style == 2) 0x55000000 else 0x22000000)
        }
        items.forEachIndexed { i, item ->
            val x = pad + i * (tileW + gap)
            val r = RectF(x, top, x + tileW, top + tileH)
            c.drawRoundRect(r, 44f, 44f, shadow)
            val size = minOf(tileW - 50f, 250f).toInt()
            ContextCompat.getDrawable(ctx, picture(item))?.let { dr ->
                val left = (x + (tileW - size) / 2).toInt()
                val tTop = (top + 34f).toInt()
                dr.setBounds(left, tTop, left + size, tTop + size)
                dr.draw(c)
            }
            val nameP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.ink; typeface = bold; textSize = if (n >= 4) 34f else 40f }
            val name = StaticLayout.Builder.obtain(item.name, 0, item.name.length, nameP, (tileW - 32).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
            c.save(); c.translate(x + 16f, top + 50f + size); name.draw(c); c.restore()
            if (item.why.isNotBlank()) {
                val whyP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = lk.muted; typeface = regular; textSize = if (n >= 4) 26f else 30f }
                val why = StaticLayout.Builder.obtain(item.why, 0, item.why.length, whyP, (tileW - 32).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
                c.save(); c.translate(x + 16f, top + 60f + size + name.height); why.draw(c); c.restore()
            }
        }

        // Footer: little logo and a weather note.
        val fy = H - 90f
        paint.color = 0xFF1F5FC9.toInt()
        c.drawRoundRect(RectF(pad + 8f, fy - 40f, pad + 56f, fy + 8f), 14f, 14f, paint)
        paint.color = Color.WHITE
        c.drawCircle(pad + 32f, fy - 16f, 9f, paint)
        tp.color = lk.ink; tp.typeface = bold; tp.textSize = 40f
        c.drawText("Clymo", pad + 72f, fy, tp)
        val note = d.rainFrom?.let { t("Rain from ${hh(it)}", "Βροχή από ${hh(it)}") } ?: t("No rain today", "Χωρίς βροχή σήμερα")
        tp.color = lk.label; tp.typeface = regular; tp.textSize = 32f
        c.drawText(note, W - pad - 8f - tp.measureText(note), fy, tp)
        return bmp
    }

    private fun file(ctx: Context, bmp: Bitmap): File {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "clymo-${LocalDate.now()}.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }

    fun share(ctx: Context, bmp: Bitmap) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file(ctx, bmp))
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, t("Share today's outfit", "Κοινοποίηση ντυσίματος")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Saves to Pictures/Clymo (Android 10 and newer). */
    fun save(ctx: Context, bmp: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Clymo ${LocalDate.now()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Clymo")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }.getOrDefault(false)
    }
}
