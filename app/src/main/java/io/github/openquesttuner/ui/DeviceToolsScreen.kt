package io.github.openquesttuner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceToolsScreen(tools: DeviceToolsModel, connected: Boolean, model: QuestModel,
    onBack: () -> Unit, onConnection: () -> Unit, onPower: (Boolean) -> Unit, onAccounts: () -> Unit) {
    val output by tools.output.collectAsState()
    val terminal by tools.terminal.collectAsState()
    val busy by tools.busy.collectAsState()
    val running by tools.terminalRunning.collectAsState()
    val observedWmm by tools.wmmState.collectAsState()
    val wmm = if (connected) observedWmm else WmmState()
    LaunchedEffect(connected) {
        if (connected) while (true) { tools.refreshWmm(); delay(5000) }
    }
    var tab by remember { mutableIntStateOf(0) }
    var command by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val enabled = connected && !busy
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Device workspace", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ToolPill(onClick = onConnection) { Text(if (connected) "ADB connection" else "Pair headset") }
            ToolPill(onClick = onBack) { Text("Back") }
        }
        TabRow(tab) { Tab(tab == 0, { tab = 0 }, text = { Text("Headset tools") }); Tab(tab == 1, { tab = 1 }, text = { Text("ADB shell terminal") }) }
        if (tab == 1) {
            Text("Runs as the paired Android shell on this headset. Each command starts a new shell. Interactive programs are not supported.", style = MaterialTheme.typography.bodySmall)
            Surface(Modifier.weight(1f).fillMaxWidth(), color = VisionPanel, shape = MaterialTheme.shapes.medium) {
                SelectionContainer { Text(terminal, Modifier.padding(10.dp).verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(command, { command = it }, Modifier.weight(1f), label = { Text("shell $ ") }, maxLines = 3)
                if (running) ToolPill(onClick = tools::cancelTerminal) { Text("Cancel") }
                Button(enabled = enabled && command.isNotBlank(), onClick = { tools.runTerminal(command) }) { Text(if (busy) "Running…" else "Run") }
            }
            Text("15-second limit • 512 KiB output limit • no automatic retries • commands are not saved", style = MaterialTheme.typography.bodySmall)
        } else {
            BoxWithConstraints(Modifier.weight(1f)) {
                val cards: @Composable (Modifier, Boolean) -> Unit = { modifier, first ->
                    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (first) {
                            ToolCard("Wide Motion Mode", "Helps your virtual hands keep moving naturally when you reach to the side or your hands briefly leave the cameras' view. Quest estimates their position from your arms; it cannot see hidden fingers.") {
                                val stateText = when (wmm.status) {
                                    WmmStatus.ON -> "ON · override verified"
                                    WmmStatus.OFF -> "OFF · not configured"
                                    WmmStatus.PAUSED -> "OFF · fusion disabled, setup retained"
                                    WmmStatus.MIXED -> "PARTIAL · settings do not fully match"
                                    WmmStatus.UNKNOWN -> if (connected) "UNKNOWN · checking headset" else "UNKNOWN · connect ADB to check"
                                    WmmStatus.UNSUPPORTED -> "UNAVAILABLE · requires Quest 3 / 3S"
                                }
                                val stateColor = when (wmm.status) {
                                    WmmStatus.ON -> VisionGreen
                                    WmmStatus.OFF, WmmStatus.PAUSED -> Color(0xFFFF727A)
                                    WmmStatus.MIXED -> Color(0xFFFFCA70)
                                    else -> VisionMuted
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(10.dp).background(stateColor, CircleShape))
                                    Text(stateText, color = stateColor, style = MaterialTheme.typography.labelLarge)
                                }
                                Text("Quest 3 / 3S: first enable before starting the game. After setup, Enable / Disable switches hand-position estimation on or off without removing setup. Restore previous undoes all OQT WMM changes. Reboot clears the temporary override. The light verifies settings; game support still matters.", style = MaterialTheme.typography.bodySmall, color = VisionMuted)
                                ToolRow {
                                    if (wmm.status == WmmStatus.ON) {
                                        ToolPill(enabled = enabled, onClick = { tools.wmmFusion(false) }) { Text("Disable WMM") }
                                    } else {
                                        ToolPill(enabled = enabled && wmm.status in listOf(WmmStatus.OFF, WmmStatus.PAUSED, WmmStatus.MIXED), onClick = { if (wmm.status == WmmStatus.PAUSED) tools.wmmFusion(true) else tools.wmm(true) }) { Text("Enable WMM") }
                                    }
                                    if (wmm.canRestore) ToolPill(enabled = enabled, onClick = { tools.wmm(false) }) { Text("Restore previous") }
                                    ToolPill(enabled = enabled, onClick = tools::refreshWmm) { Text("Check status") }
                                }
                                if (wmm.status == WmmStatus.ON && !wmm.canRestore) Text("Enabled outside this saved session. Disable can turn fusion off; the original setup will be saved before changing it.", style = MaterialTheme.typography.bodySmall, color = VisionMuted)
                            }
                        } else {
                            ToolCard("Power & accounts", "Commands run through local ADB. Account switching may require the headset unlock code.") {
                                ToolRow {
                                    ToolPill(enabled = enabled, onClick = { onPower(true) }) { Text("Reboot") }
                                    ToolPill(enabled = enabled, onClick = { onPower(false) }) { Text("Shut down") }
                                    ToolPill(enabled = enabled, onClick = onAccounts) { Text("Accounts") }
                                }
                                ToolPill(onClick = { openAndroidSettings(context) }) { Text("Android system settings") }
                            }
                            ToolCard("Image processing", "FidelityFX CAS / Meta Quest Super Resolution (Snapdragon). The global compositor shell controls are pending verification.") {
                                Text("Unavailable in this build", color = VisionMuted, style = MaterialTheme.typography.labelLarge)
                                Text("No guessed sharpening flags are written. Game, PCVR player and video-layer support varies.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (maxWidth >= 650.dp) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards(Modifier.weight(1f), true); cards(Modifier.weight(1f), false) }
                else Column { cards(Modifier.weight(1f), true); cards(Modifier.weight(1f), false) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Text("Device response", style = MaterialTheme.typography.labelMedium); Text(output, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    ToolPill(onClick = { expanded = true }) { Text("Expand response") }
                }
            }
        }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("Device response") }, text = { SelectionContainer { Text(output, Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } }, confirmButton = { ToolPill(onClick = { expanded = false }) { Text("Close") } })
}
@Composable private fun ToolCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodySmall, color = VisionMuted); content() } }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ToolRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}
@Composable private fun ToolPill(enabled: Boolean = true, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (enabled) VisionBlue else VisionBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = VisionBlue.copy(alpha = 0.12f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), content = content)
}
