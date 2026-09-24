package io.github.openquesttuner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.R
import io.github.openquesttuner.core.ConnectionState
import io.github.openquesttuner.core.Diagnostic
import io.github.openquesttuner.core.EyeTexture
import io.github.openquesttuner.core.FoveationLevel
import io.github.openquesttuner.core.GameProfile
import io.github.openquesttuner.core.ProfileWarning
import io.github.openquesttuner.core.QuestModel
import io.github.openquesttuner.core.QuestProperty
import io.github.openquesttuner.core.RESOLUTION_STEPS
import io.github.openquesttuner.core.RateSelection
import io.github.openquesttuner.core.RefreshRatePolicy
import io.github.openquesttuner.core.ThermalLevel
import io.github.openquesttuner.games.InstalledGame
import io.github.openquesttuner.ui.components.ChoiceRow
import io.github.openquesttuner.ui.components.ThermalBanner

/**
 * Profil d'un jeu (US2) : six réglages, chacun sur « Par défaut du jeu » tant qu'on n'y touche pas
 * (FR-011, FR-012). Le brouillon n'est enregistré que par « Enregistrer » ou « Appliquer et lancer ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    game: InstalledGame,
    saved: GameProfile?,
    model: QuestModel,
    connectionState: ConnectionState,
    tuning: Boolean,
    onBack: () -> Unit,
    onSave: (GameProfile) -> Unit,
    onApplyAndLaunch: (GameProfile) -> Unit,
    onOpenConnection: () -> Unit,
    onDelete: () -> Unit,
    thermalLevel: ThermalLevel,
    diagnostic: Diagnostic?,
    refreshRates: List<Int>,
) {
    // Suit aussi le profil enregistré, qui peut arriver après l'ouverture de l'écran au démarrage,
    // et revient à « Par défaut du jeu » partout quand le profil est supprimé.
    var draft by remember(game.packageName, saved) { mutableStateOf(saved ?: GameProfile()) }
    // Palier auquel la résolution vient d'être abaissée pour la fréquence choisie (spec 003,
    // FR-006) : le message reste affiché jusqu'au changement suivant du profil.
    var loweredToStep by remember(game.packageName, saved) { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_text, game.label)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(game.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // FR-014 : suppression proposée seulement si un profil est enregistré.
                    if (saved != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_profile_title))
                        }
                    }
                },
            )
        },
        bottomBar = {
            ActionBar(
                connected = connectionState is ConnectionState.Connected,
                tuning = tuning,
                launchOnly = draft.isEmpty,
                onSave = { onSave(draft) },
                onApplyAndLaunch = { onApplyAndLaunch(draft) },
                onOpenConnection = onOpenConnection,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ThermalBanner(thermalLevel)
                ModelInfo(model)
                // FR-033 : seulement si l'état du casque est connu (connecté et diagnostic lu).
                if (connectionState is ConnectionState.Connected && diagnostic != null) {
                    ActiveIndicator(active = draft.isActiveOn(diagnostic.active))
                }
                Settings(
                    draft = draft,
                    model = model,
                    refreshRates = refreshRates,
                    loweredToStep = loweredToStep,
                    onChange = {
                        draft = it
                        loweredToStep = null
                    },
                    onRateSelected = { selection ->
                        draft = selection.profile
                        loweredToStep = selection.loweredToStep
                    },
                )
                Warnings(draft.warnings(model))
                // Spec 003, FR-009 : à fréquence élevée, tout le casque reste à cette fréquence.
                val highRate = draft.refreshRate?.takeIf(RefreshRatePolicy::isHigh)
                InfoCard(
                    listOfNotNull(
                        stringResource(R.string.settings_persist_info),
                        highRate?.let { stringResource(R.string.settings_persist_high_rate, it) },
                    ).joinToString("\n"),
                )
            }
        }
    }
}

@Composable
private fun ModelInfo(model: QuestModel) {
    // L'écran montre le profil enregistré, pas l'état actuel du casque : « Tout réinitialiser »
    // vide le casque mais garde les profils.
    Text(stringResource(R.string.profile_saved_hint), style = MaterialTheme.typography.bodyMedium)
    if (model == QuestModel.UNKNOWN) {
        NoticeCard(stringResource(R.string.profile_unknown_model), MaterialTheme.colorScheme.tertiary)
    } else {
        Text(
            stringResource(R.string.profile_model_detected, model.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Les réglages affichés sont-ils ceux qui sont actifs sur le casque ? (FR-033) */
