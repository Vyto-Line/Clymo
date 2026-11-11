package com.alex.dresscast.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.alex.dresscast.Mood

private class Puff(val x: Float, val y: Float, val r: Float)

private fun DrawScope.cloud(color: Color, dx: Float, puffs: List<Puff>, left: Float, top: Float, right: Float, bottom: Float) {
    puffs.forEach { drawCircle(color, it.r, Offset(it.x + dx, it.y)) }
    drawRect(color, Offset(left + dx, top), Size(right - left, bottom - top))
}

/** The drawn weather picture behind today's summary. Gently animated. */
@Composable
fun WeatherScene(mood: Mood, c: SceneColors, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "scene")
    val fall by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "fall")
    val drift by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    val glow by t.animateFloat(0.15f, 0.3f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")

    val far = remember { PathParser().parsePathString("M0 192 C70 178 140 182 200 172 S300 150 350 158 L350 210 L0 210 Z").toPath() }
    val near = remember { PathParser().parsePathString("M0 202 C90 192 170 200 250 190 S330 182 350 186 L350 210 L0 210 Z").toPath() }

    Canvas(modifier) {
        drawRect(c.sky)
        val s = size.width / 350f
        withTransform({
            translate(0f, size.height - 210f * s)
            scale(s, s, pivot = Offset.Zero)
        }) {
            when (mood) {
                Mood.Mild -> {
                    drawCircle(c.sun, 26f, Offset(282f, 60f))
                    cloud(c.cloud, drift, listOf(Puff(262f, 84f, 16f), Puff(286f, 76f, 20f), Puff(310f, 86f, 15f)), 262f, 84f, 310f, 101f)
                    cloud(c.cloud, -drift, listOf(Puff(196f, 44f, 9f), Puff(208f, 38f, 12f), Puff(222f, 45f, 8f)), 196f, 44f, 222f, 53f)
                }
                Mood.Hot -> {
                    drawCircle(c.sun.copy(alpha = glow), 58f, Offset(282f, 62f))
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
    }
}
