package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchKeyTest {

    @Test
    fun `les accents sont retires et la casse ignoree`() {
        assertEquals("elephant rouge", searchKey("Éléphant Rouge"))
    }

    @Test
    fun `les espaces de bord sont retires`() {
        assertEquals("beat saber", searchKey("  Beat SABER "))
    }

    @Test
    fun `le tri par cle place Elite entre Echo et Fable`() {
        assertEquals(listOf("Echo", "Élite", "Fable"), listOf("Fable", "Élite", "Echo").sortedBy(::searchKey))
    }

    // --- matchesQuery (FR-009)

    @Test
    fun `une partie du nom suffit, sans casse ni accents`() {
        assertTrue(matchesQuery("Beat Saber", "saber"))
        assertTrue(matchesQuery("Élite Dangerous", "elite"))
        assertTrue(matchesQuery("Elite Dangerous", "ÉLITE"))
        assertTrue(matchesQuery("Beat Saber", "  SABER "))
    }

    @Test
    fun `une requete vide garde tout`() {
        assertTrue(matchesQuery("X", ""))
        assertTrue(matchesQuery("X", "   "))
    }

    @Test
    fun `un nom sans rapport est ecarte`() {
        assertFalse(matchesQuery("Beat Saber", "zzz"))
    }
}
