package com.alex.dresscast

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Settings, saved on the phone. Reading them in the UI updates the screen when they change. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("dresscast", Context.MODE_PRIVATE)

    private val all = mutableListOf<Pref<*>>()

    /** Re-read everything from disk, e.g. after a widget changed something while the app was closed. */
    fun reload() = all.forEach { it.reload() }

    private inner class Pref<T>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(read())

        init { all.add(this) }

        fun reload() { state.value = read() }

        @Suppress("UNCHECKED_CAST")
        private fun read(): T = when (def) {
            is String -> (sp.getString(key, def) ?: def) as T
            is Int -> sp.getInt(key, def) as T
            is Float -> sp.getFloat(key, def) as T
            is Boolean -> sp.getBoolean(key, def) as T
            else -> def
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val e = sp.edit()
            when (value) {
                is String -> e.putString(key, value)
                is Int -> e.putInt(key, value)
                is Float -> e.putFloat(key, value)
                is Boolean -> e.putBoolean(key, value)
            }
            e.commit()
        }
    }

    var city: String by Pref("city", "Athens, Greece")
    var lat: Float by Pref("lat", 37.98f)
    var lon: Float by Pref("lon", 23.73f)
    var useGps: Boolean by Pref("useGps", false)
    var hour: Int by Pref("hour", 8)
    var minute: Int by Pref("minute", 0)
    /** A different morning reminder time on Saturdays and Sundays. */
    var wkTime: Boolean by Pref("wkTime", false)
    var wkHour: Int by Pref("wkHour", 9)
    var wkMinute: Int by Pref("wkMinute", 0)
    /** -2 = runs cold … 2 = runs warm. */
    var comfort: Int by Pref("comfort", 0)
    var fahrenheit: Boolean by Pref("fahrenheit", false)
    /** "System", "Light" or "Dark". */
    var theme: String by Pref("theme", "System")
    var evening: Boolean by Pref("evening", true)
    var onboarded: Boolean by Pref("onboarded", false)
    var checkedDay: String by Pref("checkedDay", "")
    var checked: String by Pref("checked", "")
    /** Learned from "too cold / too warm" answers, in °C. Negative = you feel colder. */
    var learn: Float by Pref("learn", 0f)
    var feedbackDay: String by Pref("feedbackDay", "")
    /** The last answer: -1 too cold, 0 just right, 1 too warm. */
    var feedbackLast: Int by Pref("feedbackLast", 0)
    /** The hours you're usually outside; advice only looks at these. */
    var outFrom: Int by Pref("outFrom", 7)
    var outTo: Int by Pref("outTo", 22)
    /** Different hours outside on Saturday and Sunday. */
    var weekend: Boolean by Pref("weekend", false)
    var wFrom: Int by Pref("wFrom", 10)
    var wTo: Int by Pref("wTo", 24)
    /** Saved places as JSON: [{"name":…,"lat":…,"lon":…}]. */
    var savedPlaces: String by Pref("savedPlaces", "[]")
    var rainAlert: Boolean by Pref("rainAlert", true)
    /** "System", "en" or "el". */
    var lang: String by Pref("lang", "System")
    var lastUpdateCheck: String by Pref("lastUpdateCheck", "0")
    // Plans and calendars
    var plans: String by Pref("plans", "[]")
    var calOn: Boolean by Pref("calOn", false)
    /** Calendar ids switched off, comma-separated (all others are on). */
    var calOff: String by Pref("calOff", "")
    var calOnlyOut: Boolean by Pref("calOnlyOut", false)
    // Weather warnings
    var warnOn: Boolean by Pref("warnOn", true)
    /** 1 = yellow and above, 2 = orange and above, 3 = red only. */
    var warnLevel: Int by Pref("warnLevel", 1)
    var warnNotified: String by Pref("warnNotified", "")
    /** Cached region lookup: "lat,lon|EMMA ids|feed". */
    var emma: String by Pref("emma", "")
    // Pollen
    var pollenOn: Boolean by Pref("pollenOn", true)
    var pollenTypes: String by Pref("pollenTypes", "olive,grass")
    var pollenNotifiedDay: String by Pref("pollenNotifiedDay", "")
    /** Accessories never to suggest, comma-separated: glasses, cap, umbrella, scarf, boots. */
    var noItems: String by Pref("noItems", "")
    val off: Set<String> get() = noItems.split(',').filter { it.isNotBlank() }.toSet()
    var wardrobeV: Int by Pref("wardrobeV", 0)
    // Day tips and sunscreen
    var tipLaundry: Boolean by Pref("tipLaundry", true)
    var tipBeach: Boolean by Pref("tipBeach", true)
    var tipCar: Boolean by Pref("tipCar", true)
    var sunOn: Boolean by Pref("sunOn", true)
    var sunNotifiedDay: String by Pref("sunNotifiedDay", "")
    var shareStyle: Int by Pref("shareStyle", 0)
    var dynamic: Boolean by Pref("dynamic", false)
    var diary: String by Pref("diary", "[]")
    var trip: String by Pref("trip", "")
    var tripChecked: String by Pref("tripChecked", "")
    var tripCache: String by Pref("tripCache", "")
    var tripRemind: Boolean by Pref("tripRemind", true)
    var profiles: String by Pref("profiles", "[]")
    var tipAir: Boolean by Pref("tipAir", true)
    var shareCity: Boolean by Pref("shareCity", true)
    /** The newest version we've already sent a notification about. */
    var updateNotified: String by Pref("updateNotified", "")

    /** The hours outside that apply on [date]: weekend hours on Sat/Sun if set. 24 means midnight. */
    fun hoursFor(date: java.time.LocalDate): Pair<Int, Int> {
        val wk = date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY
        val (a, b) = if (weekend && wk) wFrom to wTo else outFrom to outTo
        var from = minOf(a, b).coerceIn(0, 23)
        var to = (maxOf(a, b) - if (maxOf(a, b) >= 24) 1 else 0).coerceIn(from, 23)
        // Your own plans outside stretch the day, e.g. a 21:00 dinner when you're usually home by 20:00.
        PlanStore.load(this).filter { it.where != "in" && it.occursOn(date) }.forEach { pl ->
            from = minOf(from, pl.startHour)
            to = maxOf(to, pl.endHour.coerceAtMost(23))
        }
        return from to to
    }

    /** How many degrees to add to the real temperature before choosing clothes. */
    val shift: Double get() = comfort * 1.5 + learn
}
