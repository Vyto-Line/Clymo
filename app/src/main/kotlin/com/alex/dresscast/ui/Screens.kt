package com.alex.dresscast.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.Advice
import com.alex.dresscast.AppState
import com.alex.dresscast.DayAdvice
import com.alex.dresscast.Fmt
import com.alex.dresscast.Issue
import com.alex.dresscast.Item
import com.alex.dresscast.L
import com.alex.dresscast.L.t
import com.alex.dresscast.Mood
import com.alex.dresscast.Scheduler
import com.alex.dresscast.Updater
import com.alex.dresscast.hh
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun Screen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun Title(text: String, pal: Pal) {
    Text(text, color = pal.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display, lineHeight = 34.sp)
}

private fun open(ctx: Context, issue: Issue) {
    runCatching { ctx.startActivity(issue.intent) }
}

// ---------------------------------------------------------------- Today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(s: AppState, pal: Pal, onOpenDetails: () -> Unit = {}) {
    val ctx = LocalContext.current
    val fc = s.forecast
    val day = fc?.days?.firstOrNull()
    val f = Fmt(s.prefs.fahrenheit)
    PullToRefreshBox(isRefreshing = s.pulling, onRefresh = { s.refresh(byUser = true) }, modifier = Modifier.fillMaxSize()) {
        Screen {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.pin, null, Modifier.size(16.dp), tint = pal.ink)
                Text(" " + s.prefs.city, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", L.locale)), color = pal.muted, fontSize = 13.sp)
            }

            s.issues.firstOrNull()?.let { issue ->
                Card(pal, Modifier.border(2.dp, pal.accent, RoundedCornerShape(20.dp))) {
                    Text(t("Your reminder might not arrive", "Η υπενθύμιση μπορεί να μην έρθει"), color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(issue.title + ". " + issue.text, color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                    Pill(issue.button, pal, primary = true) { open(ctx, issue) }
                }
            }

            if (s.update != null) UpdateCard(s, pal, compact = true)

            // Hero: drawn weather scene with today's summary on top. Tap for details.
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val heroScale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "hero")
            Box(
                Modifier
                    .enter(0)
                    .graphicsLayer { scaleX = heroScale; scaleY = heroScale }
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(pal.scene.sky)
                    .clickable(interactionSource = press, indication = null, enabled = day != null, role = Role.Button,
                        onClickLabel = t("Show details", "Λεπτομέρειες"), onClick = onOpenDetails)
            ) {
                WeatherScene(day?.mood ?: Mood.Mild, pal.scene, Modifier.fillMaxSize(), storm = (day?.code ?: 0) >= 95)
                if (day != null) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.8f))
                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("Details", "Λεπτομέρειες"), color = Color(0xFF14181F), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Ico.chevron, null, Modifier.size(14.dp), tint = Color(0xFF14181F))
                    }
                }
                Column(Modifier.padding(start = 18.dp, top = 12.dp, end = 96.dp)) {
                    CountUp(day?.tMax, f, pal.scene.ink)
                    Text(
                        day?.headline ?: if (s.error != null) t("Couldn't load the weather", "Δεν φόρτωσε ο καιρός") else t("Loading…", "Φόρτωση…"),
                        color = pal.scene.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display, lineHeight = 26.sp,
                    )
                    Text(day?.sub ?: (s.error?.let { t("Check your internet connection.", "Έλεγξε τη σύνδεσή σου.") } ?: ""),
                        color = pal.scene.ink, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }

            s.offlineSince?.let {
                val time = SimpleDateFormat("HH:mm", L.locale).format(Date(it))
                Text(t("Offline · showing the forecast from $time", "Εκτός σύνδεσης · πρόγνωση από τις $time"), color = pal.muted, fontSize = 13.sp)
            }

            if (day != null) {
                val checked = s.checked()
                Row(Modifier.fillMaxWidth()) {
                    Label(t("Today, wear", "Σήμερα φόρεσε"), pal.muted, Modifier.weight(1f))
                    Text(t("${checked.count { it < day.items.size }} of ${day.items.size} ready", "${checked.count { it < day.items.size }} από ${day.items.size} έτοιμα"),
                        color = pal.muted, fontSize = 13.sp)
                }
                day.items.withIndex().chunked(2).forEachIndexed { r, row ->
                    Row(Modifier.enter(1 + r), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (i, item) ->
                            OutfitCard(item, i in checked, pal, Modifier.weight(1f)) { s.toggle(i) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Box(Modifier.enter(3)) { PeriodsCard(day, f, pal) }
                Box(Modifier.enter(4)) { Feedback(s, pal) }
            }
        }
    }
}

/** "A new version is available" with an Update button and download progress. */
@Composable
private fun UpdateCard(s: AppState, pal: Pal, compact: Boolean) {
    val r = s.update ?: return
    val ctx = LocalContext.current
    Card(pal, Modifier.border(2.dp, pal.accent, RoundedCornerShape(20.dp))) {
        Text(t("Update available: DressCast ${r.version}", "Νέα έκδοση: DressCast ${r.version}"), color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(t("You have ${Updater.currentName(ctx)}.", "Έχεις την ${Updater.currentName(ctx)}."), color = pal.cardMuted, fontSize = 13.sp)
        val notes = r.notes.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.contains("DressCast.apk") }
        if (!compact && notes.isNotEmpty()) {
            Text(notes.joinToString("\n"), color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        val progress = s.updateProgress
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = pal.accent,
                trackColor = pal.track,
            )
            Text(t("Downloading… ${(progress * 100).roundToInt()}%", "Λήψη… ${(progress * 100).roundToInt()}%"), color = pal.cardMuted, fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp))
        } else {
            s.updateStatus?.let { Text(it, color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Pill(t("Update", "Ενημέρωση"), pal, Modifier.padding(top = 10.dp), primary = true) { s.installUpdate(ctx) }
        }
    }
}

/** The big temperature, counting up to its value when it appears or changes. */
@Composable
private fun CountUp(value: Double?, f: Fmt, color: Color) {
    if (value == null) {
        Text("–", color = color, fontSize = 60.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display, lineHeight = 64.sp)
        return
    }
    val anim = remember { Animatable((value - 8).toFloat()) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }
    Text(f.t(anim.value.toDouble()), color = color, fontSize = 60.sp, fontWeight = FontWeight.Bold,
        fontFamily = Fonts.display, letterSpacing = (-2).sp, lineHeight = 64.sp)
}

@Composable
private fun Feedback(s: AppState, pal: Pal) {
    var thanks by remember { mutableStateOf<String?>(null) }
    thanks?.let {
        Card(pal) { Text(it, color = pal.ink, fontSize = 14.sp) }
        return
    }
    if (!s.askFeedback || LocalTime.now().hour < 12) return
    Card(pal) {
        Text(t("How was today's outfit?", "Πώς ήταν το σημερινό ντύσιμο;"), color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(t("Your answer tunes tomorrow's advice.", "Η απάντησή σου βελτιώνει τις αυριανές προτάσεις."), color = pal.cardMuted, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(t("Too cold", "Κρύωσα"), pal, Modifier.weight(1f)) {
                s.feedback(-1); thanks = t("Got it. We'll dress you a little warmer.", "Εντάξει. Θα σου προτείνουμε λίγο πιο ζεστά ρούχα.")
            }
            Pill(t("Just right", "Τέλεια"), pal, Modifier.weight(1f)) {
                s.feedback(0); thanks = t("Great, thanks!", "Τέλεια, ευχαριστούμε!")
            }
            Pill(t("Too warm", "Ζεστάθηκα"), pal, Modifier.weight(1f)) {
                s.feedback(1); thanks = t("Got it. We'll dress you a little lighter.", "Εντάξει. Θα σου προτείνουμε λίγο πιο ελαφριά ρούχα.")
            }
        }
    }
}

@Composable
private fun OutfitCard(item: Item, checked: Boolean, pal: Pal, modifier: Modifier, onToggle: () -> Unit) {
    // A little squeeze-and-bounce when ticked, and the ring and tick fade in.
    val bounce = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) { first = false; return@LaunchedEffect }
        bounce.animateTo(0.93f, tween(90))
        bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
    }
    val ring by animateColorAsState(if (checked) pal.accent else pal.card, tween(250), label = "ring")
    val fill by animateColorAsState(if (checked) pal.accent else Color.Transparent, tween(250), label = "fill")
    Column(
        modifier
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .clip(RoundedCornerShape(20.dp))
            .background(pal.card)
            .border(2.dp, ring, RoundedCornerShape(20.dp))
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
                    .background(fill)
                    .border(2.dp, if (checked) pal.accent else pal.cardMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(visible = checked, enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(Ico.check, null, Modifier.size(14.dp), tint = pal.onAccent)
                }
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
                    Text(f.t(p.feel), color = pal.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display)
                    Text(t("${p.rain}% rain", "${p.rain}% βροχή"), color = if (wet) pal.accent else pal.cardMuted, fontSize = 12.sp,
                        fontWeight = if (wet) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Week

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(s: AppState, pal: Pal) {
    val fc = s.forecast
    val f = Fmt(s.prefs.fahrenheit)
    PullToRefreshBox(isRefreshing = s.pulling, onRefresh = { s.refresh(byUser = true) }, modifier = Modifier.fillMaxSize()) {
        Screen {
            Column {
                Text(s.prefs.city, color = pal.muted, fontSize = 13.sp)
                Title(t("Week ahead", "Η εβδομάδα"), pal)
                Text(t("Plan laundry and packing a few days early.", "Οργάνωσε πλύσιμο και βαλίτσα από νωρίς."), color = pal.muted, fontSize = 15.sp)
            }
            if (fc == null || fc.days.isEmpty()) {
                Text(if (s.error != null) t("Couldn't load the forecast.", "Δεν φόρτωσε η πρόγνωση.") else t("Loading…", "Φόρτωση…"), color = pal.muted)
                return@Screen
            }
            val lo = fc.days.minOf { it.tMin } - 1
            val hi = fc.days.maxOf { it.tMax } + 1
            val grow = remember(fc) { Animatable(0f) }
            LaunchedEffect(fc) { grow.animateTo(1f, tween(900, delayMillis = 150, easing = FastOutSlowInEasing)) }
            Card(pal, Modifier.enter(0), padding = 0) {
                fc.days.forEachIndexed { i, d ->
                    val name = when (i) {
                        0 -> t("Today", "Σήμερα")
                        1 -> t("Tomorrow", "Αύριο")
                        else -> d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, L.locale)
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
                                val width = ((d.tMax - d.tMin) / (hi - lo)).toFloat().coerceAtLeast(0.04f) * grow.value
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
                        val days = rainy.joinToString(t(" and ", " και ")) { it.date.dayOfWeek.getDisplayName(TextStyle.FULL, L.locale) }
                        Text("  " + t("Rain expected on $days.", "Βροχή αναμένεται: $days."), color = pal.ink, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
private fun SettingRow(title: String, sub: String?, pal: Pal, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = pal.ink, fontSize = 16.sp)
            if (sub != null) Text(sub, color = pal.cardMuted, fontSize = 13.sp)
        }
        trailing()
    }
}

@Composable
private fun Divider(pal: Pal) = HorizontalDivider(Modifier.padding(vertical = 12.dp), color = pal.track)

@Composable
fun SettingsScreen(s: AppState, pal: Pal) {
    val p = s.prefs
    val ctx = LocalContext.current
    val locate = rememberLocate(s)
    var askCity by remember { mutableStateOf(false) }
    if (askCity) CityDialog({ askCity = false }) { s.setCity(it) }

    Screen {
        Title(t("Settings", "Ρυθμίσεις"), pal)

        Label(t("Reminders on time", "Υπενθυμίσεις στην ώρα τους"), pal.muted)
        Card(pal) {
            if (s.issues.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.check, null, Modifier.size(18.dp), tint = pal.accent)
                    Text("  " + t("All set. Your reminders can arrive on time.", "Όλα έτοιμα. Οι υπενθυμίσεις θα έρχονται στην ώρα τους."), color = pal.ink, fontSize = 15.sp)
                }
            } else {
                s.issues.forEachIndexed { i, issue ->
                    if (i > 0) Divider(pal)
                    SettingRow(issue.title, issue.text, pal) { Pill(issue.button, pal, primary = true) { open(ctx, issue) } }
                }
            }
        }

        Label(t("Notifications", "Ειδοποιήσεις"), pal.muted)
        Card(pal) {
            val next = SimpleDateFormat("EEEE d MMM, HH:mm", L.locale).format(Date(Scheduler.nextTime(p.hour, p.minute)))
            SettingRow(t("Morning reminder", "Πρωινή υπενθύμιση"), t("Next one: $next", "Επόμενη: $next"), pal) {
                Pill("%02d:%02d".format(p.hour, p.minute), pal) {
                    pickTime(ctx, p.hour, p.minute) { h, m ->
                        p.hour = h; p.minute = m
                        Scheduler.scheduleAll(ctx)
                    }
                }
            }
            Divider(pal)
            SettingRow(t("Evening preview", "Βραδινή προεπισκόπηση"), t("Tomorrow's outfit at 21:00", "Το αυριανό ντύσιμο στις 21:00"), pal) {
                Switch(p.evening, { p.evening = it; Scheduler.scheduleAll(ctx) })
            }
            Divider(pal)
            SettingRow(t("Rain warning", "Ειδοποίηση βροχής"), t("An hour before rain, while you're out", "Μία ώρα πριν βρέξει, όσο είσαι έξω"), pal) {
                Switch(p.rainAlert, { p.rainAlert = it; s.rebuild() })
            }
            Divider(pal)
            Pill(t("Send a test notification", "Δοκιμαστική ειδοποίηση"), pal, Modifier.fillMaxWidth(), icon = Ico.bell) { s.testNotification() }
        }

        Label(t("Your day", "Η μέρα σου"), pal.muted)
        Card(pal) {
            Text(t("When are you usually outside?", "Πότε είσαι συνήθως έξω;"), color = pal.ink, fontSize = 16.sp)
            Text(t("Advice only looks at these hours. If you're home by 17:00, evening rain won't count.",
                "Οι προτάσεις λαμβάνουν υπόψη μόνο αυτές τις ώρες. Αν είσαι σπίτι στις 17:00, η βραδινή βροχή δεν μετράει."),
                color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(t("From ", "Από ") + hh(p.outFrom), pal, Modifier.weight(1f)) {
                    pickTime(ctx, p.outFrom, 0) { h, _ -> p.outFrom = h; s.rebuild() }
                }
                Pill(t("Until ", "Έως ") + hh(p.outTo), pal, Modifier.weight(1f)) {
                    pickTime(ctx, p.outTo, 0) { h, _ -> p.outTo = h; s.rebuild() }
                }
            }
        }

        Label(t("Location", "Τοποθεσία"), pal.muted)
        Card(pal) {
            Text(if (p.useGps) t("Using your location: ", "Η τοποθεσία σου: ") + p.city else p.city, color = pal.ink, fontSize = 16.sp)
            s.status?.let { Text(it, color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(t("My location", "Τοποθεσία μου"), pal, Modifier.weight(1f), primary = true, icon = Ico.pin) { locate() }
                Pill(t("Type a city", "Γράψε πόλη"), pal, Modifier.weight(1f)) { askCity = true }
            }
        }

        Label(t("Advice", "Προτάσεις"), pal.muted)
        Card(pal) {
            ComfortQuestion(p.comfort, pal) { p.comfort = it; s.rebuild() }
            if (abs(p.learn) >= 0.5f) {
                Divider(pal)
                val deg = abs(p.learn).roundToInt().coerceAtLeast(1)
                val text = if (p.learn < 0)
                    t("From your feedback, we dress you about $deg° warmer.", "Από τις απαντήσεις σου, σε ντύνουμε περίπου $deg° πιο ζεστά.")
                else t("From your feedback, we dress you about $deg° lighter.", "Από τις απαντήσεις σου, σε ντύνουμε περίπου $deg° πιο ελαφριά.")
                SettingRow(text, null, pal) { Pill(t("Reset", "Επαναφορά"), pal) { s.resetLearning() } }
            }
            Divider(pal)
            Text(t("Units", "Μονάδες"), color = pal.ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf(false to "°C", true to "°F"), p.fahrenheit, pal) { p.fahrenheit = it; s.rebuild() }
        }

        Label(t("Accessories", "Αξεσουάρ"), pal.muted)
        Card(pal) {
            Text(t("Suggest these when the weather calls for them. Turn off anything you can't or don't want to wear.",
                "Πρότεινέ τα όταν το απαιτεί ο καιρός. Απενεργοποίησε ό,τι δεν μπορείς ή δεν θέλεις να φοράς."),
                color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
            val accessories = listOf(
                Triple("glasses", Ico.glasses, t("Sunglasses", "Γυαλιά ηλίου")),
                Triple("cap", Ico.sun, t("Caps and hats", "Καπέλα")),
                Triple("umbrella", Ico.umbrella, t("Umbrella", "Ομπρέλα")),
                Triple("scarf", Ico.scarf, t("Scarf and gloves", "Κασκόλ και γάντια")),
                Triple("boots", Ico.shoe, t("Boots and waterproof shoes", "Μπότες και αδιάβροχα παπούτσια")),
            )
            val off = p.off
            accessories.forEachIndexed { i, (key, icon, name) ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = pal.track)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = pal.accent)
                    }
                    Text(name, color = pal.ink, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Switch(key !in off, { on ->
                        p.noItems = (if (on) off - key else off + key).joinToString(",")
                        s.rebuild()
                    })
                }
            }
            if ("glasses" in off || "umbrella" in off) {
                Text(
                    listOfNotNull(
                        if ("glasses" in off) t("Without sunglasses, we'll suggest a cap on sunny days.", "Χωρίς γυαλιά ηλίου, θα προτείνουμε καπέλο τις ηλιόλουστες μέρες.") else null,
                        if ("umbrella" in off) t("Without an umbrella, we'll suggest a hooded rain jacket.", "Χωρίς ομπρέλα, θα προτείνουμε αδιάβροχο με κουκούλα.") else null,
                    ).joinToString(" "),
                    color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Label(t("Appearance", "Εμφάνιση"), pal.muted)
        Card(pal) {
            Text(t("Theme", "Θέμα"), color = pal.ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf("System" to t("System", "Σύστημα"), "Light" to t("Light", "Φωτεινό"), "Dark" to t("Dark", "Σκούρο")), p.theme, pal) { p.theme = it }
            Spacer(Modifier.height(14.dp))
            Text(t("Language", "Γλώσσα"), color = pal.ink, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            Segmented(listOf("System" to t("System", "Σύστημα"), "en" to "English", "el" to "Ελληνικά"), p.lang, pal) { s.setLanguage(it) }
        }

        Label(t("About", "Σχετικά"), pal.muted)
        if (s.update != null) UpdateCard(s, pal, compact = false)
        else Card(pal) {
            SettingRow("DressCast " + Updater.currentName(ctx), s.updateStatus, pal) {
                Pill(t("Check for updates", "Έλεγχος ενημερώσεων"), pal) { s.checkUpdate(byUser = true) }
            }
        }

        Text(t("Weather data by Open-Meteo.com", "Δεδομένα καιρού: Open-Meteo.com"), color = pal.muted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
            Title(t("Let's set up your mornings", "Ας ρυθμίσουμε τα πρωινά σου"), pal)
            Text(t("Three quick choices. You can change them later.", "Τρεις γρήγορες επιλογές. Μπορείς να τις αλλάξεις αργότερα."), color = pal.muted, fontSize = 15.sp)
        }

        Card(pal) {
            Label(t("1 · Where are you?", "1 · Πού βρίσκεσαι;"), pal.cardMuted)
            Text(if (p.useGps) t("Using your location: ", "Η τοποθεσία σου: ") + p.city else p.city, color = pal.ink, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            s.status?.let { Text(it, color = pal.cardMuted, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(t("Use my location", "Η τοποθεσία μου"), pal, Modifier.weight(1f), primary = true) { locate() }
                Pill(t("Type a city", "Γράψε πόλη"), pal, Modifier.weight(1f)) { askCity = true }
            }
        }

        Card(pal) {
            Label(t("2 · When should we remind you?", "2 · Πότε να σε ειδοποιούμε;"), pal.cardMuted)
            val presets = listOf(7 to 0, 7 to 30, 8 to 0, 8 to 30)
            val isPreset = (p.hour to p.minute) in presets
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (h, m) ->
                    TimeChip("%02d:%02d".format(h, m), p.hour == h && p.minute == m, pal, Modifier.weight(1f)) {
                        p.hour = h; p.minute = m
                    }
                }
            }
            TimeChip(if (isPreset) t("Other time…", "Άλλη ώρα…") else "%02d:%02d".format(p.hour, p.minute), !isPreset, pal,
                Modifier.fillMaxWidth().padding(top = 6.dp)) {
                pickTime(ctx, p.hour, p.minute) { h, m -> p.hour = h; p.minute = m }
            }
        }

        Card(pal) {
            Label(t("3 · Cold or warm?", "3 · Κρύο ή ζέστη;"), pal.cardMuted)
            Spacer(Modifier.height(10.dp))
            ComfortQuestion(p.comfort, pal) { p.comfort = it }
        }

        Pill(t("Start", "Ξεκίνα"), pal, Modifier.fillMaxWidth().height(54.dp), primary = true) {
            p.onboarded = true
            Scheduler.scheduleAll(ctx)
            s.refresh()
        }
    }
}

/** "Do you usually feel colder or warmer than other people?" Shared by Setup and Settings. */
@Composable
private fun ComfortQuestion(value: Int, pal: Pal, onChange: (Int) -> Unit) {
    Text(t("Do you usually feel colder or warmer than other people?", "Συνήθως κρυώνεις ή ζεσταίνεσαι περισσότερο από τους άλλους;"),
        color = pal.ink, fontSize = 16.sp, lineHeight = 21.sp)
    Spacer(Modifier.height(10.dp))
    val current = if (value < 0) -2 else if (value > 0) 2 else 0
    Segmented(
        listOf(-2 to t("Colder than most", "Κρυώνω εύκολα"), 0 to t("About the same", "Όπως οι περισσότεροι"), 2 to t("Warmer than most", "Ζεσταίνομαι εύκολα")),
        current, pal, onChange,
    )
    Text(
        t("If you're often cold, we'll suggest a warmer layer a little sooner.", "Αν κρυώνεις συχνά, θα σου προτείνουμε ζεστότερα ρούχα λίγο νωρίτερα."),
        color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
    )
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
    val items = listOf<Pair<ImageVector, String>>(
        Ico.shirt to t("Today", "Σήμερα"),
        Ico.calendar to t("Week", "Εβδομάδα"),
        Ico.sliders to t("Settings", "Ρυθμίσεις"),
    )
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
