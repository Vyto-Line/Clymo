package com.alex.dresscast.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.Details
import com.alex.dresscast.DetailsBuilder
import com.alex.dresscast.Fmt
import com.alex.dresscast.HourInfo
import com.alex.dresscast.L
import com.alex.dresscast.L.t
import com.alex.dresscast.Mood
import com.alex.dresscast.hh
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.roundToInt

/** "Today in detail": opened by tapping the weather picture. */
@Composable
fun DetailsScreen(s: AppState, pal: Pal, onBack: () -> Unit) {
    val fc = s.forecast
    val day = fc?.days?.firstOrNull()
    val d = s.details
    val f = Fmt(s.prefs.fahrenheit)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.card)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = t("Back", "Πίσω") },
                contentAlignment = Alignment.Center,
            ) { Icon(Ico.back, null, Modifier.size(20.dp), tint = pal.ink) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(t("Today in detail", "Η μέρα αναλυτικά"), color = pal.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display)
                Text(s.prefs.city.substringBefore(",") + " · " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", L.locale)),
                    color = pal.muted, fontSize = 13.sp)
            }
        }

        if (day == null || d == null) {
            Text(t("Loading…", "Φόρτωση…"), color = pal.muted)
            return@Column
        }

        Box(
            Modifier
                .enter(0)
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(pal.scene.sky)
        ) {
            WeatherScene(day.mood, pal.scene, Modifier.fillMaxSize(), storm = day.code >= 95)
            Column(Modifier.padding(start = 18.dp, top = 14.dp, end = 110.dp)) {
                Text(f.t(day.now ?: day.tMax), color = pal.scene.ink, fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display, lineHeight = 46.sp)
                Text(day.headline, color = pal.scene.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        day.feelNow?.let { t("Feels like ${f.t(it)} now", "Αίσθηση ${f.t(it)} τώρα") },
                        t("High ${f.t(day.tMax)} · Low ${f.t(day.tMin)}", "Μέγ. ${f.t(day.tMax)} · Ελάχ. ${f.t(day.tMin)}"),
                    ).joinToString(" · "),
                    color = pal.scene.ink, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }

        if (d.hours.size >= 2) {
            val nowHour = java.time.LocalTime.now().hour
            var sel by remember(d.hours) {
                mutableIntStateOf(d.hours.indexOfFirst { it.hour == nowHour }.takeIf { it >= 0 } ?: 0)
            }
            val listState = rememberLazyListState()
            LaunchedEffect(sel) { listState.animateScrollToItem((sel - 2).coerceAtLeast(0)) }
            Card(pal, Modifier.enter(1)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(t("Hour by hour", "Ώρα με την ώρα"), color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(t("Tap an hour", "Πάτα σε μια ώρα"), color = pal.cardMuted, fontSize = 12.sp)
                }
                HourChart(d.hours, f, pal, sel, { sel = it }, Modifier.fillMaxWidth().height(190.dp).padding(top = 8.dp))
                HourCard(d.hours[sel], d.hours[sel].hour == nowHour, f, pal, s.prefs.shift)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(pal.accent, t("Feels like", "Αίσθηση"), pal)
                    Legend(pal.cardMuted, t("Actual", "Πραγματική"), pal, dashed = true)
                    Legend(pal.accent.copy(alpha = 0.35f), t("Rain chance", "Πιθ. βροχής"), pal, bar = true)
                }
                LazyRow(Modifier.padding(top = 12.dp), state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(d.hours) { i, h -> HourChip(h, i == sel, f, pal) { sel = i } }
                }
            }
        }

        Card(pal, Modifier.enter(2)) {
            Text(t("Why this outfit", "Γιατί αυτό το ντύσιμο"), color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            d.reasons.forEach { r ->
                Row(Modifier.padding(top = 12.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                        Icon(Ico.garment(r.item.icon), null, Modifier.size(20.dp), tint = pal.accent)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(r.item.name, color = pal.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(r.text, color = pal.cardMuted, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }

        val stats = buildList {
            add(Triple(t("Wind", "Άνεμος"), "${d.wind.roundToInt()} km/h",
                listOf(DetailsBuilder.windFrom(d.windDir), t("gusts ${d.gusts.roundToInt()}", "ριπές ${d.gusts.roundToInt()}")).filter { it.isNotBlank() }.joinToString(", ")))
            add(Triple(t("UV index", "Δείκτης UV"), "${d.uv.roundToInt()} · ${DetailsBuilder.uvLevel(d.uv)}",
                if (d.uv >= 6) t("Sunscreen at midday", "Αντηλιακό το μεσημέρι") else t("Low risk for most of the day", "Χαμηλός κίνδυνος το μεγαλύτερο μέρος της μέρας")))
            d.humidityNow?.let { now ->
                val later = d.humidityLater
                add(Triple(t("Humidity", "Υγρασία"), "$now%",
                    if (later != null && later >= now + 10) t("Rising to $later% later", "Ανεβαίνει στο $later% αργότερα") else t("Steady through the day", "Σταθερή όλη μέρα")))
            }
            if (d.sunrise != null && d.sunset != null) {
                val dl = d.daylightMin?.let { t("${it / 60} h ${it % 60} min of daylight", "${it / 60} ώρ. ${it % 60} λεπ. φως ημέρας") } ?: ""
                add(Triple(t("Sunrise · sunset", "Ανατολή · δύση"), "${d.sunrise} · ${d.sunset}", dl))
            }
            add(Triple(t("Rain", "Βροχή"), "%.1f mm".format(d.rainMm),
                d.rainWindow?.let { (a, b) -> t("Mostly ${hh(a)}–${hh(b % 24)}", "Κυρίως ${hh(a)}–${hh(b % 24)}") } ?: t("No rain expected", "Δεν αναμένεται βροχή")))
            d.aqi?.let { aqi ->
                val (level, note) = DetailsBuilder.aqiLevel(aqi)
                add(Triple(t("Air quality", "Ποιότητα αέρα"), "$aqi · $level", note))
            }
        }
        stats.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.enter(3 + row), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, value, note) -> Stat(label, value, note, pal, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        DetailsBuilder.yesterdayText(d, s.prefs)?.let { text ->
            Card(pal, Modifier.enter(7)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                        Icon(if ((d.vsYesterday ?: 0.0) >= 0) Ico.up else Ico.down, null, Modifier.size(20.dp), tint = pal.accent)
                    }
                    Text(text, color = pal.ink, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        val updated = s.fetchedAt?.let { SimpleDateFormat("HH:mm", L.locale).format(Date(it)) }
        Text(
            (updated?.let { t("Updated $it · ", "Ενημέρωση $it · ") } ?: "") + t("Weather data by Open-Meteo.com", "Δεδομένα: Open-Meteo.com"),
            color = pal.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String, pal: Pal, dashed: Boolean = false, bar: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(if (bar) 8.dp else 16.dp).height(10.dp)) {
            if (bar) drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
            else drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 5f, cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null)
        }
        Text(" $label", color = pal.cardMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Stat(label: String, value: String, note: String, pal: Pal, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(pal.card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(L.locale), color = pal.cardMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(value, color = pal.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display, lineHeight = 24.sp)
        if (note.isNotBlank()) Text(note, color = pal.cardMuted, fontSize = 13.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun HourChip(h: HourInfo, selected: Boolean, f: Fmt, pal: Pal, onClick: () -> Unit) {
    val wet = h.rain >= 50
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) pal.tint else pal.track)
            .border(2.dp, if (selected) pal.accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(hh(h.hour), color = pal.cardMuted, fontSize = 12.sp)
        Icon(Ico.weather(h.code), null, Modifier.size(20.dp), tint = if (wet) pal.accent else pal.ink)
        Text(f.t(h.feel), color = pal.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("${h.rain}%", color = if (wet) pal.accent else pal.cardMuted, fontSize = 11.sp)
    }
}

/** The weather at the hour you tapped. */
@Composable
private fun HourCard(h: HourInfo, isNow: Boolean, f: Fmt, pal: Pal, shift: Double) {
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(pal.tint.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hh(h.hour) + if (isNow) t(" · now", " · τώρα") else "", color = pal.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display)
            Icon(Ico.weather(h.code), null, Modifier.padding(start = 10.dp).size(20.dp), tint = pal.accent)
            Text(" " + com.alex.dresscast.Advice.describe(h.code), color = pal.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(
                t("Temperature", "Θερμοκρασία") to f.t(h.temp),
                t("Feels like", "Αίσθηση") to f.t(h.feel),
                t("Rain", "Βροχή") to "${h.rain}%",
                t("Wind", "Άνεμος") to (h.wind?.let { "${it.roundToInt()} km/h" } ?: "–"),
            ).forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(label, color = pal.cardMuted, fontSize = 11.sp, maxLines = 1)
                    Text(value, color = pal.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(DetailsBuilder.hourTip(h.feel, h.rain, shift), color = pal.muted, fontSize = 13.sp)
    }
}

/** Feels-like line, actual temperature (dashed) and rain bars. Draws itself in when it appears. */
@Composable
private fun HourChart(hours: List<HourInfo>, f: Fmt, pal: Pal, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val progress = remember(hours) { Animatable(0f) }
    LaunchedEffect(hours) { progress.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    val measurer = rememberTextMeasurer()
    val lo = hours.minOf { minOf(it.feel, it.temp) } - 1
    val hi = hours.maxOf { maxOf(it.feel, it.temp) } + 1
    val warmest = hours.maxBy { it.feel }
    val coldest = hours.minBy { it.feel }
    val labelStyle = TextStyle(color = pal.cardMuted, fontSize = 11.sp, fontFamily = Fonts.body)
    val valueStyle = TextStyle(color = pal.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = Fonts.body)
    val desc = t("Chart of how warm it feels each hour, from ${f.t(coldest.feel)} to ${f.t(warmest.feel)} at ${hh(warmest.hour)}.",
        "Γράφημα της θερμοκρασίας ανά ώρα, από ${f.t(coldest.feel)} έως ${f.t(warmest.feel)} στις ${hh(warmest.hour)}.")

    // Tap or drag anywhere on the chart to pick the nearest hour.
    fun pick(x: Float, width: Float, density: Float) {
        val left = 14 * density
        val step = (width - 28 * density) / (hours.size - 1)
        onSelect(((x - left) / step).roundToInt().coerceIn(0, hours.lastIndex))
    }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Canvas(
        modifier
            .semantics { contentDescription = desc }
            .pointerInput(hours) { detectTapGestures { pick(it.x, size.width.toFloat(), density) } }
            .pointerInput(hours) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat(), density) } }
    ) {
        val p = progress.value
        val left = 14.dp.toPx()
        val right = size.width - 14.dp.toPx()
        val top = 18.dp.toPx()
        val lineBottom = size.height * 0.58f
        val barBottom = size.height - 20.dp.toPx()
        val barMax = barBottom - lineBottom - 14.dp.toPx()
        val step = (right - left) / (hours.size - 1)
        fun x(i: Int) = left + i * step
        fun y(v: Double) = (top + (hi - v) / (hi - lo) * (lineBottom - top)).toFloat()

        // Rain bars grow up.
        val barW = (step * 0.55f).coerceAtMost(14.dp.toPx())
        hours.forEachIndexed { i, h ->
            val bh = barMax * h.rain / 100f * p
            if (bh > 0.5f) drawRoundRect(
                if (h.rain >= 50) pal.accent else pal.accent.copy(alpha = 0.35f),
                topLeft = Offset(x(i) - barW / 2, barBottom - bh), size = Size(barW, bh),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            )
        }
        drawLine(pal.track, Offset(left, barBottom), Offset(right, barBottom), strokeWidth = 2f)

        fun line(values: List<Double>): Path = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
        }
        fun partial(path: Path): Path {
            val m = PathMeasure()
            m.setPath(path, false)
            val out = Path()
            m.getSegment(0f, m.length * p, out, true)
            return out
        }
        drawPath(partial(line(hours.map { it.temp })), pal.cardMuted, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        drawPath(partial(line(hours.map { it.feel })), pal.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // The hour you picked: a vertical line and a marker on the line.
        if (p >= 1f) {
            val sx = x(selected)
            drawLine(pal.accent.copy(alpha = 0.35f), Offset(sx, top - 6.dp.toPx()), Offset(sx, barBottom), strokeWidth = 2.dp.toPx())
            val c = Offset(sx, y(hours[selected].feel))
            drawCircle(pal.card, 7.dp.toPx(), c)
            drawCircle(pal.accent, 7.dp.toPx(), c, style = Stroke(3.dp.toPx()))
        }

        // Mark the coldest and warmest hours once the line reaches them.
        listOf(coldest, warmest).distinct().forEach { h ->
            val i = hours.indexOf(h)
            if (p >= i.toFloat() / (hours.size - 1)) {
                val c = Offset(x(i), y(h.feel))
                drawCircle(pal.card, 5.dp.toPx(), c)
                drawCircle(pal.accent, 5.dp.toPx(), c, style = Stroke(2.5f.dp.toPx()))
                val text = measurer.measure(f.t(h.feel), valueStyle)
                val ty = if (h == warmest) c.y - text.size.height - 6.dp.toPx() else c.y + 8.dp.toPx()
                drawText(text, topLeft = Offset((c.x - text.size.width / 2).coerceIn(0f, size.width - text.size.width), ty.coerceAtLeast(0f)))
            }
        }

        // Hour labels every few hours.
        val every = if (hours.size > 18) 3 else 2
        hours.forEachIndexed { i, h ->
            if (i % every == 0 || i == selected) {
                val style = if (i == selected) labelStyle.copy(color = pal.accent, fontWeight = FontWeight.Bold) else labelStyle
                val text = measurer.measure("%02d".format(h.hour), style)
                drawText(text, topLeft = Offset(x(i) - text.size.width / 2, barBottom + 4.dp.toPx()))
            }
        }
    }
}
