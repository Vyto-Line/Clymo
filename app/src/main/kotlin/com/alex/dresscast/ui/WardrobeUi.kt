package com.alex.dresscast.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.L.t
import com.alex.dresscast.Wardrobe

/** Settings row that opens My wardrobe. */
@Composable
fun WardrobeEntry(s: AppState, pal: Pal, onOpen: () -> Unit) {
    val total = Wardrobe.all.size
    val owned = Wardrobe.all.count { it.key !in s.prefs.off }
    Card(pal) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                Icon(Ico.jacket, null, Modifier.size(20.dp), tint = pal.accent)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(t("My wardrobe", "Η γκαρνταρόμπα μου"), color = pal.ink, fontSize = 16.sp)
                Text(t("$owned of $total items · only these are suggested", "$owned από $total · προτείνονται μόνο αυτά"), color = pal.cardMuted, fontSize = 13.sp)
            }
            Icon(Ico.chevron, null, Modifier.size(16.dp), tint = pal.cardMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WardrobeScreen(s: AppState, pal: Pal, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val p = s.prefs
    val off = p.off
    Screen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(pal.card).clickable(role = Role.Button, onClickLabel = t("Back", "Πίσω"), onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Ico.back, t("Back", "Πίσω"), Modifier.size(20.dp), tint = pal.ink) }
            Text(t("My wardrobe", "Η γκαρνταρόμπα μου"), color = pal.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                fontFamily = Fonts.display, modifier = Modifier.padding(start = 12.dp))
        }
        Text(
            t("Tick what you own. Clymo only suggests these, and finds an alternative when something is missing.",
                "Σημείωσε τι έχεις. Το Clymo προτείνει μόνο αυτά, και βρίσκει εναλλακτική όταν κάτι λείπει."),
            color = pal.muted, fontSize = 14.sp, lineHeight = 20.sp,
        )
        Card(pal) { WardrobeChips(off, pal) { Wardrobe.toggle(p, it); s.rebuild() } }
        // A live example of how the alternatives work, based on what's missing.
        val example = when {
            "coat" in off && "winterjacket" !in off -> t("Without a wool coat, on cold days you'll see: Winter jacket + sweater.",
                "Χωρίς μάλλινο παλτό, τις κρύες μέρες θα δεις: Χειμερινό μπουφάν + πουλόβερ.")
            "umbrella" in off && "raincoat" !in off -> t("Without an umbrella, on rainy days you'll see a hooded rain jacket.",
                "Χωρίς ομπρέλα, τις βροχερές μέρες θα δεις αδιάβροχο με κουκούλα.")
            "glasses" in off && "cap" !in off -> t("Without sunglasses, on sunny days you'll see a cap.",
                "Χωρίς γυαλιά ηλίου, τις ηλιόλουστες μέρες θα δεις καπέλο.")
            "waterproof" in off && "boots" !in off -> t("Without waterproof shoes, in heavy rain you'll see boots.",
                "Χωρίς αδιάβροχα παπούτσια, σε δυνατή βροχή θα δεις μποτάκια.")
            else -> t("Untick anything you don't have, and Clymo will suggest the next best thing.",
                "Ξετσέκαρε ό,τι δεν έχεις και το Clymo θα προτείνει το επόμενο καλύτερο.")
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.tint).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ico.jacket, null, Modifier.size(22.dp), tint = pal.accent)
            Text(example, color = pal.ink, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** The wardrobe as tappable chips in groups; [off] holds the keys you don't have. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WardrobeChips(off: Set<String>, pal: Pal, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Wardrobe.groups.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(g.name.uppercase(com.alex.dresscast.L.locale), color = pal.cardMuted, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.pieces.forEach { piece ->
                        val on = piece.key !in off
                        Row(
                            Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                                .background(if (on) pal.tint else Color.Transparent)
                                .border(1.5.dp, if (on) pal.accent else pal.track, RoundedCornerShape(20.dp))
                                .toggleable(on, role = Role.Checkbox) { onToggle(piece.key) }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (on) Icon(Ico.check, null, Modifier.size(14.dp), tint = pal.accent)
                            Text((if (on) " " else "") + piece.name, color = if (on) pal.accent else pal.cardMuted, fontSize = 14.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
