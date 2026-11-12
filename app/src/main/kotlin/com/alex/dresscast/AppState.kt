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

    // ---- Plans, calendars, warnings, pollen
    var warnings by mutableStateOf<List<Warning>>(emptyList())
    var pollen by mutableStateOf<List<PollenDay>>(emptyList())
    /** Today's plans and calendar events, with what the weather does during each. */
    var plans by mutableStateOf<List<Pair<PlanItem, PlanTip?>>>(emptyList())
    var calendars by mutableStateOf<List<PhoneCalendar>>(emptyList())
    var tips by mutableStateOf<List<DayTip>>(emptyList())
    var sun by mutableStateOf<SunInfo?>(null)

    // ---- Updates
    var update by mutableStateOf<Release?>(null)
    var updateStatus by mutableStateOf<String?>(null)
    var updateProgress by mutableStateOf<Float?>(null)

    private val scope = MainScope()
    private var lastJson: JSONObject? = null

    init {
        Wardrobe.migrate(prefs)
        L.use(prefs)
        // Show the saved forecast straight away, then refresh.
        Repo.cached(ctx)?.let { c ->
            lastJson = c.json
            fetchedAt = c.fetchedAt
            forecast = runCatching { Advice.build(c.json, prefs) }.getOrNull()
            details = forecast?.days?.firstOrNull()?.let { DetailsBuilder.build(c.json, prefs, it) }
            extras(c.json)
        }
        warnings = Warnings.cached(ctx, prefs)
    }

    /** Plans, pollen and calendars from the saved forecast (no download). */
    private fun extras(json: JSONObject) {
        pollen = runCatching { Pollen.days(json, prefs) }.getOrDefault(emptyList())
        tips = runCatching { Tips.today(json, prefs) }.getOrDefault(emptyList())
        sun = if (prefs.sunOn) runCatching { Tips.sun(json, prefs) }.getOrNull() else null
        calendars = Calendars.list(ctx)
        val h = runCatching { Hourly(json) }.getOrNull()
        plans = Calendars.itemsFor(ctx, prefs, LocalDate.now()).map { it to h?.let { hh -> PlanAdvice.of(it, hh, prefs) } }
    }

    /** Downloads the official warnings for your area. */
    fun refreshWarnings() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { Warnings.fetch(ctx, prefs) }.getOrNull() }
            if (list != null) {
                warnings = list
                withContext(Dispatchers.IO) { Notifier.warnings(ctx, Warnings.fresh(prefs, list)) }
            } else warnings = Warnings.cached(ctx, prefs)
        }
    }

    fun savePlan(plan: Plan) {
        PlanStore.upsert(prefs, plan)
        rebuild()
    }

    fun deletePlan(id: String) {
        PlanStore.delete(prefs, id)
        rebuild()
    }

    /** The weather and tip for a plan that's still being edited, for the preview in the sheet. */
    fun preview(plan: Plan, date: LocalDate): PlanTip? {
        val json = lastJson ?: return null
        val item = PlanItem("preview", plan.title, date, plan.start, plan.end, plan.where, "", 0, plan)
        return runCatching { PlanAdvice.of(item, Hourly(json), prefs) }.getOrNull()
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
            updateStatus = t("Allow Clymo to install updates, then tap Update again.", "Επίτρεψε στο Clymo να εγκαθιστά ενημερώσεις και πάτησε ξανά Ενημέρωση.")
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
                refreshWarnings()
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
        extras(json)
        Scheduler.scheduleRain(ctx, fc)
        Scheduler.schedulePlans(ctx)
        Scheduler.scheduleSun(ctx, json)
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

    // ---- Ticked-off outfit items, remembered for today only (shared with the checklist widget).
    fun checked(): Set<Int> = Checks.get(prefs)

    fun toggle(i: Int) {
        Checks.toggle(prefs, i)
        Widget.updateAll(ctx, forecast)
    }

    // ---- "How was today's outfit?"
    val askFeedback: Boolean get() = prefs.feedbackDay != LocalDate.now().toString()

    /** -1 = too cold, 0 = just right, 1 = too warm. */
    fun feedback(answer: Int) {
        forecast?.days?.firstOrNull { it.date == LocalDate.now() }?.let { runCatching { Diary.record(prefs, it, answer) } }
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
