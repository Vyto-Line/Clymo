package com.alex.dresscast.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.alex.dresscast.Garment
import com.alex.dresscast.Mood

data class SceneColors(
    val sky: Color,
    val ink: Color,
    val far: Color,
    val near: Color,
    val edge: Color = far,
    val sun: Color = Color(0xFFFFD54F),
    val cloud: Color = Color.White,
    val cloud2: Color = Color.White,
    val rain: Color = Color.White,
    val flake: Color = Color.White,
)

/** All colours for one screen: they follow the day's weather and light/dark mode. */
data class Pal(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val cardMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val scene: SceneColors,
) {
    val tint: Color get() = accent.copy(alpha = 0.18f)
    val track: Color get() = if (dark) Color.White.copy(alpha = 0.08f) else Color(0xFFEEF1F5)
}

fun palette(mood: Mood, dark: Boolean): Pal {
    val ink = if (dark) Color(0xFFEEF2F7) else Color(0xFF14181F)
    val cardMuted = if (dark) Color(0xFFB3BDCA) else Color(0xFF5B6472)
    val muted = if (dark) Color(0xFFB3BDCA) else Color(0xFF36404F)
    val onAccent = if (dark) Color(0xFF0E1726) else Color.White
    fun pal(bg: Long, dbg: Long, dcard: Long, acc: Long, dacc: Long, light: SceneColors, night: SceneColors) = Pal(
        dark = dark,
        bg = Color(if (dark) dbg else bg),
        card = if (dark) Color(dcard) else Color.White,
        ink = ink, muted = muted, cardMuted = cardMuted,
        accent = Color(if (dark) dacc else acc),
        onAccent = onAccent,
        scene = if (dark) night else light,
    )
    return when (mood) {
        Mood.Mild -> pal(0xFFCFE4F7, 0xFF0F1B2A, 0xFF1A2A3E, 0xFF1F5FC9, 0xFF8DBBFF,
            SceneColors(sky = Color(0xFF8EC0EE), ink = Color(0xFF0E1A2B), far = Color(0xFF7FB0A8), near = Color(0xFF5E9488)),
            SceneColors(sky = Color(0xFF1C3550), ink = Color.White, far = Color(0xFF23455A), near = Color(0xFF1A3646),
                sun = Color(0xFFE8C15A), cloud = Color(0xFF3A5574)))
        Mood.Hot -> pal(0xFFFFD9B0, 0xFF24140A, 0xFF3A2414, 0xFFB4400B, 0xFFFDBA74,
            SceneColors(sky = Color(0xFFFFC27A), ink = Color(0xFF2A1606), far = Color(0xFFE7A35C), near = Color(0xFFD48740),
                sun = Color(0xFFFFF0B8)),
            SceneColors(sky = Color(0xFF4A2610), ink = Color.White, far = Color(0xFF5E3416), near = Color(0xFF3E200C),
                sun = Color(0xFFF6C05A)))
        Mood.Rainy -> pal(0xFFC9D3DE, 0xFF131920, 0xFF222B35, 0xFF345577, 0xFFA9C1DD,
            SceneColors(sky = Color(0xFF5A6A80), ink = Color.White, far = Color(0xFF4C5C70), near = Color(0xFF3E4C5E),
                cloud = Color(0xFF7A8AA0), cloud2 = Color(0xFF46566A), rain = Color(0xFFDCE6F0)),
            SceneColors(sky = Color(0xFF1B232D), ink = Color.White, far = Color(0xFF232D38), near = Color(0xFF19212A),
                cloud = Color(0xFF334050), cloud2 = Color(0xFF28323F), rain = Color(0xFF8FA3BA)))
        Mood.Cold -> pal(0xFFDDE7F3, 0xFF0E1726, 0xFF1A2638, 0xFF1F5FC9, 0xFF93C5FD,
            SceneColors(sky = Color(0xFFC6DBF0), ink = Color(0xFF0E1A2B), far = Color(0xFFE9F1F9), near = Color.White,
                edge = Color(0xFF9FB4CC), sun = Color(0xFFFFF4C2)),
            SceneColors(sky = Color(0xFF15233A), ink = Color.White, far = Color(0xFF2A3A52), near = Color(0xFF33465F),
                edge = Color(0xFF4A5F7C), sun = Color(0xFFE9E2B0), flake = Color(0xFFC9D6E8)))
    }
}

