package io.github.openquesttuner.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ThermalLevel

/** Avertissement « casque chaud », affiché à partir de l'état « modéré » (FR-032). */
@Composable
fun ThermalBanner(level: ThermalLevel, modifier: Modifier = Modifier) {
    if (!level.warning) return
    val accent = if (level >= ThermalLevel.SEVERE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    Surface(
        color = accent.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.thermal_warning, stringResource(level.labelRes())),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@StringRes
fun ThermalLevel.labelRes(): Int = when (this) {
    ThermalLevel.NONE -> R.string.thermal_none
    ThermalLevel.LIGHT -> R.string.thermal_light
    ThermalLevel.MODERATE -> R.string.thermal_moderate
    ThermalLevel.SEVERE -> R.string.thermal_severe
    ThermalLevel.CRITICAL -> R.string.thermal_critical
    ThermalLevel.EMERGENCY -> R.string.thermal_emergency
    ThermalLevel.SHUTDOWN -> R.string.thermal_shutdown
    ThermalLevel.UNKNOWN -> R.string.thermal_unknown
}
