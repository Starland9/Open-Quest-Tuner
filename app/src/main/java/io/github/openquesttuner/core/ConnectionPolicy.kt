package io.github.openquesttuner.core

import java.io.IOException
import java.net.ConnectException

enum class ConnectPhase { PAIRING, DISCOVERY, CONNECT }

/** Où tenter une connexion : port découvert par mDNS, ou port connu. */
sealed interface ConnectTarget {
    data object Discover : ConnectTarget
    data class Port(val port: Int) : ConnectTarget
}

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
     * Ordre des tentatives de reconnexion au démarrage (FR-005). En sans fil, le port TLS change à
     * chaque activation : la découverte mDNS passe avant le port enregistré, qui n'est qu'un repli.
     */
    fun reconnectTargets(lastMethod: ConnectionMethod?, lastWirelessPort: Int?): List<ConnectTarget> =
        when (lastMethod) {
            ConnectionMethod.WIRELESS -> listOfNotNull(ConnectTarget.Discover, lastWirelessPort?.let(ConnectTarget::Port))
            ConnectionMethod.PC -> listOf(ConnectTarget.Port(PC_PORT))
            null -> emptyList()
        }

    private fun Throwable.isDiscoveryFailure(): Boolean =
        this is InterruptedException ||
            (this is IOException && message?.contains(DISCOVERY_FAILURE_MESSAGE) == true)
}
