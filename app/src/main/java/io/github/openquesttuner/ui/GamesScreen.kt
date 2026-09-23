package io.github.openquesttuner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.games.InstalledGame
import io.github.openquesttuner.ui.components.GameIcon

/** Liste des jeux VR installés (FR-008) ; un appui ouvre le profil du jeu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    games: List<InstalledGame>,
    loading: Boolean,
    onOpenGame: (InstalledGame) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = actions)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when {
                loading && games.isEmpty() -> CircularProgressIndicator(Modifier.padding(32.dp))
                games.isEmpty() -> Text(
                    stringResource(R.string.games_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(games, key = { it.packageName }) { game ->
                        GameRow(game, onClick = { onOpenGame(game) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: InstalledGame, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Cible large : on vise aux contrôleurs ou aux mains (FR-030).
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameIcon(game.packageName, game.label)
        Column(Modifier.weight(1f)) {
            Text(game.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                game.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
