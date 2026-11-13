package com.alex.dresscast.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.alex.dresscast.Mood

private class Puff(val x: Float, val y: Float, val r: Float)

private fun DrawScope.cloud(color: Color, dx: Float, puffs: List<Puff>, left: Float, top: Float, right: Float, bottom: Float) {
    puffs.forEach { drawCircle(color, it.r, Offset(it.x + dx, it.y)) }
    drawRect(color, Offset(left + dx, top), Size(right - left, bottom - top))
}

private fun DrawScope.rays(color: Color, center: Offset, inner: Float, outer: Float, angle: Float) {
    rotate(angle, center) {
        for (k in 0 until 8) {
            rotate(k * 45f, center) {
                drawLine(color, Offset(center.x, center.y - inner), Offset(center.x, center.y - outer), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }
    }
}

/**
 * The drawn weather picture behind today's summary. Gently animated: rays turn, clouds drift,
 * rain and snow fall, and stormy days get the odd lightning flash.
 */
@Composable
fun WeatherScene(mood: Mood, c: SceneColors, modifier: Modifier = Modifier, storm: Boolean = false) {
    // Battery: the picture stays still when battery saver is on, or when you've turned the animation off.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val animate = remember { com.alex.dresscast.Prefs(ctx).motion } &&
        !(ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode ?: false)
    // Values are read inside the drawing below, so only the drawing repeats each frame, not the whole screen.
    val fallS: androidx.compose.runtime.State<Float>
    val driftS: androidx.compose.runtime.State<Float>
    val glowS: androidx.compose.runtime.State<Float>
    val spinS: androidx.compose.runtime.State<Float>
    val flashS: androidx.compose.runtime.State<Float>
    if (animate) {
        val t = rememberInfiniteTransition(label = "scene")
        fallS = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "fall")
        driftS = t.animateFloat(-6f, 6f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
        glowS = t.animateFloat(0.15f, 0.3f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
        spinS = t.animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "spin")
        flashS = t.animateFloat(
            0f, 0f,
            infiniteRepeatable(keyframes {
                durationMillis = 5200
                0f at 0
                0f at 3600
                0.85f at 3680
                0.1f at 3780
                0.6f at 3860
                0f at 4100
            }),
            label = "flash",
        )
    } else {
        fallS = remember { androidx.compose.runtime.mutableFloatStateOf(0.35f) }
        driftS = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        glowS = remember { androidx.compose.runtime.mutableFloatStateOf(0.22f) }
        spinS = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        flashS = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }
    val fall by fallS
    val drift by driftS
    val glow by glowS
    val spin by spinS
    val flash by flashS

    val far = remember { PathParser().parsePathString("M0 192 C70 178 140 182 200 172 S300 150 350 158 L350 210 L0 210 Z").toPath() }
    val near = remember { PathParser().parsePathString("M0 202 C90 192 170 200 250 190 S330 182 350 186 L350 210 L0 210 Z").toPath() }
    val bolt = remember { PathParser().parsePathString("M276 74 L262 104 L274 104 L264 132 L290 96 L277 96 L286 74 Z").toPath() }

    Canvas(modifier) {
        drawRect(c.sky)
        val s = size.width / 350f
        withTransform({
            translate(0f, size.height - 210f * s)
            scale(s, s, pivot = Offset.Zero)
        }) {
            when (mood) {
                Mood.Mild -> {
                    rays(c.sun, Offset(282f, 60f), 33f, 42f, spin)
                    drawCircle(c.sun, 26f, Offset(282f, 60f))
                    cloud(c.cloud, drift, listOf(Puff(262f, 84f, 16f), Puff(286f, 76f, 20f), Puff(310f, 86f, 15f)), 262f, 84f, 310f, 101f)
                    cloud(c.cloud, -drift, listOf(Puff(196f, 44f, 9f), Puff(208f, 38f, 12f), Puff(222f, 45f, 8f)), 196f, 44f, 222f, 53f)
                }
                Mood.Hot -> {
                    drawCircle(c.sun.copy(alpha = glow), 58f, Offset(282f, 62f))
                    rays(c.sun.copy(alpha = 0.7f), Offset(282f, 62f), 48f, 60f, -spin)
                    drawCircle(c.sun.copy(alpha = 0.45f), 44f, Offset(282f, 62f))
                    drawCircle(c.sun, 32f, Offset(282f, 62f))
                }
                Mood.Rainy -> {
                    val drops = listOf(240f to 72f, 262f to 84f, 284f to 70f, 306f to 86f, 328f to 72f,
                        250f to 108f, 272f to 120f, 294f to 106f, 316f to 122f, 338f to 108f, 260f to 144f, 304f to 142f)
                    val dy = fall * 36f
                    drops.forEach { (x, y) ->
                        val yy = 66f + ((y - 66f + dy) % 108f)
                        drawLine(c.rain, Offset(x, yy), Offset(x - 8f, yy + 18f), strokeWidth = 2f, cap = StrokeCap.Round)
                    }
                    if (storm) drawPath(bolt, Color(0xFFFFE27A).copy(alpha = (flash * 1.2f).coerceAtMost(1f)))
                    cloud(c.cloud, drift * 0.5f, listOf(Puff(230f, 40f, 26f), Puff(268f, 30f, 32f), Puff(306f, 42f, 26f), Puff(336f, 36f, 22f)), 230f, 40f, 336f, 66f)
                    cloud(c.cloud2, -drift * 0.5f, listOf(Puff(250f, 66f, 14f), Puff(274f, 60f, 18f), Puff(298f, 68f, 12f)), 250f, 66f, 298f, 80f)
                }
                Mood.Cold -> {
                    drawCircle(c.sun, 22f, Offset(290f, 74f))
                    listOf(Puff(210f, 40f, 2f), Puff(236f, 28f, 1.6f), Puff(322f, 34f, 2f), Puff(258f, 112f, 1.6f), Puff(330f, 120f, 2f), Puff(180f, 90f, 1.6f))
                        .forEach { p -> drawCircle(c.flake, p.r, Offset(p.x + drift, (p.y + fall * 160f) % 170f)) }
                }
            }
            drawPath(far, c.far)
            drawPath(far, c.edge, style = Stroke(1.5f))
            drawPath(near, c.near)
            drawPath(near, c.edge, style = Stroke(1.5f))
        }
        if (storm) drawRect(Color.White.copy(alpha = flash * 0.35f))
    }
}
