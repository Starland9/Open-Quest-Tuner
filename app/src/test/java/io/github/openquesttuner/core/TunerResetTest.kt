package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunerResetTest {

    private val shell = FakeShellBackend()
    private val tuner = Tuner(shell, QuestModel.QUEST_3)

    private val allResets = QuestProperty.entries.map { ShellCommand.resetProperty(it).text }

    @Test
    fun `remet les 7 proprietes a vide dans l'ordre et rien d'autre`() = runTest {
        assertEquals(ResetResult(failed = emptyList()), tuner.resetAll())
        assertEquals(allResets, shell.executed)
    }

    @Test
    fun `un echec n'empeche pas de tenter les proprietes suivantes`() = runTest {
        shell.respond("setprop ${QuestProperty.CPU_LEVEL.key}", ShellResult(exitCode = 1, output = "denied"))

        val result = tuner.resetAll()

        assertEquals(ResetResult(failed = listOf(QuestProperty.CPU_LEVEL)), result)
        assertEquals(allResets, shell.executed)
    }

    @Test
    fun `tous les echecs sont listes dans l'ordre de l'enum`() = runTest {
        shell.respond("setprop ${QuestProperty.FOVEATION_DYNAMIC.key}", ShellResult(exitCode = 1, output = ""))
        shell.respond("setprop ${QuestProperty.REFRESH_RATE.key}", ShellResult(exitCode = null, output = ""))

        assertEquals(
            ResetResult(failed = listOf(QuestProperty.REFRESH_RATE, QuestProperty.FOVEATION_DYNAMIC)),
            tuner.resetAll(),
        )
    }

    @Test
    fun `connexion absente donne null sans rien executer`() = runTest {
        shell.disconnectAt = ""

        assertNull(tuner.resetAll())
        assertEquals(emptyList<String>(), shell.executed)
    }

    @Test
    fun `connexion perdue en cours de route donne null`() = runTest {
        shell.disconnectAt = "setprop ${QuestProperty.GPU_LEVEL.key}"

        assertNull(tuner.resetAll())
    }
}
