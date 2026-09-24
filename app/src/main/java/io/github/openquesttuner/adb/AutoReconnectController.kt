package io.github.openquesttuner.adb

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import io.github.openquesttuner.core.AuthorizationLifetime
import io.github.openquesttuner.core.AutoReconnectManager
import io.github.openquesttuner.core.AutoReconnectPolicy
import io.github.openquesttuner.core.AutoReconnectStatus
import io.github.openquesttuner.core.AutoReconnectStore
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.DisableResult
import io.github.openquesttuner.core.EnableResult
import io.github.openquesttuner.core.ExpiryChangeResult
import io.github.openquesttuner.core.ExpiryChoiceStatus
import io.github.openquesttuner.core.PrepareResult
import io.github.openquesttuner.core.QuestModel
import io.github.openquesttuner.core.ReconnectIssue
import io.github.openquesttuner.core.WirelessDebuggingSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Reconnexion autonome après un redémarrage (spec 002) : branche [AutoReconnectPolicy] et
 * [AutoReconnectManager] sur l'ADB embarqué, et expose leur état à l'interface. Aucune décision
 * n'est prise ici (principe IV).
 */
class AutoReconnectController(
    context: Context,
    private val adb: AdbShellBackend,
    private val switch: WirelessDebuggingSwitch,
    store: AutoReconnectStore,
    model: QuestModel,
    private val scope: CoroutineScope,
) {

    val manager = AutoReconnectManager(adb, switch, store)

    /** Badge « expérimental » tant que la reconnexion n'est pas vérifiée sur ce modèle (FR-018). */
    val experimental: Boolean = !model.autoReconnectVerified

    private val _status = MutableStateFlow(manager.status())
    val status: StateFlow<AutoReconnectStatus> = _status.asStateFlow()

    private val _lastIssue = MutableStateFlow<ReconnectIssue?>(null)

    /** Cause du dernier échec, affichée tant que l'appli reste déconnectée (FR-009). */
    val lastIssue: StateFlow<ReconnectIssue?> = _lastIssue.asStateFlow()

    private val _preparing = MutableStateFlow(false)

    /** Réactivation du débogage sans fil en cours, fenêtre réseau comprise (FR-007). */
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val _expiryStatus = MutableStateFlow(manager.expiryStatus())

    /** Choix « autorisations sans expiration » (FR-021). */
    val expiryStatus: StateFlow<ExpiryChoiceStatus> = _expiryStatus.asStateFlow()

    private val _lifetime = MutableStateFlow(manager.lifetime())

    /** Délai d'expiration des autorisations, expliqué à l'activation (FR-002). */
    val lifetime: StateFlow<AuthorizationLifetime> = _lifetime.asStateFlow()

    /** Une seule reconnexion à la fois : démarrage, « Se reconnecter », « Se connecter ». */
    private val reconnectLock = Mutex()

    private val prepare: suspend () -> PrepareResult = {
        _preparing.value = true
        try {
            AutoReconnectPolicy.prepareWireless(switch)
        } finally {
            _preparing.value = false
        }
    }

    init {
        scope.launch {
            adb.state.collect { state ->
                if (state is ConnectionState.Connected) {
                    _lastIssue.value = null
                    // Rétablissement du délai ou retrait de la permission en attente.
                    manager.onConnected()
                    refresh()
                }
            }
        }
        watchWifi(context)
    }

    /** Relit les choix, la permission et le délai : démarrage, connexion, retour au premier plan. */
    fun refresh() {
        _status.value = manager.status()
        _expiryStatus.value = manager.expiryStatus()
        _lifetime.value = manager.lifetime()
    }

    /** Reconnexion au démarrage de l'appli, et bouton « Se reconnecter » (FR-005). */
    suspend fun reconnectNow() {
        if (!reconnectLock.tryLock()) return
        try {
            refresh()
            val status = _status.value
            val issue = adb.reconnectLast(prepare = if (status != AutoReconnectStatus.INACTIVE) prepare else null)
            _lastIssue.value = AutoReconnectPolicy.visibleIssue(status, issue)
        } finally {
            reconnectLock.unlock()
        }
    }

    /** « Se connecter » sans port : prépare le sans-fil si l'option n'est pas inactive (FR-005). */
    suspend fun connectWireless() {
        if (!reconnectLock.tryLock()) return
        try {
            refresh()
            val status = _status.value
            if (status != AutoReconnectStatus.INACTIVE) {
                _lastIssue.value = AutoReconnectPolicy.visibleIssue(status, adb.connectWirelessPrepared(prepare))
            } else {
                adb.connectWireless()
            }
        } finally {
            reconnectLock.unlock()
        }
    }

    /**
     * Relance la reconnexion quand un Wi-Fi arrive, si c'est lui qui manquait (FR-010, research.md
     * R4). Abonnement pour toute la vie du processus ; [reconnectLock] évite deux relances.
     */
    private fun watchWifi(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!AutoReconnectPolicy.shouldRetryOnWifi(_lastIssue.value, adb.state.value is ConnectionState.Connected)) return
                    Log.i(TAG, "Wi-Fi revenu : nouvelle reconnexion")
                    scope.launch {
                        // Le réseau annoncé ne devient le réseau actif qu'un instant plus tard.
                        delay(WIFI_SETTLE_MS)
                        reconnectNow()
                    }
                }
            },
        )
    }

    /**
     * « Activer » après confirmation, et « Réactiver » (FR-002, FR-016). Case « ne jamais faire
     * expirer » cochée : l'option d'abord (C9), puis le choix (C11). Si C11 échoue, l'option reste
     * active (data-model.md, « Résultats d'opérations »).
     * @return le résultat de l'option, et celui du choix s'il a été demandé.
     */
    suspend fun enable(neverExpire: Boolean = false): Pair<EnableResult, ExpiryChangeResult?> {
        val result = manager.enable()
        val expiry = if (result == EnableResult.ENABLED && neverExpire) manager.setNeverExpire(true) else null
        refresh()
        return result to expiry
    }

    /** « Désactiver », avec ou sans connexion (FR-013, FR-014). */
    suspend fun disable(): DisableResult = manager.disable().also { refresh() }

    /** Case « ne jamais faire expirer » cochée ou décochée depuis la carte (FR-021, FR-023). */
    suspend fun setNeverExpire(on: Boolean): ExpiryChangeResult = manager.setNeverExpire(on).also { refresh() }

    private companion object {
        const val TAG = "OqtAdb"
        const val WIFI_SETTLE_MS = 2_000L
    }
}
