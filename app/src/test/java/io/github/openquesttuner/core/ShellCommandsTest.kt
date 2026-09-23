package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textes exacts de contracts/shell-commands.md : toute différence est une rupture de contrat. */
class ShellCommandsTest {

    @Test
    fun `C1 setProperty`() {
        assertEquals(
            "setprop debug.oculus.refreshRate 90",
            ShellCommand.setProperty(QuestProperty.REFRESH_RATE, 90).text,
        )
        assertEquals(
            "setprop debug.oculus.foveation.dynamic 0",
            ShellCommand.setProperty(QuestProperty.FOVEATION_DYNAMIC, 0).text,
        )
    }

    @Test
    fun `C2 resetProperty ecrit une chaine vide`() {
        assertEquals("setprop debug.oculus.cpuLevel ''", ShellCommand.resetProperty(QuestProperty.CPU_LEVEL).text)
    }

    @Test
    fun `C3 forceStop`() {
        assertEquals(
            "am force-stop --user current 'com.beatgames.beatsaber'",
            ShellCommand.forceStop("com.beatgames.beatsaber").text,
        )
    }

    @Test
    fun `C4 launch`() {
        assertEquals(
            "am start --user current -n 'com.a.b/com.unity3d.player.UnityPlayerActivity'",
            ShellCommand.launch("com.a.b", "com.unity3d.player.UnityPlayerActivity").text,
        )
    }

    @Test
    fun `C5 readProperties et C6 probe`() {
        assertEquals("getprop", ShellCommand.readProperties().text)
        assertEquals("true", ShellCommand.probe().text)
    }

    @Test
    fun `C7 et C8 activent et lisent le debogage sans fil`() {
        assertEquals("settings put global adb_wifi_enabled 1", ShellCommand.enableWirelessDebugging().text)
        assertEquals("settings get global adb_wifi_enabled", ShellCommand.readWirelessDebugging().text)
    }

    @Test
    fun `wireText ajoute le marqueur de code de sortie`() {
        val command = ShellCommand.probe()
        assertEquals(command.text + "; echo __OQT_EXIT__:\$?", command.wireText)
    }

    @Test
    fun `une classe interne avec dollar est acceptee et reste entre quotes simples`() {
        assertEquals(
            "am start --user current -n 'com.a.b/com.a.b.Main\$Inner'",
            ShellCommand.launch("com.a.b", "com.a.b.Main\$Inner").text,
        )
    }

    @Test
    fun `les noms de paquets dangereux ou invalides sont refuses`() {
        listOf("com.foo; reboot", "com.foo'bar", "\$(reboot)", "com", "", "com.foo bar", "com..foo", "1com.foo")
            .forEach { bad ->
                assertThrows("paquet accepté à tort : $bad", IllegalArgumentException::class.java) {
                    ShellCommand.forceStop(bad)
                }
                assertThrows("paquet accepté à tort : $bad", IllegalArgumentException::class.java) {
                    ShellCommand.launch(bad, "com.a.Main")
                }
            }
    }

    @Test
    fun `les noms d'activites dangereux ou invalides sont refuses`() {
        listOf("com.a.Main; reboot", "com.a Main", "com.a.Main'", "", "com.a.Main|ls", "com.a/Main")
            .forEach { bad ->
                assertThrows("activité acceptée à tort : $bad", IllegalArgumentException::class.java) {
                    ShellCommand.launch("com.a.b", bad)
                }
            }
    }

    @Test
    fun `les valeurs hors de la plage absolue sont refusees`() {
        assertThrows(IllegalArgumentException::class.java) {
            ShellCommand.setProperty(QuestProperty.TEXTURE_WIDTH, 5000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShellCommand.setProperty(QuestProperty.CPU_LEVEL, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ShellCommand.setProperty(QuestProperty.FOVEATION_DYNAMIC, 2)
        }
    }

    @Test
    fun `les bornes de la plage absolue sont acceptees`() {
        QuestProperty.entries.forEach { property ->
            ShellCommand.setProperty(property, property.absoluteRange.first)
            ShellCommand.setProperty(property, property.absoluteRange.last)
        }
    }

    @Test
    fun `aucune commande generable n'ecrit une propriete persistante`() {
        val all = buildList {
            QuestProperty.entries.forEach { property ->
                add(ShellCommand.resetProperty(property))
                add(ShellCommand.setProperty(property, property.absoluteRange.first))
                add(ShellCommand.setProperty(property, property.absoluteRange.last))
            }
            add(ShellCommand.forceStop("com.a.b"))
            add(ShellCommand.launch("com.a.b", "com.a.b.Main"))
            add(ShellCommand.readProperties())
            add(ShellCommand.probe())
            add(ShellCommand.enableWirelessDebugging())
            add(ShellCommand.readWirelessDebugging())
        }
        all.forEach { command ->
            assertFalse(command.wireText, command.wireText.contains("persist."))
        }
        assertTrue(QuestProperty.entries.all { it.key.startsWith("debug.oculus.") })
    }
}
