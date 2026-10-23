@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.openquesttuner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.GameSession
import io.github.openquesttuner.core.OptimizationAdvice
import io.github.openquesttuner.core.QuestModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(model: QuestModel, connectionState: ConnectionState, gameCount: Int, onOpenConnection: () -> Unit, onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("Welcome to Open Quest Tuner Pro", "Connect your headset", "Device capability scan", "Build your game library", "You're ready")
    val bodies = listOf(
        "Per-game Quest optimisation with automatic profile switching, Auto Tune, thermal intelligence and updateable recommendations.",
        "Open Quest Tuner uses its embedded ADB connection to read and apply supported Quest tuning properties. Pair once, then enable automatic reconnect if you want a hands-off experience.",
        "Detected: ${model.displayName}. Recommendations are constrained by this headset's supported refresh rates, resolution limits and safe tuning rules.",
        if (gameCount > 0) "$gameCount launchable applications are already available. Each can use its own named profiles, adaptive history and community recommendations." else "The library will scan installed launchable applications. Profiles can be created manually or generated with Auto Tune.",
        "Start with Balanced or Auto Tune. Open Quest Tuner records real game metrics. Choose Learn & Play for measured per-game learning. Advanced controls are available when you need them.",
    )
    Scaffold(topBar = { OqtTopAppBar(title = { Text("Setup ${page + 1} of ${titles.size}") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(progress = { (page + 1f) / titles.size }, modifier = Modifier.fillMaxWidth())
            Text(titles[page], style = MaterialTheme.typography.headlineMedium)
            Text(bodies[page], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (page == 1) {
                val connected = connectionState is ConnectionState.Connected
                Card { Column(Modifier.fillMaxWidth().padding(10.dp)) { Text(if (connected) "✓ Headset connected" else "Headset not connected", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Button(onClick = onOpenConnection) { Text(if (connected) "Connection details" else "Connect headset") } } }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                if (page > 0) OutlinedButton(onClick = { page-- }) { Text("Back") }
                Button(onClick = { if (page == titles.lastIndex) onFinish() else page++ }) { Text(if (page == titles.lastIndex) "Open dashboard" else "Continue") }
            }
        }
        }
    }
}

@Composable
fun OptimizationAssistantCard(advice: OptimizationAdvice, sessions: List<GameSession>, onApply: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Optimization Assistant", style = MaterialTheme.typography.titleMedium)
            Text(advice.headline, style = MaterialTheme.typography.titleLarge)
            Text(advice.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${sessions.size} recorded sessions • review measured evidence below", style = MaterialTheme.typography.labelMedium)
            if (advice.action != io.github.openquesttuner.core.AssistantAction.NONE) Button(onClick = onApply) { Text(if (advice.goal != null) "Apply ${advice.goal.label}" else "Re-apply profile") }
        }
    }
}

@Composable
fun SessionHistoryCard(sessions: List<GameSession>, onClear: () -> Unit) {
    Card { Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Recent Sessions", style = MaterialTheme.typography.titleMedium); if (sessions.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") } }
        if (sessions.isEmpty()) Text("No completed sessions yet. Play a game to begin session intelligence.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        sessions.takeLast(5).reversed().forEach { s ->
            val mins = (s.durationMs / 60_000L).coerceAtLeast(1)
            HorizontalDivider()
            Text("${s.profileName ?: "Game Default"} • ${mins} min", style = MaterialTheme.typography.titleSmall)
            Text("Override matches ${s.stabilityPercent}% • Thermal pressure ${s.thermalPressurePercent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSettingsScreen(safeAuto: Boolean, notifications: Boolean, onSafeAuto: (Boolean) -> Unit, onNotifications: (Boolean) -> Unit, onRestartOnboarding: () -> Unit, onBack: () -> Unit, onReset: () -> Unit, resetting: Boolean, connected: Boolean, advancedLearning: @Composable () -> Unit = {}) {
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(topBar = { OqtTopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Optimization Assistant", style = MaterialTheme.typography.titleLarge)
            SettingsToggle("Assistant notifications", "Allow the assistant to surface important profile and thermal recommendations.", notifications, onNotifications)
            HorizontalDivider()
            Text("Advanced",style=MaterialTheme.typography.titleLarge)
            advancedLearning()
            HorizontalDivider()
            Text("Headset settings", style = MaterialTheme.typography.titleLarge)
            Text("Restore active tuning settings to their defaults. Your saved profiles are kept.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { confirmReset = true }, enabled = connected && !resetting) { Text(if (resetting) "Resetting…" else "Reset active tuning") }
            if (!connected) Text("Connect the headset to reset active tuning.", style = MaterialTheme.typography.bodySmall)
            Text("Product setup", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onRestartOnboarding) { Text("Run setup wizard again") }
            Text("Automatic tuning never fabricates FPS data. Auto Learn uses fresh game counters and repeated comparisons. Untested starter profiles remain separate.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false }, title = { Text("Reset active tuning?") },
        text = { Text("The seven managed settings will return to headset defaults. Saved profiles remain available.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("Reset tuning") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable private fun SettingsToggle(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onChecked) } }
}
