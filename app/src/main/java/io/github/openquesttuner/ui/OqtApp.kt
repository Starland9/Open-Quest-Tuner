package io.github.openquesttuner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.ui.components.ConnectionBadge

@Composable
fun OqtApp(vm: MainViewModel = viewModel()) {
    val backStack by vm.backStack.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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
            val switchingToWireless by vm.switchingToWireless.collectAsState()
            val games by vm.games.collectAsState()
            val loadingGames by vm.loadingGames.collectAsState()
            val profiles by vm.profiles.collectAsState()
            val filteredGames by vm.filteredGames.collectAsState()
            val query by vm.query.collectAsState()
            val tuningPackage by vm.tuningPackage.collectAsState()
            val thermalLevel by vm.thermalLevel.collectAsState()
            val diagnostic by vm.diagnostic.collectAsState()
            val resetting by vm.resetting.collectAsState()
            when (val screen = backStack.last()) {
                Screen.Games -> GamesScreen(
                    games = filteredGames,
                    totalGames = games.size,
                    loading = loadingGames,
                    query = query,
                    onQueryChange = vm::setQuery,
                    profiledPackages = profiles.keys,
                    connected = connectionState is ConnectionState.Connected,
                    tuningPackage = tuningPackage,
                    onOpenGame = { vm.navigate(Screen.Profile(it.packageName)) },
                    onLaunch = vm::quickLaunch,
                    onRefresh = vm::refreshGames,
                    onOpenConnection = { vm.navigate(Screen.Connection) },
                    thermalLevel = thermalLevel,
                    actions = {
                        ConnectionBadge(
                            state = connectionState,
                            onClick = { vm.navigate(Screen.Connection) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )
                Screen.Connection -> ConnectionScreen(
                    state = connectionState,
                    onBack = { vm.back() },
                    onPair = vm::pair,
                    onConnectWireless = vm::connectWireless,
                    onConnectPc = vm::connectPc,
                    onDisconnect = vm::disconnect,
                    onDeveloperOptionsUnavailable = { vm.showMessage(R.string.open_dev_options_failed) },
                    switchingToWireless = switchingToWireless,
                    onSwitchToWireless = vm::switchToWireless,
                    resetting = resetting,
                    onResetAll = vm::resetAll,
                    thermalLevel = thermalLevel,
                    diagnostic = diagnostic,
                    onRefreshDiagnostic = vm::refreshDiagnostic,
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
                            tuning = tuningPackage != null,
                            onBack = { vm.back() },
                            onSave = { vm.saveProfile(game.packageName, it) },
                            onApplyAndLaunch = { vm.applyAndLaunch(game, it) },
                            onOpenConnection = { vm.navigate(Screen.Connection) },
                            onDelete = { vm.deleteProfile(game.packageName) },
                            thermalLevel = thermalLevel,
                            diagnostic = diagnostic,
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}
