@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.openquesttuner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.*

/** Per-game Tune only. Shared theme, global editor and the other workspaces are unchanged. */
@Composable
internal fun TuneWorkspace(
    draft: GameProfile, model: QuestModel, refreshRates: List<Int>, loweredToStep: Int?,
    profileName: String, onNameChange: (String) -> Unit, enabled: Boolean,
    connected: Boolean, diagnostic: Diagnostic?, onChange: (GameProfile) -> Unit,
    onRateSelected: (RateSelection) -> Unit, onPerformance: () -> Unit,
    presets: @Composable () -> Unit, warnings: @Composable () -> Unit,
) {
    val rows = tuneReadbackRows(draft, connected, diagnostic)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp && LocalDensity.current.fontScale <= 1.2f
        val editor: @Composable (Modifier) -> Unit = { modifier ->
            Column(if (wide) modifier.verticalScroll(rememberScrollState()) else modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TunePanel("Display") {
                    val unavailable = draft.refreshRate?.takeIf { it !in refreshRates }
                    TuneChoices("Refresh rate", listOf(null to "Default") + (refreshRates + listOfNotNull(unavailable)).distinct().map {
                        it to "$it${if (RefreshRatePolicy.isExperimental(it)) "*" else ""}"
                    }, draft.refreshRate, enabled, setOfNotNull(unavailable), onSelect = {
                        onRateSelected(RefreshRatePolicy.selectRate(draft, it, model))
                    })
                    if (unavailable != null) TuneHint("Saved $unavailable Hz is unavailable on this headset; select a supported rate.")
                    if (refreshRates.any(RefreshRatePolicy::isExperimental)) TuneHint("* Experimental refresh rate; resolution limits still apply.")
                    ResolutionControl(draft, model, enabled, onChange)
                    if (loweredToStep != null && draft.refreshRate != null) TuneHint(stringResource(R.string.resolution_lowered, loweredToStep / 100.0, draft.refreshRate))
                }
                TunePanel("Performance") {
                    TuneChoices("CPU level", listOf(null to "Default") + model.cpuLevels.map { it to "$it" }, draft.cpuLevel, enabled, onSelect = { onChange(draft.copy(cpuLevel = it)) })
                    TuneChoices("GPU level", listOf(null to "Default") + model.gpuLevels.map { it to "$it" }, draft.gpuLevel, enabled, onSelect = { onChange(draft.copy(gpuLevel = it)) })
                    if ((draft.cpuLevel ?: -1) > model.alwaysAvailableCpuMax || (draft.gpuLevel ?: -1) > model.alwaysAvailableGpuMax)
                        TuneHint("Higher levels depend on headset conditions and may not be granted.")
                }
                TunePanel("Rendering") {
                    TuneChoices("Foveation", listOf(null to "Default") + FoveationLevel.entries.map { it to when (it) {
                        FoveationLevel.OFF -> "Off"; FoveationLevel.LOW -> "Low"; FoveationLevel.MEDIUM -> "Medium"
                        FoveationLevel.HIGH -> "High"; FoveationLevel.HIGH_TOP -> "High top"
                    } }, draft.foveationLevel, enabled, onSelect = { onChange(draft.copy(foveationLevel = it)) })
                    // Three states are essential: Default clears the override, Off explicitly writes zero.
                    TuneChoices("Dynamic FFR", listOf(null to "Default", false to "Off", true to "On"), draft.dynamicFoveation, enabled, onSelect = { onChange(draft.copy(dynamicFoveation = it)) })
                }
            }
        }
        val readback: @Composable (Modifier) -> Unit = { modifier ->
            Column(if (wide) modifier.verticalScroll(rememberScrollState()) else modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TunePanel("Requested / Read back") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Setting", Modifier.weight(1.25f), style = MaterialTheme.typography.labelMedium)
                        Text("Requested", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text("Device", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(20.dp))
                    }
                    HorizontalDivider(color = VisionBorder)
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.property.tuneLabel(), Modifier.weight(1.25f), style = MaterialTheme.typography.bodySmall)
                            Text(tuneValue(row.property, row.requested?.toString()), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(if (row.status == TuneReadbackStatus.UNAVAILABLE) "—" else tuneValue(row.property, row.device), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            when (row.status) {
                                TuneReadbackStatus.MATCH -> Icon(Icons.Default.Check, "Matches readback", Modifier.size(20.dp), tint = VisionGreen)
                                TuneReadbackStatus.DIFFERENT -> Text("≠", Modifier.width(20.dp), color = MaterialTheme.colorScheme.tertiary)
                                TuneReadbackStatus.UNAVAILABLE -> Text("—", Modifier.width(20.dp), color = VisionMuted)
                            }
                        }
                        HorizontalDivider(color = VisionBorder.copy(alpha = 0.5f))
                    }
                    TuneHint(when {
                        !connected -> "Connect ADB to read headset values. You can still edit and save."
                        rows.any { it.status == TuneReadbackStatus.UNAVAILABLE } -> "Some headset values are unavailable; a dash is not a verified match."
                        rows.all { it.status == TuneReadbackStatus.MATCH } -> "All overrides match the latest headset readback."
                        else -> "Requested values differ from the latest headset readback."
                    })
                }
                var help by remember { mutableStateOf(false) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onPerformance, shape = RoundedCornerShape(50)) { Text("Open Performance") }
                    OutlinedButton(onClick = { help = true }, shape = RoundedCornerShape(50)) { Text("Readback help") }
                }
                if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("What readback confirms") },
                    text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TuneHint("Readback confirms the override, not the game's visual result. Compare actual game performance after applying.")
                        TuneHint("Default lets the game choose. Reset clears this draft only; apply it to reset headset overrides.")
                        TuneHint("Experimental on this model: " + QuestProperty.entries.filterNot(model::isVerified).joinToString { it.tuneLabel() })
                    } }, confirmButton = { TextButton(onClick = { help = false }) { Text("Close") } })
                warnings()
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TunePanel("") {
                if (wide) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profile name", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(profileName, onNameChange, Modifier.width(170.dp), placeholder = { Text("Optional name") },
                        singleLine = true, enabled = enabled, textStyle = MaterialTheme.typography.bodySmall)
                    Box(Modifier.weight(1f)) { presets() }
                } else {
                    OutlinedTextField(profileName, onNameChange, Modifier.fillMaxWidth(), label = { Text("Profile name (optional)") }, singleLine = true, enabled = enabled)
                    presets()
                }
            }
            if (wide) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editor(Modifier.weight(0.57f).fillMaxHeight())
                readback(Modifier.weight(0.43f).fillMaxHeight())
            } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                editor(Modifier.fillMaxWidth())
                readback(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResolutionControl(draft: GameProfile, model: QuestModel, enabled: Boolean, onChange: (GameProfile) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val texture = draft.eyeTexture
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Texture", Modifier.width(88.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { onChange(draft.copy(eyeTexture = tuneResolutionStep(draft, model, -1))) }, enabled = enabled && texture != null,
            contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.width(40.dp)) { Text("−") }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(texture?.let { "${it.width} × ${it.height}" } ?: "Game default", maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem(text = { Text("Game default") }, onClick = { expanded = false; onChange(draft.copy(eyeTexture = null)) })
                RESOLUTION_STEPS.forEach { step ->
                    val value = EyeTexture.forStep(model.defaultEyeTexture, step)
                    DropdownMenuItem(text = { Text("$step% · ${value.width} × ${value.height}") },
                        enabled = RefreshRatePolicy.allowed(draft.refreshRate, value, model.defaultEyeTexture),
                        onClick = { expanded = false; onChange(draft.copy(eyeTexture = value)) })
                }
            }
        }
        OutlinedButton(onClick = { onChange(draft.copy(eyeTexture = tuneResolutionStep(draft, model, 1))) },
            enabled = enabled && tuneResolutionStep(draft, model, 1) != texture,
            contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.width(40.dp)) { Text("+") }
    }
}

