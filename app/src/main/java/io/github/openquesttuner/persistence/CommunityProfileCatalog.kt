package io.github.openquesttuner.persistence

import io.github.openquesttuner.core.*
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CommunityProfileCatalogStore(context: Context) {
    private val prefs = context.getSharedPreferences("community_catalog_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _catalog = MutableStateFlow(load())
    val catalog: StateFlow<CommunityCatalog> = _catalog.asStateFlow()

    fun compatible(packageName: String, model: QuestModel): List<CommunityProfile> = _catalog.value.profiles
        .filter { it.packageName == packageName && (it.questModels.isEmpty() || model.name in it.questModels) }
        .sortedWith(compareByDescending<CommunityProfile> { it.verified }.thenByDescending { it.rating }.thenByDescending { it.revision })

    fun decodeAndValidate(text: String): CommunityCatalog {
        val parsed = json.decodeFromString<CommunityCatalog>(text)
        require(parsed.schemaVersion == 1) { "Unsupported catalog schema ${parsed.schemaVersion}" }
        require(parsed.profiles.all { ShellCommand.isValidPackageName(it.packageName) && it.id.isNotBlank() && it.name.isNotBlank() && it.revision > 0 && it.rating in 0.0..5.0 }) { "Catalog contains invalid entries" }
        return parsed
    }

    fun import(text: String): Result<CommunityCatalog> = runCatching {
        val parsed = decodeAndValidate(text)
        val current = _catalog.value
        require(parsed.catalogRevision >= current.catalogRevision) { "Catalog revision is older than the installed catalog" }
        prefs.edit().putString(KEY, json.encodeToString(parsed)).apply()
        _catalog.value = parsed
        parsed
    }

    fun export(): String = json.encodeToString(_catalog.value)

    private fun load(): CommunityCatalog = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString<CommunityCatalog>(it) }
    }.getOrNull() ?: CommunityCatalog()

    private companion object { const val KEY = "catalog" }
}
