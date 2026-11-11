package com.alex.dresscast

import android.content.Context
import android.util.Xml
import com.alex.dresscast.L.t
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An official weather warning from the national weather service, via MeteoAlarm. */
data class Warning(
    val id: String,
    /** 1 yellow, 2 orange, 3 red. */
    val level: Int,
    /** MeteoAlarm awareness type: 1 wind, 2 snow/ice, 3 thunderstorm, 4 fog, 5 heat, 6 cold, 7 coastal, 8 fire, 10 rain, 12/13 flood. */
    val type: Int,
    val event: String,
    val headline: String,
    val description: String,
    val area: String,
    val sender: String,
    val onset: ZonedDateTime,
    val expires: ZonedDateTime,
) {
    val active get() = !onset.isAfter(ZonedDateTime.now())

    val color: Long get() = when (level) { 3 -> 0xFFD32F2F; 2 -> 0xFFF57C00; else -> 0xFFF2C200 }

    val levelName get() = when (level) {
        3 -> t("Red", "Κόκκινη"); 2 -> t("Orange", "Πορτοκαλί"); else -> t("Yellow", "Κίτρινη")
    }

    val typeName get() = when (type) {
        1 -> t("wind", "ανέμων")
        2 -> t("snow and ice", "χιονιού και παγετού")
        3 -> t("thunderstorms", "καταιγίδων")
        4 -> t("fog", "ομίχλης")
        5 -> t("high temperatures", "υψηλών θερμοκρασιών")
        6 -> t("low temperatures", "χαμηλών θερμοκρασιών")
        7 -> t("coastal events", "θαλάσσιων φαινομένων")
        8 -> t("forest fires", "πυρκαγιών")
        9 -> t("avalanches", "χιονοστιβάδων")
        10 -> t("rain", "βροχών")
        12, 13 -> t("flooding", "πλημμυρών")
        else -> event.lowercase(L.locale)
    }

    /** What it means for your clothes. */
    val tip get() = when (type) {
        1 -> t("Windproof jacket, and leave the umbrella", "Αντιανεμικό και άφησε την ομπρέλα")
        2 -> t("Warm layers and boots with grip", "Ζεστά ρούχα και μποτάκια με καλό πάτο")
        3, 10 -> t("Umbrella and waterproof shoes", "Ομπρέλα και αδιάβροχα παπούτσια")
        12, 13 -> t("Waterproof shoes; avoid underpasses and streams", "Αδιάβροχα παπούτσια· απόφυγε υπόγειες διαβάσεις και ρέματα")
        4 -> t("Something bright if you walk by the road", "Κάτι φωτεινό αν περπατάς δίπλα στον δρόμο")
        5 -> t("Light, loose clothes, a cap and water", "Ελαφριά, φαρδιά ρούχα, καπέλο και νερό")
        6 -> t("Warm coat, hat and gloves", "Ζεστό παλτό, σκούφος και γάντια")
        8 -> t("Avoid outdoor fires and forest walks", "Απόφυγε φωτιές και βόλτες σε δάση")
        else -> t("Check before you go out", "Έλεγξε πριν βγεις")
    }

    /** "Orange warning for thunderstorms" */
    val title get() = t("$levelName warning for $typeName", "$levelName προειδοποίηση $typeName")

    /** "Tomorrow 06:00–21:00" or "Until 21:00". */
    val whenText: String get() {
        val zone = ZoneId.systemDefault()
        val a = onset.withZoneSameInstant(zone)
        val b = expires.withZoneSameInstant(zone)
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val today = java.time.LocalDate.now()
        fun day(d: java.time.LocalDate) = when (d) {
            today -> t("Today", "Σήμερα")
            today.plusDays(1) -> t("Tomorrow", "Αύριο")
            else -> d.format(DateTimeFormatter.ofPattern("EEE d", L.locale)).replaceFirstChar { it.uppercase(L.locale) }
        }
        return if (active) {
            t("Now until ", "Τώρα έως ") + (if (b.toLocalDate() == today) "" else day(b.toLocalDate()) + " ") + b.format(hm)
        } else {
            day(a.toLocalDate()) + " " + a.format(hm) + "–" +
                (if (b.toLocalDate() == a.toLocalDate()) "" else day(b.toLocalDate()) + " ") + b.format(hm)
        }
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("level", level).put("type", type).put("event", event)
        .put("headline", headline).put("description", description).put("area", area).put("sender", sender)
        .put("onset", onset.toString()).put("expires", expires.toString())

    companion object {
        fun from(o: JSONObject) = Warning(
            o.getString("id"), o.getInt("level"), o.getInt("type"), o.optString("event"), o.optString("headline"),
            o.optString("description"), o.optString("area"), o.optString("sender"),
            ZonedDateTime.parse(o.getString("onset")), ZonedDateTime.parse(o.getString("expires")),
        )
    }
}

/** Your MeteoAlarm region (EMMA_ID) and its country feed. */
data class Region(val ids: Set<String>, val feed: String)

object Warnings {

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "warnings.json")

    /**
     * Which warning region a point is in. Uses the bundled region shapes; the answer is remembered
     * for the place so the 1.8 MB file is only read when you move.
     */
    fun region(ctx: Context, p: Prefs): Region? {
        val key = String.format(Locale.US, "%.2f,%.2f", p.lat, p.lon)
        p.emma.split('|').takeIf { it.size == 3 && it[0] == key }?.let {
            return if (it[1].isBlank()) null else Region(it[1].split(',').toSet(), it[2])
        }
        val x = p.lon.toDouble()
        val y = p.lat.toDouble()
        val all = runCatching { JSONArray(ctx.assets.open("emma.json").bufferedReader().use { it.readText() }) }.getOrNull() ?: return null
        val hits = mutableListOf<Pair<String, String>>()
        var nearest: Triple<String, String, Double>? = null
        var boxed: Triple<String, String, Double>? = null
        for (i in 0 until all.length()) {
            val o = all.getJSONObject(i)
            val b = o.getJSONArray("b")
            val minx = b.getDouble(0); val miny = b.getDouble(1); val maxx = b.getDouble(2); val maxy = b.getDouble(3)
            // Remember the closest region too, for points just off the coast.
            val dx = maxOf(minx - x, 0.0, x - maxx)
            val dy = maxOf(miny - y, 0.0, y - maxy)
            val d = dx + dy
            if (d > 0) {
                if (d < 0.15 && (nearest == null || d < nearest.third)) nearest = Triple(o.getString("c"), o.getString("k"), d)
                continue
            }
            val rings = o.getJSONArray("r")
            var inside = false
            for (r in 0 until rings.length()) {
                val ring = rings.getJSONArray(r)
                var j = ring.length() - 1
                for (k in 0 until ring.length()) {
                    val a = ring.getJSONArray(k); val c = ring.getJSONArray(j)
                    val ax = a.getDouble(0); val ay = a.getDouble(1); val cx = c.getDouble(0); val cy = c.getDouble(1)
                    if ((ay > y) != (cy > y) && x < (cx - ax) * (y - ay) / (cy - ay) + ax) inside = !inside
                    j = k
                }
            }
            if (inside) hits += o.getString("c") to o.getString("k")
            else {
                // Inside the box but not the shape (sea, a bay): keep the smallest such box as a last resort.
                val area = (maxx - minx) * (maxy - miny)
                if (boxed == null || area < boxed.third) boxed = Triple(o.getString("c"), o.getString("k"), area)
            }
        }
        val chosen = hits.ifEmpty { listOfNotNull((nearest ?: boxed)?.let { it.first to it.second }) }
        val region = chosen.takeIf { it.isNotEmpty() }?.let { Region(it.map { h -> h.first }.toSet(), it.first().second) }
        p.emma = key + "|" + (region?.ids?.joinToString(",") ?: "") + "|" + (region?.feed ?: "")
        return region
    }

    /** Downloads the warnings for your region, keeps those at your chosen level, saves them. Background thread. */
    fun fetch(ctx: Context, p: Prefs = Prefs(ctx)): List<Warning> {
        if (!p.warnOn) { cacheFile(ctx).delete(); return emptyList() }
        val region = region(ctx, p) ?: run { save(ctx, emptyList()); return emptyList() }
        val feed = get("https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-${region.feed}")
        val entries = parseFeed(feed).filter { it.emma in region.ids }
        val now = ZonedDateTime.now()
        val horizon = now.plusHours(48)
        val out = mutableListOf<Warning>()
        for (e in entries) {
            if (e.expires != null && e.expires.isBefore(now)) continue
            if (e.onset != null && e.onset.isAfter(horizon)) continue
            if (e.level < p.warnLevel) continue          // includes green "Minor" (0) entries
            val w = runCatching { e.cap?.let { parseCap(get(it), e) } }.getOrNull() ?: e.toWarning() ?: continue
            if (w.level < p.warnLevel || w.level < 1 || w.expires.isBefore(now) || w.onset.isAfter(horizon)) continue
            out += w
        }
        // The same warning often appears once per language or update; keep the strongest one per type and time.
        val list = out.groupBy { "${it.type}-${it.onset.toEpochSecond()}" }.map { (_, v) -> v.maxBy { it.level } }
            .sortedWith(compareByDescending<Warning> { it.active }.thenByDescending { it.level }.thenBy { it.onset })
        save(ctx, list)
        return list
    }

    fun cached(ctx: Context, p: Prefs = Prefs(ctx)): List<Warning> = if (!p.warnOn) emptyList() else runCatching {
        val a = JSONArray(cacheFile(ctx).readText())
        val now = ZonedDateTime.now()
        (0 until a.length()).map { Warning.from(a.getJSONObject(it)) }.filter { it.expires.isAfter(now) && it.level >= p.warnLevel }
    }.getOrDefault(emptyList())

    private fun save(ctx: Context, list: List<Warning>) = runCatching {
        cacheFile(ctx).writeText(JSONArray(list.map { it.toJson() }).toString())
    }

    /** Warnings not yet notified. Marks them as notified. */
    fun fresh(p: Prefs, list: List<Warning>): List<Warning> {
        val seen = p.warnNotified.split(',').filter { it.isNotBlank() }.toMutableList()
        val keyOf = { w: Warning -> "${w.type}-${w.level}-${w.onset.toEpochSecond()}" }
        val new = list.filter { keyOf(it) !in seen }
        seen += new.map(keyOf)
        p.warnNotified = seen.takeLast(40).joinToString(",")
        return new
    }

    private class Entry(
        val emma: String, val level: Int, val event: String, val area: String,
        val onset: ZonedDateTime?, val expires: ZonedDateTime?, val cap: String?, val id: String,
    ) {
        fun toWarning(): Warning? {
            if (onset == null || expires == null || level < 1) return null
            return Warning(id, level, typeFromEvent(event), event, event, "", area, "", onset, expires)
        }
    }

    private fun time(s: String?): ZonedDateTime? = s?.trim()?.takeIf { it.isNotEmpty() }?.let {
        runCatching { OffsetDateTime.parse(it).toZonedDateTime() }.getOrNull()
    }

    private fun severity(s: String) = when (s.trim().lowercase()) { "moderate" -> 1; "severe" -> 2; "extreme" -> 3; else -> 0 }

    private fun typeFromEvent(e: String): Int {
        val s = e.lowercase()
        return when {
            "wind" in s -> 1; "snow" in s || "ice" in s -> 2; "thunder" in s -> 3; "fog" in s -> 4
            "high temp" in s || "heat" in s -> 5; "low temp" in s || "cold" in s -> 6; "coast" in s -> 7
            "fire" in s -> 8; "avalanch" in s -> 9; "flood" in s -> 12; "rain" in s -> 10; else -> 0
        }
    }

    /** The Atom feed: one entry per warning and area, with the CAP link for the full text. */
    private fun parseFeed(xml: String): List<Entry> {
        val out = mutableListOf<Entry>()
        val x = Xml.newPullParser()
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        x.setInput(xml.reader())
        var inEntry = false
        var f = mutableMapOf<String, String>()
        var valueName = ""
        var ev = x.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val n = x.name
                    when {
                        n == "entry" -> { inEntry = true; f = mutableMapOf() }
                        inEntry && n == "link" && x.getAttributeValue(null, "type") == "application/cap+xml" ->
                            f["cap"] = x.getAttributeValue(null, "href") ?: ""
                        inEntry && (n == "valueName" || n == "cap:valueName") -> valueName = x.nextText()
                        inEntry && (n == "value" || n == "cap:value") -> {
                            val v = x.nextText()
                            if (valueName == "EMMA_ID") f["emma"] = v.trim()
                        }
                        inEntry && n in setOf("cap:event", "cap:areaDesc", "cap:onset", "cap:effective", "cap:expires", "cap:severity", "cap:identifier", "id") ->
                            f[n.removePrefix("cap:")] = x.nextText()
                    }
                }
                XmlPullParser.END_TAG -> if (x.name == "entry") {
                    inEntry = false
                    out += Entry(
                        emma = f["emma"] ?: "", level = severity(f["severity"] ?: ""), event = f["event"] ?: "",
                        area = f["areaDesc"] ?: "", onset = time(f["onset"] ?: f["effective"]), expires = time(f["expires"]),
                        cap = f["cap"]?.takeIf { it.isNotBlank() }, id = f["identifier"] ?: f["id"] ?: "",
                    )
                }
            }
            ev = x.next()
        }
        return out
    }

    /** The full CAP warning, in Greek or English when the service provides it. */
    private fun parseCap(xml: String, e: Entry): Warning? {
        data class Info(val lang: String, val m: MutableMap<String, String> = mutableMapOf())
        val infos = mutableListOf<Info>()
        var cur: Info? = null
        var pName = ""
        var identifier = e.id
        val x = Xml.newPullParser()
        x.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        x.setInput(xml.reader())
        var ev = x.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val n = x.name.substringAfter(':')
                when (n) {
                    "info" -> cur = Info("").also { infos += it }
                    "identifier" -> if (cur == null) identifier = x.nextText()
                    "valueName" -> pName = x.nextText()
                    "value" -> { val v = x.nextText(); if (cur != null && pName.startsWith("awareness")) cur.m[pName] = v }
                    "language", "event", "headline", "description", "senderName", "onset", "effective", "expires", "severity", "areaDesc" ->
                        if (cur != null) cur.m[n] = x.nextText()
                }
            }
            ev = x.next()
        }
        if (infos.isEmpty()) return null
        val want = if (L.greek) "el" else "en"
        val info = infos.firstOrNull { it.m["language"]?.lowercase()?.startsWith(want) == true }
            ?: infos.firstOrNull { it.m["language"]?.lowercase()?.startsWith("en") == true } ?: infos.first()
        val m = info.m
        val first = infos.first().m
        // "2; yellow; Moderate" → 1 (yellow), "3; orange; Severe" → 2, "4; red; Extreme" → 3
        val level = (m["awareness_level"] ?: first["awareness_level"])?.substringBefore(';')?.trim()?.toIntOrNull()?.minus(1)
            ?: severity(m["severity"] ?: first["severity"] ?: "").takeIf { it > 0 } ?: e.level
        val type = (m["awareness_type"] ?: first["awareness_type"])?.substringBefore(';')?.trim()?.toIntOrNull()
            ?: typeFromEvent(first["event"] ?: e.event)
        val onset = time(m["onset"] ?: first["onset"] ?: m["effective"]) ?: e.onset ?: return null
        val expires = time(m["expires"] ?: first["expires"]) ?: e.expires ?: return null
        return Warning(
            id = identifier, level = level.coerceIn(0, 3), type = type, event = m["event"] ?: e.event,
            headline = (m["headline"] ?: "").trim(), description = (m["description"] ?: "").trim(),
            area = m["areaDesc"] ?: e.area, sender = (m["senderName"] ?: first["senderName"] ?: "").trim(),
            onset = onset, expires = expires,
        )
    }

    private fun get(url: String): String {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15000
        con.readTimeout = 20000
        con.setRequestProperty("User-Agent", "DressCast (Android)")
        try {
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally {
            con.disconnect()
        }
    }
}
