package io.github.openquesttuner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Secondary page header beneath the shared brand bar. Text may grow with accessibility settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OqtTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() } },
        modifier = modifier, navigationIcon = navigationIcon, actions = actions,
        expandedHeight = 48.dp, windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

@Composable
fun CompactGameSearch(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = 40.dp), shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(query, onQueryChange, Modifier.weight(1f).semantics { contentDescription = "Search installed games" },
                singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { input -> Box { if (query.isEmpty()) Text("Search games…", color = MaterialTheme.colorScheme.onSurfaceVariant); input() } })
        }
    }
}
