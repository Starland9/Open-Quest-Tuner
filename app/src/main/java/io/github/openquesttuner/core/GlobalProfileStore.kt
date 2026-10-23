package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/** Optional headset-wide fallback used only when a game has no dedicated profile. */
class GlobalProfileStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = true }
    private val _profile = MutableStateFlow(GameProfile())
    val profile: StateFlow<GameProfile> = _profile.asStateFlow()
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun load() {
        if (!file.exists()) return
        runCatching {
            val saved = json.decodeFromString(SavedGlobalProfile.serializer(), file.readText())
            _profile.value = saved.profile
            _enabled.value = saved.enabled
        }
    }

    fun save(profile: GameProfile, enabled: Boolean = _enabled.value) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(SavedGlobalProfile.serializer(), SavedGlobalProfile(enabled, profile)))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save global profile" }
        }
        _profile.value = profile
        _enabled.value = enabled
    }

    fun setEnabled(enabled: Boolean) = save(_profile.value, enabled)
}

@kotlinx.serialization.Serializable
private data class SavedGlobalProfile(val enabled: Boolean = false, val profile: GameProfile = GameProfile())
