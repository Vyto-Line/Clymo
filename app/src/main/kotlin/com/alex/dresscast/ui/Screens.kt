package com.alex.dresscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.Advice
import com.alex.dresscast.DayAdvice
import com.alex.dresscast.Fmt
import com.alex.dresscast.Item
import com.alex.dresscast.Mood
import com.alex.dresscast.Scheduler
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

@Composable
private fun Screen(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

// ---------------------------------------------------------------- Today

@Composable
fun TodayScreen(s: AppState, pal: Pal) {
    val fc = s.forecast
    val day = fc?.days?.firstOrNull()
    val f = Fmt(s.prefs.fahrenheit)
    Screen {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ico.pin, null, Modifier.size(16.dp), tint = pal.ink)
            Text(" " + s.prefs.city, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(LocalDate.now().format(dateFmt), color = pal.muted, fontSize = 13.sp)
        }

        // Hero: drawn weather scene with today's summary on top.
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(pal.scene.sky)
        ) {
            WeatherScene(day?.mood ?: Mood.Mild, pal.scene, Modifier.fillMaxSize())
            Column(Modifier.padding(start = 18.dp, top = 12.dp, end = 96.dp)) {
                Text(day?.let { f.t(it.tMax) } ?: "–", color = pal.scene.ink, fontSize = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp, lineHeight = 64.sp)
                Text(
                    day?.headline ?: if (s.error != null) "Couldn't load the weather" else "Loading…",
                    color = pal.scene.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
                )
                Text(day?.sub ?: (s.error?.let { "Check your internet connection." } ?: ""), color = pal.scene.ink, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }

        if (day != null) {
            val checked = s.checked()
            Row(Modifier.fillMaxWidth()) {
                Label("Today, wear", pal.muted, Modifier.weight(1f))
                Text("${checked.count { it < day.items.size }} of ${day.items.size} ready", color = pal.muted, fontSize = 13.sp)
            }
            day.items.withIndex().chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (i, item) ->
                        OutfitCard(item, i in checked, pal, Modifier.weight(1f)) { s.toggle(i) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            PeriodsCard(day, f, pal)
        }
    }
}

@Composable
private fun OutfitCard(item: Item, checked: Boolean, pal: Pal, modifier: Modifier, onToggle: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pal.card)
            .border(2.dp, if (checked) pal.accent else pal.card, RoundedCornerShape(20.dp))
            .toggleable(value = checked, role = Role.Checkbox) { onToggle() }
            .padding(start = 14.dp, top = 12.dp, end = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                Icon(Ico.garment(item.icon), null, Modifier.size(22.dp), tint = pal.accent)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) pal.accent else Color.Transparent)
                    .border(2.dp, if (checked) pal.accent else pal.cardMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(Ico.check, null, Modifier.size(14.dp), tint = pal.onAccent)
            }
        }
        Text(item.name, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(item.why, color = pal.cardMuted, fontSize = 13.sp)
    }
}

