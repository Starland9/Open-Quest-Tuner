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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.AuthorizationLifetime
import io.github.openquesttuner.core.AutoReconnectStatus
import io.github.openquesttuner.core.ConnectionInput
import io.github.openquesttuner.core.ConnectionMethod
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.Diagnostic
import io.github.openquesttuner.core.ExpiryChoiceStatus
import io.github.openquesttuner.core.ReconnectIssue
import io.github.openquesttuner.core.ThermalLevel
import io.github.openquesttuner.ui.components.ExperimentalBadge
import io.github.openquesttuner.ui.components.isBusy
import io.github.openquesttuner.ui.components.labelRes
import io.github.openquesttuner.ui.components.messageRes

/** État de la reconnexion autonome montré par l'écran Connexion (spec 002). */
data class AutoReconnectUi(
    val status: AutoReconnectStatus,
    val experimental: Boolean,
    val busy: Boolean,
    val preparing: Boolean,
    val issue: ReconnectIssue?,
    val lifetime: AuthorizationLifetime,
    val expiryStatus: ExpiryChoiceStatus,
)

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
    autoReconnect: AutoReconnectUi,
    onEnableAutoReconnect: (neverExpire: Boolean) -> Unit,
    onReconnectNow: () -> Unit,
    onSetNeverExpire: (on: Boolean) -> Unit,
    onDisableAutoReconnect: () -> Unit,
) {
    val busy = state.isBusy || switchingToWireless || autoReconnect.preparing
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
                StatusCard(state, autoReconnect, onDisconnect, switchingToWireless, onSwitchToWireless)
                AutoReconnectCard(
                    ui = autoReconnect,
                    connected = state is ConnectionState.Connected,
                    connectionBusy = busy,
                    onEnable = onEnableAutoReconnect,
                    onReconnect = onReconnectNow,
                    onSetNeverExpire = onSetNeverExpire,
                    onDisable = onDisableAutoReconnect,
                )
                ToolsCard(connected = state is ConnectionState.Connected, resetting = resetting, onResetAll = onResetAll)
                DiagnosticCard(
                    connected = state is ConnectionState.Connected,
                    thermalLevel = thermalLevel,
                    diagnostic = diagnostic,
                    onRefresh = onRefreshDiagnostic,
                )
                // Première connexion via PC (une seule fois), puis « Passer en sans fil » ;
                // l'appairage par code n'est qu'un secours (spec US1, amendée le 2026-09-23).
                PcCard(busy = busy, onConnectPc = onConnectPc)
                WirelessCard(
                    busy = busy,
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
    autoReconnect: AutoReconnectUi,
    onDisconnect: () -> Unit,
    switchingToWireless: Boolean,
    onSwitchToWireless: () -> Unit,
) {
    val preparing = autoReconnect.preparing
    SectionCard(title = stringResource(R.string.connection_status_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.isBusy || preparing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(
                stringResource(if (preparing) R.string.auto_reconnect_preparing else state.labelRes()),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (preparing) {
            // La fenêtre « autoriser sur ce réseau » d'Horizon OS peut apparaître (FR-007).
            Text(stringResource(R.string.auto_reconnect_preparing_hint), style = MaterialTheme.typography.bodyMedium)
        }
        val issue = autoReconnect.issue
        if (!preparing && state == ConnectionState.Disconnected && issue != null) {
            // Cause et étape suivante, sans message d'erreur alarmant (FR-009).
            Text(
                stringResource(issue.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/**
 * Reconnexion autonome (spec 002) : activer après confirmation (FR-002, FR-016), réactiver sans
 * nouvelle confirmation, se reconnecter à la demande (FR-005, FR-011), et choix « autorisations
 * sans expiration » (FR-021, FR-025 : confirmation pour l'activer seulement).
 */
@Composable
private fun AutoReconnectCard(
    ui: AutoReconnectUi,
    connected: Boolean,
    connectionBusy: Boolean,
    onEnable: (neverExpire: Boolean) -> Unit,
    onReconnect: () -> Unit,
    onSetNeverExpire: (on: Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    var confirmEnable by rememberSaveable { mutableStateOf(false) }
    var confirmNeverExpire by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.auto_reconnect_title)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(ui.status.labelRes()), style = MaterialTheme.typography.titleSmall)
            if (ui.experimental) ExperimentalBadge()
        }
        Text(stringResource(R.string.auto_reconnect_summary), style = MaterialTheme.typography.bodyMedium)
        when (ui.status) {
            AutoReconnectStatus.INACTIVE -> {
                Button(
                    onClick = { confirmEnable = true },
                    enabled = connected && !ui.busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.auto_reconnect_enable))
                }
                if (!connected) Hint(R.string.auto_reconnect_connect_first)
            }
            AutoReconnectStatus.NEEDS_REACTIVATION -> {
                Text(stringResource(R.string.auto_reconnect_needs_reactivation_hint), style = MaterialTheme.typography.bodyMedium)
                // La confirmation donnée à l'activation vaut tant que l'option n'est pas désactivée (FR-016).
                Button(
                    onClick = { onEnable(false) },
                    enabled = connected && !ui.busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.auto_reconnect_reactivate))
                }
            }
            AutoReconnectStatus.ACTIVE -> Unit
        }
        if (ui.status != AutoReconnectStatus.INACTIVE && !connected) {
            OutlinedButton(
                onClick = onReconnect,
                enabled = !connectionBusy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.auto_reconnect_reconnect))
            }
        }
        if (ui.status != AutoReconnectStatus.INACTIVE) {
            // Avec ou sans connexion, sans confirmation : cela réduit les droits. Ne coupe ni la
            // connexion ni le débogage sans fil (FR-013, FR-014).
            OutlinedButton(onClick = onDisable, enabled = !ui.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.auto_reconnect_disable))
            }
        }
        if (ui.status != AutoReconnectStatus.INACTIVE && ui.expiryStatus != ExpiryChoiceStatus.NOT_APPLICABLE) {
            Text(stringResource(ui.expiryStatus.labelRes()), style = MaterialTheme.typography.bodyMedium)
            when (ui.expiryStatus) {
                ExpiryChoiceStatus.OFF -> OutlinedButton(
                    onClick = { confirmNeverExpire = true },
                    enabled = connected && !ui.busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.never_expire_enable))
                }
                // Revenir au délai d'origine réduit les droits : pas de confirmation (FR-025).
                ExpiryChoiceStatus.ON -> OutlinedButton(
                    onClick = { onSetNeverExpire(false) },
                    enabled = connected && !ui.busy,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.never_expire_disable))
                }
                ExpiryChoiceStatus.RESTORE_PENDING, ExpiryChoiceStatus.NOT_APPLICABLE -> Unit
            }
        }
    }
    if (confirmEnable) {
        AutoReconnectDialog(
            title = R.string.auto_reconnect_dialog_title,
            lifetime = ui.lifetime,
            onConfirm = { neverExpire ->
                confirmEnable = false
                onEnable(neverExpire)
            },
            onDismiss = { confirmEnable = false },
        )
    }
    if (confirmNeverExpire) {
        AlertDialog(
            onDismissRequest = { confirmNeverExpire = false },
            title = { Text(stringResource(R.string.never_expire_dialog_title)) },
            text = { Text(stringResource(R.string.never_expire_consequences)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmNeverExpire = false
                        onSetNeverExpire(true)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.never_expire_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmNeverExpire = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Explication de la reconnexion autonome avant tout octroi (FR-002). Sert aussi à la proposition
 * qui suit « Passer en sans fil » (FR-003), avec un autre titre. Si les autorisations expirent sur
 * ce casque, elle le dit et propose la case « ne jamais faire expirer », non cochée (FR-021).
 */
@Composable
fun AutoReconnectDialog(
    @StringRes title: Int,
    lifetime: AuthorizationLifetime,
    onConfirm: (neverExpire: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var neverExpire by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.auto_reconnect_dialog_body), style = MaterialTheme.typography.bodyMedium)
                if (lifetime is AuthorizationLifetime.Days) {
                    Text(lifetime.text(), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = neverExpire, role = Role.Checkbox, onValueChange = { neverExpire = it }),
                    ) {
                        Checkbox(checked = neverExpire, onCheckedChange = null)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.never_expire_label), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        stringResource(R.string.never_expire_consequences),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(neverExpire) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.auto_reconnect_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.auto_reconnect_dialog_later))
            }
        },
    )
}

/** « L'autorisation expire après N jours sans connexion via PC » (FR-002). */
@Composable
private fun AuthorizationLifetime.Days.text(): String =
    if (days == 0L) {
        stringResource(R.string.auto_reconnect_lifetime_less_than_day)
    } else {
        val count = days.toInt()
        pluralStringResource(R.plurals.auto_reconnect_lifetime_days, count, count)
    }

@StringRes
private fun ExpiryChoiceStatus.labelRes(): Int = when (this) {
    ExpiryChoiceStatus.ON -> R.string.never_expire_status_on
    ExpiryChoiceStatus.RESTORE_PENDING -> R.string.never_expire_status_pending
    ExpiryChoiceStatus.OFF, ExpiryChoiceStatus.NOT_APPLICABLE -> R.string.never_expire_status_off
}

@StringRes
private fun AutoReconnectStatus.labelRes(): Int = when (this) {
    AutoReconnectStatus.INACTIVE -> R.string.auto_reconnect_status_inactive
    AutoReconnectStatus.ACTIVE -> R.string.auto_reconnect_status_active
    AutoReconnectStatus.NEEDS_REACTIVATION -> R.string.auto_reconnect_status_needs_reactivation
}

@Composable
private fun Hint(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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

