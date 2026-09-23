package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellOutputTest {

    @Test
    fun `extrait le code de sortie et retire le marqueur`() {
        assertEquals(ShellResult(0, "hello"), ShellOutput.parse("hello\n__OQT_EXIT__:0\n"))
    }

    @Test
    fun `conserve un code non nul`() {
        val result = ShellOutput.parse("setprop: failed\n__OQT_EXIT__:1\n")
        assertEquals(1, result.exitCode)
        assertEquals("setprop: failed", result.output)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `retire les retours chariot`() {
        assertEquals(ShellResult(0, "a\nb"), ShellOutput.parse("a\r\nb\r\n__OQT_EXIT__:0\r\n"))
    }

    @Test
    fun `seul le dernier marqueur compte`() {
        val result = ShellOutput.parse("a\n__OQT_EXIT__:3\nb\n__OQT_EXIT__:0\n")
        assertEquals(0, result.exitCode)
        assertEquals("a\n__OQT_EXIT__:3\nb", result.output)
    }

    @Test
    fun `marqueur colle a une sortie sans saut de ligne final`() {
        assertEquals(ShellResult(0, "abc"), ShellOutput.parse("abc__OQT_EXIT__:0\n"))
    }

    @Test
    fun `sans marqueur le resultat est un echec au code inconnu`() {
        val result = ShellOutput.parse("sortie coupée")
        assertNull(result.exitCode)
        assertEquals("sortie coupée", result.output)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `sortie vide avec succes`() {
        val result = ShellOutput.parse("__OQT_EXIT__:0\n")
        assertEquals(ShellResult(0, ""), result)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parseGetprop ne garde que les proprietes debug oculus non vides, triees`() {
        val output = """
            [debug.oculus.textureWidth]: [2016]
            [ro.product.model]: [Quest 3]
            [debug.oculus.cpuLevel]: []
            [debug.oculus.refreshRate]: [120]
            ligne malformée
            [debug.oculus.foveation.level]: [2]
            [debug.other]: [1]
        """.trimIndent()
        val props = ShellOutput.parseGetprop(output)
        assertEquals(
            listOf("debug.oculus.foveation.level", "debug.oculus.refreshRate", "debug.oculus.textureWidth"),
            props.keys.toList(),
        )
        assertEquals("120", props["debug.oculus.refreshRate"])
        assertEquals("2", props["debug.oculus.foveation.level"])
    }

    @Test
    fun `parseGetprop sur une sortie vide`() {
        assertTrue(ShellOutput.parseGetprop("").isEmpty())
    }

    @Test
    fun `detecte une erreur de lancement d'am start`() {
        assertTrue(
            ShellOutput.isLaunchError(
                "Starting: Intent { cmp=com.a/.Main }\nError: Activity class {com.a/com.a.Main} does not exist.",
            ),
        )
        assertFalse(ShellOutput.isLaunchError("Starting: Intent { cmp=com.a/.Main }"))
    }
}
