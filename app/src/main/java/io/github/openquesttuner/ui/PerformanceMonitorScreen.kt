package io.github.openquesttuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.*
import io.github.openquesttuner.games.InstalledGame
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun PerformanceMonitorScreen(samples: List<PerformanceSample>, activePackage: String?, games: List<InstalledGame>,
    profiles: Map<String, GameProfile>, connected: Boolean, recording: Boolean, onRecord: (Boolean) -> Unit,
    onBaseline: (String) -> Unit, comparison: (String) -> String, onTune: (InstalledGame) -> Unit,
    onLearn: (InstalledGame) -> Unit, onTools: () -> Unit, onBack: () -> Unit, onClear: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<InstalledGame?>(null) }
    var clear by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val clipboard = LocalClipboardManager.current
    val grouped = remember(samples) { samples.groupBy { it.packageName } }
    val visible = games.filter { it.label.contains(query, true) }.sortedWith(
        performanceGameOrder(activePackage) { grouped[it]?.lastOrNull()?.timestampMs })
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Performance workspace", style = MaterialTheme.typography.titleLarge)
                Text(if (recording) "● Recording locally while you play" else "Per-game metrics and profile comparisons", style = MaterialTheme.typography.bodySmall, color = if (recording) VisionGreen else VisionMuted)
            }
            OutlinedButton(onClick = onTools) { Text("Headset tools") }
            Button(enabled = connected || recording, onClick = { onRecord(!recording) }) { Text(if (recording) "Stop logging" else "Log while gaming") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactGameSearch(query, { query = it; page = 0 }, Modifier.weight(1f))
            TextButton(onClick = { clear = true }) { Text("Clear logs") }
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("FPS and GPU time use fresh, app-attributed VrApi counters. A dash means unavailable. Android UI timing is kept separate.", style = MaterialTheme.typography.bodySmall, color = VisionMuted)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val geometry = PerformanceWorkspaceLayout.page(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
            val columns = geometry.columns
            val rowHeight = geometry.rowHeightDp
            val perPage = geometry.size
            val pages = ((visible.size + perPage - 1) / perPage).coerceAtLeast(1)
            val current = page.coerceIn(0, pages - 1)
            Column(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) Text("No matching installed games.")
                LazyVerticalGrid(GridCells.Fixed(columns), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible.drop(current * perPage).take(perPage), key = { it.packageName }) { game ->
                        val log = grouped[game.packageName].orEmpty()
                        val latest = log.lastOrNull()
                        val live = connected && game.packageName == activePackage && latest != null && now - latest.timestampMs in 0..8000
                        Card(Modifier.fillMaxWidth().heightIn(min = rowHeight.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text(game.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                    Text(if (live) "● LIVE" else "HISTORY", color = if (live) VisionGreen else VisionMuted, style = MaterialTheme.typography.labelSmall)
                                }
                                val vr = latest?.telemetry?.takeIf { it.isVr }
                                Text("${vr?.measuredFps?.let { "%.1f FPS".format(it) } ?: "— FPS"}   ·   ${vr?.appGpuMs?.let { "%.2f ms GPU".format(it) } ?: "— GPU"}   ·   ${vr?.staleFrames?.let { "$it stale" } ?: "— stale"}", style = MaterialTheme.typography.bodyMedium)
                                FpsHistory(log, Modifier.fillMaxWidth().height(28.dp))
                                Text(when {
                                    latest == null -> "No capture yet • launch this game to log it"
                                    !latest.telemetry.isVr -> "VR counters unavailable • Android UI data only"
                                    profiles[game.packageName]?.isActiveOn(latest.activeProperties) == false -> "Profile readback mismatch • inspect settings"
                                    latest.thermal.warning -> "Thermal pressure • consider a lighter profile"
                                    else -> "${log.count { it.telemetry.isVr }} VR observations • ${latest.thermal.name.lowercase()} thermal state"
                                }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VisionMuted)
                                Row { TextButton(onClick = { selected = game }) { Text("Log / compare") }; TextButton(onClick = { onTune(game) }) { Text("Tune profile") }; TextButton(onClick = { onLearn(game) }) { Text("Auto Learn") } }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = current > 0, onClick = { page = current - 1 }) { Text("Previous") }
                    Text("${visible.size} games • page ${current + 1} / $pages", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = current + 1 < pages, onClick = { page = current + 1 }) { Text("Next") }
                }
            }
        }
    }
    selected?.let { game ->
        val log = grouped[game.packageName].orEmpty()
        val comparisonText = remember(samples, revision, game.packageName) { comparison(game.packageName) }
        val assessment = PerformanceIntelligence.assess(log, profiles[game.packageName])
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(game.label) }, text = {
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Text(assessment.headline, style = MaterialTheme.typography.titleSmall) }
                item { Text(comparisonText, style = MaterialTheme.typography.bodySmall) }
                item { Row {
                    TextButton(enabled = log.count { it.telemetry.isVr } >= 10, onClick = { onBaseline(game.packageName); revision++ }) { Text("Save baseline") }
                    TextButton(enabled = log.isNotEmpty(), onClick = { clipboard.setText(AnnotatedString(performanceCsv(log))) }) { Text("Copy CSV") }
                } }
                item { Text("Suggestions", style = MaterialTheme.typography.titleSmall) }
                items(assessment.details) { Text(it, style = MaterialTheme.typography.bodySmall) }
                item { Text("Property verification", style = MaterialTheme.typography.titleSmall) }
                item { Text("These are system property readbacks. Rendering changes must be confirmed in the game with directly measured runtime stats (eye buffers, CPU/GPU levels).", style = MaterialTheme.typography.bodySmall) }
                val latest = log.lastOrNull()
                val expected = profiles[game.packageName]
                if (latest != null && expected != null) items(expected.toPropertyValues().entries.toList()) { (property, value) ->
                    Text("${property.key.substringAfter("debug.oculus.")}: requested ${value ?: "default"} · read ${latest.activeProperties[property.key] ?: "default"}", style = MaterialTheme.typography.bodySmall)
                }
                item { Text("Recent observations", style = MaterialTheme.typography.titleSmall) }
                items(log.takeLast(12).asReversed()) { sample ->
                    val t = sample.telemetry
                    SelectionContainer { Text("${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(sample.timestampMs))} · ${t.source} · ${t.measuredFps?.let { "%.1f FPS".format(it) } ?: "VR FPS —"} · ${sample.thermal.name.lowercase()}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear performance logs?") }, text = { Text("Saved observations and baselines will be removed. Profiles are retained.") }, confirmButton = { TextButton(onClick = { onClear(); clear = false }) { Text("Clear logs") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
@Composable private fun FpsHistory(log: List<PerformanceSample>, modifier: Modifier) {
    val values = log.takeLast(30).map { it.telemetry.measuredFps }
    Canvas(modifier) {
        drawLine(VisionBorder, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        val max = (log.takeLast(30).mapNotNull { it.telemetry.reportedRefreshRate }.maxOrNull() ?: 120).toFloat()
        values.zipWithNext().forEachIndexed { index, (a, b) -> if (a != null && b != null) drawLine(VisionBlue,
            Offset(size.width * index / (values.size - 1), size.height * (1 - a.toFloat().coerceIn(0f, max) / max)),
            Offset(size.width * (index + 1) / (values.size - 1), size.height * (1 - b.toFloat().coerceIn(0f, max) / max)), 2.dp.toPx())
        }
    }
}
private fun performanceCsv(log: List<PerformanceSample>): String = "timestamp_ms,package,source,vr_fps,gpu_ms,stale_frames,thermal,profile_properties\n" + log.joinToString("\n") {
    "${it.timestampMs},${it.packageName},${it.telemetry.source},${it.telemetry.measuredFps ?: ""},${it.telemetry.appGpuMs ?: ""},${it.telemetry.staleFrames ?: ""},${it.thermal.name},\"${it.activeProperties.entries.joinToString(";") { e -> "${e.key}=${e.value}" }.replace("\"", "\"\"")}\""
}
