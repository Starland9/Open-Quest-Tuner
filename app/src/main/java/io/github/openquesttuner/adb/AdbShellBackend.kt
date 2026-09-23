package io.github.openquesttuner.adb

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * [ShellBackend] sur l'adbd du casque lui-même, via libadb-android (contracts/shell-backend.md).
 *
 * Toutes les décisions (classification des échecs, probe et nouvelles tentatives, ordre de
 * reconnexion) viennent de [ConnectionPolicy] ; ce fichier ne fait que brancher libadb dessus.
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
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ConnectionState.Failed(
                    ConnectionMethod.WIRELESS,
                    ConnectionPolicy.classify(e, ConnectPhase.PAIRING),
                )
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

    /** Rejoue la dernière méthode réussie ; en cas d'échec l'état reste `Disconnected` (FR-005). */
    suspend fun reconnectLast() {
        val method = prefs.lastMethod ?: return
        for (target in ConnectionPolicy.reconnectTargets(method, prefs.lastWirelessPort)) {
            if (connect(method, target, silent = true)) return
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
        try {
            rawExec(command)
        } catch (e: IOException) {
            connectionLost()
            throw ShellUnavailableException(e)
        } catch (e: InterruptedException) {
            connectionLost()
            throw ShellUnavailableException(e)
        } catch (e: IllegalStateException) {
            // openStream enveloppe AdbPairingRequiredException dans une IllegalStateException.
            connectionLost()
            throw ShellUnavailableException(e)
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
            val outcome = try {
                ConnectionPolicy.connectWithProbe(
                    connect = { openConnection(target) },
                    probe = { runCatching { rawExec(ShellCommand.probe()) }.getOrNull()?.isSuccess == true },
                    reset = { disconnectQuietly() },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                disconnectQuietly()
                fail(method, ConnectionPolicy.classify(e, phase), silent)
                return@withLock false
            }
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
        const val LOCALHOST = "127.0.0.1"
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val READ_BUFFER_SIZE = 8 * 1024
    }
}
