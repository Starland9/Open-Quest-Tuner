package io.github.openquesttuner.ui

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.openquesttuner.OqtApplication
import io.github.openquesttuner.R
import io.github.openquesttuner.core.AuthorizationLifetime
import io.github.openquesttuner.core.AutoReconnectStatus
import io.github.openquesttuner.core.ConnectionInput
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.Diagnostic
import io.github.openquesttuner.core.DisableResult
import io.github.openquesttuner.core.EnableResult
import io.github.openquesttuner.core.ExpiryChangeResult
import io.github.openquesttuner.core.ExpiryChoiceStatus
import io.github.openquesttuner.core.GameProfile
import io.github.openquesttuner.core.QuestModel
import io.github.openquesttuner.core.QuestProperty
import io.github.openquesttuner.core.ReconnectIssue
import io.github.openquesttuner.core.ThermalLevel
import io.github.openquesttuner.core.TuneResult
import io.github.openquesttuner.core.TuneStep
import io.github.openquesttuner.core.WirelessSwitchResult
import io.github.openquesttuner.core.matchesQuery
import io.github.openquesttuner.games.InstalledGame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Écrans de l'appli ; la navigation est une simple pile gardée dans le ViewModel. */
sealed interface Screen {
    data object Games : Screen
    data object Connection : Screen
    data class Profile(val packageName: String) : Screen
}