/** Line icons drawn from the same shapes as the mockups (24×24, stroke). */
object Ico {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0 Z"

    private fun make(name: String, vararg paths: String, width: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(p).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val jacket = make("jacket", "M9 3L4 6v15h5V10M15 3l5 3v15h-5V10M9 3l3 4 3-4M12 7v14")
    val shirt = make("shirt", "M8 3L3 6l2 4 3-1v12h8V9l3 1 2-4-5-3a4 4 0 0 1-8 0z")
    val umbrella = make("umbrella", "M3 12a9 9 0 0 1 18 0z", "M12 12v7a2 2 0 0 0 4 0")
    val glasses = make("glasses", circle(7f, 14f, 3.5f), circle(17f, 14f, 3.5f), "M10.5 14h3M3.5 14L5 7M20.5 14L19 7")
    val sun = make("sun", circle(12f, 12f, 4f), "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val shorts = make("shorts", "M6 4h12l2 15h-6l-2-8-2 8H4z")
    val shoe = make("shoe", "M3 18h18v-3l-6-1.5L12 9 9 10.5 3 9z")
    val scarf = make("scarf", "M3 8h11a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7")
    val cloudSun = make("cloudSun", "M8 4v1M3.5 8.5h1M4.8 5.3l.7.7M11.2 5.3l-.7.7", "M5.6 10.4A3 3 0 0 1 10.9 8",
        "M9 20h8a3.5 3.5 0 0 0 .4-7A5 5 0 0 0 8 13.5 3.3 3.3 0 0 0 9 20z", width = 1.8f)
    val rain = make("rain", "M7 14h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.1 5.6 4.2 4.2 0 0 0 7 14z", "M8 18l-1 3M12 18l-1 3M16 18l-1 3", width = 1.8f)
    val pin = make("pin", "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", circle(12f, 9.5f, 2.5f))
    val calendar = make("calendar", "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M3 10h18M8 3v4M16 3v4")
    val sliders = make("sliders", "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12", circle(16f, 6f, 2f), circle(10f, 12f, 2f), circle(18f, 18f, 2f))
    val check = make("check", "M5 12.5l4.5 4.5L19 7", width = 3.5f)
    val back = make("back", "M15 5l-7 7 7 7")
    val up = make("up", "M12 19V5M6 11l6-6 6 6")
    val down = make("down", "M12 5v14M6 13l6 6 6-6")
    val chevron = make("chevron", "M9 5l7 7-7 7", width = 2.5f)
    val bell = make("bell", "M6 9a6 6 0 0 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9z", "M10 20a2 2 0 0 0 4 0")

    fun garment(g: Garment) = when (g) {
        Garment.Jacket -> jacket
        Garment.Shirt -> shirt
        Garment.Umbrella -> umbrella
        Garment.Glasses -> glasses
        Garment.Sun -> sun
        Garment.Shorts -> shorts
        Garment.Shoe -> shoe
        Garment.Scarf -> scarf
    }

    fun weather(code: Int) = when {
        code == 0 -> sun
        code <= 3 || code in 45..48 -> cloudSun
        else -> rain
    }
}

/** Inter for text, Space Grotesk for big numbers and headings (Inter in Greek, which Space Grotesk lacks). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object Fonts {
    private fun inter(w: Int) = androidx.compose.ui.text.font.Font(
        com.alex.dresscast.R.font.inter,
        androidx.compose.ui.text.font.FontWeight(w),
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(w),
        ),
    )

    private fun grotesk(w: Int) = androidx.compose.ui.text.font.Font(
        com.alex.dresscast.R.font.space_grotesk,
        androidx.compose.ui.text.font.FontWeight(w),
        variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
            androidx.compose.ui.text.font.FontVariation.weight(w),
        ),
    )

    val body = androidx.compose.ui.text.font.FontFamily(inter(400), inter(500), inter(600), inter(700))
    private val grotesque = androidx.compose.ui.text.font.FontFamily(grotesk(500), grotesk(700))
    val display get() = if (com.alex.dresscast.L.greek) body else grotesque
}
