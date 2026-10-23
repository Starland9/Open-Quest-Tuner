package io.github.openquesttuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.adb.PairingService
import io.github.openquesttuner.core.*
import io.github.openquesttuner.ui.components.isBusy
import io.github.openquesttuner.ui.components.messageRes
import androidx.compose.ui.res.stringResource

/** Small state holder retained for the reconnect engine; advanced details stay out of the pairing UI. */
data class AutoReconnectUi(
    val status: AutoReconnectStatus,
    val experimental: Boolean,
    val busy: Boolean,
    val preparing: Boolean,
    val issue: ReconnectIssue?,
    val lifetime: AuthorizationLifetime,
    val expiryStatus: ExpiryChoiceStatus,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    state: ConnectionState,
    pairingServices: List<PairingService>,
    pairingScanning: Boolean,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onPair: (PairingService, String) -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onDeveloperOptionsUnavailable: () -> Unit,
    autoReconnect: AutoReconnectUi,
    onReconnectNow: () -> Unit,
    onEnableAutoReconnect: () -> Unit,
    onDisableAutoReconnect: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember(pairingServices) { mutableStateOf<PairingService?>(pairingServices.singleOrNull()) }
    var code by rememberSaveable { mutableStateOf("") }
    val busy = state.isBusy || autoReconnect.preparing

    DisposableEffect(Unit) {
        if (state !is ConnectionState.Connected) onStartDiscovery()
        onDispose(onStopDiscovery)
    }

    Scaffold(topBar = {
        OqtTopAppBar(
            title = { Text("Quest connection") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { inset ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inset)) {
        FlowRow(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            maxItemsInEachRow = if (maxWidth >= 820.dp) 2 else 1,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Wireless ADB", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state is ConnectionState.Connected) "Your Quest is connected."
                    else "Open Android Settings, choose Wireless debugging → Pair device with pairing code. OpenQuest Tuner discovers the pairing service automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state is ConnectionState.Failed) Text(stringResource(state.reason.messageRes()), color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                autoReconnect.issue?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.tertiary) }

                if (state is ConnectionState.Connected) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Automatic reconnect will be used when possible.", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                        }
                    }
                } else {
                    Button(onClick = {
                        openAndroidSettings(context, onDeveloperOptionsUnavailable)
                    }) { Text("Open Android Settings") }

                    PairingDiscoveryPanel(
                        services = pairingServices,
                        scanning = pairingScanning,
                        selected = selected,
                        onSelect = { selected = it },
                        onRescan = onStartDiscovery,
                    )

                    selected?.let { service ->
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(6) },
                            label = { Text("6-digit pairing code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { onPair(service, code) },
                            enabled = !busy && ConnectionInput.isValidPairingCode(code),
                            modifier = Modifier.heightIn(min = 40.dp),
                        ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Pair Quest") }
                    }
                }
            }

            run {
                Column(Modifier.weight(.85f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Automatic reconnect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("ADB identity is stored privately on this headset. Pairing should only be required again if Android removes the trust relationship.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when (autoReconnect.status) {
                        AutoReconnectStatus.ACTIVE -> "Enabled"
                        AutoReconnectStatus.INACTIVE -> "Disabled"
                        AutoReconnectStatus.NEEDS_REACTIVATION -> "Needs reactivation"
                    }, color = MaterialTheme.colorScheme.primary)
                    if (autoReconnect.status == AutoReconnectStatus.ACTIVE) {
                        OutlinedButton(onClick = onDisableAutoReconnect, enabled = !busy && !autoReconnect.busy) { Text("Disable auto-reconnect") }
                    } else Button(onClick = onEnableAutoReconnect, enabled = state is ConnectionState.Connected && !busy && !autoReconnect.busy) {
                        Text(if (autoReconnect.status == AutoReconnectStatus.NEEDS_REACTIVATION) "Reactivate" else "Enable auto-reconnect")
                    }
                    OutlinedButton(onClick = onReconnectNow, enabled = !busy && !autoReconnect.busy) { Text("Reconnect now") }
                }
            }
        }
        }
    }
}

@Composable
private fun PairingDiscoveryPanel(
    services: List<PairingService>, scanning: Boolean, selected: PairingService?,
    onSelect: (PairingService) -> Unit, onRescan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (services.size > 1) "${services.size} pairing services found" else if (services.size == 1) "Quest pairing service found" else "Looking for your Quest…", fontWeight = FontWeight.SemiBold)
                Text("No IP address or port entry required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRescan) { Icon(Icons.Default.Refresh, "Rescan") }
        }
        if (services.isEmpty() && scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        services.forEach { service ->
            val active = selected?.id == service.id
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(service) },
                shape = MaterialTheme.shapes.medium,
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(service.name.ifBlank { "Quest" }, fontWeight = FontWeight.Medium)
                        Text("Wireless debugging available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = active, onClick = { onSelect(service) })
                }
            }
        }
        if (services.isEmpty() && !scanning) Text("No pairing service found. Keep the pairing-code screen open and select Rescan.", style = MaterialTheme.typography.bodySmall)
    }
}
