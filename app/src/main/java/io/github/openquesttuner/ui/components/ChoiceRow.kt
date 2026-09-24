package io.github.openquesttuner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Un réglage : un titre, puis une rangée de puces dont une seule est sélectionnée.
 * L'option de valeur `null` représente « Par défaut du jeu » (FR-012) ; c'est à l'appelant de la
 * placer en tête de [options].
 *
 * [experimentalOptions] : puces marquées « Expérimental » une à une, en plus du titre (spec 003,
 * FR-003). [disabledOptions] : puces visibles mais indisponibles ; une option indisponible mais
 * sélectionnée reste affichée comme sélectionnée (spec 003, FR-005).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T?, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    experimental: Boolean,
    modifier: Modifier = Modifier,
    helpText: String? = null,
    enabled: Boolean = true,
    experimentalOptions: Set<T> = emptySet(),
    disabledOptions: Set<T> = emptySet(),
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (experimental) ExperimentalBadge()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = {
                        if (value != null && value in experimentalOptions) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label)
                                ExperimentalBadge()
                            }
                        } else {
                            Text(label)
                        }
                    },
                    enabled = enabled && (value == null || value !in disabledOptions),
                    // Cible d'au moins 48dp : on vise aux contrôleurs ou aux mains (FR-030).
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (helpText != null) {
            Text(
                helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
