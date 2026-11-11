package com.alex.dresscast

import java.util.Locale

/** English / Greek text. Use L.t("English", "Ελληνικά") wherever text is shown. */
object L {
    @Volatile
    var greek: Boolean = false
        private set

    val locale: Locale get() = if (greek) Locale.forLanguageTag("el") else Locale.ENGLISH

    /** lang: "System", "en" or "el". */
    fun use(lang: String) {
        greek = when (lang) {
            "el" -> true
            "en" -> false
            else -> Locale.getDefault().language == "el"
        }
    }

    fun use(p: Prefs) = use(p.lang)

    fun t(en: String, el: String) = if (greek) el else en
}
