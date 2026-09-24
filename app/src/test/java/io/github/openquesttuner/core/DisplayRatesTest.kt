package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Conversion des modes d'affichage en fréquences entières (contracts/display-rates.md). */
class DisplayRatesTest {

    @Test
    fun `les frequences sont arrondies a l'entier le plus proche`() {
        assertEquals(
            setOf(72, 90, 144, 207),
            DisplayRates.ratesFromModes(listOf(72.00001f, 90.0f, 207.00003f, 144.00002f)),
        )
        assertEquals(setOf(60), DisplayRates.ratesFromModes(listOf(59.94f)))
    }

    @Test
    fun `deux modes a la meme frequence n'en font qu'une`() {
        // Le Quest 3 déclare 90 Hz à 4128×2208 et à 3104×1664.
        assertEquals(setOf(90), DisplayRates.ratesFromModes(listOf(90.0f, 90.0f)))
    }

    @Test
    fun `les valeurs invalides sont ignorees`() {
        assertEquals(
            setOf(120),
            DisplayRates.ratesFromModes(listOf(0f, -90f, Float.NaN, Float.POSITIVE_INFINITY, 120f)),
        )
        assertEquals(emptySet<Int>(), DisplayRates.ratesFromModes(emptyList()))
    }
}
