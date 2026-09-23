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
            when (backStack.last()) {
                Screen.Games -> GamesScreen(
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
                )
                // Écran branché par l'US2.
                is Screen.Profile -> Unit
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}
