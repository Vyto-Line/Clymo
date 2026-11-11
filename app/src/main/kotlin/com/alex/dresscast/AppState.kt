package com.alex.dresscast

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alex.dresscast.L.t
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
    var details by mutableStateOf<Details?>(null)
    /** When the forecast on screen was downloaded. */
    var fetchedAt by mutableStateOf<Long?>(null)
    var error by mutableStateOf<String?>(null)
    /** Showing a saved forecast because the download failed; holds when it was saved. */
    var offlineSince by mutableStateOf<Long?>(null)
    var loading by mutableStateOf(false)
    var pulling by mutableStateOf(false)
    /** Messages about location / city search, shown in Settings and Setup. */
    var status by mutableStateOf<String?>(null)
    var issues by mutableStateOf<List<Issue>>(emptyList())
    /** Bumped when the language changes so every screen redraws. */
    var langVersion by mutableIntStateOf(0)

    // ---- Updates
    var update by mutableStateOf<Release?>(null)
    var updateStatus by mutableStateOf<String?>(null)
    var updateProgress by mutableStateOf<Float?>(null)

    private val scope = MainScope()
    private var lastJson: JSONObject? = null

    init {
        L.use(prefs)
        // Show the saved forecast straight away, then refresh.
        Repo.cached(ctx)?.let { c ->
            lastJson = c.json
            fetchedAt = c.fetchedAt
            forecast = runCatching { Advice.build(c.json, prefs) }.getOrNull()
            details = forecast?.days?.firstOrNull()?.let { DetailsBuilder.build(c.json, prefs, it) }
        }
    }

    /** Looks for a newer version: always when asked, otherwise at most every 6 hours. */
    fun checkUpdate(byUser: Boolean = false) {
        val last = prefs.lastUpdateCheck.toLongOrNull() ?: 0L
        if (!byUser && System.currentTimeMillis() - last < 6 * 3_600_000L) return
        if (byUser) updateStatus = t("Checking…", "Έλεγχος…")
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Updater.newer(ctx) } }
            prefs.lastUpdateCheck = System.currentTimeMillis().toString()
            r.onSuccess {
                update = it
                if (byUser) updateStatus = if (it == null) t("You have the latest version.", "Έχεις την τελευταία έκδοση.") else null
            }.onFailure {
                if (byUser) updateStatus = t("Couldn't check for updates. Are you online?", "Δεν έγινε έλεγχος. Είσαι συνδεδεμένος;")
            }
        }
    }

    /** Downloads the new version and opens Android's install screen. [activity] must be an Activity. */
    fun installUpdate(activity: Context) {
        val r = update ?: return
        if (updateProgress != null) return
        if (!Updater.canInstall(ctx)) {
            updateStatus = t("Allow DressCast to install updates, then tap Update again.", "Επίτρεψε στο DressCast να εγκαθιστά ενημερώσεις και πάτησε ξανά Ενημέρωση.")
            runCatching { activity.startActivity(Updater.allowInstallIntent(ctx)) }
            return
        }
        updateStatus = null
        updateProgress = 0f
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { Updater.download(ctx, r) { updateProgress = it } }
                Updater.install(activity, file)
            } catch (e: Exception) {
                updateStatus = t("Download failed. Try again.", "Η λήψη απέτυχε. Δοκίμασε ξανά.")
            } finally {
                updateProgress = null
            }
        }
    }

    fun refresh(byUser: Boolean = false) {
        if (loading) return
        loading = true
        pulling = byUser
        issues = Reliability.issues(ctx)
        checkUpdate()
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) { Repo.fetchJson(ctx, prefs) }
                lastJson = json
                fetchedAt = System.currentTimeMillis()
                offlineSince = null
                error = null
                rebuild()
            } catch (e: Exception) {
                val cached = withContext(Dispatchers.IO) { Repo.cached(ctx) }
                if (cached != null) {
                    lastJson = cached.json
                    fetchedAt = cached.fetchedAt
                    offlineSince = cached.fetchedAt
                    rebuild()
                } else {
                    error = e.message ?: t("Network error", "Σφάλμα δικτύου")
                }
            } finally {
                loading = false
                pulling = false
            }
        }
    }

    /** Re-run the advice after a setting change, without downloading again. */
    fun rebuild() {
        val json = lastJson ?: return refresh()
        L.use(prefs)
        val fc = runCatching { Advice.build(json, prefs) }.onFailure { error = it.message }.getOrNull() ?: return
        forecast = fc
        details = fc.days.firstOrNull()?.let { DetailsBuilder.build(json, prefs, it) }
        Scheduler.scheduleRain(ctx, fc)
        Widget.updateAll(ctx, fc)
    }

    fun setLanguage(lang: String) {
        prefs.lang = lang
        L.use(prefs)
        langVersion++
        rebuild()
    }

    fun setCity(name: String) {
        if (name.isBlank()) return
        status = t("Searching…", "Αναζήτηση…")
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
                status = e.message ?: t("Couldn't find that city", "Δεν βρέθηκε η πόλη")
            }
        }
    }

    fun locate() {
        status = t("Finding your location…", "Εύρεση τοποθεσίας…")
        Loc.current(ctx) { loc ->
            if (loc == null) {
                status = t("Couldn't get your location. Is location turned on?", "Δεν βρέθηκε η τοποθεσία. Είναι ενεργή η τοποθεσία;")
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
            val fc = withContext(Dispatchers.IO) { Repo.load(ctx, prefs) }
            if (fc != null) Notifier.show(ctx, fc, 0) else Notifier.error(ctx, t("no internet", "χωρίς σύνδεση"))
        }
    }

    // ---- Ticked-off outfit items, remembered for today only.
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

    // ---- "How was today's outfit?"
    val askFeedback: Boolean get() = prefs.feedbackDay != LocalDate.now().toString()

    /** -1 = too cold, 0 = just right, 1 = too warm. */
    fun feedback(answer: Int) {
        prefs.learn = (prefs.learn + answer * 0.75f).coerceIn(-3f, 3f)
        prefs.feedbackDay = LocalDate.now().toString()
        prefs.feedbackLast = answer
        if (answer != 0) rebuild()
    }

    fun resetLearning() {
        prefs.learn = 0f
        rebuild()
    }
}
