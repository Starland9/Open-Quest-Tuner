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
import io.github.openquesttuner.core.GameProfileLibrary
import io.github.openquesttuner.core.GameFilter
import io.github.openquesttuner.core.GameSort
import io.github.openquesttuner.core.ProfileBackup
import io.github.openquesttuner.core.ProfileBackupCodec
import io.github.openquesttuner.core.AutoTuneEngine
import io.github.openquesttuner.core.AutoTuneRecommendation
import io.github.openquesttuner.core.TuneGoal
import io.github.openquesttuner.core.RuntimeTelemetry
import io.github.openquesttuner.core.TuneTrialResult
import io.github.openquesttuner.core.ClosedLoopTuningEngine
import io.github.openquesttuner.core.PerformanceSample
import io.github.openquesttuner.core.PerformanceIntelligence
import io.github.openquesttuner.core.PerformanceAssessment
import io.github.openquesttuner.core.GameOptimizationHistory
import io.github.openquesttuner.core.AdaptiveSuggestion
import io.github.openquesttuner.core.AdaptiveTuneEngine
import io.github.openquesttuner.core.CommunityCatalog
import io.github.openquesttuner.core.CommunityProfile
import io.github.openquesttuner.core.CatalogUpdateState
import io.github.openquesttuner.core.OptimizationScore
import io.github.openquesttuner.core.OptimizationScoreEngine
import io.github.openquesttuner.core.GameSession
import io.github.openquesttuner.core.OptimizationAdvice
import io.github.openquesttuner.core.OptimizationAssistant
import io.github.openquesttuner.core.AssistantAction
import io.github.openquesttuner.core.QuestModel
import io.github.openquesttuner.core.QuestProperty
import io.github.openquesttuner.core.ReconnectIssue
import io.github.openquesttuner.core.RefreshRatePolicy
import io.github.openquesttuner.core.ThermalLevel
import io.github.openquesttuner.core.TuneResult
import io.github.openquesttuner.core.TuneStep
import io.github.openquesttuner.core.ViolationReason
import io.github.openquesttuner.core.WirelessSwitchResult
import io.github.openquesttuner.core.matchesQuery
import io.github.openquesttuner.core.primary
import io.github.openquesttuner.games.InstalledGame
import io.github.openquesttuner.adb.PairingService
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
import kotlinx.coroutines.delay
import java.io.IOException

/** Écrans de l'appli ; la navigation est une simple pile gardée dans le ViewModel. */
sealed interface Screen {
    data object Dashboard : Screen
    data object Games : Screen
    data object Connection : Screen
    data object GlobalProfile : Screen
    data object PerformanceMonitor : Screen
    data object CatalogUpdates : Screen
    data object Onboarding : Screen
    data object DeviceTools : Screen
    data object Settings : Screen
    data class Profile(val packageName: String, val section: Int = 2) : Screen
}

