package io.github.openquesttuner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.*
import io.github.openquesttuner.games.InstalledGame

@Composable fun GameLearningPanel(vm: MainViewModel,game: InstalledGame) {
    val state by vm.learningState.collectAsState()
    val starting by vm.learningStarting.collectAsState()
    val connection by vm.connectionState.collectAsState()
    val events by vm.learningEvents.collectAsState()
    val completed by vm.completedLearning.collectAsState()
    val hud by vm.learningOverlayStatus.collectAsState()
    var advanced by remember(game.packageName) { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val clipboard=LocalClipboardManager.current
    val learned=completed[game.packageName]?.context==vm.learningContext(game.packageName) && vm.hasLearned(game.packageName)
    val own=state.packageName==game.packageName
    val blocked=state.active || state.recoveryRequired || starting
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(if(learned) "Your game profile is ready" else "Automatic game tuning",style=MaterialTheme.typography.titleMedium)
        Text(game.label,style=MaterialTheme.typography.bodyMedium)
        if(learned) {
            Text("The measured profile is saved to this game. Launch normally; learning will not run again unless you reset the profile.")
            Button(enabled=!blocked && connection is ConnectionState.Connected,onClick={vm.quickLaunch(game)}) {Text("Launch game")}
            TextButton(enabled=!blocked,onClick={reset=true}) {Text("Reset profile to defaults")}
        } else if(!state.active) {
            Text("Press Auto Tune & Play. OQT checks available measurements, tests supported settings, restores rejected changes and saves a verified profile automatically.")
            Text("Save your progress first: tests can restart the game automatically. Play a familiar section after each restart. Untestable settings stay unchanged.",style=MaterialTheme.typography.bodySmall)
            Button(enabled=!blocked && connection is ConnectionState.Connected,onClick={vm.autoLearn(game)}) {Text(if(starting) "Preparing…" else "Auto Tune & Play")}
            if(connection !is ConnectionState.Connected)Text("Connect embedded ADB first. Android pairing authorization is required once.",style=MaterialTheme.typography.bodySmall)
        }
        if(own || state.recoveryRequired) {
            Text(LearningExperience.title(state),style=MaterialTheme.typography.titleSmall)
            Text(state.detail,style=MaterialTheme.typography.bodySmall)
            if(state.active) {
                val progress=if(state.phase=="Verifying final profile")null else LearningExperience.progress(state)
                if(progress==null)LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
                Text("${state.completedWindows} checks complete${state.totalWindows?.let { " / $it" } ?: ""}${state.remainingSeconds?.let { " · ${it}s in this step" } ?: ""}")
                Button(onClick=vm::stopLearning) {Text("Stop & restore")}
            }
        }
        if(state.active && !own)Text("Another game is being tuned. Finish or stop that session first.")
        if(state.recoveryRequired)Button(enabled=connection is ConnectionState.Connected,onClick=vm::restoreLearning) {Text("Restore interrupted test")}
        TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "Hide diagnostics" else "Diagnostics")}
        if(advanced) {
            Text("Native HUD: $hud",style=MaterialTheme.typography.bodySmall)
            Text("60-second measurements, repeated controls, automatic settling and safety checks. Native GPU timing and actual refresh must be readable. No guessed GPU times or render dimensions.",style=MaterialTheme.typography.bodySmall)
            Text("Resolution tests remain unavailable until directly observed game dimensions can be collected. A successful property write is not a rendering measurement.",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={clipboard.setText(AnnotatedString("OQT ${io.github.openquesttuner.BuildConfig.VERSION_NAME}\nGame ${game.packageName}\nForeground ${vm.foregroundDiagnostics()}\nNative history ${vm.nativeHudDiagnostics()}\nHUD $hud\nPhase ${state.phase}: ${state.detail}\n"+events.filter { it.packageName==game.packageName }.takeLast(80).joinToString("\n") { "${it.timestampMs} ${it.message} requested=${it.profile} observedCpu=${it.observedCpu} observedGpu=${it.observedGpu}" }))}) {Text("Copy saved diagnostics")}
            events.filter { it.packageName==game.packageName }.takeLast(6).reversed().forEach { Text(it.message,style=MaterialTheme.typography.bodySmall) }
        }
    } }
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Reset this game's profile?")},text={Text("Removes the saved profile and unlocks Auto Tune for this game. Default settings will be used on its next launch.")},confirmButton={TextButton(onClick={reset=false;vm.resetLearningProfile(game)}) {Text("Reset profile")}},dismissButton={TextButton(onClick={reset=false}) {Text("Cancel")}})
}

@Composable fun LearningOverlaySettings(vm: MainViewModel) {
    val enabled by vm.learningOverlayEnabled.collectAsState()
    val status by vm.learningOverlayStatus.collectAsState()
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Immersive Auto Tune HUD — experimental",style=MaterialTheme.typography.titleSmall)
        Text("Automatically checks native overlay access during tuning. It does not open a floating Android panel. If unavailable, tuning continues without a HUD.",style=MaterialTheme.typography.bodySmall)
        Switch(checked=enabled,onCheckedChange=vm::setLearningOverlay)
        Text(status,style=MaterialTheme.typography.bodySmall)
    }
}
