package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fréquences au-delà de 120 Hz (spec 003, data-model.md). */
class RefreshRatePolicyTest {

    private val quest3Screen = (72..207).toSet()

    // --- Fréquences proposées (US1)

    @Test
    fun `liste fermee des frequences elevees`() {
        assertEquals(120, RefreshRatePolicy.STANDARD_MAX_RATE)
        assertEquals(listOf(144, 160, 180, 200), RefreshRatePolicy.HIGH_REFRESH_RATES)
    }

    @Test
    fun `Quest 3, frequences du modele puis frequences elevees declarees`() {
        assertEquals(
            listOf(72, 80, 90, 96, 100, 120, 144, 160, 180, 200),
            RefreshRatePolicy.available(QuestModel.QUEST_3, quest3Screen),
        )
    }

    @Test
    fun `rien au-dela de 200 Hz, meme declare`() {
        assertEquals(
            RefreshRatePolicy.available(QuestModel.QUEST_3, quest3Screen),
            RefreshRatePolicy.available(QuestModel.QUEST_3, (72..240).toSet()),
        )
    }

    @Test
    fun `seules les frequences elevees declarees sont proposees`() {
        assertEquals(
            QuestModel.QUEST_3.refreshRates + 144,
            RefreshRatePolicy.available(QuestModel.QUEST_3, (72..150).toSet()),
        )
    }

    @Test
    fun `ecran illisible, aucune frequence elevee`() {
        assertEquals(QuestModel.QUEST_3.refreshRates, RefreshRatePolicy.available(QuestModel.QUEST_3, emptySet()))
    }

    @Test
    fun `la regle ne depend que de l'ecran`() {
        assertEquals(
            listOf(72, 80, 90, 144, 160, 180, 200),
            RefreshRatePolicy.available(QuestModel.QUEST_PRO, quest3Screen),
        )
        assertEquals(
            QuestModel.UNKNOWN.refreshRates,
            RefreshRatePolicy.available(QuestModel.UNKNOWN, (72..120).toSet()),
        )
    }

    @Test
    fun `toute frequence au-dela de 120 Hz est experimentale`() {
        assertFalse(RefreshRatePolicy.isHigh(120))
        assertTrue(RefreshRatePolicy.isHigh(144))
        RefreshRatePolicy.available(QuestModel.QUEST_3, quest3Screen).forEach { rate ->
            assertEquals("fréquence $rate", RefreshRatePolicy.isHigh(rate), RefreshRatePolicy.isExperimental(rate))
        }
    }

    // --- Limites de résolution (US2)

    private val q3 = QuestModel.QUEST_3
    private val default = q3.defaultEyeTexture
    private fun step(percent: Int) = EyeTexture.forStep(default, percent)

    @Test
    fun `palier maximal par frequence`() {
        assertEquals(100, RefreshRatePolicy.maxResolutionStep(144))
        assertEquals(100, RefreshRatePolicy.maxResolutionStep(160))
        assertEquals(90, RefreshRatePolicy.maxResolutionStep(180))
        assertEquals(80, RefreshRatePolicy.maxResolutionStep(200))
        listOf(120, 72, null).forEach { assertEquals(null, RefreshRatePolicy.maxResolutionStep(it)) }
    }

    @Test
    fun `resolution permise selon la frequence`() {
        assertTrue(RefreshRatePolicy.allowed(200, null, default))
        assertEquals(EyeTexture(1344, 1408), step(80))
        assertTrue(RefreshRatePolicy.allowed(200, step(80), default))
        assertFalse(RefreshRatePolicy.allowed(200, step(90), default))
        // Tailles personnalisées : chaque dimension est comparée.
        assertTrue(RefreshRatePolicy.allowed(200, EyeTexture(1344, 1400), default))
        assertFalse(RefreshRatePolicy.allowed(200, EyeTexture(1352, 1400), default))
        assertFalse(RefreshRatePolicy.allowed(200, EyeTexture(1344, 1416), default))
        assertTrue(RefreshRatePolicy.allowed(120, step(150), default))
        assertTrue(RefreshRatePolicy.allowed(null, step(150), default))
    }

    @Test
    fun `paliers indisponibles`() {
        assertEquals(listOf(100, 110, 120, 130, 140, 150), RefreshRatePolicy.unavailableSteps(180, default))
        assertEquals(listOf(90, 100, 110, 120, 130, 140, 150), RefreshRatePolicy.unavailableSteps(200, default))
        assertEquals(listOf(110, 120, 130, 140, 150), RefreshRatePolicy.unavailableSteps(144, default))
        assertEquals(emptyList<Int>(), RefreshRatePolicy.unavailableSteps(120, default))
        assertEquals(emptyList<Int>(), RefreshRatePolicy.unavailableSteps(null, default))
    }

    @Test
    fun `choisir une frequence elevee abaisse une resolution trop haute`() {
        val profile = GameProfile(refreshRate = 120, eyeTexture = step(150), cpuLevel = 3, gpuLevel = 4)
        val selection = RefreshRatePolicy.selectRate(profile, 200, q3)
        assertEquals(profile.copy(refreshRate = 200, eyeTexture = step(80)), selection.profile)
        assertEquals(80, selection.loweredToStep)

        val custom = RefreshRatePolicy.selectRate(GameProfile(eyeTexture = EyeTexture(1600, 1700)), 180, q3)
        assertEquals(step(90), custom.profile.eyeTexture)
        assertEquals(90, custom.loweredToStep)
    }

    @Test
    fun `resolution deja permise ou laissee au jeu, rien n'est abaisse`() {
        val low = GameProfile(eyeTexture = step(80))
        assertEquals(RateSelection(low.copy(refreshRate = 200), null), RefreshRatePolicy.selectRate(low, 200, q3))
        val gameDefault = GameProfile(cpuLevel = 2)
        assertEquals(
            RateSelection(gameDefault.copy(refreshRate = 200), null),
            RefreshRatePolicy.selectRate(gameDefault, 200, q3),
        )
    }

    @Test
    fun `revenir a 120 Hz ou moins ne remonte pas la resolution`() {
        val atLimit = GameProfile(refreshRate = 200, eyeTexture = step(80))
        assertEquals(RateSelection(atLimit.copy(refreshRate = 120), null), RefreshRatePolicy.selectRate(atLimit, 120, q3))
        val high = GameProfile(refreshRate = 120, eyeTexture = step(150))
        assertEquals(RateSelection(high.copy(refreshRate = null), null), RefreshRatePolicy.selectRate(high, null, q3))
    }
}