/** Message éphémère (snackbar), résolu en texte localisé au moment de l'affichage. */
data class UiMessage(@param:StringRes val res: Int, val args: List<Any> = emptyList())

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OqtApplication).container

    private val _backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Dashboard))
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val connectionState: StateFlow<ConnectionState> = container.adb.state
    val pairingServices: StateFlow<List<PairingService>> = container.pairingDiscovery.services
    val pairingScanning: StateFlow<Boolean> = container.pairingDiscovery.scanning

    val questModel: QuestModel = container.questModel
    val systemResources = container.systemResources.state

    val profiles: StateFlow<Map<String, GameProfile>> = container.profileStore.profiles
    val profileLibraries: StateFlow<Map<String, GameProfileLibrary>> = container.profileLibraryStore.libraries
    val favorites: StateFlow<Set<String>> = container.profileLibraryStore.favorites
    val globalProfile: StateFlow<GameProfile> = container.globalProfileStore.profile
    val globalProfileEnabled: StateFlow<Boolean> = container.globalProfileStore.enabled
    val autoApply: StateFlow<Boolean> = container.proSettings.autoApply
    val gameFilter: StateFlow<GameFilter> = container.proSettings.filter
    val gameSort: StateFlow<GameSort> = container.proSettings.sort
    private val _activeGamePackage = MutableStateFlow<String?>(null)
    val activeGamePackage: StateFlow<String?> = _activeGamePackage.asStateFlow()
    private val _lastAutoAppliedPackage = MutableStateFlow<String?>(null)
    val lastAutoAppliedPackage: StateFlow<String?> = _lastAutoAppliedPackage.asStateFlow()
    private val _performanceSamples = container.performanceLog.samples
    val performanceSamples: StateFlow<List<PerformanceSample>> = _performanceSamples
    val optimizationHistory: StateFlow<Map<String, GameOptimizationHistory>> = container.optimizationHistory.history
    val communityCatalog: StateFlow<CommunityCatalog> = container.communityCatalog.catalog
    val catalogUpdateState: StateFlow<CatalogUpdateState> = container.catalogUpdater.state
    val sessions: StateFlow<Map<String, List<GameSession>>> = container.sessionHistory.sessions
    val onboardingComplete: StateFlow<Boolean> = container.productExperience.onboardingComplete
    val safeAutoRecommendations: StateFlow<Boolean> = container.productExperience.safeAutoRecommendations
    val assistantNotifications: StateFlow<Boolean> = container.productExperience.assistantNotifications


    private val _refreshRates = MutableStateFlow(availableRefreshRates())

    /**
     * Fréquences proposées dans les profils : celles du modèle, puis les fréquences élevées que
     * l'écran déclare (spec 003, FR-001). Relues à l'ouverture de chaque profil.
     */
    val refreshRates: StateFlow<List<Int>> = _refreshRates.asStateFlow()

    private val _games = MutableStateFlow<List<InstalledGame>>(emptyList())
    val games: StateFlow<List<InstalledGame>> = _games.asStateFlow()

    private val _loadingGames = MutableStateFlow(true)
    val loadingGames: StateFlow<Boolean> = _loadingGames.asStateFlow()

    private val _query = MutableStateFlow("")
    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()
    val query: StateFlow<String> = _query.asStateFlow()

    /** Liste filtrée au fil de la saisie, sans casse ni accents (FR-009). */
    val filteredGames: StateFlow<List<InstalledGame>> = combine(combine(_games, profiles, profileLibraries) { games, saved, libraries -> Triple(games, saved, libraries) }, _query, favorites, gameFilter, gameSort) { data, query, favorites, filter, sort ->
        val (games, saved, libraries) = data
        games.filter { game ->
            matchesQuery(game.label, query) && when (filter) {
                GameFilter.ALL -> true
                GameFilter.FAVOURITES -> game.packageName in favorites
                GameFilter.OPTIMISED -> game.packageName in saved
                GameFilter.CUSTOM -> (libraries[game.packageName]?.profiles?.size ?: 0) > 1
                GameFilter.NO_PROFILE -> game.packageName !in saved
            }
        }.sortedWith(when (sort) {
            GameSort.NAME -> compareByDescending<InstalledGame> { it.packageName in favorites }.thenBy { it.label.lowercase() }
            GameSort.PROFILE -> compareByDescending<InstalledGame> { it.packageName in saved }.thenBy { it.label.lowercase() }
        })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _tuningPackage = MutableStateFlow<String?>(null)

    /**
     * Jeu en cours de lancement, ou `null`. Tous les boutons de lancement sont désactivés pendant
     * l'opération, pour éviter les doubles envois.
     */
    val tuningPackage: StateFlow<String?> = _tuningPackage.asStateFlow()

    val thermalLevel: StateFlow<ThermalLevel> = container.thermal.level

    /** Fréquence réelle de l'écran, lisible sans connexion (spec 003, FR-010). */
    val displayRefreshRate: StateFlow<Int?> = container.display.currentRefreshRate

    private val _diagnostic = MutableStateFlow<Diagnostic?>(null)

    /** Dernière lecture des propriétés actives ; `null` hors connexion ou si la lecture a échoué. */
    val diagnostic: StateFlow<Diagnostic?> = _diagnostic.asStateFlow()

    val tools = DeviceToolsModel(application, container, viewModelScope)
    val recording = container.recording.asStateFlow()
    val learningState = container.learning.trial.state
    val renderVerificationStatus = container.learning.renderStatus
    fun cleanupOvrCapture() { viewModelScope.launch { container.learning.cleanupCapture() } }
    val learningOverlayEnabled = container.learningStore.overlayEnabled
    val learningOverlayStatus = container.learningOverlayStatus.asStateFlow()
    val learningEvents = container.learningStore.events
    private val _learningStarting=MutableStateFlow(false)
    val learningStarting=_learningStarting.asStateFlow()
    fun learningSetup(pkg: String)=container.learningStore.setup(pkg)
    fun setLearningOverlay(enabled: Boolean) {
        if(!container.learningStore.setOverlayEnabled(enabled)) { tools.report("Could not save the overlay option.");return }
        if(!enabled)container.immersiveHud.close() else container.immersiveHud.retryOnNextUpdate()
        container.learningOverlayStatus.value=if(enabled) "Native HUD will be checked during Auto Tune" else "Off"
    }
    fun allowLearningOverlay() { tools.report("Native HUD needs no Android overlay-window permission.") }
    val completedLearning=container.learningStore.completed
    fun hasLearned(pkg: String)=container.learningStore.isCompleted(pkg) && profiles.value.containsKey(pkg)
    fun foregroundDiagnostics()=container.tuner.foregroundEvidence
    fun nativeHudDiagnostics()=container.immersiveHud.diagnostics()
    fun resetLearningProfile(game: InstalledGame) { viewModelScope.launch {
        if(learningState.value.active || learningState.value.recoveryRequired)return@launch
        try {
            container.profileStore.save(game.packageName,GameProfile())
            if(!container.learningStore.resetCompletion(game.packageName))tools.report("Default profile saved, but Auto Tune reset could not be recorded.")
        }catch(_: Exception) {tools.report("Profile reset failed; saved completion retained.")}
    } }
    fun autoLearn(game: InstalledGame) {
        if(hasLearned(game.packageName)) { launchSaved(game);return }
        startLearning(game,io.github.openquesttuner.core.AutomaticTuningPolicy.setup(),openGame=true)
    }
    private fun launchSaved(game: InstalledGame) { quickLaunch(game) }
    val learningWindows = container.learningStore.observed
    fun learningContext(pkg: String) = container.learningStore.contextFor(pkg)
    fun learningHalfRate(pkg: String)=container.learningStore.halfRate(pkg)
    fun measuredRecommendations(pkg: String, halfRate: Boolean = container.learningStore.halfRate(pkg)): List<io.github.openquesttuner.core.LearnedRecommendation> =
        learningContext(pkg)?.let { io.github.openquesttuner.core.GameLearning.recommendations(it,learningWindows.value,questModel,halfRate) }.orEmpty()
    fun startLearning(game: InstalledGame, config: io.github.openquesttuner.core.LearningConfig, openGame: Boolean = false) {
        if (_learningStarting.value || learningState.value.active || learningState.value.recoveryRequired || hasLearned(game.packageName)) return
        _learningStarting.value=true
        viewModelScope.launch {
            try {
                if(connectionState.value !is ConnectionState.Connected) { tools.report("Connect your headset before learning.");return@launch }
                if (!container.learningStore.setHalfRate(game.packageName,config.halfRate) || !container.learningStore.saveSetup(game.packageName,config)) { tools.report("Cannot save learning setup; test not started."); return@launch }
                container.learning.start(game,config)
                if (learningState.value.active) {
                    startLogging(true,learningOnly=true)
                    if(openGame && !container.learning.openSelectedGame()) {
                        container.learning.trial.stop();tools.report("Could not open the game. Learning stopped and previous settings restored.")
                    }
                }
            } catch (_: Exception) {
                container.learning.trial.stop(); tools.report("Background learning could not start. Check the connection and retry.")
            } finally { _learningStarting.value=false }
        }
    }
    fun stopLearning() { viewModelScope.launch { container.learning.trial.stop() } }
    fun resumeLearningGame() { viewModelScope.launch { if(!container.learning.openSelectedGame()) tools.report("Could not resume the learning game.") } }
    fun launchLearningPhase() { viewModelScope.launch { container.learning.trial.launchPhase() } }
    fun restoreLearning() { viewModelScope.launch { container.learning.trial.recover() } }
    fun discardLearningRecovery() { viewModelScope.launch { container.learning.trial.discardRecovery() } }
    fun clearMeasuredLearning(pkg: String) { if (!learningState.value.active) container.learningStore.clear(pkg) }
    fun applyMeasured(game: InstalledGame, recommendation: io.github.openquesttuner.core.LearnedRecommendation) {
        if (learningContext(game.packageName) != recommendation.context || learningState.value.active || learningState.value.recoveryRequired) return
        container.profileLibraryStore.saveNamed(game.packageName,recommendation.goal.label,recommendation.profile)
        applyAndLaunch(game,recommendation.profile)
    }

    init {
        viewModelScope.launch {
            learningState.collect { state ->
                // A just-finished test owns the current game's restored/live result.
                // Do not let the normal switcher apply a deferred full-profile winner mid-game.
                if (!state.active && !state.recoveryRequired && state.packageName != null)
                    _lastAutoAppliedPackage.value = state.packageName
            }
        }
        refreshGames()
        if (!onboardingComplete.value) _backStack.value = listOf(Screen.Onboarding)
        // Relecture à chaque connexion ; hors connexion, l'état du casque est inconnu.
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is ConnectionState.Connected) refreshDiagnostic() else { container.sessionHistory.finish(); _diagnostic.value = null; _activeGamePackage.value = null }
            }
        }
        viewModelScope.launch {
            while (true) {
                if (connectionState.value is ConnectionState.Connected) {
                    autoSwitchTick()
                    capturePerformanceSample()
                }
                delay(2000)
            }
        }
    }

    fun navigate(screen: Screen) {
        // L'indicateur « actif sur le casque » et la carte Diagnostic partent d'une lecture fraîche.
        if (screen is Screen.Profile || screen == Screen.GlobalProfile || screen == Screen.Connection) refreshDiagnostic()
        // Une mise à jour d'Horizon OS qui retire une fréquence se voit à l'ouverture suivante.
        if (screen is Screen.Profile || screen == Screen.GlobalProfile) _refreshRates.value = availableRefreshRates()
        _backStack.update { stack -> if (stack.last() == screen) stack else stack + screen }
    }

    fun navigateTopLevel(screen: Screen) {
        navigate(screen)
        _backStack.value = if (screen == Screen.Dashboard) listOf(Screen.Dashboard) else listOf(Screen.Dashboard, screen)
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

    fun startPairingDiscovery() = container.pairingDiscovery.start()
    fun stopPairingDiscovery() = container.pairingDiscovery.stop()

    fun pair(service: PairingService, code: String) {
        if (!ConnectionInput.isValidPairingCode(code)) return
        viewModelScope.launch {
            container.pairingDiscovery.stop()
            container.adb.pair(service.host, service.port, code)
        }
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
        if (learningState.value.active || learningState.value.recoveryRequired) { tools.report("Stop or restore the learning test before changing profiles."); return }
        viewModelScope.launch {
            if (persist(packageName, profile)) showMessage(R.string.profile_saved)
        }
    }

    fun saveGlobalProfile(profile: GameProfile) {
        runCatching { container.globalProfileStore.save(profile) }
            .onSuccess { showMessage(R.string.global_profile_saved) }
            .onFailure { showMessage(R.string.profile_save_failed) }
    }

    fun setGlobalProfileEnabled(enabled: Boolean) {
        runCatching { container.globalProfileStore.setEnabled(enabled) }
            .onFailure { showMessage(R.string.profile_save_failed) }
    }

    fun setQuery(query: String) { _query.value = query }

    fun toggleFavoritesOnly() { container.proSettings.setFilter(if (gameFilter.value == GameFilter.FAVOURITES) GameFilter.ALL else GameFilter.FAVOURITES) }
    fun setGameFilter(filter: GameFilter) = container.proSettings.setFilter(filter)
    fun setGameSort(sort: GameSort) = container.proSettings.setSort(sort)
    fun setAutoApply(enabled: Boolean) = container.proSettings.setAutoApply(enabled)
    fun toggleFavorite(packageName: String) = container.profileLibraryStore.toggleFavorite(packageName)

    fun saveNamedProfile(packageName: String, name: String, profile: GameProfile) {
        container.profileLibraryStore.saveNamed(packageName, name, profile)
        saveProfile(packageName, profile)
    }

    fun selectNamedProfile(packageName: String, name: String) {
        container.profileLibraryStore.select(packageName, name)?.let { saveProfile(packageName, it) }
    }

    fun deleteNamedProfile(packageName: String, name: String) = container.profileLibraryStore.deleteNamed(packageName, name)

    fun cloneNamedProfile(packageName: String, sourceName: String, newName: String) =
        container.profileLibraryStore.clone(packageName, sourceName, newName)

    fun deleteProfile(packageName: String) {
        viewModelScope.launch {
            try {
                if(!container.learningStore.resetCompletion(packageName)) { tools.report("Could not reset Auto Tune completion.");return@launch }
                container.profileStore.delete(packageName)
                showMessage(R.string.profile_deleted)
            } catch (e: IOException) {
                Log.w(TAG, "Suppression du profil impossible : ${e.message}")
                showMessage(R.string.profile_delete_failed)
            }
        }
    }

    /** FR-019 : enregistrer, puis arrêter le jeu, appliquer les 7 propriétés et lancer. */
    fun applyAndLaunch(game: InstalledGame, profile: GameProfile) {
        if (learningState.value.active || learningState.value.recoveryRequired) { tools.report("Stop or restore the learning test before applying profiles."); return }
        launchGame(game, profile, persistFirst = true)
    }

    /**
     * « Lancer » depuis la liste (US3) : profil enregistré, ou tous les réglages par défaut s'il
     * n'y en a pas. Même chemin qu'« Appliquer et lancer », sans réécrire le profil.
     */
    fun quickLaunch(game: InstalledGame) =
        launchGame(game, profiles.value[game.packageName] ?: globalProfile.value.takeIf { globalProfileEnabled.value } ?: GameProfile(), persistFirst = false)

    private fun launchGame(game: InstalledGame, profile: GameProfile, persistFirst: Boolean) {
        if (_tuningPackage.value != null) return
        _tuningPackage.value = game.packageName
        viewModelScope.launch {
            try {
                if (persistFirst && !persist(game.packageName, profile)) return@launch
                val result = container.tuner.applyAndLaunch(game.packageName, game.launchActivity, profile)
                onTuneResult(game, profile, result)
            } finally {
                _tuningPackage.value = null
                refreshDiagnostic()
            }
        }
    }


    private suspend fun autoSwitchTick() {
        if (learningState.value.active || learningState.value.recoveryRequired) return
        if (_tuningPackage.value != null || tools.busy.value) return
        val foreground = container.tuner.readForegroundPackage(games.value.map { it.packageName }.toSet())
        if (_tuningPackage.value != null || tools.busy.value) return
        _activeGamePackage.value = foreground
        if (!autoApply.value) return
        val game = _games.value.firstOrNull { it.packageName == foreground }
        val targetKey = game?.packageName ?: "__default__"
        if (_lastAutoAppliedPackage.value == targetKey) return
        val profile = if (game != null) profiles.value[game.packageName]
            ?: globalProfile.value.takeIf { globalProfileEnabled.value }
            ?: GameProfile()
        else globalProfile.value.takeIf { globalProfileEnabled.value } ?: GameProfile()
        if (container.tuner.applyProfile(profile) == TuneResult.Success) {
            _lastAutoAppliedPackage.value = targetKey
            refreshDiagnostic()
        }
    }


    fun autoTuneRecommendations(packageName: String): List<AutoTuneRecommendation> =
        AutoTuneEngine.all(questModel, container.display.declaredRefreshRates()).map { starter ->
            val measured = measuredRecommendations(packageName).firstOrNull { it.goal == starter.goal }
            if (measured == null) starter.copy(confidence=0, source="Untested starter preset")
            else starter.copy(profile=measured.profile,confidence=0,source="Measured per-game recommendation",rationale=listOf(measured.reason))
        }

    fun applyAutoTune(packageName: String, goal: TuneGoal, saveAsNamed: Boolean = true) {
        if (learningState.value.active || learningState.value.recoveryRequired) { tools.report("Stop or restore the learning test first."); return }
        val recommendation = AutoTuneEngine.recommend(questModel, goal, container.display.declaredRefreshRates()).let { starter ->
            measuredRecommendations(packageName).firstOrNull { it.goal == goal }?.let { starter.copy(profile=it.profile) } ?: starter
        }
        if (saveAsNamed) container.profileLibraryStore.saveNamed(packageName, goal.label, recommendation.profile)
        saveProfile(packageName, recommendation.profile)
    }

    fun performanceAssessment(packageName: String?): PerformanceAssessment {
        val expected = packageName?.let { profiles.value[it] }
        return PerformanceIntelligence.assess(_performanceSamples.value.filter { packageName == null || it.packageName == packageName }, expected)
    }

    fun clearPerformanceHistory() { container.performanceLog.clear() }

    fun clearOptimizationHistory(packageName: String? = null) = container.optimizationHistory.clear(packageName)

    fun adaptiveSuggestion(packageName: String): AdaptiveSuggestion? {
        val rec=measuredRecommendations(packageName).firstOrNull { it.goal == (selectedGoal(packageName) ?: TuneGoal.BALANCED) }
        return AdaptiveSuggestion(packageName,if(rec==null) "Measured learning needed" else "Measured profile available",
            rec?.reason ?: "Record game metrics, then run a controlled A/B/A/B/A test. Settings matching alone does not prove frame-rate stability.",rec?.goal,0)
    }

    fun applyAdaptiveSuggestion(packageName: String) {
        val goal = adaptiveSuggestion(packageName)?.recommendedGoal ?: return
        applyAutoTune(packageName, goal)
    }


    fun optimizationScore(packageName: String): OptimizationScore = OptimizationScoreEngine.calculate(
        optimizationHistory.value[packageName], adaptiveSuggestion(packageName), profiles.value.containsKey(packageName)
    )

    fun optimizationAdvice(packageName: String): OptimizationAdvice {
        val rec=measuredRecommendations(packageName).firstOrNull { it.goal == (selectedGoal(packageName) ?: TuneGoal.BALANCED) }
        return if(rec==null) OptimizationAdvice("Measure before changing", "Use measured learning to test this game. No verified recommendation yet.")
        else OptimizationAdvice("Measured profile available", rec.reason, AssistantAction.APPLY_GOAL, rec.goal,0,false)
    }

    fun applyOptimizationAdvice(packageName: String) {
        val advice = optimizationAdvice(packageName)
        when (advice.action) {
            AssistantAction.APPLY_GOAL -> advice.goal?.let { applyAutoTune(packageName, it) }
            AssistantAction.REAPPLY_CURRENT -> profiles.value[packageName]?.let { profile -> viewModelScope.launch { container.tuner.applyProfile(profile); refreshDiagnostic() } }
            AssistantAction.NONE -> Unit
        }
    }

    private var lastAutomaticAdvice: Pair<String, TuneGoal>? = null
    private fun maybeApplySafeRecommendation(packageName: String) {
        if (!safeAutoRecommendations.value) return
        val advice = optimizationAdvice(packageName)
        val goal = advice.goal ?: return
        if (!advice.safeForAutomaticApply || advice.action != AssistantAction.APPLY_GOAL) return
        if (lastAutomaticAdvice == packageName to goal) return
        // Conservative automation only steps down for thermal protection; quality increases remain manual.
        if (goal != TuneGoal.BALANCED) return
        lastAutomaticAdvice = packageName to goal
        applyAutoTune(packageName, goal)
    }

    fun setSafeAutoRecommendations(enabled: Boolean) = container.productExperience.setSafeAutoRecommendations(enabled)
    fun setAssistantNotifications(enabled: Boolean) = container.productExperience.setNotifications(enabled)
    fun completeOnboarding() { container.productExperience.setOnboardingComplete(true); _backStack.value = listOf(Screen.Dashboard) }
    fun restartOnboarding() { container.productExperience.setOnboardingComplete(false); _backStack.value = listOf(Screen.Onboarding) }
    fun clearSessionHistory(packageName: String? = null) = container.sessionHistory.clear(packageName)

    fun catalogSourceUrl(): String = container.catalogUpdater.sourceUrl
    fun setCatalogSourceUrl(url: String) { container.catalogUpdater.sourceUrl = url }
    fun checkCatalogUpdates(install: Boolean = false) {
        viewModelScope.launch { container.catalogUpdater.checkAndUpdate(install = install) }
    }
    fun communityProfiles(packageName: String): List<CommunityProfile> =
        container.communityCatalog.compatible(packageName, questModel)

    fun importCommunityCatalog(text: String): Result<CommunityCatalog> = container.communityCatalog.import(text)
    fun exportCommunityCatalog(): String = container.communityCatalog.export()

    fun installCommunityProfile(entry: CommunityProfile) {
        container.profileLibraryStore.saveNamed(entry.packageName, entry.name, entry.profile, select = true)
        saveProfile(entry.packageName, entry.profile)
    }

    private fun selectedGoal(packageName: String): TuneGoal? {
        val name = profileLibraries.value[packageName]?.selected ?: return null
        return TuneGoal.entries.firstOrNull { it.label.equals(name, ignoreCase = true) }
    }

    private suspend fun capturePerformanceSample() {
        if (recording.value || tools.busy.value || learningState.value.active || learningState.value.recoveryRequired) return
        val generation = container.tuner.generation
        val diagnostic = container.tuner.readDiagnostic()
        if (diagnostic != null) _diagnostic.value = diagnostic
        val pkgForTelemetry = _activeGamePackage.value
        val telemetry = pkgForTelemetry?.let { container.tuner.readRuntimeTelemetry(it, displayRefreshRate.value) } ?: RuntimeTelemetry()
        val sample = PerformanceSample(
            timestampMs = System.currentTimeMillis(),
            packageName = _activeGamePackage.value,
            refreshRate = telemetry.reportedRefreshRate ?: displayRefreshRate.value,
            thermal = thermalLevel.value,
            activeProperties = diagnostic?.active.orEmpty(),
            telemetry = telemetry,
            readbackVerified = diagnostic != null,
        )
        if (generation != container.tuner.generation) return
        container.performanceLog.record(sample)
        container.learning.observe(sample)
        val pkg = sample.packageName
        if (pkg != null) {
            val expected = profiles.value[pkg]
            val selectedName = profileLibraries.value[pkg]?.selected
            container.optimizationHistory.record(sample, expected, selectedName)
            container.sessionHistory.record(sample, expected, selectedName)
            // Measured trial auto-keep is explicit per run; legacy thermal heuristics never auto-apply.
        }
    }


    fun closedLoopTrial(packageName: String): TuneTrialResult? {
        val goal = selectedGoal(packageName) ?: return null
        return ClosedLoopTuningEngine.evaluate(packageName, goal, _performanceSamples.value.takeLast(60))
    }

    fun applyClosedLoopNextStep(packageName: String) {
        val trial = closedLoopTrial(packageName) ?: return
        ClosedLoopTuningEngine.nextGoal(trial.goal, trial.verdict)?.let { applyAutoTune(packageName, it) }
    }


    fun exportProfiles(): String = ProfileBackupCodec.encode(ProfileBackup(
        profiles = profiles.value,
        libraries = profileLibraries.value,
        favorites = favorites.value,
        globalEnabled = globalProfileEnabled.value,
        globalProfile = globalProfile.value,
    ))

    fun importProfiles(text: String) {
        viewModelScope.launch {
            runCatching {
                val backup = ProfileBackupCodec.decode(text)
                backup.profiles.forEach { (pkg, profile) -> if (io.github.openquesttuner.core.ShellCommand.isValidPackageName(pkg)) container.profileStore.save(pkg, profile) }
                container.profileLibraryStore.replaceAll(backup.libraries.filterKeys(io.github.openquesttuner.core.ShellCommand::isValidPackageName), backup.favorites.filter(io.github.openquesttuner.core.ShellCommand::isValidPackageName).toSet())
                container.globalProfileStore.save(backup.globalProfile, backup.globalEnabled)
            }.onSuccess { showMessage(R.string.profile_saved) }.onFailure { showMessage(R.string.profile_save_failed) }
        }
    }

    // --- Réinitialisation (US4)

    private val _resetting = MutableStateFlow(false)
    val resetting: StateFlow<Boolean> = _resetting.asStateFlow()

    /** « Tout réinitialiser » (FR-023) : confirme le résultat, ou nomme les réglages en échec. */
    fun resetAll() {
        if (learningState.value.active || learningState.value.recoveryRequired) { tools.report("Stop or restore the learning test first."); return }
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

    private fun onTuneResult(game: InstalledGame, profile: GameProfile, result: TuneResult) {
        when (result) {
            TuneResult.Success -> showMessage(R.string.tune_success)
            TuneResult.NotConnected -> showMessage(R.string.tune_not_connected)
            is TuneResult.InvalidProfile -> {
                val primary = result.violations.primary()
                when (primary?.reason) {
                    ViolationReason.RATE_NOT_DECLARED -> showMessage(R.string.tune_rate_not_declared, primary.value)
                    ViolationReason.ABOVE_RATE_LIMIT -> {
                        val rate = requireNotNull(profile.refreshRate)
                        showMessage(
                            R.string.tune_resolution_above_limit,
                            rate,
                            requireNotNull(RefreshRatePolicy.maxResolutionStep(rate)) / 100.0,
                        )
                    }
                    else -> showMessage(
                        R.string.tune_invalid_profile,
                        result.violations.map { text(it.property.labelRes()) }.distinct().joinToString(),
                    )
                }
            }
            is TuneResult.StepFailed -> {
                Log.w(TAG, "Étape en échec : ${result.step} (${result.output.trim().take(200)})")
                when (val step = result.step) {
                    TuneStep.ForceStop -> showMessage(R.string.tune_force_stop_failed)
                    is TuneStep.SetProperty -> showMessage(R.string.tune_step_failed, text(step.property.labelRes()))
                    TuneStep.Verify -> tools.report("Profile readback failed.\n${result.output}")
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

    fun startLogging(enabled: Boolean, learningOnly: Boolean = false) {
        val context = getApplication<Application>()
        if (enabled && connectionState.value !is ConnectionState.Connected) { tools.report("Pair the headset before starting capture."); return }
        val intent = android.content.Intent(context, io.github.openquesttuner.monitor.PerformanceRecordingService::class.java)
        if(learningOnly) intent.action=io.github.openquesttuner.monitor.PerformanceRecordingService.LEARNING_ONLY
        if (enabled) context.startForegroundService(intent) else context.stopService(intent)
    }
    fun savePerformanceBaseline(pkg: String) { container.performanceLog.saveBaseline(pkg) }
    fun comparePerformance(pkg: String): String = io.github.openquesttuner.core.PerformanceComparison.describe(
        container.performanceLog.baseline(pkg), performanceSamples.value.filter { it.packageName == pkg })

    private fun availableRefreshRates(): List<Int> =
        RefreshRatePolicy.available(questModel, container.display.declaredRefreshRates())

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