@Composable
private fun PeriodsCard(day: DayAdvice, f: Fmt, pal: Pal) {
    Card(pal, padding = 12) {
        Row(Modifier.fillMaxWidth()) {
            day.periods.forEach { p ->
                val wet = p.rain >= 50
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name, color = pal.cardMuted, fontSize = 13.sp)
                    Icon(Ico.weather(p.code), null, Modifier.size(26.dp), tint = if (wet) pal.accent else pal.ink)
                    Text(f.t(p.feel), color = pal.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${p.rain}% rain", color = if (wet) pal.accent else pal.cardMuted, fontSize = 12.sp,
                        fontWeight = if (wet) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Week

@Composable
fun WeekScreen(s: AppState, pal: Pal) {
    val fc = s.forecast
    val f = Fmt(s.prefs.fahrenheit)
    Screen {
        Column {
            Text(s.prefs.city, color = pal.muted, fontSize = 13.sp)
            Text("Week ahead", color = pal.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Plan laundry and packing a few days early.", color = pal.muted, fontSize = 15.sp)
        }
        if (fc == null) {
            Text(if (s.error != null) "Couldn't load the forecast." else "Loading…", color = pal.muted)
            return@Screen
        }
        val lo = fc.days.minOf { it.tMin } - 1
        val hi = fc.days.maxOf { it.tMax } + 1
        Card(pal, padding = 0) {
            fc.days.forEachIndexed { i, d ->
                val name = when (i) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.width(84.dp)) {
                        Text(name, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(Advice.describe(d.code), color = pal.cardMuted, fontSize = 12.sp, maxLines = 1)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BoxWithConstraints(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(pal.track)) {
                            val start = ((d.tMin - lo) / (hi - lo)).toFloat()
                            val width = ((d.tMax - d.tMin) / (hi - lo)).toFloat().coerceAtLeast(0.04f)
                            Box(
                                Modifier
                                    .offset(x = maxWidth * start)
                                    .width(maxWidth * width)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (d.mood == Mood.Hot || d.tMax >= 22) Color(0xFFE08A2E) else pal.accent)
                            )
                        }
                        Text("${f.t(d.tMin)} – ${f.t(d.tMax)}", color = pal.cardMuted, fontSize = 13.sp)
                    }
                    Text(
                        d.tag, color = pal.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(pal.tint).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                if (i < fc.days.lastIndex) HorizontalDivider(color = pal.track)
            }
        }
        val rainy = fc.days.drop(1).filter { it.mood == Mood.Rainy }
        if (rainy.isNotEmpty()) {
            Card(pal) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.umbrella, null, Modifier.size(24.dp), tint = pal.accent)
                    Text(
                        "  Rain expected on " + rainy.joinToString(" and ") {
                            it.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                        } + ".",
                        color = pal.ink, fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(s: AppState, pal: Pal) {
    val p = s.prefs
    val ctx = LocalContext.current
    val locate = rememberLocate(s)
    var askCity by remember { mutableStateOf(false) }
    if (askCity) CityDialog({ askCity = false }) { s.setCity(it) }

    Screen {
        Text("Settings", color = pal.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Label("Notifications", pal.muted)
        Card(pal) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Morning reminder", color = pal.ink, fontSize = 16.sp)
                    val next = SimpleDateFormat("EEEE d MMM, HH:mm", Locale.ENGLISH).format(Date(Scheduler.nextTime(p.hour, p.minute)))
                    Text("Next one: $next", color = pal.cardMuted, fontSize = 13.sp)
                }
                Pill("%02d:%02d".format(p.hour, p.minute), pal) {
                    pickTime(ctx, p.hour, p.minute) { h, m ->
                        p.hour = h; p.minute = m
                        Scheduler.scheduleAll(ctx)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = pal.track)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Evening preview", color = pal.ink, fontSize = 16.sp)
                    Text("Tomorrow's outfit at 21:00", color = pal.cardMuted, fontSize = 13.sp)
                }
                Switch(p.evening, { p.evening = it; Scheduler.scheduleAll(ctx) })
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = pal.track)
            Pill("Send a test notification", pal, Modifier.fillMaxWidth(), icon = Ico.bell) { s.testNotification() }
        }

        Label("Location", pal.muted)
        Card(pal) {
            Text(if (p.useGps) "Using your location: ${p.city}" else p.city, color = pal.ink, fontSize = 16.sp)
            s.status?.let { Text(it, color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("My location", pal, Modifier.weight(1f), primary = true, icon = Ico.pin) { locate() }
                Pill("Type a city", pal, Modifier.weight(1f)) { askCity = true }
            }
        }

        Label("Advice", pal.muted)
        Card(pal) {
            Text("How you feel the cold", color = pal.ink, fontSize = 16.sp)
            Slider(
                value = p.comfort.toFloat(),
                onValueChange = { p.comfort = Math.round(it) },
                onValueChangeFinished = { s.rebuild() },
                valueRange = -2f..2f,
                steps = 3,
            )
            Row(Modifier.fillMaxWidth()) {
                Text("Run cold", color = pal.cardMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Normal", color = pal.cardMuted, fontSize = 12.sp)
                Text("Run warm", color = pal.cardMuted, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            Spacer(Modifier.height(14.dp))
            Text("Units", color = pal.ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf(false to "°C", true to "°F"), p.fahrenheit, pal) { p.fahrenheit = it; s.rebuild() }
        }

        Label("Appearance", pal.muted)
        Card(pal) {
            Segmented(listOf("System" to "System", "Light" to "Light", "Dark" to "Dark"), p.theme, pal) { p.theme = it }
        }

        Text("Weather data by Open-Meteo.com", color = pal.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ---------------------------------------------------------------- First launch

@Composable
fun SetupScreen(s: AppState, pal: Pal) {
    val p = s.prefs
    val ctx = LocalContext.current
    val locate = rememberLocate(s)
    var askCity by remember { mutableStateOf(false) }
    if (askCity) CityDialog({ askCity = false }) { s.setCity(it) }

    Screen {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(pal.accent), contentAlignment = Alignment.Center) {
            Icon(Ico.sun, null, Modifier.size(28.dp), tint = pal.onAccent)
        }
        Column {
            Text("Let's set up your mornings", color = pal.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            Text("Three quick choices. You can change them later.", color = pal.muted, fontSize = 15.sp)
        }

        Card(pal) {
            Label("1 · Where are you?", pal.cardMuted)
            Text(if (p.useGps) "Using your location: ${p.city}" else p.city, color = pal.ink, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            s.status?.let { Text(it, color = pal.cardMuted, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Use my location", pal, Modifier.weight(1f), primary = true) { locate() }
                Pill("Type a city", pal, Modifier.weight(1f)) { askCity = true }
            }
        }

        Card(pal) {
            Label("2 · When should we remind you?", pal.cardMuted)
            val presets = listOf(7 to 0, 7 to 30, 8 to 0, 8 to 30)
            val isPreset = (p.hour to p.minute) in presets
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (h, m) ->
                    TimeChip("%02d:%02d".format(h, m), p.hour == h && p.minute == m, pal, Modifier.weight(1f)) {
                        p.hour = h; p.minute = m
                    }
                }
            }
            TimeChip(if (isPreset) "Other time…" else "%02d:%02d".format(p.hour, p.minute), !isPreset, pal, Modifier.fillMaxWidth().padding(top = 6.dp)) {
                pickTime(ctx, p.hour, p.minute) { h, m -> p.hour = h; p.minute = m }
            }
        }

        Card(pal) {
            Label("3 · How do you feel the cold?", pal.cardMuted)
            Spacer(Modifier.height(10.dp))
            Segmented(listOf(-2 to "I run cold", 0 to "Normal", 2 to "I run warm"), p.comfort.coerceIn(-2, 2).let { if (it < 0) -2 else if (it > 0) 2 else 0 }, pal) { p.comfort = it }
            Text("\"I run cold\" suggests a warmer layer about 3° sooner.", color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Pill("Start", pal, Modifier.fillMaxWidth().height(54.dp), primary = true) {
            p.onboarded = true
            Scheduler.scheduleAll(ctx)
            s.refresh()
        }
    }
}

@Composable
private fun TimeChip(text: String, on: Boolean, pal: Pal, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (on) pal.tint else Color.Transparent)
            .border(1.dp, if (on) pal.accent else pal.track, RoundedCornerShape(22.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (on) pal.accent else pal.ink, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------- Bottom bar

@Composable
fun BottomBar(tab: Int, pal: Pal, onSelect: (Int) -> Unit) {
    val items = listOf<Pair<ImageVector, String>>(Ico.shirt to "Today", Ico.calendar to "Week", Ico.sliders to "Settings")
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(pal.card)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val on = i == tab
            Column(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) pal.tint else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (on) pal.accent else pal.cardMuted)
                Text(label, color = if (on) pal.accent else pal.cardMuted, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}
