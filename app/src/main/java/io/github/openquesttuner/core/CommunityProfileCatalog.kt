package io.github.openquesttuner.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CommunityProfile(
    val id: String,
    val packageName: String,
    val name: String,
    val profile: GameProfile,
    val questModels: Set<String> = emptySet(),
    val revision: Int = 1,
    val author: String = "Community",
    val description: String = "",
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val verified: Boolean = false,
)

@Serializable
data class CommunityCatalog(
    val schemaVersion: Int = 1,
    val catalogRevision: Int = 1,
    val updatedAt: String = "",
    val profiles: List<CommunityProfile> = emptyList(),
)

