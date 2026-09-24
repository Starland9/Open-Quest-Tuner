package io.github.openquesttuner.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoReconnectPolicyTest {

    // --- status

    @Test
    fun `statut derive du choix et de la permission`() {
        assertEquals(AutoReconnectStatus.INACTIVE, AutoReconnectPolicy.status(optionEnabled = false, rightHeld = false))
        assertEquals(AutoReconnectStatus.INACTIVE, AutoReconnectPolicy.status(optionEnabled = false, rightHeld = true))
        assertEquals(AutoReconnectStatus.ACTIVE, AutoReconnectPolicy.status(optionEnabled = true, rightHeld = true))
        assertEquals(
            AutoReconnectStatus.NEEDS_REACTIVATION,
            AutoReconnectPolicy.status(optionEnabled = true, rightHeld = false),
        )
    }

    // --- prepareWireless : étapes 1 à 6 de data-model.md

    @Test
    fun `1 debogage desactive`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply { debuggingEnabled = false; rightHeld = true }
        assertEquals(PrepareResult.Issue(ReconnectIssue.DEBUGGING_DISABLED), AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(0, switch.enableCalls)
    }

    @Test
    fun `2 pas de Wi-Fi`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply { onWifi = false; rightHeld = true }
        assertEquals(PrepareResult.Issue(ReconnectIssue.NO_WIFI), AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(0, switch.enableCalls)
    }

    @Test
    fun `3 sans-fil deja actif, permission inutile`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply { wirelessEnabled = true; rightHeld = false }
        assertEquals(PrepareResult.Ready, AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(0, switch.enableCalls)
    }

    @Test
    fun `4 permission absente`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply { rightHeld = false }
        assertEquals(PrepareResult.Issue(ReconnectIssue.RIGHT_LOST), AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(0, switch.enableCalls)
    }

    @Test
    fun `5 ecrit puis attend l'acceptation`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply { rightHeld = true; acceptAfterReads = 3 }
        assertEquals(PrepareResult.Ready, AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(1, switch.enableCalls)
    }

    @Test
    fun `6 fenetre refusee, abandon au bout de 60 secondes`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply { rightHeld = true; acceptAfterReads = null }
        assertEquals(PrepareResult.Issue(ReconnectIssue.NETWORK_NOT_ALLOWED), AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(1, switch.enableCalls)
        assertTrue(testScheduler.currentTime >= ConnectionPolicy.WIRELESS_ACCEPT_TIMEOUT_MS)
    }

    @Test
    fun `6 bis l'ecriture relue avant la fenetre du casque ne compte pas comme acceptee`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply {
            rightHeld = true
            acceptAfterReads = null
            now = { testScheduler.currentTime }
            echoWindowMs = 50
        }
        assertEquals(PrepareResult.Issue(ReconnectIssue.NETWORK_NOT_ALLOWED), AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(ConnectionPolicy.WIRELESS_ACCEPT_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `7 une lecture en erreur compte comme desactive`() = runTest {
        val switch = FakeWirelessDebuggingSwitch().apply { rightHeld = true; readThrows = true }
        assertEquals(PrepareResult.Issue(ReconnectIssue.NETWORK_NOT_ALLOWED), AutoReconnectPolicy.prepareWireless(switch))
        assertEquals(1, switch.enableCalls)
        assertTrue(testScheduler.currentTime >= ConnectionPolicy.WIRELESS_ACCEPT_TIMEOUT_MS)
    }

    // --- issueFor : research.md R7

    @Test
    fun `cause d'un echec de connexion sans fil`() {
        assertEquals(ReconnectIssue.AUTHORIZATION_LOST, AutoReconnectPolicy.issueFor(FailureReason.PAIRING_REQUIRED))
        assertEquals(ReconnectIssue.AUTHORIZATION_LOST, AutoReconnectPolicy.issueFor(FailureReason.NOT_AUTHORIZED))
        assertEquals(ReconnectIssue.WIRELESS_NOT_STARTED, AutoReconnectPolicy.issueFor(FailureReason.SERVICE_NOT_FOUND))
        listOf(FailureReason.PORT_CLOSED, FailureReason.PAIRING_CODE_REJECTED, FailureReason.UNKNOWN, null).forEach {
            assertEquals(ReconnectIssue.UNKNOWN, AutoReconnectPolicy.issueFor(it))
        }
    }

    // --- reconnect

    private val wirelessMarked = ReconnectAttempt(ConnectionMethod.WIRELESS, ConnectTarget.Discover, prepareWireless = true)
    private val wirelessPort = ReconnectAttempt(ConnectionMethod.WIRELESS, ConnectTarget.Port(37000))
    private val pc = ReconnectAttempt(ConnectionMethod.PC, ConnectTarget.Port(5555))

    /** Enregistre les appels ; [results] donne le résultat de `connect` par tentative (échec par défaut). */
    private class Calls(
        private val prepareResult: PrepareResult = PrepareResult.Ready,
        private val results: Map<ReconnectAttempt, FailureReason?> = emptyMap(),
    ) {
        var prepares = 0
        val connected = mutableListOf<ReconnectAttempt>()

        suspend fun prepare(): PrepareResult {
            prepares++
            return prepareResult
        }

        suspend fun connect(attempt: ReconnectAttempt): FailureReason? {
            connected += attempt
            return if (attempt in results) results[attempt] else FailureReason.UNKNOWN
        }
    }

    @Test
    fun `sans tentative marquee, pas de preparation ni de cause`() = runTest {
        val unmarked = wirelessMarked.copy(prepareWireless = false)
        val calls = Calls()
        val outcome = AutoReconnectPolicy.reconnect(listOf(unmarked, pc), calls::prepare, calls::connect)
        assertEquals(ReconnectOutcome.Failed(null), outcome)
        assertEquals(0, calls.prepares)
        assertEquals(listOf(unmarked, pc), calls.connected)
    }

    @Test
    fun `preparation en echec, le sans-fil est saute et le PC reussit`() = runTest {
        val calls = Calls(PrepareResult.Issue(ReconnectIssue.NO_WIFI), mapOf(pc to null))
        val outcome = AutoReconnectPolicy.reconnect(listOf(wirelessMarked, wirelessPort, pc), calls::prepare, calls::connect)
        assertEquals(ReconnectOutcome.Connected, outcome)
        assertEquals(listOf(pc), calls.connected)
    }

    @Test
    fun `preparation en echec et PC en echec, la cause de la preparation est rendue`() = runTest {
        val calls = Calls(PrepareResult.Issue(ReconnectIssue.NO_WIFI))
        val outcome = AutoReconnectPolicy.reconnect(listOf(wirelessMarked, wirelessPort, pc), calls::prepare, calls::connect)
        assertEquals(ReconnectOutcome.Failed(ReconnectIssue.NO_WIFI), outcome)
        assertEquals(listOf(pc), calls.connected)
    }

    @Test
    fun `cle refusee apres une preparation reussie`() = runTest {
        val calls = Calls(PrepareResult.Ready, mapOf(wirelessMarked to FailureReason.PAIRING_REQUIRED, pc to FailureReason.PORT_CLOSED))
        val outcome = AutoReconnectPolicy.reconnect(listOf(wirelessMarked, pc), calls::prepare, calls::connect)
        assertEquals(ReconnectOutcome.Failed(ReconnectIssue.AUTHORIZATION_LOST), outcome)
    }

    @Test
    fun `la premiere cause du sans-fil est gardee`() = runTest {
        val calls = Calls(
            PrepareResult.Ready,
            mapOf(wirelessMarked to FailureReason.SERVICE_NOT_FOUND, wirelessPort to FailureReason.PORT_CLOSED),
        )
        val outcome = AutoReconnectPolicy.reconnect(listOf(wirelessMarked, wirelessPort, pc), calls::prepare, calls::connect)
        assertEquals(ReconnectOutcome.Failed(ReconnectIssue.WIRELESS_NOT_STARTED), outcome)
    }

    @Test
    fun `via PC d'abord et PC reussi, pas de preparation`() = runTest {
        val calls = Calls(results = mapOf(pc to null))
        val outcome = AutoReconnectPolicy.reconnect(listOf(pc, wirelessMarked, wirelessPort), calls::prepare, calls::connect)
        assertEquals(ReconnectOutcome.Connected, outcome)
        assertEquals(0, calls.prepares)
        assertEquals(listOf(pc), calls.connected)
    }

    @Test
    fun `une seule preparation pour deux tentatives sans fil`() = runTest {
        val secondMarked = wirelessPort.copy(prepareWireless = true)
        val calls = Calls(PrepareResult.Ready, mapOf(secondMarked to null))
        val outcome = AutoReconnectPolicy.reconnect(listOf(wirelessMarked, secondMarked, pc), calls::prepare, calls::connect)
        assertEquals(ReconnectOutcome.Connected, outcome)
        assertEquals(1, calls.prepares)
    }

    // --- visibleIssue et shouldRetryOnWifi

    @Test
    fun `option inactive, aucune cause affichee`() {
        assertNull(AutoReconnectPolicy.visibleIssue(AutoReconnectStatus.INACTIVE, ReconnectIssue.NO_WIFI))
        assertEquals(
            ReconnectIssue.NO_WIFI,
            AutoReconnectPolicy.visibleIssue(AutoReconnectStatus.ACTIVE, ReconnectIssue.NO_WIFI),
        )
        assertEquals(
            ReconnectIssue.RIGHT_LOST,
            AutoReconnectPolicy.visibleIssue(AutoReconnectStatus.NEEDS_REACTIVATION, ReconnectIssue.RIGHT_LOST),
        )
        assertNull(AutoReconnectPolicy.visibleIssue(AutoReconnectStatus.ACTIVE, null))
    }

    @Test
    fun `relance au retour du Wi-Fi seulement apres NO_WIFI et hors connexion`() {
        assertTrue(AutoReconnectPolicy.shouldRetryOnWifi(ReconnectIssue.NO_WIFI, connected = false))
        assertFalse(AutoReconnectPolicy.shouldRetryOnWifi(ReconnectIssue.NO_WIFI, connected = true))
        assertFalse(AutoReconnectPolicy.shouldRetryOnWifi(null, connected = false))
        ReconnectIssue.entries.filter { it != ReconnectIssue.NO_WIFI }.forEach { issue ->
            assertFalse(AutoReconnectPolicy.shouldRetryOnWifi(issue, connected = false))
        }
    }
}
