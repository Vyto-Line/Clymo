package com.alex.dresscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.L
import com.alex.dresscast.L.t
import com.alex.dresscast.Plan
import com.alex.dresscast.PlanAdvice
import com.alex.dresscast.PlanItem
import com.alex.dresscast.PlanTip
import com.alex.dresscast.Pollen
import com.alex.dresscast.PollenDay
import com.alex.dresscast.Warning
import java.time.LocalDate
import java.time.LocalTime

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

// ---------------------------------------------------------------- Warnings

/** The coloured strip at the top of Today when there's an official warning. Tap for details. */
@Composable
fun WarningBanner(list: List<Warning>, pal: Pal, onOpen: (Warning) -> Unit) {
    val w = list.firstOrNull() ?: return
    val c = Color(w.color)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.copy(alpha = if (pal.dark) 0.22f else 0.16f))
            .border(1.5.dp, c, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = t("Warning details", "Λεπτομέρειες προειδοποίησης")) { onOpen(w) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c), contentAlignment = Alignment.Center) {
            Icon(Ico.warn, null, Modifier.size(19.dp), tint = if (w.level == 1) Color(0xFF14181F) else Color.White)
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(w.title, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
            Text(
                listOf(w.area, w.whenText.lowercase(L.locale)).filter { it.isNotBlank() }.joinToString(" · ") +
                    if (list.size > 1) t(" · +${list.size - 1} more", " · +${list.size - 1} ακόμα") else "",
                color = pal.muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Ico.chevron, null, Modifier.size(16.dp), tint = pal.ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarningSheet(list: List<Warning>, first: Warning, pal: Pal, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            (listOf(first) + list.filter { it != first }).forEachIndexed { i, w ->
                if (i > 0) Divider(pal)
                val c = Color(w.color)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                    Text("  " + (w.levelName + " " + t("warning", "προειδοποίηση")).uppercase(L.locale), color = pal.cardMuted, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Text(w.headline.ifBlank { w.title }, color = pal.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display, lineHeight = 27.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (w.area.isNotBlank()) Fact(t("Area", "Περιοχή"), w.area, pal)
                    Fact(t("When", "Πότε"), w.whenText, pal)
                    Fact(t("From", "Από"), (w.sender.ifBlank { t("National weather service", "Εθνική μετεωρολογική υπηρεσία") }) + t(", via MeteoAlarm", ", μέσω MeteoAlarm"), pal)
                }
                if (w.description.isNotBlank()) Text(w.description, color = pal.ink, fontSize = 15.sp, lineHeight = 21.sp)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.tint).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ico.jacket, null, Modifier.size(20.dp), tint = pal.accent)
                    Text("  " + w.tip, color = pal.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, pal: Pal) {
    Row {
        Text(label, color = pal.cardMuted, fontSize = 14.sp, modifier = Modifier.width(84.dp))
        Text(value, color = pal.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Plans on Today

@Composable
fun PlansCard(s: AppState, pal: Pal, onAdd: () -> Unit, onEdit: (Plan) -> Unit) {
    val items = s.plans
    val now = LocalTime.now()
    Card(pal, padding = 14) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Your plans today", "Τα σχέδιά σου σήμερα"), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(pal.tint).clickable(role = Role.Button, onClick = onAdd)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ico.plus, null, Modifier.size(14.dp), tint = pal.accent)
                Text(" " + t("Plan", "Σχέδιο"), color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (items.isEmpty()) {
            Text(
                if (s.prefs.calOn) t("Nothing on your calendar today. Add a plan to get advice for it.", "Τίποτα στο ημερολόγιο σήμερα. Πρόσθεσε ένα σχέδιο για να πάρεις προτάσεις.")
                else t("Add a plan, or link your calendars in Settings, to get advice for each one.", "Πρόσθεσε ένα σχέδιο ή σύνδεσε τα ημερολόγιά σου στις Ρυθμίσεις, για προτάσεις σε καθένα."),
                color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
        items.forEachIndexed { i, (item, tip) ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 2.dp).height(1.dp).background(pal.track))
            PlanRow(item, tip, item.end.isBefore(now), pal) { item.plan?.let(onEdit) }
        }
    }
}

@Composable
private fun PlanRow(item: PlanItem, tip: PlanTip?, past: Boolean, pal: Pal, onClick: () -> Unit) {
    val alpha = if (past) 0.5f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = item.plan != null, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.width(62.dp)) {
            Text(hm(item.start), color = pal.ink.copy(alpha = alpha), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(item.color)))
                Text(" " + item.source, color = pal.cardMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, color = pal.ink.copy(alpha = alpha), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(PlanAdvice.whereLabel(item.where), tip?.weather).joinToString(" · "),
                color = pal.cardMuted, fontSize = 13.sp,
            )
            if (tip != null && !past) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Ico.garment(tip.icon), null, Modifier.size(16.dp), tint = pal.accent)
                Text(" " + tip.tip, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------- Pollen on Today

@Composable
fun PollenCard(day: PollenDay, mine: Set<String>, pal: Pal) {
    val worst = day.worst(mine) ?: day.items.maxByOrNull { it.level } ?: return
    val shown = (day.items.filter { it.type in mine } + day.items.filter { it.type !in mine && it.level >= 2 })
        .ifEmpty { day.items.sortedByDescending { it.peak } }.take(4)
    Card(pal, padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(Pollen.levelColor(worst.level)).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Ico.leaf, null, Modifier.size(18.dp), tint = Color(Pollen.levelColor(worst.level)))
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(t("Pollen · ", "Γύρη · ") + worst.levelName, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(Pollen.summary(day, mine), color = pal.cardMuted, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { p ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(pal.track).padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(p.name, color = pal.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(Pollen.levelColor(p.level))))
                        Text(" " + p.levelName, color = pal.cardMuted, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- New / edit plan

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanSheet(s: AppState, pal: Pal, editing: Plan?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var tomorrow by remember { mutableStateOf(editing?.date?.isAfter(LocalDate.now()) == true) }
    var start by remember { mutableStateOf(editing?.start ?: LocalTime.of(18, 0)) }
    var end by remember { mutableStateOf(editing?.end ?: LocalTime.of(19, 30)) }
    var days by remember { mutableStateOf(editing?.days ?: emptySet()) }
    var where by remember { mutableStateOf(editing?.where ?: "out") }
    var remind by remember { mutableStateOf(editing?.remind ?: true) }

    val date = if (tomorrow) LocalDate.now().plusDays(1) else LocalDate.now()
    fun current() = Plan(
        id = editing?.id ?: java.util.UUID.randomUUID().toString(),
        title = title.trim().ifBlank { t("Plan", "Σχέδιο") },
        start = start, end = if (end.isAfter(start)) end else start.plusMinutes(60).coerceAtMostEndOfDay(start),
        date = if (days.isEmpty()) date else null, days = days, where = where, remind = remind,
    )
    // For repeating plans, preview the next day it happens on.
    val previewDate = if (days.isEmpty()) date else (0..6).map { LocalDate.now().plusDays(it.toLong()) }.first { (it.dayOfWeek.value - 1) in days }
    val preview = s.preview(current(), previewDate)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (editing == null) t("New plan", "Νέο σχέδιο") else t("Edit plan", "Επεξεργασία"), color = pal.ink, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, fontFamily = Fonts.display, modifier = Modifier.weight(1f))
                if (editing != null) Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { s.deletePlan(editing.id); onDismiss() }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Ico.close, null, Modifier.size(16.dp), tint = Color(0xFFD32F2F))
                    Text(" " + t("Delete", "Διαγραφή"), color = Color(0xFFD32F2F), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            SheetLabel(t("What?", "Τι;"), pal)
            OutlinedTextField(
                title, { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("e.g. Gym", "π.χ. Γυμναστήριο")) },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = pal.ink, unfocusedTextColor = pal.ink, focusedBorderColor = pal.accent),
            )

            SheetLabel(t("When?", "Πότε;"), pal)
            if (days.isEmpty()) Segmented(listOf(false to t("Today", "Σήμερα"), true to t("Tomorrow", "Αύριο")), tomorrow, pal) { tomorrow = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(hm(start), pal, Modifier.weight(1f)) {
                    pickTime(ctx, start.hour, start.minute) { h, m ->
                        val len = java.time.Duration.between(start, end).toMinutes().coerceAtLeast(30)
                        start = LocalTime.of(h, m); end = start.plusMinutes(len).coerceAtMostEndOfDay(start)
                    }
                }
                Text("–", color = pal.ink, fontSize = 18.sp)
                Pill(hm(end), pal, Modifier.weight(1f)) { pickTime(ctx, end.hour, end.minute) { h, m -> end = LocalTime.of(h, m) } }
            }
            val names = listOf(t("M", "Δ"), t("T", "Τ"), t("W", "Τ"), t("T", "Π"), t("F", "Π"), t("S", "Σ"), t("S", "Κ"))
            val full = listOf(t("Mon", "Δευ"), t("Tue", "Τρι"), t("Wed", "Τετ"), t("Thu", "Πεμ"), t("Fri", "Παρ"), t("Sat", "Σαβ"), t("Sun", "Κυρ"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                names.forEachIndexed { i, n ->
                    val on = i in days
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(if (on) pal.accent else Color.Transparent)
                            .border(1.5.dp, if (on) pal.accent else pal.track, CircleShape)
                            .toggleable(on, role = Role.Checkbox) { days = if (on) days - i else days + i },
                        contentAlignment = Alignment.Center,
                    ) { Text(n, color = if (on) pal.onAccent else pal.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Text(
                if (days.isEmpty()) t("Once", "Μία φορά") else t("Repeats: ", "Επαναλαμβάνεται: ") + days.sorted().joinToString(", ") { full[it] },
                color = pal.cardMuted, fontSize = 13.sp,
            )

            SheetLabel(t("Where?", "Πού;"), pal)
            Segmented(listOf("out" to t("Outside", "Έξω"), "in" to t("Inside", "Μέσα"), "walk" to t("Commute", "Διαδρομή")), where, pal) { where = it }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.tint).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.garment(preview?.icon ?: com.alex.dresscast.Garment.Shirt), null, Modifier.size(20.dp), tint = pal.accent)
                    Text("  " + (preview?.tip ?: t("Advice appears once the forecast loads", "Η πρόταση εμφανίζεται μόλις φορτώσει η πρόγνωση")),
                        color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (preview != null) Text(
                    when (where) {
                        "in" -> t("You'll be inside; this counts only the trip there and back. ", "Θα είσαι μέσα· λογαριάζουμε μόνο τη διαδρομή πήγαινε-έλα. ")
                        else -> ""
                    } + t("At ${hm(start)}: ", "Στις ${hm(start)}: ") + preview.weather + ".",
                    color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Remind me before I leave", "Υπενθύμιση πριν φύγεις"), color = pal.ink, fontSize = 16.sp)
                    Text(t("30 minutes before, with what to take", "30 λεπτά πριν, με το τι να πάρεις"), color = pal.cardMuted, fontSize = 13.sp)
                }
                Switch(remind, { remind = it })
            }

            Pill(t("Save", "Αποθήκευση"), pal, Modifier.fillMaxWidth(), primary = true) {
                s.savePlan(current())
                onDismiss()
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun LocalTime.coerceAtMostEndOfDay(from: LocalTime = LocalTime.MIN): LocalTime =
    if (this.isBefore(from)) LocalTime.of(23, 59) else this

@Composable
private fun SheetLabel(text: String, pal: Pal) {
    Text(text, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = 20.dp))
}

// ---------------------------------------------------------------- Settings cards

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarsCard(s: AppState, pal: Pal, onAskPermission: () -> Unit) {
    val p = s.prefs
    Card(pal) {
        SettingRow(t("Link calendars", "Σύνδεση ημερολογίων"), t("Your plans appear on Today with what to wear", "Τα σχέδιά σου εμφανίζονται στο Σήμερα με προτάσεις ρούχων"), pal) {
            Switch(p.calOn && com.alex.dresscast.Calendars.hasPerm(s.ctx), { on ->
                if (on && !com.alex.dresscast.Calendars.hasPerm(s.ctx)) onAskPermission()
                else { p.calOn = on; s.rebuild() }
            })
        }
        Text(
            t("Works with every calendar you see in Samsung Calendar or Google Calendar. Events you add in Samsung Calendar are usually saved to your Google account, so they appear under it below. They're read only on your device and never sent anywhere.",
                "Δουλεύει με όλα τα ημερολόγια που βλέπεις στο Samsung Calendar ή στο Google Calendar. Ό,τι προσθέτεις στο Samsung Calendar συνήθως αποθηκεύεται στον λογαριασμό σου Google, γι' αυτό εμφανίζεται εκεί παρακάτω. Τα διαβάζουμε μόνο στη συσκευή σου, δεν στέλνονται πουθενά."),
            color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp),
        )
        if (p.calOn && s.calendars.isNotEmpty()) {
            val off = p.calOff.split(',').mapNotNull { it.toLongOrNull() }.toSet()
            s.calendars.forEach { c ->
                Divider(pal)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(c.name, color = pal.ink, fontSize = 15.sp)
                        Text(c.account + (if (c.accountName.isNotBlank() && c.accountName != c.name && "@" in c.accountName) " · " + c.accountName else ""),
                            color = pal.cardMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Switch(c.id !in off, { on ->
                        p.calOff = (if (on) off - c.id else off + c.id).joinToString(",")
                        s.rebuild()
                    })
                }
            }
            Divider(pal)
            SettingRow(t("Only events outside", "Μόνο εκδηλώσεις έξω"), t("Skip online events and those without a place", "Παράλειψε όσες έχουν «online» ή χωρίς τοποθεσία"), pal) {
                Switch(p.calOnlyOut, { p.calOnlyOut = it; s.rebuild() })
            }
        }
    }
}

@Composable
fun WarningsCard(s: AppState, pal: Pal) {
    val p = s.prefs
    val ctx = LocalContext.current
    Card(pal) {
        SettingRow(t("Official warnings", "Επίσημες προειδοποιήσεις"), t("From your national weather service, via MeteoAlarm", "Από την ΕΜΥ μέσω MeteoAlarm"), pal) {
            Switch(p.warnOn, { p.warnOn = it; com.alex.dresscast.Scheduler.scheduleAll(ctx); s.refreshWarnings() })
        }
        if (p.warnOn) {
            Divider(pal)
            Text(t("Notify me about", "Ειδοποίησέ με για"), color = pal.ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf(1 to t("Yellow+", "Κίτρινες+"), 2 to t("Orange+", "Πορτοκαλί+"), 3 to t("Red only", "Μόνο κόκκινες")), p.warnLevel, pal) {
                p.warnLevel = it; s.refreshWarnings()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PollenSettingsCard(s: AppState, pal: Pal) {
    val p = s.prefs
    Card(pal) {
        SettingRow(t("Alert when it's high", "Ειδοποίηση όταν είναι υψηλή"), t("In the morning, with your outfit", "Το πρωί, μαζί με το ντύσιμο"), pal) {
            Switch(p.pollenOn, { p.pollenOn = it; s.refresh() })
        }
        if (p.pollenOn) {
            Divider(pal)
            Text(t("I'm allergic to", "Είμαι αλλεργικός/ή σε"), color = pal.ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            val mine = Pollen.selected(p)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pollen.TYPES.forEach { type ->
                    val on = type in mine
                    Row(
                        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (on) pal.tint else Color.Transparent)
                            .border(1.5.dp, if (on) pal.accent else pal.track, RoundedCornerShape(20.dp))
                            .toggleable(on, role = Role.Checkbox) {
                                p.pollenTypes = (if (on) mine - type else mine + type).joinToString(",")
                                s.rebuild()
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (on) Icon(Ico.check, null, Modifier.size(14.dp).padding(end = 2.dp), tint = pal.accent)
                        Text((if (on) " " else "") + Pollen.name(type), color = if (on) pal.accent else pal.ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            Text(t("The pollen forecast covers Europe (Copernicus, via Open-Meteo).", "Η πρόγνωση γύρης καλύπτει την Ευρώπη (Copernicus, μέσω Open-Meteo)."),
                color = pal.cardMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
