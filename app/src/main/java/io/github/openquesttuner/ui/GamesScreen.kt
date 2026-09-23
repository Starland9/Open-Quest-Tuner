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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ThermalLevel
import io.github.openquesttuner.games.InstalledGame
import io.github.openquesttuner.ui.components.GameIcon
import io.github.openquesttuner.ui.components.ThermalBanner

/**
 * Liste des jeux VR installés (FR-008 à FR-010) : recherche, puce « Profil », lancement direct.
 * Un appui sur la ligne ouvre le profil du jeu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    games: List<InstalledGame>,
    totalGames: Int,
    loading: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    profiledPackages: Set<String>,
    connected: Boolean,
    tuningPackage: String?,
    onOpenGame: (InstalledGame) -> Unit,
    onLaunch: (InstalledGame) -> Unit,
    onRefresh: () -> Unit,
    onOpenConnection: () -> Unit,
    thermalLevel: ThermalLevel,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    actions()
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                if (loading && totalGames > 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                ThermalBanner(thermalLevel, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (!connected) DisconnectedBanner(onOpenConnection)
                if (totalGames > 0) SearchField(query, onQueryChange)
                when {
                    loading && totalGames == 0 -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.padding(32.dp))
                    }
                    totalGames == 0 -> EmptyState(stringResource(R.string.games_empty))
                    games.isEmpty() -> EmptyState(stringResource(R.string.games_no_match, query.trim()))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(games, key = { it.packageName }) { game ->
                            GameRow(
                                game = game,
                                hasProfile = game.packageName in profiledPackages,
                                canLaunch = connected && tuningPackage == null,
                                launching = tuningPackage == game.packageName,
                                onClick = { onOpenGame(game) },
                                onLaunch = { onLaunch(game) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** FR-021 : hors connexion, les lancements sont désactivés et l'accès à la connexion est direct. */
@Composable
private fun DisconnectedBanner(onOpenConnection: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.games_launch_disabled),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenConnection, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.games_connect))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_games)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
private fun GameRow(
    game: InstalledGame,
    hasProfile: Boolean,
    canLaunch: Boolean,
    launching: Boolean,
    onClick: () -> Unit,
    onLaunch: () -> Unit,
) {
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
        if (hasProfile) ProfileBadge()
        FilledTonalButton(onClick = onLaunch, enabled = canLaunch, modifier = Modifier.heightIn(min = 48.dp)) {
            if (launching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.launch))
            }
        }
    }
}

/** Puce « Profil » : le jeu a un profil non vide (FR-008, FR-015). */
@Composable
private fun ProfileBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(R.string.profile_badge),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
