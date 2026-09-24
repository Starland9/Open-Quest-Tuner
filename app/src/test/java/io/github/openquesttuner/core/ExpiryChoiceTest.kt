package io.github.openquesttuner.core

import io.github.openquesttuner.core.AuthorizationLifetime.Days
import io.github.openquesttuner.core.AuthorizationLifetime.Never
import io.github.openquesttuner.core.AuthorizationLifetime.OutOfRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** data-model.md, « Durée de vie de l'autorisation » et « `ExpiryChoiceStatus` » (spec 002, US4). */
class ExpiryChoiceTest {

    private val max = ShellCommand.MAX_KEY_LIFETIME_MS

    @Test
    fun `duree de vie lue`() {
        assertEquals(Days(7), ExpiryChoicePolicy.lifetimeOf(null))
        assertEquals(Never, ExpiryChoicePolicy.lifetimeOf(0))
        assertEquals(Days(7), ExpiryChoicePolicy.lifetimeOf(604_800_000))
        assertEquals(Days(1), ExpiryChoicePolicy.lifetimeOf(86_400_000))
        assertEquals(Days(0), ExpiryChoicePolicy.lifetimeOf(3_600_000))
        assertEquals(Days(3650), ExpiryChoicePolicy.lifetimeOf(max))
        assertEquals(OutOfRange, ExpiryChoicePolicy.lifetimeOf(-1))
        assertEquals(OutOfRange, ExpiryChoicePolicy.lifetimeOf(max + 1))
    }

    @Test
    fun `choix sans objet si le casque n'expire deja pas ou si la valeur est hors plage`() {
        listOf(0L, -1L, max + 1).forEach { current ->
            assertEquals(ExpiryChoiceStatus.NOT_APPLICABLE, ExpiryChoicePolicy.status(null, false, current))
        }
    }

    @Test
    fun `statut du choix`() {
        assertEquals(ExpiryChoiceStatus.OFF, ExpiryChoicePolicy.status(null, false, 604_800_000))
        assertEquals(ExpiryChoiceStatus.OFF, ExpiryChoicePolicy.status(null, false, null))
        listOf(0L, 604_800_000L, null).forEach { current ->
            assertEquals(ExpiryChoiceStatus.ON, ExpiryChoicePolicy.status("604800000", false, current))
            assertEquals(ExpiryChoiceStatus.RESTORE_PENDING, ExpiryChoicePolicy.status("default", true, current))
        }
    }

    @Test
    fun `valeur d'origine retenue`() {
        assertEquals("default", ExpiryChoicePolicy.encodeOriginal(null))
        assertEquals("604800000", ExpiryChoicePolicy.encodeOriginal(604_800_000))
    }

    @Test
    fun `commande de retablissement`() {
        assertEquals(ShellCommand.resetAuthorizationExpiry().text, ExpiryChoicePolicy.restoreCommand("default").text)
        assertEquals(
            ShellCommand.restoreAuthorizationExpiry(604_800_000).text,
            ExpiryChoicePolicy.restoreCommand("604800000").text,
        )
        listOf("abc", "0", "-1", "400000000000", "", " 604800000").forEach { bad ->
            assertThrows("valeur retenue acceptée à tort : '$bad'", IllegalArgumentException::class.java) {
                ExpiryChoicePolicy.restoreCommand(bad)
            }
        }
    }
}
