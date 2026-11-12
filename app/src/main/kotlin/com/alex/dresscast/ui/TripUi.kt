package com.alex.dresscast.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.Fmt
import com.alex.dresscast.L.t
import com.alex.dresscast.Place
import com.alex.dresscast.Repo
import com.alex.dresscast.Scheduler
import com.alex.dresscast.ShareCard
import com.alex.dresscast.Trip
import com.alex.dresscast.TripDay
import com.alex.dresscast.Trips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The card at the top of Week: start a trip, or open the current one. */
@Composable
fun TripEntryCard(s: AppState, pal: Pal, onOpen: () -> Unit) {
    val trip = remember(s.prefs.trip) { Trips.load(s.prefs) }
    Card(pal) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                Icon(Ico.suitcase, null, Modifier.size(22.dp), tint = pal.accent)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                if (trip == null) {
                    Text(t("Going on a trip?", "Πας ταξίδι;"), color = pal.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(t("Get a packing list from the forecast there", "Φτιάξε λίστα βαλίτσας από την πρόγνωση εκεί"), color = pal.cardMuted, fontSize = 13.sp)
                } else {
                    Text(trip.city, color = pal.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(trip.dates, color = pal.cardMuted, fontSize = 13.sp)
                }
            }
            Icon(if (trip == null) Ico.plus else Ico.chevron, null, Modifier.size(18.dp), tint = pal.accent)
        }
    }
}

@Composable
fun TripScreen(s: AppState, pal: Pal, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = s.prefs
    var version by remember { mutableIntStateOf(0) }
    val trip = remember(p.trip, version) { Trips.load(p) }
    var editing by remember { mutableStateOf(trip == null) }
    var days by remember(trip) { mutableStateOf(Trips.cached(p)) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(trip) {
        if (trip == null) return@LaunchedEffect
        loading = true
        withContext(Dispatchers.IO) { runCatching { Trips.fetch(p, trip) }.getOrNull() }?.let { days = it }
        loading = false
    }
    if (editing) TripSheet(trip, pal, onDismiss = { editing = false; if (Trips.load(p) == null) onBack() }) { newTrip ->
        Trips.save(p, newTrip)
        Scheduler.scheduleTrip(ctx)
        days = emptyList()
        version++
        editing = false
    }
    val items = remember(days, p.noItems, p.learn, p.comfort) { Trips.pack(days, p) }
    val checked = remember(p.tripChecked) { Trips.checked(p) }
    val f = Fmt(p.fahrenheit)

    Screen {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(pal.card).clickable(role = Role.Button, onClickLabel = t("Back", "Πίσω"), onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Ico.back, t("Back", "Πίσω"), Modifier.size(20.dp), tint = pal.ink) }
            Text(t("Trip", "Ταξίδι"), color = pal.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display,
                modifier = Modifier.padding(start = 12.dp).weight(1f))
            if (trip != null) Text(t("Remove", "Διαγραφή"), color = pal.cardMuted, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                    Trips.save(p, null); Scheduler.scheduleTrip(ctx); onBack()
                }.padding(8.dp))
        }
        if (trip == null) return@Screen

        Card(pal, padding = 14) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                    Icon(Ico.suitcase, null, Modifier.size(24.dp), tint = pal.accent)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(trip.city, color = pal.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(trip.dates, color = pal.cardMuted, fontSize = 13.sp)
                }
                Text(t("Change", "Αλλαγή"), color = pal.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { editing = true }.padding(8.dp))
            }
            if (days.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { d -> TripDayChip(d, f, pal) }
                }
                Text(Trips.summary(days, p), color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
            } else Text(
                when {
                    loading -> t("Loading the forecast…", "Φόρτωση πρόγνωσης…")
                    trip.from.isAfter(LocalDate.now().plusDays(15)) ->
                        t("The forecast reaches about 16 days ahead. Your list appears closer to the trip.",
                            "Η πρόγνωση φτάνει περίπου 16 μέρες μπροστά. Η λίστα θα εμφανιστεί πιο κοντά στο ταξίδι.")
                    else -> t("Couldn't load the forecast. Pull to try again later.", "Δεν φόρτωσε η πρόγνωση. Δοκίμασε ξανά αργότερα.")
                },
                color = pal.cardMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (items.isNotEmpty()) Card(pal, padding = 14) {
            Row(Modifier.fillMaxWidth()) {
                Text(t("In the suitcase", "Στη βαλίτσα"), color = pal.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(t("${items.count { it.key in checked }} of ${items.size} packed", "${items.count { it.key in checked }} από ${items.size} έτοιμα"),
                    color = pal.cardMuted, fontSize = 13.sp)
            }
            items.forEachIndexed { i, it ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(pal.track))
                val done = it.key in checked
                Row(
                    Modifier.fillMaxWidth().toggleable(done, role = Role.Checkbox) { _ -> Trips.toggle(p, it.key) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF6F1E8)), contentAlignment = Alignment.Center) {
                        Image(painterResource(ShareCard.drawable(it.key)), null, Modifier.size(38.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp).alpha(if (done) 0.55f else 1f)) {
                        Text(it.name + "  ×" + it.qty, color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (it.why.isNotBlank()) Text(it.why, color = pal.cardMuted, fontSize = 12.sp)
                    }
                    Box(
                        Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (done) pal.accent else Color.Transparent)
                            .border(2.dp, if (done) pal.accent else pal.track, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { if (done) Icon(Ico.check, null, Modifier.size(16.dp), tint = pal.onAccent) }
                }
            }
        }
        if (items.isNotEmpty()) {
            Text(t("Only clothes from your wardrobe. Updates if the forecast changes before you leave.",
                "Μόνο ρούχα από τη γκαρνταρόμπα σου. Ενημερώνεται αν αλλάξει η πρόγνωση πριν φύγεις."),
                color = pal.muted, fontSize = 12.sp, lineHeight = 17.sp)
            Card(pal) {
                SettingRow(t("Remind me the evening before", "Υπενθύμιση το προηγούμενο βράδυ"), t("At 20:00, with what's left to pack", "Στις 20:00, με ό,τι λείπει"), pal) {
                    Switch(p.tripRemind, { p.tripRemind = it; Scheduler.scheduleTrip(ctx) })
                }
            }
            Pill(t("Share the list", "Κοινοποίηση λίστας"), pal, Modifier.fillMaxWidth(), primary = true) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Trips.shareText(trip, items))
                ctx.startActivity(Intent.createChooser(send, t("Share the list", "Κοινοποίηση λίστας")))
            }
        }
    }
}

