package io.github.openquesttuner.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.ConnectException

/** Même nom simple que l'exception de libadb-android : le cœur la reconnaît sans en dépendre. */
private class AdbPairingRequiredException : Exception("pairing required")

@OptIn(ExperimentalCoroutinesApi::class)
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

    // --- awaitWirelessEnabled

    @Test
    fun `attend que le debogage sans fil soit actif`() = runTest {
        var reads = 0
        val enabled = ConnectionPolicy.awaitWirelessEnabled(read = { ++reads >= 3 })
        assertEquals(true, enabled)
        assertEquals(3, reads)
    }

    @Test
    fun `abandonne au bout de 60 secondes sans acceptation`() = runTest {
        var reads = 0
        val start = testScheduler.currentTime
        val enabled = ConnectionPolicy.awaitWirelessEnabled(read = { reads++; false })
        assertEquals(false, enabled)
        assertEquals(60_000L, testScheduler.currentTime - start)
        assertEquals(true, reads in 59..61)
    }

    @Test
    fun `une lecture en erreur compte comme non actif`() = runTest {
        var reads = 0
        val enabled = ConnectionPolicy.awaitWirelessEnabled(
            read = {
                reads++
                if (reads == 1) throw IOException("lecture impossible") else true
            },
        )
        assertEquals(true, enabled)
        assertEquals(2, reads)
    }

    // --- reconnectAttempts

    private fun attempt(method: ConnectionMethod, target: ConnectTarget) = ReconnectAttempt(method, target)

    @Test
    fun `sans fil d'abord, mDNS puis port enregistre puis repli sur le port 5555`() {
        // Sur Quest 3 (vros 207), le port 5555 survit au redémarrage alors que le TLS est coupé.
        assertEquals(
            listOf(
                attempt(ConnectionMethod.WIRELESS, ConnectTarget.Discover),
                attempt(ConnectionMethod.WIRELESS, ConnectTarget.Port(37000)),
                attempt(ConnectionMethod.PC, ConnectTarget.Port(5555)),
            ),
            ConnectionPolicy.reconnectAttempts(ConnectionMethod.WIRELESS, 37000),
        )
        assertEquals(
            listOf(
                attempt(ConnectionMethod.WIRELESS, ConnectTarget.Discover),
                attempt(ConnectionMethod.PC, ConnectTarget.Port(5555)),
            ),
            ConnectionPolicy.reconnectAttempts(ConnectionMethod.WIRELESS, null),
        )
    }

    @Test
    fun `via PC d'abord, port 5555 puis repli sur le sans fil`() {
        assertEquals(
            listOf(
                attempt(ConnectionMethod.PC, ConnectTarget.Port(5555)),
                attempt(ConnectionMethod.WIRELESS, ConnectTarget.Discover),
                attempt(ConnectionMethod.WIRELESS, ConnectTarget.Port(37000)),
            ),
            ConnectionPolicy.reconnectAttempts(ConnectionMethod.PC, 37000),
        )
    }

    @Test
    fun `aucune methode connue, aucune tentative`() {
        assertEquals(emptyList<ReconnectAttempt>(), ConnectionPolicy.reconnectAttempts(null, 37000))
    }

    // --- withRetries : réponses perdues par libadb (research.md R3)

    @Test
    fun `une reussite au premier essai ne rejoue rien`() = runTest {
        val attempts = mutableListOf<Int>()
        val result = ConnectionPolicy.withRetries { attempt -> attempts += attempt; Result.success("ok") }

        assertEquals(Result.success("ok"), result)
        assertEquals(listOf(1), attempts)
    }

    @Test
    fun `deux echecs de transport puis une reussite`() = runTest {
        val attempts = mutableListOf<Int>()
        val result = ConnectionPolicy.withRetries { attempt ->
            attempts += attempt
            if (attempt < 3) Result.failure(IOException("pas de réponse")) else Result.success("ok")
        }

        assertEquals(Result.success("ok"), result)
        assertEquals(listOf(1, 2, 3), attempts)
    }

    @Test
    fun `apres trois echecs le dernier echec est renvoye`() = runTest {
        var attempts = 0
        val result = ConnectionPolicy.withRetries<String> { attempt ->
            attempts++
            Result.failure(IOException("échec $attempt"))
        }

        assertEquals(ConnectionPolicy.MAX_EXEC_ATTEMPTS, attempts)
        assertEquals("échec 3", result.exceptionOrNull()?.message)
    }

    @Test
    fun `un code de sortie non nul n'est pas rejoue`() = runTest {
        var attempts = 0
        val result = ConnectionPolicy.withRetries { attempts++; Result.success(ShellResult(exitCode = 1, output = "denied")) }

        assertEquals(1, attempts)
        assertEquals(ShellResult(exitCode = 1, output = "denied"), result.getOrNull())
    }
}
