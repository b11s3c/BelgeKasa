package com.belgetarayici.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Interface language. Turkish text stays in the code as the message key; [tr] looks up the
 * English translation in [En] when English is active. [lang] is Compose state, so every screen
 * re-reads its texts as soon as the language is switched in Settings.
 */
object L {
    /** Effective language: "tr" or "en". */
    var lang by mutableStateOf("tr")
        private set

    val locale: Locale get() = if (lang == "en") Locale.ENGLISH else Locale.forLanguageTag("tr-TR")

    /** [setting] is "tr", "en" or "system" (Turkish on Turkish phones, English elsewhere). */
    fun apply(setting: String, system: Locale) {
        lang = when (setting) {
            "tr", "en" -> setting
            else -> if (system.language == "tr") "tr" else "en"
        }
    }

    val SETTINGS = listOf("system", "tr", "en")
}

/** The current-language text for Turkish message [s]. */
fun tr(s: String): String = if (L.lang == "en") En.map[s] ?: s else s

/**
 * [tr] for a message with `%s` placeholders. English plurals are written "page(s)"; the "(s)"
 * follows the first numeric argument ("1 page", "3 pages").
 */
fun trf(s: String, vararg args: Any?): String {
    var text = tr(s)
    if ("(s)" in text) {
        val n = args.firstOrNull { it is Number } as Number?
        text = text.replace("(s)", if (n?.toLong() == 1L) "" else "s")
    }
    return String.format(L.locale, text, *args)
}
