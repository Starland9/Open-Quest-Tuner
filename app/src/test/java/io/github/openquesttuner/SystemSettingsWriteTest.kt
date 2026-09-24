package io.github.openquesttuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Règle d'écriture unique (specs/002-standalone-reconnect/contracts/wireless-switch.md, FR-015,
 * principe I) : l'appli n'écrit qu'un réglage système, `adb_wifi_enabled = 1`, et à un seul
 * endroit.
 */
class SystemSettingsWriteTest {

    private val sourceDir = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }

    private val settingsWrite = Regex("""Settings\.(Global|Secure|System)\.put\w*\(""")

    private data class Match(val file: File, val line: Int, val text: String) {
        override fun toString() = "${file.invariantSeparatorsPath}:$line: $text"
    }

    @Test
    fun `une seule ecriture de reglage systeme, dans AndroidWirelessSwitch`() {
        val matches = sourceDir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (settingsWrite.containsMatchIn(line)) Match(file, index + 1, line.trim()) else null
            }
        }.toList()
        val report = matches.joinToString("\n")

        val outside = matches.filterNot { it.file.invariantSeparatorsPath.endsWith("adb/AndroidWirelessSwitch.kt") }
        assertTrue("Écriture de réglage système hors d'AndroidWirelessSwitch.kt :\n$report", outside.isEmpty())
        assertEquals("AndroidWirelessSwitch.kt doit contenir exactement une écriture :\n$report", 1, matches.size)
        assertTrue(
            "La seule écriture doit être adb_wifi_enabled = 1 :\n$report",
            matches.single().text.contains("KEY_ADB_WIFI_ENABLED, 1)"),
        )
    }
}
