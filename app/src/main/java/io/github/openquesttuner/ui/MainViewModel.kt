package io.github.openquesttuner.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.openquesttuner.OqtApplication
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ConnectionInput
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.WirelessSwitchResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun navigate(screen: Screen) {
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

    /** Port vide : découverte automatique (mDNS). */
    fun connectWireless(portText: String) {
        val port = if (portText.isBlank()) null else ConnectionInput.parsePort(portText) ?: return
        viewModelScope.launch { container.adb.connectWireless(port) }
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
                    WirelessSwitchResult.SWITCHED -> Unit // L'état « Connecté (sans fil) » suffit.
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
}
