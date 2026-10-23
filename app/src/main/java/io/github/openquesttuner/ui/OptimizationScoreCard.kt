package io.github.openquesttuner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.OptimizationScore

@Composable
fun OptimizationScoreCard(score: OptimizationScore) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Legacy setup / thermal score", style = MaterialTheme.typography.titleLarge); Text("${score.overall}/100", style = MaterialTheme.typography.headlineSmall) }
        LinearProgressIndicator(progress = { score.overall / 100f }, modifier = Modifier.fillMaxWidth())
        Text(score.status, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Profile application"); Text(score.application?.let { "$it%" } ?: "Learning") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Thermal stability"); Text(score.thermal?.let { "$it%" } ?: "Learning") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Observation"); Text("${score.observationMinutes} min") }
        Text("This legacy score measures override matching and thermals, not game FPS. Use measured learning for recommendations.", style = MaterialTheme.typography.bodySmall)
        Text(score.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
