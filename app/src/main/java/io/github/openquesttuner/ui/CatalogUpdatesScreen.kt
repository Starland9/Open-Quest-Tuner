@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.openquesttuner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.openquesttuner.core.CatalogUpdateState
import io.github.openquesttuner.core.CommunityCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogUpdatesScreen(catalog: CommunityCatalog, state: CatalogUpdateState, sourceUrl: String, onSourceUrl: (String) -> Unit, onCheck: () -> Unit, onInstall: () -> Unit, onBack: () -> Unit) {
    var url by remember(sourceUrl) { mutableStateOf(sourceUrl) }
    Scaffold(topBar = { OqtTopAppBar(title = { Text("Profile Catalogue") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Updateable recommendations", style = MaterialTheme.typography.headlineSmall)
            Text("Community and curated game profiles can update independently from the APK. Downloads require HTTPS and older catalogue revisions cannot replace newer ones.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Installed catalogue", style = MaterialTheme.typography.titleMedium); Text("Revision ${catalog.catalogRevision} • ${catalog.profiles.size} profiles"); if (catalog.updatedAt.isNotBlank()) Text("Published ${catalog.updatedAt}")
            } }
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS catalogue JSON URL") }, singleLine = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onSourceUrl(url); onCheck() }, enabled = state !is CatalogUpdateState.Checking) { Text("Check for updates") }
                if (state is CatalogUpdateState.Available) Button(onClick = { onSourceUrl(url); onInstall() }) { Text("Install revision ${state.remoteRevision}") }
            }
            when (state) {
                CatalogUpdateState.Idle -> Text("No update check performed yet.")
                CatalogUpdateState.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { CircularProgressIndicator(Modifier.size(20.dp)); Text("Checking catalogue…") }
                is CatalogUpdateState.Available -> Text("Revision ${state.remoteRevision} is available (installed ${state.installedRevision}).")
                is CatalogUpdateState.Current -> Text("Catalogue is current at revision ${state.revision}.")
                is CatalogUpdateState.Updated -> Text("Updated successfully to revision ${state.revision}.")
                is CatalogUpdateState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()
            Text("Safety", style = MaterialTheme.typography.titleMedium)
            Text("Imported entries are schema-checked, package names are validated, ratings must be valid, and all installed recommendations still pass through the normal GameProfile validation and tuner safety path.")
        }
        }
    }
}
