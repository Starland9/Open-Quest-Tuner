package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunerDiagnosticTest {

    private val shell = FakeShellBackend()
    private val display = FakeDisplayRates()
    private val tuner = Tuner(shell, QuestModel.QUEST_3, display)

    private val getpropOutput = """
        [debug.oculus.refreshRate]: [120]
        [debug.oculus.cpuLevel]: []
        [persist.oculus.timewarpRenderTime]: [2.7]
        [debug.oculus.extraKickoffHeadroom]: [0.25e-3]
        [debug.oculus.gpuLevel]: [5]
        [ro.product.model]: [Quest 3]
    """.trimIndent()

    @Test
    fun `n'execute que la lecture des proprietes`() = runTest {
        tuner.readDiagnostic()
        assertEquals(listOf(ShellCommand.readProperties().text), shell.executed)
    }

    @Test
    fun `garde les cles debug oculus non vides, triees`() = runTest {
        shell.respond("getprop", ShellResult(exitCode = 0, output = getpropOutput))

        assertEquals(
            Diagnostic(
                active = linkedMapOf(
                    "debug.oculus.extraKickoffHeadroom" to "0.25e-3",
                    "debug.oculus.gpuLevel" to "5",
                    "debug.oculus.refreshRate" to "120",
                ),
            ),
            tuner.readDiagnostic(),
        )
        assertEquals(
            listOf("debug.oculus.extraKickoffHeadroom", "debug.oculus.gpuLevel", "debug.oculus.refreshRate"),
            tuner.readDiagnostic()?.active?.keys?.toList(),
        )
    }

    @Test
    fun `connexion absente donne null`() = runTest {
        shell.disconnectAt = ""
        assertNull(tuner.readDiagnostic())
    }

    @Test
    fun `une lecture en echec donne null`() = runTest {
        shell.respond("getprop", ShellResult(exitCode = 1, output = ""))
        assertNull(tuner.readDiagnostic())
    }
}
