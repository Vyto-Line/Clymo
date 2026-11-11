package com.alex.dresscast

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Settings, saved on the phone. Reading them in the UI updates the screen when they change. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("dresscast", Context.MODE_PRIVATE)

    private inner class Pref<T>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(read())

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
    /** The hours you're usually outside; advice only looks at these. */
    var outFrom: Int by Pref("outFrom", 7)
    var outTo: Int by Pref("outTo", 22)
    var rainAlert: Boolean by Pref("rainAlert", true)
    /** "System", "en" or "el". */
    var lang: String by Pref("lang", "System")

    /** How many degrees to add to the real temperature before choosing clothes. */
    val shift: Double get() = comfort * 1.5 + learn
}
