package com.alex.dresscast

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** Everything the screens show, plus the actions they can take. */
class AppState(val ctx: Context) {
    val prefs = Prefs(ctx)
    var forecast by mutableStateOf<Forecast?>(null)
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    /** Messages about location / city search, shown in Settings and Setup. */
    var status by mutableStateOf<String?>(null)

    private val scope = MainScope()
    private var lastJson: JSONObject? = null

    fun refresh() {
        loading = true
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) { Repo.fetchJson(ctx, prefs) }
                lastJson = json
                rebuild()
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Network error"
            } finally {
                loading = false
            }
        }
    }

    /** Re-run the advice after a setting change, without downloading again. */
    fun rebuild() {
        val json = lastJson ?: return refresh()
        forecast = runCatching { Advice.build(json, prefs.city, prefs.comfort, prefs.fahrenheit) }
            .onFailure { error = it.message }
            .getOrNull() ?: forecast
    }

    fun setCity(name: String) {
        if (name.isBlank()) return
        status = "Searching…"
        scope.launch {
            try {
                val place = withContext(Dispatchers.IO) { Repo.geocode(name) }
                prefs.city = place.name
                prefs.lat = place.lat.toFloat()
                prefs.lon = place.lon.toFloat()
                prefs.useGps = false
                status = null
                refresh()
            } catch (e: Exception) {
                status = e.message ?: "Couldn't find that city"
            }
        }
    }

    fun locate() {
        status = "Finding your location…"
        Loc.current(ctx) { loc ->
            if (loc == null) {
                status = "Couldn't get your location. Is location turned on?"
            } else {
                scope.launch {
                    val name = withContext(Dispatchers.IO) { Loc.placeName(ctx, loc.latitude, loc.longitude) }
                    Loc.save(prefs, loc.latitude, loc.longitude, name)
                    status = null
                    refresh()
                }
            }
        }
    }

    fun testNotification() {
        scope.launch {
            try {
                val fc = withContext(Dispatchers.IO) { Repo.load(ctx, prefs) }
                Notifier.show(ctx, fc, 0)
            } catch (e: Exception) {
                Notifier.error(ctx, e.message ?: "network error")
            }
        }
    }

    // Ticked-off outfit items, remembered for today only.
    fun checked(): Set<Int> =
        if (prefs.checkedDay == LocalDate.now().toString())
            prefs.checked.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        else emptySet()

    fun toggle(i: Int) {
        val now = checked().toMutableSet()
        if (!now.add(i)) now.remove(i)
        prefs.checkedDay = LocalDate.now().toString()
        prefs.checked = now.joinToString(",")
    }
}
