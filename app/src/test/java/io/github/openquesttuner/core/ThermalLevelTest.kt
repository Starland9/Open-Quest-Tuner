package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalLevelTest {

    @Test
    fun `les codes Android 0 a 6 suivent l'ordre de l'enum`() {
        assertEquals(
            listOf(
                ThermalLevel.NONE,
                ThermalLevel.LIGHT,
                ThermalLevel.MODERATE,
                ThermalLevel.SEVERE,
                ThermalLevel.CRITICAL,
                ThermalLevel.EMERGENCY,
                ThermalLevel.SHUTDOWN,
            ),
            (0..6).map(ThermalLevel::fromAndroidStatus),
        )
    }

    @Test
    fun `un code inconnu donne UNKNOWN`() {
        assertEquals(ThermalLevel.UNKNOWN, ThermalLevel.fromAndroidStatus(-1))
        assertEquals(ThermalLevel.UNKNOWN, ThermalLevel.fromAndroidStatus(7))
    }

    @Test
    fun `l'avertissement commence a MODERATE`() {
        listOf(ThermalLevel.NONE, ThermalLevel.LIGHT, ThermalLevel.UNKNOWN).forEach { assertFalse(it.name, it.warning) }
        listOf(
            ThermalLevel.MODERATE,
            ThermalLevel.SEVERE,
            ThermalLevel.CRITICAL,
            ThermalLevel.EMERGENCY,
            ThermalLevel.SHUTDOWN,
        ).forEach { assertTrue(it.name, it.warning) }
    }
}
