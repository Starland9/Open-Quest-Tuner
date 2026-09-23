package io.github.openquesttuner.core

import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

/**
 * Seule porte d'accès au shell du casque (contracts/shell-backend.md, principe IV).
 * Le cœur ne connaît que cette interface ; l'implémentation actuelle est l'ADB embarqué.
 */
interface ShellBackend {
    /** État courant de la connexion, observé par l'interface. */
    val state: StateFlow<ConnectionState>

    /**
     * Exécute une commande de la liste fermée (contracts/shell-commands.md).
     * @throws ShellUnavailableException si aucune connexion n'est établie ou si elle vient d'être
     *         perdue ; l'état passe alors à [ConnectionState.Disconnected].
     */
    suspend fun exec(command: ShellCommand): ShellResult
}

data class ShellResult(val exitCode: Int?, val output: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

class ShellUnavailableException(cause: Throwable? = null) :
    IOException("Shell ADB indisponible", cause)

enum class ConnectionMethod { WIRELESS, PC }

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Pairing : ConnectionState
    data class Connecting(val method: ConnectionMethod) : ConnectionState
    data class Connected(val method: ConnectionMethod) : ConnectionState
    data class Failed(val method: ConnectionMethod?, val reason: FailureReason) : ConnectionState
}

enum class FailureReason {
    /** Connexion refusée : port 5555 fermé, ou débogage sans fil désactivé. */
    PORT_CLOSED,

    /** Invite d'autorisation refusée ou délai dépassé. */
    NOT_AUTHORIZED,

    /** Le port TLS exige un appairage préalable. */
    PAIRING_REQUIRED,

    /** Code d'appairage faux ou expiré. */
    PAIRING_CODE_REJECTED,

    /** Découverte mDNS du port de connexion sans résultat. */
    SERVICE_NOT_FOUND,

    UNKNOWN,
}
