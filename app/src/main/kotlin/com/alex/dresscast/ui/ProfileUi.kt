package com.alex.dresscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.L.t
import com.alex.dresscast.Profile
import com.alex.dresscast.Profiles

/** Me / family members, at the top of Today. Only shown once you've added someone. */
@Composable
fun ProfileChips(s: AppState, pal: Pal, onAdd: () -> Unit) {
    val list = remember(s.prefs.profiles) { Profiles.load(s.prefs) }
    if (list.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip(t("Me", "Εγώ"), "", pal.accent, s.profileSel == null, pal) { s.profileSel = null }
        list.forEach { pr ->
            Chip(pr.name, pr.initial, Color(Profiles.colors[pr.color.coerceIn(0, Profiles.colors.size - 1)]), s.profileSel == pr.id, pal) { s.profileSel = pr.id }
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(pal.card.copy(alpha = 0.6f)).clickable(role = Role.Button, onClickLabel = t("Add someone", "Προσθήκη"), onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Ico.plus, t("Add someone", "Προσθήκη"), Modifier.size(18.dp), tint = pal.ink) }
    }
}

@Composable
private fun Chip(name: String, initial: String, color: Color, on: Boolean, pal: Pal, onClick: () -> Unit) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (on) pal.card else pal.card.copy(alpha = 0.55f))
            .border(if (on) 2.dp else 0.dp, if (on) pal.ink else Color.Transparent, RoundedCornerShape(20.dp))
            .selectable(on, role = Role.Tab, onClick = onClick)
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (initial.isBlank()) Icon(Ico.pin, null, Modifier.size(14.dp), tint = Color.White)
            else Text(initial, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("  $name", color = pal.ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

/** Settings: the family members you dress in the morning. */
@Composable
fun FamilyCard(s: AppState, pal: Pal, onEdit: (Profile?) -> Unit) {
    val list = remember(s.prefs.profiles) { Profiles.load(s.prefs) }
    Card(pal) {
        Text(t("Add a child or partner with their own feel for the cold, hours outside, wardrobe and morning reminder.",
            "Πρόσθεσε παιδί ή σύντροφο με δική του αίσθηση του κρύου, ώρες έξω, γκαρνταρόμπα και πρωινή ειδοποίηση."),
            color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
        list.forEach { pr ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onEdit(pr) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(Profiles.colors[pr.color.coerceIn(0, Profiles.colors.size - 1)])), contentAlignment = Alignment.Center) {
                    Text(pr.initial, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(pr.name, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(Profiles.comfortLabel(pr.comfort) + " · " + Profiles.hours(pr) +
                        if (pr.notify) " · " + "%02d:%02d".format(pr.hour, pr.minute) else "", color = pal.cardMuted, fontSize = 12.sp)
                }
                Icon(Ico.chevron, null, Modifier.size(16.dp), tint = pal.cardMuted)
            }
        }
        Pill(t("Add someone", "Προσθήκη προφίλ"), pal, Modifier.fillMaxWidth().padding(top = 6.dp)) { onEdit(null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(s: AppState, pal: Pal, editing: Profile?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var color by remember { mutableStateOf(editing?.color ?: (Profiles.load(s.prefs).size % Profiles.colors.size)) }
    var comfort by remember { mutableStateOf(editing?.comfort ?: 0) }
    var from by remember { mutableStateOf(editing?.from ?: 8) }
    var to by remember { mutableStateOf(editing?.to ?: 14) }
    var off by remember { mutableStateOf(editing?.off ?: emptySet()) }
    var notify by remember { mutableStateOf(editing?.notify ?: true) }
    var hour by remember { mutableStateOf(editing?.hour ?: 7) }
    var minute by remember { mutableStateOf(editing?.minute ?: 15) }
    var showWardrobe by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) t("New profile", "Νέο προφίλ") else editing.name, color = pal.ink, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, fontFamily = Fonts.display, modifier = Modifier.weight(1f))
                if (editing != null) Text(t("Delete", "Διαγραφή"), color = Color(0xFFD32F2F), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { s.deleteProfile(editing.id); onDismiss() }.padding(8.dp))
            }
            OutlinedTextField(
                name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("Name", "Όνομα")) }, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = pal.ink, unfocusedTextColor = pal.ink, focusedBorderColor = pal.accent),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Profiles.colors.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                            .border(if (color == i) 3.dp else 0.dp, if (color == i) pal.ink else Color.Transparent, CircleShape)
                            .selectable(color == i, role = Role.RadioButton) { color = i },
                    )
                }
            }
            Text(t("Usually…", "Συνήθως…"), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Segmented(listOf(-1 to Profiles.comfortLabel(-1), 0 to Profiles.comfortLabel(0), 1 to Profiles.comfortLabel(1)), comfort, pal) { comfort = it }
            Text(t("Hours outside (school, work…)", "Ώρες έξω (σχολείο, δουλειά…)"), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("%02d:00".format(from), pal, Modifier.weight(1f)) { pickTime(ctx, from, 0) { h, _ -> from = h; if (to <= from) to = (from + 1).coerceAtMost(23) } }
                Text("–", color = pal.ink, fontSize = 18.sp)
                Pill("%02d:00".format(to), pal, Modifier.weight(1f)) { pickTime(ctx, to, 0) { h, _ -> to = h.coerceAtLeast(from) } }
            }
            SettingRow(t("Morning reminder", "Πρωινή ειδοποίηση"), if (notify) "%02d:%02d".format(hour, minute) else null, pal) {
                Switch(notify, { notify = it })
            }
            if (notify) Pill(t("Change time", "Αλλαγή ώρας"), pal, Modifier.fillMaxWidth()) { pickTime(ctx, hour, minute) { h, m -> hour = h; minute = m } }
            SettingRow(t("Their wardrobe", "Η γκαρνταρόμπα του/της"), t("${com.alex.dresscast.Wardrobe.all.count { it.key !in off }} items", "${com.alex.dresscast.Wardrobe.all.count { it.key !in off }} ρούχα"), pal) {
                Text(if (showWardrobe) t("Hide", "Απόκρυψη") else t("Edit", "Αλλαγή"), color = pal.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { showWardrobe = !showWardrobe }.padding(8.dp))
            }
            if (showWardrobe) WardrobeChips(off, pal) { k -> off = if (k in off) off - k else off + k }
            Pill(t("Save", "Αποθήκευση"), pal, Modifier.fillMaxWidth(), primary = true) {
                if (name.isNotBlank()) {
                    val pr = (editing ?: Profile(name = name.trim())).copy(
                        name = name.trim(), color = color, comfort = comfort, from = from, to = to,
                        noItems = off.joinToString(","), notify = notify, hour = hour, minute = minute,
                    )
                    s.saveProfile(pr)
                    onDismiss()
                }
            }
        }
    }
}
