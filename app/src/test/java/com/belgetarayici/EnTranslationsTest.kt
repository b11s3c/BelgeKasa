package com.belgetarayici

import com.belgetarayici.i18n.En
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every Turkish message in the code has an English text, and no screen text skips [tr]. */
class EnTranslationsTest {
    private val sources = File("src/main/java/com/belgetarayici").walkTopDown().filter { it.extension == "kt" }.toList()

    private fun unescape(s: String) = s.replace(Regex("""\\(u[0-9a-fA-F]{4}|.)""")) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("u") && e.length == 5 -> e.substring(1).toInt(16).toChar().toString()
            e == "n" -> "\n"
            e == "t" -> "\t"
            else -> e
        }
    }

    private val literal = """"((?:[^"\\]|\\.)*)""""

    private fun messages(): Set<String> = sources.flatMap { f ->
        val text = f.readText()
        val calls = Regex("""\btrf?\($literal""").findAll(text).map { it.groupValues[1] }
        // Enum labels kept as raw Turkish and shown through a `tr(labelTr)` getter.
        val enumLabels = Regex("""enum class \w+\(([^)]*)\)\s*\{(.*?);""", RegexOption.DOT_MATCHES_ALL).findAll(text).flatMap { m ->
            val params = m.groupValues[1].split(',').map { it.trim() }
            val translated = params.indices.filter { params[it].contains("Tr: String") }
            Regex("""[A-Z_0-9]+\(([^)]*)\)""").findAll(m.groupValues[2]).flatMap { e ->
                val args = Regex(literal).findAll(e.groupValues[1]).map { it.groupValues[1] }.toList()
                translated.mapNotNull { args.getOrNull(it) }
            }
        }
        (calls + enumLabels).map(::unescape).toList()
    }.toSet()

    @Test fun everyMessageHasEnglish() {
        val missing = messages().filter { it !in En.map }
        assertTrue("No English for:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun placeholdersMatch() {
        val bad = En.map.filter { (tr, en) -> Regex("%s").findAll(tr).count() != Regex("%s").findAll(en).count() }
        assertTrue("Placeholder count differs: $bad", bad.isEmpty())
    }

    @Test fun screenTextsGoThroughTr() {
        // Turkish letters in a string literal on a screen almost always mean an untranslated text.
        val allowed = setOf("çğıİöşüÇĞÖŞÜ", "Türkçe")
        val offenders = sources.filter { it.parentFile.name == "ui" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, line) -> !line.trimStart().startsWith("//") && !line.trimStart().startsWith("*") }
                .flatMap { (i, line) ->
                    Regex(literal).findAll(line).filter { m ->
                        val s = m.groupValues[1]
                        s.any { it in "çğıöşüÇĞİÖŞÜ" } && s !in allowed && !line.substring(0, m.range.first).trimEnd().endsWith("trf(") &&
                            !line.substring(0, m.range.first).trimEnd().endsWith("tr(") && !Regex("""^\s*[A-Z_0-9]+\(""").containsMatchIn(line)
                    }.map { "${f.name}:${i + 1}: ${it.value}" }
                }
        }
        assertTrue("Not passed through tr():\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
