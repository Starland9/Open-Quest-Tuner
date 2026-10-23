package io.github.openquesttuner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import io.github.openquesttuner.ui.components.GameCover
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.*
import io.github.openquesttuner.games.InstalledGame
import io.github.openquesttuner.ui.components.GameIcon

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GamesScreen(
    games: List<InstalledGame>, totalGames: Int, loading: Boolean, query: String,
    onQueryChange: (String) -> Unit, profiles: Map<String, GameProfile>, favorites: Set<String>,
    favoritesOnly: Boolean, profileLibraries: Map<String, GameProfileLibrary>,
    gameFilter: GameFilter, gameSort: GameSort, onFilter: (GameFilter) -> Unit, onSort: (GameSort) -> Unit,
    autoApply: Boolean, onAutoApply: (Boolean) -> Unit, activeGamePackage: String?, questModel: QuestModel, displayRefreshRate: Int?,
    onToggleFavorite: (String) -> Unit, onToggleFavoritesOnly: () -> Unit,
    connected: Boolean, tuningPackage: String?, onOpenGame: (InstalledGame) -> Unit,
    onLaunch: (InstalledGame) -> Unit, onRefresh: () -> Unit, onOpenConnection: () -> Unit,
    thermalLevel: ThermalLevel, globalProfileEnabled: Boolean, onOpenGlobalProfile: () -> Unit,
    onExportProfiles: () -> String, onImportProfiles: (String) -> Unit, onOpenPerformanceMonitor: () -> Unit, onOpenCatalogUpdates: () -> Unit, onOpenSettings: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    autoTunedPackages: Set<String> = emptySet(),
) {
    var transfer by remember { mutableStateOf<String?>(null) }
    var backup by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val layoutPreferences = remember(context) { context.getSharedPreferences("launcher_layout", android.content.Context.MODE_PRIVATE) }
    var tileSize by remember { mutableStateOf(LauncherTileSize.fromStored(layoutPreferences.getString("tile_size", null))) }
    var onlineCovers by remember { mutableStateOf(layoutPreferences.getBoolean("online_covers", true)) }
    val clipboard = LocalClipboardManager.current
    val gridState = rememberLazyGridState()
    val headerActions: @Composable () -> Unit = {
        IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, "Refresh games", Modifier.size(20.dp)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Library options", Modifier.size(20.dp)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                LauncherTileSize.entries.forEach { size ->
                    DropdownMenuItem(text = { Text("${if (tileSize == size) "✓ " else ""}${size.label} game tiles") }, onClick = {
                        tileSize = size; layoutPreferences.edit().putString("tile_size", size.name).apply(); menu = false
                    })
                }
                DropdownMenuItem(text = { Text("${if (onlineCovers) "✓ " else ""}GitHub cover artwork") }, onClick = {
                    onlineCovers = !onlineCovers; layoutPreferences.edit().putBoolean("online_covers", onlineCovers).apply(); menu = false
                })
                DropdownMenuItem(text = { Text("Headset defaults: ${if (globalProfileEnabled) "enabled" else "disabled"}") }, onClick = { menu = false; onOpenGlobalProfile() })
                DropdownMenuItem(text = { Text("Export profiles") }, onClick = { menu = false; backup = onExportProfiles(); transfer = "Export" })
                DropdownMenuItem(text = { Text("Import profiles") }, onClick = { menu = false; backup = ""; transfer = "Import" })
                GameSort.entries.forEach { sort -> DropdownMenuItem(text = { Text("${if (sort == gameSort) "✓ " else ""}Sort by ${if (sort == GameSort.NAME) "name" else "profile"}") }, onClick = { menu = false; onSort(sort) }) }
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
            val gridColumns = LauncherGridLayout.columns(maxWidth.value, tileSize)
            val narrow = maxWidth < 600.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(if (narrow) Modifier.weight(1f) else Modifier.width(132.dp)) {
                        Text("Game library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${games.size} shown · $totalGames installed", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!narrow) CompactGameSearch(query, onQueryChange, Modifier.weight(1f))
                    headerActions()
                }
                if (narrow) CompactGameSearch(query, onQueryChange, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GameFilter.entries.forEach { filter -> FilterChip(selected = filter == gameFilter, onClick = { onFilter(filter) }, label = { Text(filter.shortLabel()) }) }
                    }
                    Text("Auto apply", style = MaterialTheme.typography.labelMedium)
                    Switch(autoApply, onAutoApply)
                }
                if (!connected) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Connect Quest to apply and launch.", modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenConnection) { Text("Connect") }
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (games.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (loading) "Scanning installed games…" else "No games match this view", style = MaterialTheme.typography.titleMedium)
                        Text(if (query.isNotBlank()) "Try a different search or clear the filters." else "Refresh to scan installed applications.")
                        if (!loading) OutlinedButton(onClick = { onQueryChange(""); onFilter(GameFilter.ALL); onRefresh() }) { Text("Reset & refresh") }
                    }
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns), state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(games, key = { it.packageName }) { game ->
                        LauncherGameTile(game, profiles[game.packageName], activeGamePackage == game.packageName, game.packageName in favorites,
                            tileSize, onlineCovers, connected && tuningPackage == null, tuningPackage == game.packageName,
                            { onOpenGame(game) }, { if (connected) onLaunch(game) else onOpenConnection() }, { onToggleFavorite(game.packageName) },
                            launchAvailable = !connected || tuningPackage == null, learned = game.packageName in autoTunedPackages)
                    }
                }
            }
        }
    }
    transfer?.let { mode -> AlertDialog(
        onDismissRequest = { transfer = null }, title = { Text("$mode profiles") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (mode == "Import") "Paste an Open Quest Tuner backup. Matching profiles and headset defaults will be updated." else "Copy this backup and save it somewhere safe.")
            OutlinedTextField(backup, { backup = it }, readOnly = mode == "Export", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), label = { Text("Profile backup JSON") })
        } },
        confirmButton = { Button(onClick = { if (mode == "Import") onImportProfiles(backup) else clipboard.setText(AnnotatedString(backup)); transfer = null }, enabled = backup.isNotBlank()) { Text(if (mode == "Import") "Import backup" else "Copy backup") } },
        dismissButton = { TextButton(onClick = { transfer = null }) { Text("Cancel") } },
    ) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherGameTile(
    game: InstalledGame, profile: GameProfile?, active: Boolean, favorite: Boolean,
    tileSize: LauncherTileSize, onlineCovers: Boolean, canLaunch: Boolean, launching: Boolean,
    open: () -> Unit, launch: () -> Unit, toggleFavorite: () -> Unit, launchAvailable: Boolean, learned: Boolean,
) {
    var options by remember(game.packageName) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth().combinedClickable(
            enabled = launchAvailable, role = Role.Button,
            onClickLabel = if(learned) "Launch ${game.label}" else "Choose Tune or Launch for ${game.label}", onClick = { if(learned)launch() else options = true },
            onLongClickLabel = "Options for ${game.label}", onLongClick = { options = true },
        ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (active) VisionGreen else VisionBorder),
    ) {
        Column(Modifier.padding(LauncherGridLayout.TILE_PADDING_DP.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val artworkWidth = minOf(tileSize.artworkWidthDp.dp, maxWidth)
                Box(Modifier.width(artworkWidth).aspectRatio(16f / 9f).align(Alignment.TopCenter)) {
                    GameCover(game.packageName, game.label, onlineCovers, Modifier.fillMaxSize())
                    if (favorite) Surface(Modifier.align(Alignment.TopStart).padding(3.dp), shape = MaterialTheme.shapes.small, color = VisionPanel.copy(alpha = .9f)) {
                        Icon(Icons.Default.Star, "Favourite", Modifier.padding(3.dp).size(13.dp), tint = VisionBlue)
                    }
                    if (active || learned || profile != null && profile!=GameProfile()) Surface(Modifier.align(Alignment.BottomStart).padding(3.dp), shape = MaterialTheme.shapes.small, color = VisionPanel.copy(alpha = .9f)) {
                        Text(if (active) "Running" else if(learned) "Tuned" else "Custom", Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 9.sp, lineHeight = 11.sp, color = if (active) VisionGreen else VisionBlue)
                    }
                    if (launching) CircularProgressIndicator(Modifier.align(Alignment.Center).size(22.dp), strokeWidth = 2.dp)
                }

            }
            Text(game.label, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
    if (options) AlertDialog(onDismissRequest = { options = false }, title = { Text(game.label) },
        text = { Column { Text(profile?.compactSummary() ?: "Using game defaults"); TextButton(onClick = toggleFavorite) { Text(if (favorite) "Remove favourite" else "Add favourite") } } },
        confirmButton = { Button(enabled = canLaunch, onClick = { options = false; launch() }) { Text("Launch") } },
        dismissButton = { OutlinedButton(onClick = { options = false; open() }) { Text("Tune / profiles") } })

}

private fun GameFilter.shortLabel() = when (this) { GameFilter.ALL -> "All games"; GameFilter.FAVOURITES -> "Favourites"; GameFilter.OPTIMISED -> "Profile saved"; GameFilter.CUSTOM -> "Multiple profiles"; GameFilter.NO_PROFILE -> "No profile" }
private fun GameProfile.compactSummary(): String = listOfNotNull(refreshRate?.let { "$it Hz" }, eyeTexture?.let { "${it.width} × ${it.height}" }, gpuLevel?.let { "GPU $it" }, cpuLevel?.let { "CPU $it" }).joinToString(" · ").ifBlank { "Custom profile" }
