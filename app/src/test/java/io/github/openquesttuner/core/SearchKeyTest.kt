package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
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
}