/** Message éphémère (snackbar), résolu en texte localisé au moment de l'affichage. */
data class UiMessage(@param:StringRes val res: Int, val args: List<Any> = emptyList())

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OqtApplication).container

    private val _backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Games))
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val connectionState: StateFlow<ConnectionState> = container.adb.state

    val questModel: QuestModel = container.questModel

    val profiles: StateFlow<Map<String, GameProfile>> = container.profileStore.profiles

    private val _games = MutableStateFlow<List<InstalledGame>>(emptyList())
    val games: StateFlow<List<InstalledGame>> = _games.asStateFlow()

    private val _loadingGames = MutableStateFlow(true)
    val loadingGames: StateFlow<Boolean> = _loadingGames.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Liste filtrée au fil de la saisie, sans casse ni accents (FR-009). */
    val filteredGames: StateFlow<List<InstalledGame>> = combine(_games, _query) { games, query ->
        games.filter { matchesQuery(it.label, query) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tuningPackage = MutableStateFlow<String?>(null)

    /**
     * Jeu en cours de lancement, ou `null`. Tous les boutons de lancement sont désactivés pendant
     * l'opération, pour éviter les doubles envois.
     */
    val tuningPackage: StateFlow<String?> = _tuningPackage.asStateFlow()

    val thermalLevel: StateFlow<ThermalLevel> = container.thermal.level

    private val _diagnostic = MutableStateFlow<Diagnostic?>(null)

    /** Dernière lecture des propriétés actives ; `null` hors connexion ou si la lecture a échoué. */
    val diagnostic: StateFlow<Diagnostic?> = _diagnostic.asStateFlow()

    init {
        refreshGames()
        // Relecture à chaque connexion ; hors connexion, l'état du casque est inconnu.
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is ConnectionState.Connected) refreshDiagnostic() else _diagnostic.value = null
            }
        }
    }

    fun navigate(screen: Screen) {
        // L'indicateur « actif sur le casque » et la carte Diagnostic partent d'une lecture fraîche.
        if (screen is Screen.Profile || screen == Screen.Connection) refreshDiagnostic()
        _backStack.update { stack -> if (stack.last() == screen) stack else stack + screen }
    }

    /** @return `false` s'il n'y a plus d'écran à dépiler. */
    fun back(): Boolean {
        if (_backStack.value.size <= 1) return false
        _backStack.update { it.dropLast(1) }
        return true
    }

    fun showMessage(@StringRes res: Int, vararg args: Any) {
        _messages.trySend(UiMessage(res, args.toList()))
    }

    // --- Connexion (US1). Les saisies sont validées ici et ne vont jamais au shell (FR-025).

    fun pair(portText: String, code: String) {
        val port = ConnectionInput.parsePort(portText) ?: return
        if (!ConnectionInput.isValidPairingCode(code)) return
        viewModelScope.launch { container.adb.pair(port, code) }
    }

    /**
     * Port vide : découverte automatique (mDNS). Si la reconnexion autonome n'est pas inactive,
     * le débogage sans fil est d'abord réactivé (spec 002, FR-005).
     */
    fun connectWireless(portText: String) {
        val port = if (portText.isBlank()) null else ConnectionInput.parsePort(portText) ?: return
        viewModelScope.launch {
            if (port == null && autoReconnectStatus.value != AutoReconnectStatus.INACTIVE) {
                autoReconnect.connectWireless()
            } else {
                container.adb.connectWireless(port)
            }
        }
    }

    private val _switchingToWireless = MutableStateFlow(false)
    val switchingToWireless: StateFlow<Boolean> = _switchingToWireless.asStateFlow()

    /** « Passer en sans fil » depuis une connexion via PC (FR-001). */
    fun switchToWireless() {
        if (_switchingToWireless.value) return
        _switchingToWireless.value = true
        viewModelScope.launch {
            try {
                when (container.adb.switchToWireless()) {
                    // L'état « Connecté (sans fil) » suffit ; on propose ensuite la reconnexion autonome.
                    WirelessSwitchResult.SWITCHED ->
                        if (autoReconnectStatus.value == AutoReconnectStatus.INACTIVE) _autoReconnectProposal.value = true
                    WirelessSwitchResult.NOT_ACCEPTED -> showMessage(R.string.switch_not_accepted)
                    WirelessSwitchResult.WIRELESS_FAILED -> showMessage(R.string.switch_wireless_failed)
                    WirelessSwitchResult.NOT_CONNECTED -> showMessage(R.string.switch_not_connected)
                }
            } finally {
                _switchingToWireless.value = false
            }
        }
    }

    fun connectPc() {
        viewModelScope.launch { container.adb.connectPc() }
    }

    fun disconnect() {
        viewModelScope.launch { container.adb.disconnect() }
    }

    // --- Reconnexion autonome (spec 002)

    private val autoReconnect = container.autoReconnect

    val autoReconnectStatus: StateFlow<AutoReconnectStatus> = autoReconnect.status

    val autoReconnectExperimental: Boolean = autoReconnect.experimental

    /** Réactivation du débogage sans fil en cours (FR-007). */
    val autoReconnectPreparing: StateFlow<Boolean> = autoReconnect.preparing

    /** Cause du dernier échec de reconnexion autonome (FR-009). */
    val autoReconnectIssue: StateFlow<ReconnectIssue?> = autoReconnect.lastIssue

    /** Choix « autorisations sans expiration » (FR-021). */
    val expiryStatus: StateFlow<ExpiryChoiceStatus> = autoReconnect.expiryStatus

    /** Délai d'expiration des autorisations, expliqué dans la fenêtre d'activation (FR-002). */
    val authorizationLifetime: StateFlow<AuthorizationLifetime> = autoReconnect.lifetime

    private val _autoReconnectBusy = MutableStateFlow(false)
    val autoReconnectBusy: StateFlow<Boolean> = _autoReconnectBusy.asStateFlow()

    private val _autoReconnectProposal = MutableStateFlow(false)

    /** Proposition après un « Passer en sans fil » réussi (FR-003). Rien n'est persisté. */
    val autoReconnectProposal: StateFlow<Boolean> = _autoReconnectProposal.asStateFlow()

    /**
     * « Activer » après confirmation, ou « Réactiver » (FR-016 : la confirmation d'origine vaut).
     * [neverExpire] : case « ne jamais faire expirer » cochée dans la fenêtre (FR-021).
     */
    fun enableAutoReconnect(neverExpire: Boolean = false) = runAutoReconnect {
        val (result, expiry) = autoReconnect.enable(neverExpire)
        when (result) {
            EnableResult.ENABLED -> showMessage(R.string.auto_reconnect_enabled)
            EnableResult.UNAVAILABLE -> showMessage(R.string.auto_reconnect_unavailable)
            EnableResult.NOT_CONNECTED -> showMessage(R.string.auto_reconnect_not_connected)
        }
        expiry?.let { showExpiryMessage(on = true, it) }
    }

    /** « Désactiver » (FR-013) : sans confirmation, puisque cela réduit les droits. */
    fun disableAutoReconnect() = runAutoReconnect {
        when (autoReconnect.disable()) {
            DisableResult.REVOKED -> showMessage(R.string.auto_reconnect_disabled)
            DisableResult.REVOKE_PENDING -> showMessage(R.string.auto_reconnect_disabled_pending)
            DisableResult.KEPT_EXTERNAL_GRANT -> showMessage(R.string.auto_reconnect_disabled_kept)
        }
    }

    /** Choix « ne jamais faire expirer » depuis la carte : activer (confirmé) ou rétablir le délai. */
    fun setNeverExpire(on: Boolean) = runAutoReconnect {
        showExpiryMessage(on, autoReconnect.setNeverExpire(on))
    }

    private fun showExpiryMessage(on: Boolean, result: ExpiryChangeResult) {
        when (result) {
            ExpiryChangeResult.APPLIED -> showMessage(if (on) R.string.never_expire_applied else R.string.never_expire_restored)
            ExpiryChangeResult.RESTORE_PENDING -> showMessage(R.string.never_expire_pending)
            ExpiryChangeResult.FAILED -> showMessage(R.string.never_expire_failed)
            ExpiryChangeResult.NOT_CONNECTED -> showMessage(R.string.auto_reconnect_not_connected)
            // Le casque n'expire déjà pas les autorisations : le choix disparaît de l'écran.
            ExpiryChangeResult.NOT_APPLICABLE -> Unit
        }
    }

    /** « Se reconnecter » (FR-005). */
    fun reconnectNow() {
        viewModelScope.launch { autoReconnect.reconnectNow() }
    }

    fun dismissAutoReconnectProposal() {
        _autoReconnectProposal.value = false
    }

    private fun runAutoReconnect(block: suspend () -> Unit) {
        if (_autoReconnectBusy.value) return
        _autoReconnectBusy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _autoReconnectBusy.value = false
            }
        }
    }

    // --- Jeux et profils (US2)

    fun refreshGames() {
        viewModelScope.launch {
            _loadingGames.value = true
            val games = container.games.loadGames()
            _games.value = games
            _loadingGames.value = false
            // Un profil ouvert sur un jeu désinstallé entre-temps n'a plus d'objet.
            val installed = games.mapTo(HashSet()) { it.packageName }
            _backStack.update { stack -> stack.filter { it !is Screen.Profile || it.packageName in installed } }
        }
    }

    fun saveProfile(packageName: String, profile: GameProfile) {
        viewModelScope.launch {
            if (persist(packageName, profile)) showMessage(R.string.profile_saved)
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun deleteProfile(packageName: String) {
        viewModelScope.launch {
            try {
                container.profileStore.delete(packageName)
                showMessage(R.string.profile_deleted)
            } catch (e: IOException) {
                Log.w(TAG, "Suppression du profil impossible : ${e.message}")
                showMessage(R.string.profile_delete_failed)
            }
        }
    }

    /** FR-019 : enregistrer, puis arrêter le jeu, appliquer les 7 propriétés et lancer. */
    fun applyAndLaunch(game: InstalledGame, profile: GameProfile) = launchGame(game, profile, persistFirst = true)

    /**
     * « Lancer » depuis la liste (US3) : profil enregistré, ou tous les réglages par défaut s'il
     * n'y en a pas. Même chemin qu'« Appliquer et lancer », sans réécrire le profil.
     */
    fun quickLaunch(game: InstalledGame) =
        launchGame(game, profiles.value[game.packageName] ?: GameProfile(), persistFirst = false)

    private fun launchGame(game: InstalledGame, profile: GameProfile, persistFirst: Boolean) {
        if (_tuningPackage.value != null) return
        _tuningPackage.value = game.packageName
        viewModelScope.launch {
            try {
                if (persistFirst && !persist(game.packageName, profile)) return@launch
                val result = container.tuner.applyAndLaunch(game.packageName, game.launchActivity, profile)
                onTuneResult(game, result)
            } finally {
                _tuningPackage.value = null
                refreshDiagnostic()
            }
        }
    }

    // --- Réinitialisation (US4)

    private val _resetting = MutableStateFlow(false)
    val resetting: StateFlow<Boolean> = _resetting.asStateFlow()

    /** « Tout réinitialiser » (FR-023) : confirme le résultat, ou nomme les réglages en échec. */
    fun resetAll() {
        if (_resetting.value) return
        _resetting.value = true
        viewModelScope.launch {
            try {
                val result = container.tuner.resetAll()
                when {
                    result == null -> showMessage(R.string.tune_not_connected)
                    result.failed.isEmpty() -> showMessage(R.string.reset_success)
                    else -> showMessage(
                        R.string.reset_partial,
                        result.failed.map { text(it.labelRes()) }.distinct().joinToString(),
                    )
                }
            } finally {
                _resetting.value = false
                refreshDiagnostic()
            }
        }
    }

    // --- Diagnostic (US5)

    fun refreshDiagnostic() {
        if (connectionState.value !is ConnectionState.Connected) return
        viewModelScope.launch { _diagnostic.value = container.tuner.readDiagnostic() }
    }

    private suspend fun persist(packageName: String, profile: GameProfile): Boolean = try {
        container.profileStore.save(packageName, profile)
        true
    } catch (e: IOException) {
        Log.w(TAG, "Enregistrement du profil impossible : ${e.message}")
        showMessage(R.string.profile_save_failed)
        false
    }

    private fun onTuneResult(game: InstalledGame, result: TuneResult) {
        when (result) {
            TuneResult.Success -> showMessage(R.string.tune_success)
            TuneResult.NotConnected -> showMessage(R.string.tune_not_connected)
            is TuneResult.InvalidProfile -> showMessage(
                R.string.tune_invalid_profile,
                result.violations.map { text(it.property.labelRes()) }.distinct().joinToString(),
            )
            is TuneResult.StepFailed -> {
                Log.w(TAG, "Étape en échec : ${result.step} (${result.output.trim().take(200)})")
                when (val step = result.step) {
                    TuneStep.ForceStop -> showMessage(R.string.tune_force_stop_failed)
                    is TuneStep.SetProperty -> showMessage(R.string.tune_step_failed, text(step.property.labelRes()))
                    TuneStep.Launch -> if (container.games.isInstalled(game.packageName)) {
                        showMessage(R.string.tune_launch_failed)
                    } else {
                        // Jeu désinstallé entre l'affichage de la liste et le lancement.
                        showMessage(R.string.tune_game_not_found)
                        refreshGames()
                    }
                }
            }
        }
    }

    private fun text(@StringRes res: Int): String = getApplication<Application>().getString(res)

    private companion object {
        const val TAG = "OqtTuner"
    }
}

/** Nom du réglage affiché à l'utilisateur ; largeur et hauteur forment ensemble la résolution. */
@StringRes
fun QuestProperty.labelRes(): Int = when (this) {
    QuestProperty.REFRESH_RATE -> R.string.setting_refresh_rate
    QuestProperty.TEXTURE_WIDTH, QuestProperty.TEXTURE_HEIGHT -> R.string.setting_resolution
    QuestProperty.CPU_LEVEL -> R.string.setting_cpu
    QuestProperty.GPU_LEVEL -> R.string.setting_gpu
    QuestProperty.FOVEATION_LEVEL -> R.string.setting_foveation
    QuestProperty.FOVEATION_DYNAMIC -> R.string.setting_dynamic_foveation
}
