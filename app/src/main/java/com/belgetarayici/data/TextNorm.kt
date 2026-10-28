package com.belgetarayici.data

import java.util.Locale

/** Search normalisation: Turkish-aware lower case, then diacritics folded (ş→s, ı→i, ...). */
object TextNorm {
    private val TR = Locale.forLanguageTag("tr")
    private val FOLD = mapOf('ç' to 'c', 'ğ' to 'g', 'ı' to 'i', 'ö' to 'o', 'ş' to 's', 'ü' to 'u', 'â' to 'a', 'î' to 'i', 'û' to 'u')

    fun normalize(s: String): String {
        val lower = s.lowercase(TR)
        val sb = StringBuilder(lower.length)
        for (c in lower) {
            if (c == '̇') continue // combining dot left by lowercasing "İ" outside tr locale
            sb.append(FOLD[c] ?: c)
        }
        return sb.toString().replace(Regex("\\s+"), " ")
    }
}
