package io.github.openquesttuner.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R

/** Pastille « Expérimental » : réglage non vérifié sur ce casque (FR-017, principe III). */
@Composable
fun ExperimentalBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.tertiary,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(R.string.experimental),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