@Composable
private fun <T> TuneChoices(label: String, options: List<Pair<T?, String>>, selected: T?, enabled: Boolean,
    disabled: Set<T> = emptySet(), onSelect: (T?) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.width(88.dp), style = MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, title) ->
                FilterChip(selected = selected == value, onClick = { onSelect(value) }, enabled = enabled && value !in disabled,
                    shape = RoundedCornerShape(50), label = { Text(title, style = MaterialTheme.typography.labelMedium) })
            }
        }
    }
}

@Composable private fun TunePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, VisionBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
@Composable private fun TuneHint(text: String) { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
private fun QuestProperty.tuneLabel(): String = when (this) {
    QuestProperty.REFRESH_RATE -> "Refresh rate"; QuestProperty.TEXTURE_WIDTH -> "Texture width"; QuestProperty.TEXTURE_HEIGHT -> "Texture height"
    QuestProperty.CPU_LEVEL -> "CPU level"; QuestProperty.GPU_LEVEL -> "GPU level"; QuestProperty.FOVEATION_LEVEL -> "Foveation"
    QuestProperty.FOVEATION_DYNAMIC -> "Dynamic FFR"
}
private fun tuneValue(property: QuestProperty, value: String?): String = when {
    value == null -> "Default"
    property == QuestProperty.REFRESH_RATE -> "$value Hz"
    property == QuestProperty.FOVEATION_DYNAMIC -> when (value) { "1" -> "On"; "0" -> "Off"; else -> value }
    else -> value
}

@Composable
internal fun TuneActionBar(connected: Boolean, tuning: Boolean, draft: GameProfile, saved: GameProfile?,
    readbackMatches: Boolean, onReset: () -> Unit, onSave: () -> Unit, onApplyAndLaunch: () -> Unit, onOpenConnection: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when { tuning -> "Applying…"; draft != (saved ?: GameProfile()) -> "Unsaved changes"; readbackMatches -> "Readback matches"; else -> "Not applied / unverified" },
                Modifier.weight(1f).widthIn(min = 110.dp).heightIn(min = 40.dp).wrapContentHeight(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall, color = if (readbackMatches) VisionGreen else VisionMuted)
            if (!connected) OutlinedButton(onClick = onOpenConnection, enabled = !tuning) { Text("Connect ADB") }
            OutlinedButton(onClick = onReset, enabled = !tuning) { Text("Reset") }
            OutlinedButton(onClick = onSave, enabled = !tuning) { Text("Save profile") }
            Button(onClick = onApplyAndLaunch, enabled = connected && !tuning) {
                if (tuning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (draft.isEmpty) "Launch" else "Apply & launch")
            }
        }
    }
}
