package com.alex.dresscast.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Fades and slides a block up into place, a little after the one before it. */
@Composable
fun Modifier.enter(index: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(55L * index)
        shown = true
    }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "enter")
    return this.graphicsLayer {
        alpha = a
        translationY = (1f - a) * 48f
    }
}

@Composable
private fun c(target: Color): Color = animateColorAsState(target, tween(700), label = "colour").value

/** The same palette, but every colour glides to its new value when the weather mood changes. */
@Composable
fun animated(p: Pal): Pal {
    val s = p.scene
    return p.copy(
        bg = c(p.bg),
        card = c(p.card),
        ink = c(p.ink),
        muted = c(p.muted),
        cardMuted = c(p.cardMuted),
        accent = c(p.accent),
        onAccent = c(p.onAccent),
        scene = s.copy(sky = c(s.sky), ink = c(s.ink), far = c(s.far), near = c(s.near), edge = c(s.edge)),
    )
}
