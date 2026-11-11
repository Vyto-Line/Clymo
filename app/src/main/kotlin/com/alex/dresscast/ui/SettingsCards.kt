package com.alex.dresscast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.L.t
import com.alex.dresscast.Place
import com.alex.dresscast.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

// ---------------------------------------------------------------- saved places

private fun savedList(json: String): List<Place> = runCatching {
    val a = JSONArray(json)
    (0 until a.length()).map { a.getJSONObject(it) }.map { Place(it.getString("name"), it.getDouble("lat"), it.getDouble("lon")) }
}.getOrDefault(emptyList())

private fun savedJson(list: List<Place>): String =
    JSONArray(list.map { JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon) }).toString()

/** Location: automatic switch, search with suggestions, and saved places. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationCard(s: AppState, pal: Pal) {
    val p = s.prefs
    val locate = rememberLocate(s)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Look up suggestions shortly after typing stops.
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = withContext(Dispatchers.IO) { runCatching { Repo.suggest(query) }.getOrDefault(emptyList()) }
        searching = false
    }

    fun choose(place: Place) {
        p.city = place.name
        p.lat = place.lat.toFloat()
        p.lon = place.lon.toFloat()
        p.useGps = false
        query = ""
        results = emptyList()
        s.refresh()
    }

    Card(pal) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(pal.tint), contentAlignment = Alignment.Center) {
                Icon(Ico.pin, null, Modifier.size(22.dp), tint = pal.accent)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(p.city, color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    s.status ?: if (p.useGps) t("Automatically from your location", "Αυτόματα από την τοποθεσία σου") else t("Chosen city", "Επιλεγμένη πόλη"),
                    color = pal.cardMuted, fontSize = 12.sp,
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = pal.track)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Automatic location", "Αυτόματη τοποθεσία"), color = pal.ink, fontSize = 15.sp)
                Text(t("Follows your phone wherever you are", "Ακολουθεί το κινητό όπου κι αν είσαι"), color = pal.cardMuted, fontSize = 12.sp)
            }
            Switch(p.useGps, { on -> if (on) locate() else p.useGps = false })
        }

        Text(t("Or search for a city", "Ή ψάξε πόλη"), color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(14.dp)).background(pal.track).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Ico.search, null, Modifier.size(18.dp), tint = pal.cardMuted)
            Box(Modifier.weight(1f).padding(start = 8.dp)) {
                if (query.isEmpty()) Text(t("e.g. Thessaloniki", "π.χ. Θεσσαλονίκη"), color = pal.cardMuted, fontSize = 15.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = pal.ink, fontSize = 15.sp, fontFamily = Fonts.body),
                    cursorBrush = SolidColor(pal.accent), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (searching && results.isEmpty()) Text(t("Searching…", "Αναζήτηση…"), color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        if (results.isNotEmpty()) {
            Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, pal.track, RoundedCornerShape(14.dp))) {
                results.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = pal.track)
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { choose(r) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(r.name.substringBefore(","), color = pal.ink, fontSize = 15.sp)
                        if (r.region.isNotBlank()) Text(r.region, color = pal.cardMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        // Saved places: tap to switch, long-press to remove.
        val saved = savedList(p.savedPlaces)
        Text(t("Saved places", "Αποθηκευμένα μέρη"), color = pal.cardMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            saved.forEach { place ->
                val on = !p.useGps && p.city == place.name
                Box(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) pal.tint else Color.Transparent)
                        .border(1.dp, if (on) pal.accent else pal.track, RoundedCornerShape(20.dp))
                        .pointerInput(place) {
                            detectTapGestures(
                                onTap = { choose(place) },
                                onLongPress = { p.savedPlaces = savedJson(saved.filter { it.name != place.name }) },
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(place.name.substringBefore(","), color = if (on) pal.accent else pal.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val already = saved.any { it.name == p.city }
            if (!already) {
                Box(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, pal.cardMuted, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) {
                            p.savedPlaces = savedJson(saved + Place(p.city, p.lat.toDouble(), p.lon.toDouble()))
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(t("+ Save ", "+ Αποθήκευση ") + p.city.substringBefore(","), color = pal.muted, fontSize = 14.sp)
                }
            }
        }
        if (saved.isNotEmpty()) Text(t("Long-press a place to remove it.", "Πάτα παρατεταμένα ένα μέρος για να το αφαιρέσεις."),
            color = pal.cardMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---------------------------------------------------------------- hours outside

private fun hhm(h: Int) = "%02d:00".format(h)

/** One two-handle slider for leaving and coming back, presets, and optional weekend hours. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HoursCard(s: AppState, pal: Pal) {
    val p = s.prefs
    Card(pal) {
        Range(t("I'm usually out", "Συνήθως είμαι έξω"), p.outFrom, p.outTo, pal) { a, b ->
            p.outFrom = a; p.outTo = b; s.rebuild()
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(t("Work 08–18", "Δουλειά 08–18"), 8, 18), Triple(t("All day 07–23", "Όλη μέρα 07–23"), 7, 23), Triple(t("Morning 07–13", "Πρωί 07–13"), 7, 13))
                .forEach { (label, a, b) ->
                    val on = p.outFrom == a && p.outTo == b
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) pal.tint else Color.Transparent)
                            .border(1.dp, if (on) pal.accent else pal.track, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) { p.outFrom = a; p.outTo = b; s.rebuild() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) { Text(label, color = if (on) pal.accent else pal.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = pal.track)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("Different hours at the weekend", "Άλλες ώρες το Σαββατοκύριακο"), color = pal.ink, fontSize = 15.sp)
                Text(t("For Saturday and Sunday", "Για Σάββατο και Κυριακή"), color = pal.cardMuted, fontSize = 12.sp)
            }
            Switch(p.weekend, { p.weekend = it; s.rebuild() })
        }
        if (p.weekend) {
            Range(t("At the weekend", "Το Σαββατοκύριακο"), p.wFrom, p.wTo, pal, Modifier.padding(top = 10.dp)) { a, b ->
                p.wFrom = a; p.wTo = b; s.rebuild()
            }
        }
        Text(t("Outfit advice and the rain warning only count these hours.", "Οι προτάσεις για ρούχα και η ειδοποίηση βροχής λαμβάνουν υπόψη μόνο αυτές τις ώρες."),
            color = pal.cardMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Range(title: String, from: Int, to: Int, pal: Pal, modifier: Modifier = Modifier, onDone: (Int, Int) -> Unit) {
    var range by remember(from, to) { mutableStateOf(from.toFloat()..to.toFloat()) }
    val a = range.start.roundToInt()
    val b = range.endInclusive.roundToInt()
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, color = pal.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("${hhm(a)} – ${hhm(b)}", color = pal.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display)
        }
        RangeSlider(
            value = range,
            onValueChange = { r ->
                // Keep at least one hour between leaving and coming back.
                val s = r.start.roundToInt().coerceIn(0, 23)
                val e = r.endInclusive.roundToInt().coerceIn(s + 1, 24)
                range = s.toFloat()..e.toFloat()
            },
            onValueChangeFinished = { onDone(a, b) },
            valueRange = 0f..24f,
            steps = 23,
            colors = SliderDefaults.colors(activeTrackColor = pal.accent, thumbColor = pal.accent, inactiveTrackColor = pal.track,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach { Text(it, color = pal.cardMuted, fontSize = 11.sp) }
        }
    }
}
