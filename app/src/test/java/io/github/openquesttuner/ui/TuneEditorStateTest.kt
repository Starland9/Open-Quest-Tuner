package io.github.openquesttuner.ui

import io.github.openquesttuner.core.*
import org.junit.Assert.*
import org.junit.Test

class TuneEditorStateTest {
    private val model = QuestModel.QUEST_3
    @Test fun `disconnected or absent diagnostic never produces verified matches`() {
        assertTrue(tuneReadbackRows(GameProfile(), false, Diagnostic(emptyMap())).all { it.status == TuneReadbackStatus.UNAVAILABLE })
        assertTrue(tuneReadbackRows(GameProfile(), true, null).all { it.status == TuneReadbackStatus.UNAVAILABLE })
    }
    @Test fun `empty complete diagnostic represents default overrides not numeric zero`() {
        assertTrue(tuneReadbackRows(GameProfile(), true, Diagnostic(emptyMap())).all { it.status == TuneReadbackStatus.MATCH })
        val rows = tuneReadbackRows(GameProfile(cpuLevel = 0, dynamicFoveation = false), true, Diagnostic(emptyMap()))
        assertEquals(TuneReadbackStatus.DIFFERENT, rows.first { it.property == QuestProperty.CPU_LEVEL }.status)
        assertEquals(TuneReadbackStatus.DIFFERENT, rows.first { it.property == QuestProperty.FOVEATION_DYNAMIC }.status)
    }
    @Test fun `all seven requested values compared against actual snapshot`() {
        val profile = GameProfile(90, EyeTexture(2048, 2048), 4, 3, FoveationLevel.MEDIUM, true)
        val active = profile.toPropertyValues().mapKeys { it.key.key }.mapValues { it.value.toString() }
        assertEquals(7, tuneReadbackRows(profile, true, Diagnostic(active)).size)
        assertTrue(tuneReadbackRows(profile, true, Diagnostic(active)).all { it.status == TuneReadbackStatus.MATCH })
        val changed = active + (QuestProperty.GPU_LEVEL.key to "2")
        assertEquals(listOf(QuestProperty.GPU_LEVEL), tuneReadbackRows(profile, true, Diagnostic(changed)).filter { it.status == TuneReadbackStatus.DIFFERENT }.map { it.property })
    }
    @Test fun `resolution stepper traverses valid presets and clears at lower end`() {
        var profile = GameProfile()
        assertEquals(model.defaultEyeTexture, tuneResolutionStep(profile, model, 1))
        profile = profile.copy(eyeTexture = EyeTexture.forStep(model.defaultEyeTexture, 70))
        assertNull(tuneResolutionStep(profile, model, -1))
        profile = profile.copy(eyeTexture = EyeTexture.forStep(model.defaultEyeTexture, 150))
        assertEquals(profile.eyeTexture, tuneResolutionStep(profile, model, 1))
    }
    @Test fun `resolution stepper respects high refresh caps across every model`() {
        for (headset in QuestModel.entries) for (rate in listOf(144, 160, 180, 200)) {
            var profile = GameProfile(refreshRate = rate)
            repeat(12) {
                val next = tuneResolutionStep(profile, headset, 1)
                assertTrue(RefreshRatePolicy.allowed(rate, next, headset.defaultEyeTexture))
                profile = profile.copy(eyeTexture = next)
            }
        }
    }
    @Test fun `custom texture moves to adjacent allowed step rather than being silently rewritten`() {
        val profile = GameProfile(eyeTexture = EyeTexture(1900, 1950))
        val next = tuneResolutionStep(profile, model, 1)!!
        val prev = tuneResolutionStep(profile, model, -1)!!
        assertTrue(next.width > 1900 || next.height > 1950)
        assertTrue(prev.width < 1900 && prev.height < 1950)
        assertEquals(EyeTexture(1900, 1950), profile.eyeTexture)
    }
    @Test fun `all original presets remain valid for each model`() {
        for (headset in QuestModel.entries) {
            val presets = tunePresetProfiles(headset)
            assertEquals(listOf("Default", "Battery", "Balanced", "Performance", "Quality", "HD+"), presets.map { it.first })
            assertEquals(GameProfile(), presets.first().second)
            assertTrue(presets.all { it.second.validateFor(headset).isEmpty() })
        }
    }
}
