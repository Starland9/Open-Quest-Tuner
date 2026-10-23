package io.github.openquesttuner.persistence

import io.github.openquesttuner.core.*
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ProfileLibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile_library_v2", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _libraries = MutableStateFlow(loadLibraries())
    val libraries: StateFlow<Map<String, GameProfileLibrary>> = _libraries.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun saveNamed(packageName: String, name: String, profile: GameProfile, select: Boolean = true) {
        val clean = name.trim().take(32).ifBlank { "Custom" }
        val current = _libraries.value[packageName] ?: GameProfileLibrary()
        val updated = current.copy(
            selected = if (select) clean else current.selected,
            profiles = current.profiles + (clean to profile),
        )
        setLibrary(packageName, updated)
    }

    fun select(packageName: String, name: String): GameProfile? {
        val current = _libraries.value[packageName] ?: return null
        val profile = current.profiles[name] ?: return null
        setLibrary(packageName, current.copy(selected = name))
        return profile
    }

    fun deleteNamed(packageName: String, name: String) {
        val current = _libraries.value[packageName] ?: return
        val remaining = current.profiles - name
        val selected = if (current.selected == name) remaining.keys.firstOrNull() ?: "Custom" else current.selected
        setLibrary(packageName, current.copy(selected = selected, profiles = remaining))
    }

    fun clone(packageName: String, sourceName: String, newName: String) {
        val profile = _libraries.value[packageName]?.profiles?.get(sourceName) ?: return
        saveNamed(packageName, newName, profile, select = true)
    }

    fun replaceAll(libraries: Map<String, GameProfileLibrary>, favorites: Set<String>) {
        prefs.edit().putString(KEY_LIBRARIES, json.encodeToString(libraries)).putStringSet(KEY_FAVORITES, favorites).apply()
        _libraries.value = libraries
        _favorites.value = favorites
    }

    fun toggleFavorite(packageName: String) {
        val updated = _favorites.value.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }.toSet()
        prefs.edit().putStringSet(KEY_FAVORITES, updated).apply()
        _favorites.value = updated
    }

    private fun setLibrary(packageName: String, library: GameProfileLibrary) {
        val updated = _libraries.value + (packageName to library)
        prefs.edit().putString(KEY_LIBRARIES, json.encodeToString(updated)).apply()
        _libraries.value = updated
    }

    private fun loadLibraries(): Map<String, GameProfileLibrary> = runCatching {
        prefs.getString(KEY_LIBRARIES, null)?.let { json.decodeFromString<Map<String, GameProfileLibrary>>(it) }
    }.getOrNull().orEmpty()

    private companion object {
        const val KEY_LIBRARIES = "libraries"
        const val KEY_FAVORITES = "favorites"
    }
}
