package io.github.openquesttuner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.AdaptiveSuggestion
import io.github.openquesttuner.core.GameOptimizationHistory
import io.github.openquesttuner.games.InstalledGame

@Composable
fun AdaptiveHistoryCard(history: GameOptimizationHistory?, suggestion: AdaptiveSuggestion?, onApply: () -> Unit, onClear: () -> Unit) {
    Card { Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Learning summary / override history", style = MaterialTheme.typography.titleLarge)
        if (history == null) Text("No optimisation history yet. Play this game with monitoring enabled and Open Quest Tuner will learn from verified headset signals.")
        else {
            Text("Observed ${(history.totalObservedMs / 60000.0).let { String.format("%.1f", it) }} min • ${history.totalSamples} samples")
            Text("Override matches ${history.stabilityPercent}% • Thermal pressure ${history.thermalPressurePercent}%")
            suggestion?.let { Text(it.headline, style = MaterialTheme.typography.titleMedium); Text(it.reason); if (it.recommendedGoal != null) Button(onClick = onApply) { Text("Apply ${it.recommendedGoal.label}") } }
            TextButton(onClick = onClear) { Text("Clear learning history") }
        }
    } }
}
