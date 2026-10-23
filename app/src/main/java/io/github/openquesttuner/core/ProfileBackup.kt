package io.github.openquesttuner.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProfileBackup(
    val version: Int = 1,
    val profiles: Map<String, GameProfile> = emptyMap(),
    val libraries: Map<String, GameProfileLibrary> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    val globalEnabled: Boolean = false,
    val globalProfile: GameProfile = GameProfile(),
)

object ProfileBackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(backup: ProfileBackup): String = json.encodeToString(ProfileBackup.serializer(), backup)
    fun decode(text: String): ProfileBackup = json.decodeFromString(ProfileBackup.serializer(), text)
}

/** Schema for future downloadable/curated recommendations. No network trust is implied. */
@Serializable
data class CuratedProfileEntry(
    val packageName: String,
    val gameName: String,
    val profiles: Map<String, GameProfile>,
    val source: String = "bundled",
    val revision: Int = 1,
)
