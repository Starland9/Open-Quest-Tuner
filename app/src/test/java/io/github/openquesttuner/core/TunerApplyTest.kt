package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunerApplyTest {

    private val shell = FakeShellBackend()
    private val tuner = Tuner(shell, QuestModel.QUEST_3)

    private val fullProfile = GameProfile(
        refreshRate = 120,
        eyeTexture = EyeTexture(2016, 2112),
        cpuLevel = 4,
        gpuLevel = 4,
        foveationLevel = FoveationLevel.MEDIUM,
        dynamicFoveation = false,
    )

    private fun launchText() = ShellCommand.launch(PKG, ACTIVITY).text

    @Test
    fun `sequence complete dans l'ordre du contrat`() = runTest {
        val result = tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile)

        assertEquals(TuneResult.Success, result)
        assertEquals(
            listOf(
                ShellCommand.forceStop(PKG).text,
                ShellCommand.setProperty(QuestProperty.REFRESH_RATE, 120).text,
                ShellCommand.setProperty(QuestProperty.TEXTURE_WIDTH, 2016).text,
                ShellCommand.setProperty(QuestProperty.TEXTURE_HEIGHT, 2112).text,
                ShellCommand.setProperty(QuestProperty.CPU_LEVEL, 4).text,
                ShellCommand.setProperty(QuestProperty.GPU_LEVEL, 4).text,
                ShellCommand.setProperty(QuestProperty.FOVEATION_LEVEL, 2).text,
                ShellCommand.setProperty(QuestProperty.FOVEATION_DYNAMIC, 0).text,
                launchText(),
            ),
            shell.executed,
        )
    }

    @Test
    fun `les reglages laisses par defaut sont reinitialises avant le lancement`() = runTest {
        val result = tuner.applyAndLaunch(PKG, ACTIVITY, GameProfile(refreshRate = 90))

        assertEquals(TuneResult.Success, result)
        assertEquals(
            listOf(ShellCommand.forceStop(PKG).text) +
                QuestProperty.entries.map { property ->
                    if (property == QuestProperty.REFRESH_RATE) {
                        ShellCommand.setProperty(property, 90).text
                    } else {
                        ShellCommand.resetProperty(property).text
                    }
                } +
                launchText(),
            shell.executed,
        )
    }

    @Test
    fun `un profil vide reinitialise les 7 proprietes puis lance`() = runTest {
        assertEquals(TuneResult.Success, tuner.applyAndLaunch(PKG, ACTIVITY, GameProfile()))
        assertEquals(9, shell.executed.size)
        assertEquals(QuestProperty.entries.map { ShellCommand.resetProperty(it).text }, shell.executed.subList(1, 8))
    }

    @Test
    fun `le premier echec arrete la sequence sans lancer`() = runTest {
        shell.respond("setprop ${QuestProperty.CPU_LEVEL.key}", ShellResult(exitCode = 1, output = "setprop failed"))

        val result = tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile)

        assertEquals(TuneResult.StepFailed(TuneStep.SetProperty(QuestProperty.CPU_LEVEL), "setprop failed"), result)
        assertEquals(ShellCommand.setProperty(QuestProperty.CPU_LEVEL, 4).text, shell.executed.last())
        assertTrue(shell.executed.none { it.startsWith("am start") })
    }

    @Test
    fun `un echec de l'arret du jeu arrete la sequence`() = runTest {
        shell.respond("am force-stop", ShellResult(exitCode = 255, output = "Exception"))

        val result = tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile)

        assertEquals(TuneResult.StepFailed(TuneStep.ForceStop, "Exception"), result)
        assertEquals(1, shell.executed.size)
    }

    @Test
    fun `une sortie sans code de retour compte comme un echec`() = runTest {
        shell.respond("setprop ${QuestProperty.REFRESH_RATE.key}", ShellResult(exitCode = null, output = ""))

        val result = tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile)

        assertEquals(TuneResult.StepFailed(TuneStep.SetProperty(QuestProperty.REFRESH_RATE), ""), result)
    }

    @Test
    fun `une sortie Error de am start est un echec du lancement`() = runTest {
        val output = "Starting: Intent { cmp=$PKG/$ACTIVITY }\nError: Activity class {$PKG/$ACTIVITY} does not exist."
        shell.respond("am start", ShellResult(exitCode = 0, output = output))

        val result = tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile)

        assertEquals(TuneResult.StepFailed(TuneStep.Launch, output), result)
    }

    @Test
    fun `un code non nul de am start est un echec du lancement`() = runTest {
        shell.respond("am start", ShellResult(exitCode = 1, output = "boom"))

        assertEquals(TuneResult.StepFailed(TuneStep.Launch, "boom"), tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile))
    }

    @Test
    fun `un profil invalide pour le modele n'envoie aucune commande`() = runTest {
        val result = tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile.copy(refreshRate = 144, gpuLevel = 7))

        assertEquals(
            TuneResult.InvalidProfile(
                listOf(
                    ProfileViolation(QuestProperty.REFRESH_RATE, 144),
                    ProfileViolation(QuestProperty.GPU_LEVEL, 7),
                ),
            ),
            result,
        )
        assertEquals(emptyList<String>(), shell.executed)
    }

    @Test
    fun `un nom de paquet ou d'activite invalide leve avant tout envoi`() = runTest {
        val badActivity = runCatching { tuner.applyAndLaunch(PKG, "Main; reboot", fullProfile) }
        val badPackage = runCatching { tuner.applyAndLaunch("com.example.x'; reboot", ACTIVITY, fullProfile) }

        assertTrue(badActivity.exceptionOrNull() is IllegalArgumentException)
        assertTrue(badPackage.exceptionOrNull() is IllegalArgumentException)
        assertEquals(emptyList<String>(), shell.executed)
    }

    @Test
    fun `connexion absente donne NotConnected`() = runTest {
        shell.disconnectAt = ""
        assertEquals(TuneResult.NotConnected, tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile))
    }

    @Test
    fun `connexion perdue en cours de sequence donne NotConnected sans lancer`() = runTest {
        shell.disconnectAt = "setprop ${QuestProperty.GPU_LEVEL.key}"

        assertEquals(TuneResult.NotConnected, tuner.applyAndLaunch(PKG, ACTIVITY, fullProfile))
        assertTrue(shell.executed.none { it.startsWith("am start") })
    }

    private companion object {
        const val PKG = "com.example.game"
        const val ACTIVITY = "com.unity3d.player.UnityPlayerActivity"
    }
}
