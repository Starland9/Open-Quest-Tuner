package io.github.openquesttuner.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.ui.components.ConnectionBadge

@Composable
fun OqtApp(vm: MainViewModel = viewModel()) {
    val completedLearning by vm.completedLearning.collectAsState()
    val backStack by vm.backStack.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showReconnectDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPower by remember { mutableStateOf<Boolean?>(null) }
    var showAccounts by remember { mutableStateOf(false) }
    var pendingAccount by remember { mutableStateOf<io.github.openquesttuner.core.HeadsetUser?>(null) }
    val output by vm.tools.output.collectAsState()
    val toolBusy by vm.tools.busy.collectAsState()
    val users by vm.tools.users.collectAsState()
    val deviceResult by vm.tools.result.collectAsState()
    val recording by vm.recording.collectAsState()
    val learningState by vm.learningState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(context.getString(message.res, *message.args.toTypedArray()))
        }
    }

    BackHandler(enabled = backStack.size > 1) { vm.back() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val connectionState by vm.connectionState.collectAsState()
            val pairingServices by vm.pairingServices.collectAsState()
            val pairingScanning by vm.pairingScanning.collectAsState()
            val communityCatalog by vm.communityCatalog.collectAsState()
            val catalogUpdateState by vm.catalogUpdateState.collectAsState()
            val switchingToWireless by vm.switchingToWireless.collectAsState()
            val games by vm.games.collectAsState()
            val loadingGames by vm.loadingGames.collectAsState()
            val profiles by vm.profiles.collectAsState()
            val profileLibraries by vm.profileLibraries.collectAsState()
            val favorites by vm.favorites.collectAsState()
            val favoritesOnly by vm.favoritesOnly.collectAsState()
            val gameFilter by vm.gameFilter.collectAsState()
            val gameSort by vm.gameSort.collectAsState()
            val autoApply by vm.autoApply.collectAsState()
            val activeGamePackage by vm.activeGamePackage.collectAsState()
            val globalProfile by vm.globalProfile.collectAsState()
            val globalProfileEnabled by vm.globalProfileEnabled.collectAsState()
            val filteredGames by vm.filteredGames.collectAsState()
            val query by vm.query.collectAsState()
            val tuningPackage by vm.tuningPackage.collectAsState()
            val thermalLevel by vm.thermalLevel.collectAsState()
            val diagnostic by vm.diagnostic.collectAsState()
            val resetting by vm.resetting.collectAsState()
            val refreshRates by vm.refreshRates.collectAsState()
            val displayRefreshRate by vm.displayRefreshRate.collectAsState()
            val performanceSamples by vm.performanceSamples.collectAsState()
            val systemResources by vm.systemResources.collectAsState()
            val optimizationHistory by vm.optimizationHistory.collectAsState()
            val sessions by vm.sessions.collectAsState()
            val safeAutoRecommendations by vm.safeAutoRecommendations.collectAsState()
            val assistantNotifications by vm.assistantNotifications.collectAsState()
            val autoReconnectStatus by vm.autoReconnectStatus.collectAsState()
            val autoReconnectBusy by vm.autoReconnectBusy.collectAsState()
            val preparing by vm.autoReconnectPreparing.collectAsState()
            val autoReconnectIssue by vm.autoReconnectIssue.collectAsState()
            val autoReconnectProposal by vm.autoReconnectProposal.collectAsState()
            val expiryStatus by vm.expiryStatus.collectAsState()
            val authorizationLifetime by vm.authorizationLifetime.collectAsState()
            AdaptiveAppShell(backStack.last(), vm::navigateTopLevel, vm.questModel, connectionState is ConnectionState.Connected) {
            when (val screen = backStack.last()) {
                Screen.Onboarding -> OnboardingScreen(
                    model = vm.questModel, connectionState = connectionState, gameCount = games.size,
                    onOpenConnection = { vm.navigate(Screen.Connection) }, onFinish = vm::completeOnboarding,
                )
                Screen.DeviceTools -> DeviceToolsScreen(vm.tools, connectionState is ConnectionState.Connected, vm.questModel,
                    onBack = { vm.back() }, onConnection = { vm.navigate(Screen.Connection) },
                    onPower = { pendingPower = it }, onAccounts = { showAccounts = true; vm.tools.loadUsers() })
                Screen.Settings -> ProSettingsScreen(
                    safeAuto = safeAutoRecommendations, notifications = assistantNotifications,
                    onSafeAuto = vm::setSafeAutoRecommendations, onNotifications = vm::setAssistantNotifications,
                    onRestartOnboarding = vm::restartOnboarding, onBack = { vm.back() },
                    onReset = vm::resetAll, resetting = resetting, connected = connectionState is ConnectionState.Connected,
                    advancedLearning = { LearningOverlaySettings(vm) },
                )
                Screen.Dashboard -> QuestDashboardScreen(
                    model = vm.questModel,
                    connectionState = connectionState,
                    games = games,
                    profiles = profiles,
                    activeGamePackage = activeGamePackage,
                    displayRefreshRate = displayRefreshRate,
                    thermalLevel = thermalLevel,
                    performanceSamples = performanceSamples,
                    resources = systemResources,
                    onGames = { vm.navigate(Screen.Games) },
                    onProfiles = { vm.navigate(Screen.GlobalProfile) },
                    onPerformance = { vm.navigate(Screen.PerformanceMonitor) },
                    onDevice = { vm.navigate(Screen.DeviceTools) },
                    onPower = { pendingPower = it }, onAccounts = { showAccounts = true; vm.tools.loadUsers() },
                    onCommunity = { vm.navigate(Screen.CatalogUpdates) },
                    onSettings = { vm.navigate(Screen.Settings) },
                    onOpenGame = { vm.navigate(Screen.Profile(it.packageName)) },
                    onAutoTune = { vm.navigate(Screen.Profile(it.packageName, 1)) },
                )
                Screen.Games -> GamesScreen(
                    autoTunedPackages=completedLearning.keys.filter { vm.hasLearned(it) }.toSet(),
                    games = filteredGames,
                    totalGames = games.size,
                    loading = loadingGames,
                    query = query,
                    onQueryChange = vm::setQuery,
                    profiles = profiles,
                    favorites = favorites,
                    favoritesOnly = favoritesOnly,
                    profileLibraries = profileLibraries,
                    gameFilter = gameFilter,
                    gameSort = gameSort,
                    onFilter = vm::setGameFilter,
                    onSort = vm::setGameSort,
                    autoApply = autoApply,
                    onAutoApply = vm::setAutoApply,
                    activeGamePackage = activeGamePackage,
                    questModel = vm.questModel,
                    displayRefreshRate = displayRefreshRate,
                    onToggleFavorite = vm::toggleFavorite,
                    onToggleFavoritesOnly = vm::toggleFavoritesOnly,
                    connected = connectionState is ConnectionState.Connected,
                    tuningPackage = tuningPackage,
                    onOpenGame = { vm.navigate(Screen.Profile(it.packageName)) },
                    onLaunch = vm::quickLaunch,
                    onRefresh = vm::refreshGames,
                    onOpenConnection = { vm.navigate(Screen.Connection) },
                    thermalLevel = thermalLevel,
                    globalProfileEnabled = globalProfileEnabled,
                    onOpenGlobalProfile = { vm.navigate(Screen.GlobalProfile) },
                    onExportProfiles = vm::exportProfiles,
                    onImportProfiles = vm::importProfiles,
                    onOpenPerformanceMonitor = { vm.navigate(Screen.PerformanceMonitor) },
                    onOpenCatalogUpdates = { vm.navigate(Screen.CatalogUpdates) },
                    onOpenSettings = { vm.navigate(Screen.Settings) },
                    actions = {
                        ConnectionBadge(
                            state = connectionState,
                            onClick = { vm.navigate(Screen.Connection) },
                            modifier = Modifier.padding(end = 8.dp),
                            preparing = preparing,
                        )
                    },
                )
                Screen.CatalogUpdates -> CatalogUpdatesScreen(
                    catalog = communityCatalog, state = catalogUpdateState, sourceUrl = vm.catalogSourceUrl(),
                    onSourceUrl = vm::setCatalogSourceUrl, onCheck = { vm.checkCatalogUpdates(false) }, onInstall = { vm.checkCatalogUpdates(true) }, onBack = { vm.back() },
                )
                Screen.PerformanceMonitor -> PerformanceMonitorScreen(
                    samples = performanceSamples,
                    activePackage = activeGamePackage,
                    games = games,
                    profiles = profiles, connected = connectionState is ConnectionState.Connected, recording = recording,
                    onRecord = vm::startLogging, onBaseline = vm::savePerformanceBaseline, comparison = vm::comparePerformance,
                    onTune = { vm.navigate(Screen.Profile(it.packageName,section=0)) }, onLearn = { vm.navigate(Screen.Profile(it.packageName,section=2)) }, onTools = { vm.navigate(Screen.DeviceTools) },
                    onBack = { vm.back() },
                    onClear = vm::clearPerformanceHistory,
                )
                Screen.GlobalProfile -> GlobalProfileScreen(
                    saved = globalProfile,
                    enabled = globalProfileEnabled,
                    model = vm.questModel,
                    onBack = { vm.back() },
                    onSave = vm::saveGlobalProfile,
                    onEnabledChange = vm::setGlobalProfileEnabled,
                    thermalLevel = thermalLevel,
                    refreshRates = refreshRates,
                )
                Screen.Connection -> ConnectionScreen(
                    state = connectionState,
                    pairingServices = pairingServices,
                    pairingScanning = pairingScanning,
                    onStartDiscovery = vm::startPairingDiscovery,
                    onStopDiscovery = vm::stopPairingDiscovery,
                    onPair = vm::pair,
                    onBack = { vm.back() },
                    onDisconnect = vm::disconnect,
                    onDeveloperOptionsUnavailable = { vm.showMessage(R.string.open_dev_options_failed) },
                    autoReconnect = AutoReconnectUi(
                        status = autoReconnectStatus,
                        experimental = vm.autoReconnectExperimental,
                        busy = autoReconnectBusy,
                        preparing = preparing,
                        issue = autoReconnectIssue,
                        lifetime = authorizationLifetime,
                        expiryStatus = expiryStatus,
                    ),
                    onReconnectNow = vm::reconnectNow,
                    onEnableAutoReconnect = { showReconnectDialog = true },
                    onDisableAutoReconnect = vm::disableAutoReconnect,
                )
                is Screen.Profile -> {
                    // Le ViewModel retire l'écran si le jeu disparaît de la liste (désinstallation).
                    val game = games.firstOrNull { it.packageName == screen.packageName }
                    if (game != null) {
                        ProfileScreen(
                            game = game,
                            saved = profiles[game.packageName],
                            model = vm.questModel,
                            connectionState = connectionState,
                            tuning = tuningPackage != null || learningState.active || learningState.recoveryRequired,
                            onBack = { vm.back() },
                            onSave = { vm.saveProfile(game.packageName, it) },
                            onApplyAndLaunch = { vm.applyAndLaunch(game, it) },
                            onOpenConnection = { vm.navigate(Screen.Connection) },
                            onDelete = { vm.deleteProfile(game.packageName) },
                            thermalLevel = thermalLevel,
                            diagnostic = diagnostic,
                            refreshRates = refreshRates,
                            library = profileLibraries[game.packageName],
                            onSaveNamed = { name, profile -> vm.saveNamedProfile(game.packageName, name, profile) },
                            onSelectNamed = { vm.selectNamedProfile(game.packageName, it) },
                            onDeleteNamed = { vm.deleteNamedProfile(game.packageName, it) },
                            onCloneNamed = { source, name -> vm.cloneNamedProfile(game.packageName, source, name) },
                            autoTuneRecommendations = vm.autoTuneRecommendations(game.packageName),
                            onApplyAutoTune = { vm.applyAutoTune(game.packageName, it) },
                            optimizationHistory = optimizationHistory[game.packageName],
                            adaptiveSuggestion = vm.adaptiveSuggestion(game.packageName),
                            onApplyAdaptive = { vm.applyAdaptiveSuggestion(game.packageName) },
                            onClearAdaptiveHistory = { vm.clearOptimizationHistory(game.packageName) },
                            communityProfiles = vm.communityProfiles(game.packageName),
                            onInstallCommunityProfile = vm::installCommunityProfile,
                            optimizationScore = vm.optimizationScore(game.packageName),
                            optimizationAdvice = vm.optimizationAdvice(game.packageName),
                            sessions = sessions[game.packageName].orEmpty(),
                            initialSection = screen.section,
                            onApplyOptimizationAdvice = { vm.applyOptimizationAdvice(game.packageName) },
                            onClearSessions = { vm.clearSessionHistory(game.packageName) },
                            onOpenPerformance = { vm.navigate(Screen.PerformanceMonitor) },
                            measuredLearning = { GameLearningPanel(vm,game) },
                        )
                    }
                }
            }
            }
            deviceResult?.let { result -> AlertDialog(onDismissRequest = vm.tools::dismissResult, title = { Text("Headset response") }, text = { Text(result) }, confirmButton = { TextButton(onClick = vm.tools::dismissResult) { Text("Close") } }) }
            pendingPower?.let { reboot -> AlertDialog(onDismissRequest = { pendingPower = null }, title = { Text(if (reboot) "Reboot headset?" else "Shut down headset?") },
                text = { Text("This closes the running game. Save your progress first.") },
                confirmButton = { Button(enabled = !toolBusy, onClick = { pendingPower = null; vm.tools.power(reboot) }) { Text(if (reboot) "Reboot" else "Shut down") } },
                dismissButton = { TextButton(onClick = { pendingPower = null }) { Text("Cancel") } }) }
            if (showAccounts) AlertDialog(onDismissRequest = { showAccounts = false }, title = { Text("Switch headset account") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(output, style = MaterialTheme.typography.bodySmall, maxLines = 5)
                    users.forEach { user -> OutlinedButton(enabled = !toolBusy && connectionState is ConnectionState.Connected, onClick = { pendingAccount = user; showAccounts = false }) { Text(user.name) } }
                }
            }, confirmButton = { TextButton(onClick = { showAccounts = false }) { Text("Close") } })
            pendingAccount?.let { user -> AlertDialog(onDismissRequest = { pendingAccount = null }, title = { Text("Switch to ${user.name}?") }, text = { Text("Save your game first. The headset may close this app and require the account unlock code.") }, confirmButton = { Button(onClick = { pendingAccount = null; vm.tools.switchUser(user.id) }) { Text("Switch account") } }, dismissButton = { TextButton(onClick = { pendingAccount = null }) { Text("Cancel") } }) }
            if (autoReconnectProposal || showReconnectDialog) {
                // Même fenêtre que « Activer » dans l'écran Connexion, avec un autre titre (FR-003).
                AutoReconnectDialog(
                    title = if (showReconnectDialog) R.string.auto_reconnect_dialog_title else R.string.auto_reconnect_proposal_title,
                    lifetime = authorizationLifetime,
                    onConfirm = { neverExpire ->
                        showReconnectDialog = false
                        vm.dismissAutoReconnectProposal()
                        vm.enableAutoReconnect(neverExpire)
                    },
                    onDismiss = { showReconnectDialog = false; vm.dismissAutoReconnectProposal() },
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}


@Composable
private fun AutoReconnectDialog(
    title: Int,
    lifetime: io.github.openquesttuner.core.AuthorizationLifetime,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var neverExpire by rememberSaveable {
        mutableStateOf(lifetime is io.github.openquesttuner.core.AuthorizationLifetime.Never)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.auto_reconnect_dialog_body))
                if (lifetime is io.github.openquesttuner.core.AuthorizationLifetime.Days) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { neverExpire = !neverExpire },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = neverExpire,
                            onCheckedChange = { neverExpire = it },
                        )
                        Text(stringResource(R.string.never_expire_label))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(neverExpire) }) {
                Text(stringResource(R.string.auto_reconnect_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auto_reconnect_dialog_later))
            }
        },
    )
}
