package io.github.openquesttuner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionInputTest {

    @Test
    fun `un code d'appairage fait exactement 6 chiffres`() {
        assertTrue(ConnectionInput.isValidPairingCode("123456"))
        listOf("12345", "1234567", "12a456", " 123456", "", "12 456").forEach { code ->
            assertFalse("code accepté à tort : '$code'", ConnectionInput.isValidPairingCode(code))
        }
    }

    @Test
    fun `un port valide va de 1 a 65535`() {
        assertEquals(37123, ConnectionInput.parsePort("37123"))
        assertEquals(1, ConnectionInput.parsePort("1"))
        assertEquals(65535, ConnectionInput.parsePort("65535"))
    }

    @Test
    fun `les ports invalides donnent null`() {
        listOf("0", "65536", "", "12;3", "-1", "abc", " 5555", "99999999999").forEach { text ->
            assertNull("port accepté à tort : '$text'", ConnectionInput.parsePort(text))
        }
    }
}
