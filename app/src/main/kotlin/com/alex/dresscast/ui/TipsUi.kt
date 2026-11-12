package com.alex.dresscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.DayTip
import com.alex.dresscast.L.t
import com.alex.dresscast.Scheduler
import com.alex.dresscast.SunInfo
import java.time.LocalTime

private val Orange = Color(0xFFF97316)
private val OrangeDeep = Color(0xFFC2410C)

/** Strong-sun card: only exists on UV 6+ days when you're outside during the strong sun. */
@Composable
fun SunCard(sun: SunInfo, notYetReminded: Boolean, pal: Pal) {
    val willRemind = notYetReminded && sun.remindAt.isAfter(LocalTime.now())
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card)
            .border(2.dp, Orange, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Orange.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Ico.bottle, null, Modifier.size(22.dp), tint = if (pal.dark) Orange else OrangeDeep)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(sun.title, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sun.body(willRemind), color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun tipLook(kind: String): Triple<ImageVector, Color, Color> = when (kind) {
    "beach" -> Triple(Ico.wave, Color(0xFFDCEFFB), Color(0xFF0369A1))
    "car" -> Triple(Ico.car, Color(0xFFFFF1D6), Color(0xFF9A5B00))
    else -> Triple(Ico.laundry, Color(0xFFE3F1EC), Color(0xFF0F766E))
}

@Composable
fun TipsCard(tips: List<DayTip>, pal: Pal) {
    Card(pal, padding = 14) {
        Text(t("Good day for…", "Καλή μέρα για…"), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        tips.forEachIndexed { i, tip ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(pal.track))
            val (icon, bg, fg) = tipLook(tip.kind)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (pal.dark) fg.copy(alpha = 0.22f) else bg), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(20.dp), tint = if (pal.dark) bg else fg)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(tip.title, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(tip.body, color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun TipsSettingsCard(s: AppState, pal: Pal) {
    val p = s.prefs
    val ctx = LocalContext.current
    Card(pal) {
        Text(t("Shown on Today only on the days they apply.", "Εμφανίζονται στο Σήμερα μόνο τις μέρες που ισχύουν."),
            color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        SettingRow(t("Hanging out the laundry", "Άπλωμα ρούχων"), t("When it's dry, with sun or a breeze", "Όταν είναι στεγνά και έχει ήλιο ή αέρα"), pal) {
            Switch(p.tipLaundry, { p.tipLaundry = it; s.rebuild() })
        }
        Divider(pal)
        SettingRow(t("Beach", "Θάλασσα"), t("Warm day, warm water, small waves", "Ζεστή μέρα, ζεστό νερό, μικρό κύμα"), pal) {
            Switch(p.tipBeach, { p.tipBeach = it; s.refresh() })
        }
        Divider(pal)
        SettingRow(t("Car wash", "Πλύσιμο αυτοκινήτου"), t("When rain or dust is on the way", "Όταν έρχεται βροχή ή σκόνη"), pal) {
            Switch(p.tipCar, { p.tipCar = it; s.refresh() })
        }
        Divider(pal)
        SettingRow(t("Sunscreen reminder", "Υπενθύμιση αντηλιακού"), t("Only when UV is 6+ and you're outside", "Μόνο όταν ο UV είναι 6+ και είσαι έξω"), pal) {
            Switch(p.sunOn, { p.sunOn = it; s.rebuild(); Scheduler.scheduleAll(ctx) })
        }
    }
}
