package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.ConnectException

/** Même nom simple que l'exception de libadb-android : le cœur la reconnaît sans en dépendre. */
private class AdbPairingRequiredException : Exception("pairing required")

class ConnectionPolicyTest {

    // --- classify

    @Test
    fun `connexion refusee donne PORT_CLOSED dans toutes les phases`() {
        ConnectPhase.entries.forEach { phase ->
            assertEquals(FailureReason.PORT_CLOSED, ConnectionPolicy.classify(ConnectException(), phase))
        }
    }

    @Test
    fun `autre echec pendant l'appairage donne PAIRING_CODE_REJECTED`() {
        assertEquals(
            FailureReason.PAIRING_CODE_REJECTED,
            ConnectionPolicy.classify(IOException("boom"), ConnectPhase.PAIRING),
        )
    }

    @Test
    fun `decouverte mDNS sans resultat donne SERVICE_NOT_FOUND`() {
        assertEquals(
            FailureReason.SERVICE_NOT_FOUND,
            ConnectionPolicy.classify(InterruptedException(), ConnectPhase.DISCOVERY),
        )
        assertEquals(
            FailureReason.SERVICE_NOT_FOUND,
            ConnectionPolicy.classify(
                IOException("Could not find any valid host address or port"),
                ConnectPhase.DISCOVERY,
            ),
        )
    }

    @Test
    fun `appairage requis est reconnu par le nom de l'exception`() {
        assertEquals(
            FailureReason.PAIRING_REQUIRED,
            ConnectionPolicy.classify(AdbPairingRequiredException(), ConnectPhase.CONNECT),
        )
    }

    @Test
    fun `tout le reste donne UNKNOWN`() {
        assertEquals(FailureReason.UNKNOWN, ConnectionPolicy.classify(IllegalStateException(), ConnectPhase.CONNECT))
        assertEquals(FailureReason.UNKNOWN, ConnectionPolicy.classify(IOException("x"), ConnectPhase.DISCOVERY))
    }

    // --- connectWithProbe

    private class Attempts(private val probeResults: List<Boolean>, private val connectResult: Boolean = true) {
        var connects = 0
        var resets = 0
        private var probes = 0
        suspend fun connect(): Boolean { connects++; return connectResult }
        suspend fun probe(): Boolean = probeResults.getOrElse(probes++) { probeResults.last() }
        suspend fun reset() { resets++ }
    }

    @Test
    fun `connexion refusee par l'hote donne NotAuthorized apres un reset`() = runTest {
        val attempts = Attempts(listOf(true), connectResult = false)
        val outcome = ConnectionPolicy.connectWithProbe(attempts::connect, attempts::probe, attempts::reset)
        assertEquals(ProbeOutcome.NotAuthorized, outcome)
        assertEquals(1, attempts.connects)
        assertEquals(1, attempts.resets)
    }

    @Test
    fun `premier probe reussi du premier coup`() = runTest {
        val attempts = Attempts(listOf(true))
        assertEquals(
            ProbeOutcome.Connected,
            ConnectionPolicy.connectWithProbe(attempts::connect, attempts::probe, attempts::reset),
        )
        assertEquals(1, attempts.connects)
        assertEquals(0, attempts.resets)
    }

    @Test
    fun `deux probes rates puis un succes`() = runTest {
        val attempts = Attempts(listOf(false, false, true))
        assertEquals(
            ProbeOutcome.Connected,
            ConnectionPolicy.connectWithProbe(attempts::connect, attempts::probe, attempts::reset),
        )
        assertEquals(3, attempts.connects)
        assertEquals(2, attempts.resets)
    }

    @Test
    fun `probe toujours en echec donne ProbeFailed apres 3 tentatives`() = runTest {
        val attempts = Attempts(listOf(false))
        assertEquals(
            ProbeOutcome.ProbeFailed,
            ConnectionPolicy.connectWithProbe(attempts::connect, attempts::probe, attempts::reset),
        )
        assertEquals(1 + ConnectionPolicy.MAX_PROBE_RETRIES, attempts.connects)
        assertEquals(3, attempts.connects)
        assertEquals(3, attempts.resets)
    }

    @Test
    fun `une exception levee par connect remonte a l'appelant`() {
        assertThrows(ConnectException::class.java) {
            kotlinx.coroutines.runBlocking {
                ConnectionPolicy.connectWithProbe(
                    connect = { throw ConnectException() },
                    probe = { true },
                    reset = {},
                )
            }
        }
    }

    // --- reconnectTargets

    @Test
    fun `sans fil, decouverte mDNS d'abord puis port enregistre en repli`() {
        assertEquals(
            listOf(ConnectTarget.Discover, ConnectTarget.Port(37000)),
            ConnectionPolicy.reconnectTargets(ConnectionMethod.WIRELESS, 37000),
        )
        assertEquals(listOf(ConnectTarget.Discover), ConnectionPolicy.reconnectTargets(ConnectionMethod.WIRELESS, null))
    }

    @Test
    fun `via PC, toujours le port 5555`() {
        assertEquals(listOf(ConnectTarget.Port(5555)), ConnectionPolicy.reconnectTargets(ConnectionMethod.PC, 37000))
    }

    @Test
    fun `aucune methode connue, aucune cible`() {
        assertEquals(emptyList<ConnectTarget>(), ConnectionPolicy.reconnectTargets(null, null))
    }
}
