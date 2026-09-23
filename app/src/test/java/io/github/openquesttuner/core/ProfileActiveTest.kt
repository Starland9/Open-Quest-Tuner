package io.github.openquesttuner.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileActiveTest {

    private val profile = GameProfile(
        refreshRate = 120,
        eyeTexture = EyeTexture(2016, 2112),
        cpuLevel = 4,
        gpuLevel = 5,
        foveationLevel = FoveationLevel.MEDIUM,
        dynamicFoveation = false,
    )

    private val applied = mapOf(
        "debug.oculus.refreshRate" to "120",
        "debug.oculus.textureWidth" to "2016",
        "debug.oculus.textureHeight" to "2112",
        "debug.oculus.cpuLevel" to "4",
        "debug.oculus.gpuLevel" to "5",
        "debug.oculus.foveation.level" to "2",
        "debug.oculus.foveation.dynamic" to "0",
    )

    @Test
    fun `un profil applique est actif, cles systeme ignorees`() {
        assertTrue(profile.isActiveOn(applied))
        assertTrue(profile.isActiveOn(applied + ("debug.oculus.extraKickoffHeadroom" to "0.25e-3")))
    }

    @Test
    fun `une seule valeur differente suffit a le rendre non applique`() {
        assertFalse(profile.isActiveOn(applied + ("debug.oculus.gpuLevel" to "4")))
        assertFalse(profile.isActiveOn(applied - "debug.oculus.foveation.dynamic"))
    }

    @Test
    fun `un reglage par defaut exige une propriete absente`() {
        val partial = GameProfile(refreshRate = 90)
        assertTrue(partial.isActiveOn(mapOf("debug.oculus.refreshRate" to "90")))
        assertFalse(partial.isActiveOn(mapOf("debug.oculus.refreshRate" to "90", "debug.oculus.cpuLevel" to "4")))
    }

    @Test
    fun `le profil vide est actif seulement sans propriete geree`() {
        assertTrue(GameProfile().isActiveOn(emptyMap()))
        assertTrue(GameProfile().isActiveOn(mapOf("debug.oculus.extraKickoffHeadroom" to "0.25e-3")))
        assertFalse(GameProfile().isActiveOn(applied))
    }

    @Test
    fun `le foveal dynamique active s'encode en 1`() {
        assertTrue(GameProfile(dynamicFoveation = true).isActiveOn(mapOf("debug.oculus.foveation.dynamic" to "1")))
    }
}
