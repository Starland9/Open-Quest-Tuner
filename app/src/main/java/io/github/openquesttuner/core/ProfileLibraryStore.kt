package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class GameProfileLibrary(
    val selected: String = "Custom",
    val profiles: Map<String, GameProfile> = emptyMap(),
)

/** Multiple named profiles, favourites and library preferences. Kept separate from profiles.json
 * so existing OpenQuestTuner installations remain backwards compatible. */
