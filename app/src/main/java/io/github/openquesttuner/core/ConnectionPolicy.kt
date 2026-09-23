package io.github.openquesttuner.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import kotlin.coroutines.cancellation.CancellationException

enum class ConnectPhase { PAIRING, DISCOVERY, CONNECT }

/** Où tenter une connexion : port découvert par mDNS, ou port connu. */
sealed interface ConnectTarget {
    data object Discover : ConnectTarget
    data class Port(val port: Int) : ConnectTarget
}

/** Une tentative de reconnexion : la méthode à afficher et la cible à essayer. */
data class ReconnectAttempt(val method: ConnectionMethod, val target: ConnectTarget)

/** Issue du passage en sans fil depuis une connexion via PC (FR-001). */
enum class WirelessSwitchResult { SWITCHED, NOT_ACCEPTED, WIRELESS_FAILED, NOT_CONNECTED }

sealed interface ProbeOutcome {
    data object Connected : ProbeOutcome

    /** L'hôte n'a pas accepté la clé (invite refusée ou délai dépassé). */
    data object NotAuthorized : ProbeOutcome

    /** Connexion établie, mais aucune commande n'a pu passer malgré les nouvelles tentatives. */
    data object ProbeFailed : ProbeOutcome
}

/**
 * Décisions de connexion, isolées ici pour être testées sans casque (principe IV) ;
 * la couche ADB ne fait que brancher libadb-android dessus (contracts/shell-backend.md).
 */
object ConnectionPolicy {

    const val PC_PORT = 5555

    /** Nouvelles tentatives après un probe raté (libadb-android #34, research.md R3). */
    const val MAX_PROBE_RETRIES = 2

    const val WIRELESS_ACCEPT_TIMEOUT_MS = 60_000L
    const val WIRELESS_POLL_MS = 1_000L

    private const val DISCOVERY_FAILURE_MESSAGE = "Could not find any valid host address or port"

    /** Traduit une exception de connexion en cause affichable (FR-004). */
    fun classify(error: Throwable, phase: ConnectPhase): FailureReason = when {
        error is ConnectException -> FailureReason.PORT_CLOSED
        // Comparaison par nom simple : le cœur ne dépend pas de libadb-android.
        error.javaClass.simpleName == "AdbPairingRequiredException" -> FailureReason.PAIRING_REQUIRED
        phase == ConnectPhase.DISCOVERY && error.isDiscoveryFailure() -> FailureReason.SERVICE_NOT_FOUND
        phase == ConnectPhase.PAIRING -> FailureReason.PAIRING_CODE_REJECTED
        else -> FailureReason.UNKNOWN
    }

    /**
     * Connexion suivie d'une commande de test, avec au plus [MAX_PROBE_RETRIES] nouvelles
     * tentatives. Les exceptions de [connect] remontent à l'appelant, qui les passe à [classify].
     */
    suspend fun connectWithProbe(
        connect: suspend () -> Boolean,
        probe: suspend () -> Boolean,
        reset: suspend () -> Unit,
    ): ProbeOutcome {
        repeat(1 + MAX_PROBE_RETRIES) {
            if (!connect()) {
                reset()
                return ProbeOutcome.NotAuthorized
            }
            if (probe()) return ProbeOutcome.Connected
            reset()
        }
        return ProbeOutcome.ProbeFailed
    }

    /**
     * Tentatives de reconnexion au démarrage (FR-005), dans l'ordre : d'abord la dernière méthode
     * réussie, puis l'autre en repli. Sur Quest 3 (vros 207), le port 5555 survit au redémarrage
     * alors que le débogage sans fil est coupé, et une même clé est acceptée par les deux
     * (docs/compatibility.md). En sans fil, le port TLS change à chaque activation : la découverte
     * mDNS passe avant le port enregistré.
     */
    fun reconnectAttempts(lastMethod: ConnectionMethod?, lastWirelessPort: Int?): List<ReconnectAttempt> {
        val wireless = listOfNotNull(
            ReconnectAttempt(ConnectionMethod.WIRELESS, ConnectTarget.Discover),
            lastWirelessPort?.let { ReconnectAttempt(ConnectionMethod.WIRELESS, ConnectTarget.Port(it)) },
        )
        val pc = listOf(ReconnectAttempt(ConnectionMethod.PC, ConnectTarget.Port(PC_PORT)))
        return when (lastMethod) {
            ConnectionMethod.WIRELESS -> wireless + pc
            ConnectionMethod.PC -> pc + wireless
            null -> emptyList()
        }
    }

    /**
     * Attend que le débogage sans fil soit actif : Horizon OS affiche d'abord sa fenêtre
     * « autoriser sur ce réseau », et le réglage reste à 0 tant que l'utilisateur n'a pas accepté.
     * Une lecture en erreur compte comme « pas encore actif ».
     */
    suspend fun awaitWirelessEnabled(
        read: suspend () -> Boolean,
        timeoutMs: Long = WIRELESS_ACCEPT_TIMEOUT_MS,
        pollMs: Long = WIRELESS_POLL_MS,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        while (!readSafely(read)) delay(pollMs)
        true
    } ?: false

    private suspend fun readSafely(read: suspend () -> Boolean): Boolean = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private fun Throwable.isDiscoveryFailure(): Boolean =
        this is InterruptedException ||
            (this is IOException && message?.contains(DISCOVERY_FAILURE_MESSAGE) == true)
}