@Composable
private fun ActiveIndicator(active: Boolean) {
    val color = if (active) Color(0xFF6DD58C) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(
            stringResource(if (active) R.string.profile_active else R.string.profile_not_applied),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

@Composable
private fun Settings(
    draft: GameProfile,
    model: QuestModel,
    refreshRates: List<Int>,
    loweredToStep: Int?,
    onChange: (GameProfile) -> Unit,
    onRateSelected: (RateSelection) -> Unit,
) {
    val default = stringResource(R.string.setting_default)
    fun experimental(vararg properties: QuestProperty) = properties.any { !model.isVerified(it) }

    // Fréquence enregistrée que cet écran ne propose pas (profil venu d'un autre casque, mise à
    // jour d'Horizon OS) : affichée telle quelle, indisponible, pour ne pas la perdre en silence.
    val unavailableRate = draft.refreshRate?.takeIf { it !in refreshRates }
    ChoiceRow(
        title = stringResource(R.string.setting_refresh_rate),
        options = listOf(null to default) +
            (refreshRates + listOfNotNull(unavailableRate)).map { it to stringResource(R.string.refresh_rate_value, it) },
        selected = draft.refreshRate,
        // Une résolution au-dessus du maximum de la fréquence est abaissée, et c'est dit (FR-006).
        onSelect = { onRateSelected(RefreshRatePolicy.selectRate(draft, it, model)) },
        experimental = experimental(QuestProperty.REFRESH_RATE),
        helpText = unavailableRate?.let { stringResource(R.string.refresh_rate_unavailable, it) },
        experimentalOptions = refreshRates.filter(RefreshRatePolicy::isExperimental).toSet(),
        disabledOptions = setOfNotNull(unavailableRate),
    )

    val defaultTexture = model.defaultEyeTexture
    val steps = RESOLUTION_STEPS.map { step ->
        val texture = EyeTexture.forStep(defaultTexture, step)
        texture to stringResource(R.string.resolution_step, step / 100.0, texture.width, texture.height)
    }
    // Taille qui ne correspond à aucun palier (fichier modifié à la main, autre modèle…) : on
    // l'affiche telle quelle pour ne pas la perdre en silence.
    val custom = draft.eyeTexture
        ?.takeIf { EyeTexture.stepOf(defaultTexture, it) == null }
        ?.let { listOf(it to stringResource(R.string.resolution_custom, it.width, it.height)) }
        .orEmpty()
    // Spec 003, FR-005 : à fréquence élevée, les paliers au-dessus du maximum sont indisponibles.
    val maxStep = RefreshRatePolicy.maxResolutionStep(draft.refreshRate)
    val unavailable = RefreshRatePolicy.unavailableSteps(draft.refreshRate, defaultTexture)
        .map { EyeTexture.forStep(defaultTexture, it) } +
        custom.map { it.first }.filterNot { RefreshRatePolicy.allowed(draft.refreshRate, it, defaultTexture) }
    ChoiceRow(
        title = stringResource(R.string.setting_resolution),
        options = listOf(null to default) + steps + custom,
        selected = draft.eyeTexture,
        onSelect = { onChange(draft.copy(eyeTexture = it)) },
        experimental = experimental(QuestProperty.TEXTURE_WIDTH, QuestProperty.TEXTURE_HEIGHT),
        helpText = listOfNotNull(
            stringResource(R.string.resolution_help, defaultTexture.width, defaultTexture.height),
            maxStep?.let { stringResource(R.string.resolution_limited_for_rate, draft.refreshRate ?: 0, it / 100.0) },
        ).joinToString("\n"),
        disabledOptions = unavailable.toSet(),
    )
    val lowered = loweredToStep
    val rate = draft.refreshRate
    if (lowered != null && rate != null) {
        // Dans la page plutôt qu'en snackbar : il reste lisible dans le casque (research.md R6).
        Text(
            stringResource(R.string.resolution_lowered, lowered / 100.0, rate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }

    ChoiceRow(
        title = stringResource(R.string.setting_cpu),
        options = listOf(null to default) + model.cpuLevels.map { it to it.toString() },
        selected = draft.cpuLevel,
        onSelect = { onChange(draft.copy(cpuLevel = it)) },
        experimental = experimental(QuestProperty.CPU_LEVEL),
        helpText = conditionalLevelHelp(draft.cpuLevel, model.alwaysAvailableCpuMax),
    )

    ChoiceRow(
        title = stringResource(R.string.setting_gpu),
        options = listOf(null to default) + model.gpuLevels.map { it to it.toString() },
        selected = draft.gpuLevel,
        onSelect = { onChange(draft.copy(gpuLevel = it)) },
        experimental = experimental(QuestProperty.GPU_LEVEL),
        helpText = conditionalLevelHelp(draft.gpuLevel, model.alwaysAvailableGpuMax),
    )

    ChoiceRow(
        title = stringResource(R.string.setting_foveation),
        options = listOf(null to default) + FoveationLevel.entries.map { it to stringResource(it.labelRes()) },
        selected = draft.foveationLevel,
        onSelect = { onChange(draft.copy(foveationLevel = it)) },
        experimental = experimental(QuestProperty.FOVEATION_LEVEL),
    )

    ChoiceRow(
        title = stringResource(R.string.setting_dynamic_foveation),
        options = listOf(
            null to default,
            true to stringResource(R.string.toggle_on),
            false to stringResource(R.string.toggle_off),
        ),
        selected = draft.dynamicFoveation,
        onSelect = { onChange(draft.copy(dynamicFoveation = it)) },
        experimental = experimental(QuestProperty.FOVEATION_DYNAMIC),
    )
}

/** Au-delà de ces niveaux, le casque ne les accorde que sous conditions (research.md R2). */
@Composable
private fun conditionalLevelHelp(level: Int?, alwaysAvailableMax: Int): String? =
    if (level != null && level > alwaysAvailableMax) stringResource(R.string.level_conditional, alwaysAvailableMax) else null

@Composable
private fun Warnings(warnings: Set<ProfileWarning>) {
    if (warnings.isEmpty()) return
    val lines = warnings.sorted().map { warning ->
        stringResource(
            when (warning) {
                ProfileWarning.HEAT -> R.string.warning_heat
                ProfileWarning.SMOOTHNESS -> R.string.warning_smoothness
                ProfileWarning.HIGH_REFRESH_RATE -> R.string.warning_high_refresh_rate
                ProfileWarning.HIGH_RATE_GAME_RESOLUTION -> R.string.warning_high_rate_game_resolution
            },
        )
    }
    NoticeCard(lines.joinToString("\n"), MaterialTheme.colorScheme.error)
}

@Composable
private fun NoticeCard(text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun ActionBar(
    connected: Boolean,
    tuning: Boolean,
    launchOnly: Boolean,
    onSave: () -> Unit,
    onApplyAndLaunch: () -> Unit,
    onOpenConnection: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!connected) {
                // FR-021 : lancement impossible hors connexion, mais l'enregistrement reste permis.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.launch_needs_connection),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenConnection, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.open_connection))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSave, enabled = !tuning, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.save))
                }
                Button(
                    onClick = onApplyAndLaunch,
                    enabled = connected && !tuning,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    if (tuning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        // Profil vide : rien à appliquer, le lancement remet tout par défaut.
                        Text(stringResource(if (launchOnly) R.string.launch else R.string.apply_and_launch))
                    }
                }
            }
        }
    }
}

@StringRes
private fun FoveationLevel.labelRes(): Int = when (this) {
    FoveationLevel.OFF -> R.string.foveation_off
    FoveationLevel.LOW -> R.string.foveation_low
    FoveationLevel.MEDIUM -> R.string.foveation_medium
    FoveationLevel.HIGH -> R.string.foveation_high
    FoveationLevel.HIGH_TOP -> R.string.foveation_high_top
}
