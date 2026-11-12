package com.alex.dresscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.L.t
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A different time on Saturdays and Sundays (morning reminder only). */
data class WeekendTime(val on: Boolean, val hour: Int, val minute: Int)

private fun fmt(total: Int) = "%02d:%02d".format(total / 60, total % 60)

/** A round − or + button: one step per tap, keeps stepping while held. */
@Composable
private fun StepButton(label: String, description: String, big: Boolean, pal: Pal, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val size = if (big) 64.dp else 44.dp
    Box(
        Modifier.size(size).clip(CircleShape).background(pal.track)
            .semantics { role = Role.Button; contentDescription = description; onClick { step(); true } }
            .pointerInput(Unit) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        step()
                        val repeat = this@coroutineScope.launch {
                            delay(450)
                            while (true) { step(); delay(90) }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = pal.ink, fontSize = if (big) 30.sp else 22.sp, fontWeight = FontWeight.Medium) }
}

/**
 * Pick a time with a big readout and − / + (5 minutes a tap, or an hour with [hourOnly]),
 * plus quick picks. With [weekend], also a different time for Saturday and Sunday.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSheet(
    title: String,
    subtitle: String?,
    hour: Int,
    minute: Int,
    pal: Pal,
    presets: List<Pair<Int, Int>> = listOf(6 to 30, 7 to 0, 7 to 30, 8 to 0, 8 to 30),
    hourOnly: Boolean = false,
    weekend: WeekendTime? = null,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, weekend: WeekendTime?) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val step = if (hourOnly) 60 else 5
    var total by remember { mutableIntStateOf(hour * 60 + (if (hourOnly) 0 else minute - minute % 5)) }
    var wkOn by remember { mutableStateOf(weekend?.on ?: false) }
    var wk by remember { mutableIntStateOf((weekend?.hour ?: 9) * 60 + (weekend?.minute ?: 0)) }
    fun add(v: Int, d: Int) = ((v + d) % 1440 + 1440) % 1440

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(title, color = pal.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display)
                if (subtitle != null) Text(subtitle, color = pal.cardMuted, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", t("Earlier", "Νωρίτερα"), true, pal) { total = add(total, -step) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt(total), color = pal.ink, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp,
                        fontFamily = Fonts.display, textAlign = TextAlign.Center, lineHeight = 66.sp)
                    Text(if (hourOnly) t("± 1 hour per tap", "± 1 ώρα με κάθε πάτημα") else t("± 5 minutes per tap · hold to go faster", "± 5 λεπτά με κάθε πάτημα · κράτα για γρήγορα"),
                        color = pal.cardMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                StepButton("+", t("Later", "Αργότερα"), true, pal) { total = add(total, step) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (h, m) ->
                    val v = h * 60 + m
                    val on = v == total
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (on) pal.accent else Color.Transparent)
                            .border(1.5.dp, if (on) pal.accent else pal.track, RoundedCornerShape(20.dp))
                            .selectable(on, role = Role.RadioButton) { total = v },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(fmt(v), color = if (on) pal.onAccent else pal.ink, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (weekend != null) {
                HorizontalDivider(color = pal.track)
                SettingRow(t("Different time at the weekend", "Άλλη ώρα το Σαββατοκύριακο"),
                    if (wkOn) t("Sat–Sun at ${fmt(wk)}", "Σάβ–Κυρ στις ${fmt(wk)}") else null, pal) {
                    Switch(wkOn, { wkOn = it })
                }
                if (wkOn) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.track).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Weekend", "Σαββατοκύριακο"), color = pal.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    StepButton("−", t("Weekend earlier", "Σαββατοκύριακο νωρίτερα"), false, pal) { wk = add(wk, -5) }
                    Text(fmt(wk), color = pal.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                    StepButton("+", t("Weekend later", "Σαββατοκύριακο αργότερα"), false, pal) { wk = add(wk, 5) }
                }
            }
            Pill(t("Save", "Αποθήκευση"), pal, Modifier.fillMaxWidth(), primary = true) {
                onSave(total / 60, total % 60, weekend?.let { WeekendTime(wkOn, wk / 60, wk % 60) })
                onDismiss()
            }
        }
    }
}
