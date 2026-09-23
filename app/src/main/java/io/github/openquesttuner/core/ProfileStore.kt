package io.github.openquesttuner.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/**
 * Profils par jeu, dans le fichier privé `profiles.json` (contracts/profiles-json.md).
 *
 * Un fichier corrompu n'est jamais écrasé : il est mis de côté et l'appli repart à vide. Un fichier
 * d'une version future est lu au mieux et n'est réécrit qu'au premier enregistrement.
 */
class ProfileStore(private val file: File) {

    private val _profiles = MutableStateFlow<Map<String, GameProfile>>(emptyMap())
    val profiles: StateFlow<Map<String, GameProfile>> = _profiles.asStateFlow()

    private val lock = Mutex()

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            _profiles.value = if (file.exists()) read() else emptyMap()
        }
    }

    /** Un profil vide supprime l'entrée (FR-015). */
    suspend fun save(packageName: String, profile: GameProfile) {
        require(ShellCommand.isValidPackageName(packageName)) { "Nom de paquet invalide : $packageName" }
        update { profiles -> if (profile.isEmpty) profiles - packageName else profiles + (packageName to profile) }
    }

    suspend fun delete(packageName: String) {
        update { profiles -> profiles - packageName }
    }

    /** @throws IOException si l'écriture échoue ; les profils en mémoire restent alors inchangés. */
    private suspend fun update(change: (Map<String, GameProfile>) -> Map<String, GameProfile>) =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val updated = change(_profiles.value)
                write(updated)
                _profiles.value = updated
            }
        }

    private fun read(): Map<String, GameProfile> {
        val entries = try {
            json.parseToJsonElement(file.readText()).jsonObject[KEY_PROFILES]?.jsonObject ?: JsonObject(emptyMap())
        } catch (e: IllegalArgumentException) {
            // JSON invalide, ou racine / « profiles » qui n'est pas un objet.
            setAsideCorruptFile()
            return emptyMap()
        } catch (e: IOException) {
            setAsideCorruptFile()
            return emptyMap()
        }
        return buildMap {
            for ((packageName, element) in entries) {
                if (!ShellCommand.isValidPackageName(packageName)) continue
                // Une entrée illisible (valeur d'une version future…) est ignorée sans perdre les autres.
                val profile = try {
                    json.decodeFromJsonElement(GameProfile.serializer(), element)
                } catch (e: IllegalArgumentException) {
                    continue
                }
                if (!profile.isEmpty) put(packageName, profile)
            }
        }
    }

    private fun setAsideCorruptFile() {
        file.renameTo(File(file.path + ".corrupt-" + System.currentTimeMillis()))
    }

    /** Écriture atomique : fichier temporaire, puis renommage sur le fichier final. */
    private fun write(profiles: Map<String, GameProfile>) {
        val content = buildJsonObject {
            put(KEY_VERSION, FORMAT_VERSION)
            put(
                KEY_PROFILES,
                JsonObject(profiles.toSortedMap().mapValues { (_, p) -> json.encodeToJsonElement(GameProfile.serializer(), p) }),
            )
        }
        val tmp = File(file.path + ".tmp")
        try {
            tmp.writeText(json.encodeToString(JsonObject.serializer(), content))
            if (!tmp.renameTo(file)) throw IOException("Renommage de ${tmp.name} impossible")
        } finally {
            tmp.delete()
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_PROFILES = "profiles"

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            prettyPrint = true
        }
    }
}
