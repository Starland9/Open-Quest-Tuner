package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestModelTest {

    @Test
    fun `reconnait chaque casque par son nom de code Build DEVICE`() {
        assertEquals(QuestModel.QUEST_3, QuestModel.fromBuild("eureka", "x"))
        assertEquals(QuestModel.QUEST_3S, QuestModel.fromBuild("panther", "x"))
        assertEquals(QuestModel.QUEST_2, QuestModel.fromBuild("hollywood", "x"))
        assertEquals(QuestModel.QUEST_PRO, QuestModel.fromBuild("seacliff", "x"))
    }

    @Test
    fun `ignore la casse du nom de code`() {
        assertEquals(QuestModel.QUEST_3, QuestModel.fromBuild("EUREKA", "x"))
    }

    @Test
    fun `se replie sur Build MODEL quand le nom de code est inconnu`() {
        assertEquals(QuestModel.QUEST_3, QuestModel.fromBuild("?", "Quest 3"))
        assertEquals(QuestModel.QUEST_3S, QuestModel.fromBuild("?", "Quest 3S"))
    }

    @Test
    fun `un appareil qui n'est pas un Quest est UNKNOWN`() {
        assertEquals(QuestModel.UNKNOWN, QuestModel.fromBuild("sm-g981b", "Pixel"))
    }

    @Test
    fun `UNKNOWN reprend les valeurs du Quest 3`() {
        val q3 = QuestModel.QUEST_3
        val unknown = QuestModel.UNKNOWN
        assertEquals(q3.defaultEyeTexture, unknown.defaultEyeTexture)
        assertEquals(q3.refreshRates, unknown.refreshRates)
        assertEquals(q3.cpuLevels, unknown.cpuLevels)
        assertEquals(q3.gpuLevels, unknown.gpuLevels)
        assertEquals(q3.alwaysAvailableCpuMax, unknown.alwaysAvailableCpuMax)
        assertEquals(q3.alwaysAvailableGpuMax, unknown.alwaysAvailableGpuMax)
        assertTrue(unknown.verified.isEmpty())
    }

    @Test
    fun `rien n'est verifie tant que les essais sur casque ne sont pas faits`() {
        // Principe III : tout est « expérimental » jusqu'à un essai consigné dans docs/compatibility.md.
        QuestModel.entries.forEach { model ->
            assertTrue("${model.name} ne devrait rien avoir de vérifié", model.verified.isEmpty())
        }
    }

    @Test
    fun `le palier 100 pour cent redonne la resolution par defaut de chaque modele`() {
        QuestModel.entries.forEach { model ->
            assertEquals(model.defaultEyeTexture, EyeTexture.forStep(model.defaultEyeTexture, 100))
        }
    }

    @Test
    fun `valeurs par casque conformes a research R2`() {
        assertEquals(EyeTexture(1680, 1760), QuestModel.QUEST_3.defaultEyeTexture)
        assertEquals(EyeTexture(1680, 1760), QuestModel.QUEST_3S.defaultEyeTexture)
        assertEquals(EyeTexture(1440, 1584), QuestModel.QUEST_2.defaultEyeTexture)
        assertEquals(EyeTexture(1440, 1584), QuestModel.QUEST_PRO.defaultEyeTexture)
        assertEquals(listOf(72, 80, 90), QuestModel.QUEST_PRO.refreshRates)
        assertEquals(0..5, QuestModel.QUEST_3.gpuLevels)
        assertEquals(0..4, QuestModel.QUEST_2.gpuLevels)
    }
}
