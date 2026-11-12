package com.alex.dresscast.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.Diary
import com.alex.dresscast.L
import com.alex.dresscast.L.t
import com.alex.dresscast.ShareCard
import java.time.format.DateTimeFormatter

/** Settings row that opens the outfit diary. */
@Composable
fun DiaryEntryRow(s: AppState, pal: Pal, onOpen: () -> Unit) {
    val n = remember(s.prefs.diary) { Diary.load(s.prefs).size }
    Card(pal) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                Icon(Ico.calendar, null, Modifier.size(20.dp), tint = pal.accent)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(t("Outfit diary", "Ημερολόγιο ντυσίματος"), color = pal.ink, fontSize = 16.sp)
                Text(if (n == 0) t("Fills in when you answer “How was it?”", "Γεμίζει όταν απαντάς «Πώς ήταν;»") else t("$n days", "$n μέρες"),
                    color = pal.cardMuted, fontSize = 13.sp)
            }
            Icon(Ico.chevron, null, Modifier.size(16.dp), tint = pal.cardMuted)
        }
    }
}

@Composable
fun DiaryScreen(s: AppState, pal: Pal, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val list = remember(s.prefs.diary) { Diary.load(s.prefs) }
    val insight = remember(list) { Diary.insight(list) }
    Screen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(pal.card).clickable(role = Role.Button, onClickLabel = t("Back", "Πίσω"), onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Ico.back, t("Back", "Πίσω"), Modifier.size(20.dp), tint = pal.ink) }
            Text(t("Outfit diary", "Ημερολόγιο ντυσίματος"), color = pal.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                fontFamily = Fonts.display, modifier = Modifier.padding(start = 12.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.accent).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(t("WHAT I'VE LEARNED", "ΤΙ ΕΜΑΘΑ"), color = pal.onAccent.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (insight != null) {
                Text(insight.first, color = pal.onAccent, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
                Text(insight.second, color = pal.onAccent.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 18.sp)
            } else {
                Text(t("Answer “How was it?” for a few days and I'll show you what works for you.",
                    "Απάντησε στο «Πώς ήταν;» για λίγες μέρες και θα σου δείξω τι σου ταιριάζει."),
                    color = pal.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
            }
        }
        if (list.isNotEmpty()) Card(pal, padding = 12) {
            list.forEachIndexed { i, e ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(pal.track))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(96.dp)) {
                        Text(e.date.format(DateTimeFormatter.ofPattern("EEE d", L.locale)).replaceFirstChar { it.uppercase(L.locale) },
                            color = pal.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${e.feel}° · ${e.sky}", color = pal.cardMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        e.kinds.take(3).forEach { k ->
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFF6F1E8)), contentAlignment = Alignment.Center) {
                                Image(painterResource(ShareCard.drawable(k)), null, Modifier.size(28.dp))
                            }
                        }
                    }
                    val (label, fg, bg) = when (e.answer) {
                        -1 -> Triple(t("Cold", "Κρύωσα"), Color(0xFF1F5FC9), Color(0xFFE6EEFB))
                        1 -> Triple(t("Warm", "Ζεστάθηκα"), Color(0xFFC2410C), Color(0xFFFFEDD5))
                        else -> Triple(t("Just right", "Τέλεια"), Color(0xFF0F766E), Color(0xFFE3F1EC))
                    }
                    Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
        Text(t("Fills in by itself: today's outfit and your answer to “How was it?” in the evening. It stays on your phone.",
            "Γεμίζει μόνο του: κρατά το ντύσιμο της ημέρας και την απάντησή σου στο «Πώς ήταν;» το βράδυ. Μένει μόνο στο κινητό σου."),
            color = pal.muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
