package io.github.openquesttuner.adb

import android.content.Context
import android.util.Log
import io.github.muntashirakon.adb.AdbStream
import io.github.openquesttuner.core.ConnectPhase
import io.github.openquesttuner.core.ConnectTarget
import io.github.openquesttuner.core.ConnectionMethod
import io.github.openquesttuner.core.ConnectionPolicy
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.FailureReason
import io.github.openquesttuner.core.ProbeOutcome
import io.github.openquesttuner.core.ShellBackend
import io.github.openquesttuner.core.ShellCommand
import io.github.openquesttuner.core.ShellOutput
import io.github.openquesttuner.core.ShellResult
import io.github.openquesttuner.core.ShellUnavailableException
import io.github.openquesttuner.core.WirelessSwitchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * [ShellBackend] sur l'adbd du casque lui-même, via libadb-android (contracts/shell-backend.md).
 *
 * Toutes les décisions (classification des échecs, probe et nouvelles tentatives, ordre de
 * reconnexion) viennent de [ConnectionPolicy] ; ce fichier ne fait que brancher libadb dessus.
 * Les journaux ne contiennent que des étapes et des causes, jamais de matériel de clé (FR-027).
 */
class AdbShellBackend(
    private val context: Context,
    private val identityStore: AdbIdentityStore,
    private val prefs: ConnectionPrefs,
) : ShellBackend {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val connectionLock = Mutex()

    // Créé à la demande : la clé RSA n'est générée qu'à la première connexion.
    private val managerDelegate = lazy { OqtAdbConnectionManager(identityStore.loadOrCreate()) }
    private val manager by managerDelegate

    /** Appairage TLS sur 127.0.0.1, puis connexion sans fil en cas de succès. */
    suspend fun pair(port: Int, code: String): Boolean = withContext(Dispatchers.IO) {
        val paired = connectionLock.withLock {
            disconnectQuietly()
            _state.value = ConnectionState.Pairing
            try {
                manager.pair(LOCALHOST, port, code)
                Log.i(TAG, "Appairage réussi")
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = ConnectionPolicy.classify(e, ConnectPhase.PAIRING)
                Log.w(TAG, "Appairage en échec : $reason (${e.javaClass.simpleName}: ${e.message})")
                _state.value = ConnectionState.Failed(ConnectionMethod.WIRELESS, reason)
                false
            }
        }
        paired && connectWireless()
    }

    /** Port découvert par mDNS si [port] est `null`, sinon port saisi à la main (FR-002). */
    suspend fun connectWireless(port: Int? = null): Boolean = connect(
        method = ConnectionMethod.WIRELESS,
        target = port?.let(ConnectTarget::Port) ?: ConnectTarget.Discover,
        silent = false,
        manualPort = port,
    )

    /** Port 5555 ouvert depuis un PC par `adb tcpip 5555` (FR-003). */
    suspend fun connectPc(): Boolean =
        connect(ConnectionMethod.PC, ConnectTarget.Port(ConnectionPolicy.PC_PORT), silent = false)

    /**
     * Passage en sans fil depuis une connexion via PC (FR-001) : l'appli active le débogage sans
     * fil, attend que l'utilisateur autorise le réseau dans la fenêtre d'Horizon OS, puis se
     * connecte en TLS avec la même clé, sans appairage (docs/compatibility.md).
     */
    suspend fun switchToWireless(): WirelessSwitchResult {
        val current = _state.value
        if (current !is ConnectionState.Connected) return WirelessSwitchResult.NOT_CONNECTED
        if (current.method == ConnectionMethod.WIRELESS) return WirelessSwitchResult.SWITCHED
        try {
            if (!exec(ShellCommand.enableWirelessDebugging()).isSuccess) return WirelessSwitchResult.WIRELESS_FAILED
            val enabled = ConnectionPolicy.awaitWirelessEnabled(
                read = { ShellOutput.isSettingEnabled(exec(ShellCommand.readWirelessDebugging()).output) },
            )
            if (!enabled) {
                Log.i(TAG, "Passage en sans fil : réseau non autorisé dans le délai")
                return WirelessSwitchResult.NOT_ACCEPTED
            }
        } catch (e: ShellUnavailableException) {
            return WirelessSwitchResult.NOT_CONNECTED
        }
        if (connectWireless()) return WirelessSwitchResult.SWITCHED
        Log.w(TAG, "Passage en sans fil en échec : retour au port ${ConnectionPolicy.PC_PORT}")
        connect(ConnectionMethod.PC, ConnectTarget.Port(ConnectionPolicy.PC_PORT), silent = true)
        return WirelessSwitchResult.WIRELESS_FAILED
    }

    /**
     * Rejoue la dernière méthode réussie, puis l'autre en repli ; en cas d'échec l'état reste
     * `Disconnected`, jamais `Failed` (FR-005).
     */
    suspend fun reconnectLast() {
        for (attempt in ConnectionPolicy.reconnectAttempts(prefs.lastMethod, prefs.lastWirelessPort)) {
            if (connect(attempt.method, attempt.target, silent = true)) return
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectionLock.withLock {
            disconnectQuietly()
            _state.value = ConnectionState.Disconnected
        }
    }

    override suspend fun exec(command: ShellCommand): ShellResult = withContext(Dispatchers.IO) {
        if (_state.value !is ConnectionState.Connected) throw ShellUnavailableException()
        val result = ConnectionPolicy.withRetries { attempt ->
            runWithTimeout(EXEC_TIMEOUT_MS, command).onFailure { error ->
                Log.w(TAG, "Commande sans réponse, essai $attempt/${ConnectionPolicy.MAX_EXEC_ATTEMPTS} : ${error.javaClass.simpleName}")
            }
        }
        result.getOrElse { error ->
            Log.w(TAG, "Commande en échec, connexion considérée perdue : ${error.javaClass.simpleName}")
            connectionLost()
            throw ShellUnavailableException(error)
        }
    }

    private suspend fun connect(
        method: ConnectionMethod,
        target: ConnectTarget,
        silent: Boolean,
        manualPort: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        connectionLock.withLock {
            disconnectQuietly()
            _state.value = ConnectionState.Connecting(method)
            val phase = if (target is ConnectTarget.Discover) ConnectPhase.DISCOVERY else ConnectPhase.CONNECT
            Log.i(TAG, "Connexion $method vers $target${if (silent) " (reconnexion)" else ""}")
            val outcome = try {
                ConnectionPolicy.connectWithProbe(
                    connect = { openConnection(target) },
                    probe = { runWithTimeout(PROBE_TIMEOUT_MS, ShellCommand.probe()).getOrNull()?.isSuccess == true },
                    reset = { disconnectQuietly() },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                disconnectQuietly()
                val reason = ConnectionPolicy.classify(e, phase)
                Log.w(TAG, "Connexion $method en échec : $reason (${e.javaClass.simpleName}: ${e.message})")
                fail(method, reason, silent)
                return@withLock false
            }
            Log.i(TAG, "Connexion $method : $outcome")
            when (outcome) {
                ProbeOutcome.Connected -> {
                    _state.value = ConnectionState.Connected(method)
                    prefs.lastMethod = method
                    if (method == ConnectionMethod.WIRELESS && manualPort != null) prefs.lastWirelessPort = manualPort
                    true
                }
                ProbeOutcome.NotAuthorized -> {
                    fail(method, FailureReason.NOT_AUTHORIZED, silent)
                    false
                }
                ProbeOutcome.ProbeFailed -> {
                    fail(method, FailureReason.UNKNOWN, silent)
                    false
                }
            }
        }
    }

    private fun openConnection(target: ConnectTarget): Boolean = when (target) {
        ConnectTarget.Discover -> manager.connectTls(context, DISCOVERY_TIMEOUT_MS)
        is ConnectTarget.Port -> manager.connect(LOCALHOST, target.port)
    }

    private fun fail(method: ConnectionMethod, reason: FailureReason, silent: Boolean) {
        _state.value = if (silent) ConnectionState.Disconnected else ConnectionState.Failed(method, reason)
    }

    /**
     * libadb bloque sans limite si la réponse à l'ouverture d'un flux lui échappe (réveil perdu,
     * research.md R3) ou si adbd ne répond pas : le délai interrompt le thread bloqué, ce qui
     * débloque ses `wait()` internes.
     */
    private suspend fun runWithTimeout(timeoutMs: Long, command: ShellCommand): Result<ShellResult> =
        withTimeoutOrNull(timeoutMs) {
            runInterruptible { runCatching { rawExec(command) } }
        } ?: Result.failure(IOException("Pas de réponse d'adbd en $timeoutMs ms"))

    /** Exécution sans effet sur l'état ; seules les fabriques de [ShellCommand] produisent le texte. */
    private fun rawExec(command: ShellCommand): ShellResult =
        manager.openStream("shell:" + command.wireText).use { stream -> ShellOutput.parse(readAll(stream)) }

    private fun readAll(stream: AdbStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val input = stream.openInputStream()
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                // Le marqueur de fin suffit : libadb peut ne jamais signaler la fermeture du flux
                // quand elle arrive avant la lecture de la dernière donnée (research.md R3).
                if (ShellOutput.isComplete(out.toString(Charsets.UTF_8.name()))) break
            }
        } catch (e: IOException) {
            // libadb signale parfois la fin du flux par « Stream closed. » après la dernière donnée.
            if (out.size() == 0) throw e
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun connectionLost() {
        _state.value = ConnectionState.Disconnected
        disconnectQuietly()
    }

    private fun disconnectQuietly() {
        if (!managerDelegate.isInitialized()) return
        try {
            manager.disconnect()
        } catch (e: IOException) {
            // Socket déjà fermé : rien à faire.
        }
    }

    private companion object {
        const val TAG = "OqtAdb"
        const val LOCALHOST = "127.0.0.1"
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val PROBE_TIMEOUT_MS = 2_000L
        // Une commande de la liste fermée répond en quelques dizaines de ms : au-delà de 3 s, c'est
        // une réponse perdue, rejouée par ConnectionPolicy.withRetries.
        const val EXEC_TIMEOUT_MS = 3_000L
        const val READ_BUFFER_SIZE = 8 * 1024
    }
}