@Composable
private fun TripDayChip(d: TripDay, f: Fmt, pal: Pal) {
    val wet = d.rainPct >= 50 || d.rainMm >= 1
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).background(pal.track).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Trips.dayName(d.date), color = pal.cardMuted, fontSize = 12.sp)
        Icon(Ico.weather(d.code), null, Modifier.size(20.dp).padding(vertical = 2.dp), tint = pal.ink)
        Text(f.t(d.tMax), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(f.t(d.tMin), color = pal.cardMuted, fontSize = 12.sp)
        if (wet) Text("${d.rainPct}%", color = pal.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSheet(trip: Trip?, pal: Pal, onDismiss: () -> Unit, onSave: (Trip) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var place by remember { mutableStateOf(trip?.let { Place(it.city, it.lat, it.lon) }) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var from by remember { mutableStateOf(trip?.from ?: LocalDate.now().plusDays(7)) }
    var to by remember { mutableStateOf(trip?.to ?: LocalDate.now().plusDays(10)) }
    var pickDates by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        results = withContext(Dispatchers.IO) { runCatching { Repo.suggest(query) }.getOrDefault(emptyList()) }
    }
    if (pickDates) {
        val zone = ZoneOffset.UTC
        val ds = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.atStartOfDay(zone).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = to.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedStartDateMillis?.let { from = Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                    to = ds.selectedEndDateMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: from
                    pickDates = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text(t("Cancel", "Άκυρο")) } },
        ) { DateRangePicker(state = ds, modifier = Modifier.weight(1f)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (trip == null) t("New trip", "Νέο ταξίδι") else t("Edit trip", "Αλλαγή ταξιδιού"), color = pal.ink, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, fontFamily = Fonts.display)
            Text(t("Where to?", "Πού πας;"), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            place?.let {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.tint).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.pin, null, Modifier.size(18.dp), tint = pal.accent)
                    Text("  " + it.name + if (it.region.isNotBlank()) ", " + it.region else "", color = pal.ink, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("Search a city", "Αναζήτηση πόλης")) }, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = pal.ink, unfocusedTextColor = pal.ink, focusedBorderColor = pal.accent),
            )
            results.forEach { r ->
                Text(r.name + if (r.region.isNotBlank()) ", " + r.region else "", color = pal.ink, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) {
                        place = r; query = ""; results = emptyList()
                    }.padding(vertical = 10.dp, horizontal = 6.dp))
            }
            Text(t("When?", "Πότε;"), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Pill(Trip("", 0.0, 0.0, from, to).dates, pal, Modifier.fillMaxWidth()) { pickDates = true }
            Pill(t("Make my list", "Φτιάξε τη λίστα"), pal, Modifier.fillMaxWidth(), primary = true) {
                place?.let { onSave(Trip(it.name, it.lat, it.lon, from, if (to.isBefore(from)) from else to)) }
            }
        }
    }
}
