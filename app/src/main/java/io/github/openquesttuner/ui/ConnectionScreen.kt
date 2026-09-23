package io.github.openquesttuner.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ConnectionInput
import io.github.openquesttuner.core.ConnectionMethod
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.Diagnostic
import io.github.openquesttuner.core.ThermalLevel
import io.github.openquesttuner.ui.components.isBusy
import io.github.openquesttuner.ui.components.labelRes
import io.github.openquesttuner.ui.components.messageRes

/** Connexion à l'ADB du casque (US1), puis outils (US4, US5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    state: ConnectionState,
    onBack: () -> Unit,
    onPair: (port: String, code: String) -> Unit,
    onConnectWireless: (port: String) -> Unit,
    onConnectPc: () -> Unit,
    onDisconnect: () -> Unit,
    onDeveloperOptionsUnavailable: () -> Unit,
    switchingToWireless: Boolean,
    onSwitchToWireless: () -> Unit,
    resetting: Boolean,
    onResetAll: () -> Unit,
    thermalLevel: ThermalLevel,
    diagnostic: Diagnostic?,
    onRefreshDiagnostic: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusCard(state, onDisconnect, switchingToWireless, onSwitchToWireless)
                ToolsCard(connected = state is ConnectionState.Connected, resetting = resetting, onResetAll = onResetAll)
                DiagnosticCard(
                    connected = state is ConnectionState.Connected,
                    thermalLevel = thermalLevel,
                    diagnostic = diagnostic,
                    onRefresh = onRefreshDiagnostic,
                )
                // Première connexion via PC (une seule fois), puis « Passer en sans fil » ;
                // l'appairage par code n'est qu'un secours (spec US1, amendée le 2026-09-23).
                PcCard(busy = state.isBusy || switchingToWireless, onConnectPc = onConnectPc)
                WirelessCard(
                    busy = state.isBusy || switchingToWireless,
                    onPair = onPair,
                    onConnect = onConnectWireless,
                    onDeveloperOptionsUnavailable = onDeveloperOptionsUnavailable,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: ConnectionState,
    onDisconnect: () -> Unit,
    switchingToWireless: Boolean,
    onSwitchToWireless: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.connection_status_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.isBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(stringResource(state.labelRes()), style = MaterialTheme.typography.titleLarge)
        }
        if (state is ConnectionState.Failed) {
            Text(
                stringResource(state.reason.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state is ConnectionState.Connected && state.method == ConnectionMethod.PC) {
            Text(
                stringResource(if (switchingToWireless) R.string.switch_waiting else R.string.switch_to_wireless_hint),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSwitchToWireless,
                enabled = !switchingToWireless,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (switchingToWireless) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.switch_to_wireless))
                }
            }
        }
        if (state is ConnectionState.Connected) {
            OutlinedButton(
                onClick = onDisconnect,
                enabled = !switchingToWireless,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.disconnect))
            }
        }
    }
}

/** Filet de sécurité du principe I : annuler tous les réglages en un geste (US4). */
@Composable
private fun ToolsCard(connected: Boolean, resetting: Boolean, onResetAll: () -> Unit) {
    SectionCard(title = stringResource(R.string.tools_title)) {
        Text(stringResource(R.string.reset_all_hint), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onResetAll,
            enabled = connected && !resetting,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            if (resetting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.reset_all))
            }
        }
        if (!connected) {
            Text(
                stringResource(R.string.reset_all_disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Diagnostic (US5) : état thermique toujours visible, propriétés actives une fois connecté. */
@Composable
private fun DiagnosticCard(
    connected: Boolean,
    thermalLevel: ThermalLevel,
    diagnostic: Diagnostic?,
    onRefresh: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.diagnostic_title)) {
        Text(
            stringResource(R.string.thermal_state, stringResource(thermalLevel.labelRes())),
            style = MaterialTheme.typography.bodyLarge,
            color = if (thermalLevel.warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (connected) {
            when {
                diagnostic == null -> Text(stringResource(R.string.diagnostic_unavailable), style = MaterialTheme.typography.bodyMedium)
                diagnostic.active.isEmpty() -> Text(stringResource(R.string.diagnostic_no_active), style = MaterialTheme.typography.bodyMedium)
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    diagnostic.active.forEach { (key, value) ->
                        Text("$key = $value", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.refresh))
            }
        } else {
            Text(
                stringResource(R.string.diagnostic_connect),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WirelessCard(
    busy: Boolean,
    onPair: (port: String, code: String) -> Unit,
    onConnect: (port: String) -> Unit,
    onDeveloperOptionsUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    var pairingPort by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }

    val pairingPortValid = ConnectionInput.parsePort(pairingPort) != null
    val pairingCodeValid = ConnectionInput.isValidPairingCode(pairingCode)
    val connectPortValid = connectPort.isEmpty() || ConnectionInput.parsePort(connectPort) != null

    SectionCard(title = stringResource(R.string.wireless_title)) {
        Text(
            stringResource(R.string.wireless_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Steps(R.string.wireless_step_1, R.string.wireless_step_2, R.string.wireless_step_3)
        OutlinedButton(
            onClick = {
                // L'écran d'appairage est inaccessible aux applis depuis Horizon OS v83 ; on ouvre
                // les options développeur dans un panneau séparé, à côté du nôtre (research.md R4).
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                )
                try {
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    onDeveloperOptionsUnavailable()
                } catch (e: SecurityException) {
                    onDeveloperOptionsUnavailable()
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.open_dev_options))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField(
                value = pairingPort,
                onValueChange = { pairingPort = it },
                label = R.string.pairing_port,
                maxLength = 5,
                error = if (pairingPort.isNotEmpty() && !pairingPortValid) R.string.error_port_range else null,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = pairingCode,
                onValueChange = { pairingCode = it },
                label = R.string.pairing_code,
                maxLength = 6,
                error = if (pairingCode.isNotEmpty() && !pairingCodeValid) R.string.error_code_digits else null,
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = { onPair(pairingPort, pairingCode) },
            enabled = !busy && pairingPortValid && pairingCodeValid,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.pair))
        }
        NumberField(
            value = connectPort,
            onValueChange = { connectPort = it },
            label = R.string.connection_port_optional,
            maxLength = 5,
            error = if (!connectPortValid) R.string.error_port_range else null,
            supporting = R.string.connection_port_hint,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { onConnect(connectPort) },
            enabled = !busy && connectPortValid,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.connect))
        }
    }
}

@Composable
private fun PcCard(busy: Boolean, onConnectPc: () -> Unit) {
    SectionCard(title = stringResource(R.string.pc_title)) {
        Text(stringResource(R.string.pc_step_1), style = MaterialTheme.typography.bodyMedium)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "adb tcpip 5555",
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Text(stringResource(R.string.pc_step_2), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.pc_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onConnectPc, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.connect_pc))
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Steps(@StringRes vararg steps: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, step ->
            Text("${index + 1}. ${stringResource(step)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    maxLength: Int,
    @StringRes error: Int?,
    modifier: Modifier = Modifier,
    @StringRes supporting: Int? = null,
) {
    OutlinedTextField(
        value = value,
        // Chiffres seulement : la validation complète reste celle de ConnectionInput.
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(maxLength)) },
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { res -> { Text(stringResource(res)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

