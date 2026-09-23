package io.github.openquesttuner.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProfileStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "profiles.json")

    private fun store() = ProfileStore(file)

    private val fullProfile = GameProfile(
        refreshRate = 120,
        eyeTexture = EyeTexture(2016, 2112),
        cpuLevel = 4,
        gpuLevel = 4,
        foveationLevel = FoveationLevel.MEDIUM,
        dynamicFoveation = false,
    )

    @Test
    fun `fichier absent donne une liste vide sans rien creer`() = runTest {
        val store = store()
        store.load()
        assertEquals(emptyMap<String, GameProfile>(), store.profiles.value)
        assertFalse(file.exists())
    }

    @Test
    fun `aller-retour save puis load`() = runTest {
        val writer = store()
        writer.load()
        writer.save("com.beatgames.beatsaber", fullProfile)
        writer.save("com.example.othergame", GameProfile(refreshRate = 90))

        val reader = store()
        reader.load()
        assertEquals(
            mapOf(
                "com.beatgames.beatsaber" to fullProfile,
                "com.example.othergame" to GameProfile(refreshRate = 90),
            ),
            reader.profiles.value,
        )
    }

    @Test
    fun `le fichier suit le format de profiles-json`() = runTest {
        val store = store()
        store.load()
        store.save("com.example.othergame", GameProfile(refreshRate = 90, foveationLevel = FoveationLevel.HIGH_TOP))

        val text = file.readText()
        assertTrue(text, text.contains("\"version\": 1"))
        // Le niveau fovéal est stocké par son nom, et les champs par défaut ne sont pas écrits.
        assertTrue(text, text.contains("\"foveationLevel\": \"HIGH_TOP\""))
        assertFalse(text, text.contains("cpuLevel"))
        assertFalse(text, text.contains("null"))
    }

    @Test
    fun `enregistrer un profil vide supprime l'entree`() = runTest {
        val store = store()
        store.load()
        store.save("com.example.game", fullProfile)
        store.save("com.example.game", GameProfile())

        assertEquals(emptyMap<String, GameProfile>(), store.profiles.value)
        val reader = store()
        reader.load()
        assertEquals(emptyMap<String, GameProfile>(), reader.profiles.value)
    }

    @Test
    fun `delete supprime l'entree`() = runTest {
        val store = store()
        store.load()
        store.save("com.example.a", fullProfile)
        store.save("com.example.b", GameProfile(cpuLevel = 2))
        store.delete("com.example.a")

        assertEquals(mapOf("com.example.b" to GameProfile(cpuLevel = 2)), store.profiles.value)
        val reader = store()
        reader.load()
        assertEquals(store.profiles.value, reader.profiles.value)
    }

    @Test
    fun `un profil vide present dans le fichier est ignore`() = runTest {
        file.writeText("""{ "version": 1, "profiles": { "com.example.empty": {}, "com.example.game": { "gpuLevel": 3 } } }""")
        val store = store()
        store.load()
        assertEquals(mapOf("com.example.game" to GameProfile(gpuLevel = 3)), store.profiles.value)
    }

    @Test
    fun `les champs inconnus sont ignores`() = runTest {
        file.writeText(
            """{ "version": 1, "author": "x", "profiles": { "com.example.game": { "refreshRate": 90, "sharpening": 2 } } }""",
        )
        val store = store()
        store.load()
        assertEquals(mapOf("com.example.game" to GameProfile(refreshRate = 90)), store.profiles.value)
    }

    @Test
    fun `une cle qui n'est pas un nom de paquet est ignoree`() = runTest {
        file.writeText(
            """{ "version": 1, "profiles": {
                "not a package": { "refreshRate": 90 },
                "com.example.x'; reboot; '": { "refreshRate": 90 },
                "com.example.game": { "refreshRate": 72 }
            } }""",
        )
        val store = store()
        store.load()
        assertEquals(mapOf("com.example.game" to GameProfile(refreshRate = 72)), store.profiles.value)
    }

    @Test
    fun `une entree illisible est ignoree sans perdre les autres`() = runTest {
        file.writeText(
            """{ "version": 1, "profiles": {
                "com.example.future": { "foveationLevel": "ULTRA" },
                "com.example.game": { "cpuLevel": 1 }
            } }""",
        )
        val store = store()
        store.load()
        assertEquals(mapOf("com.example.game" to GameProfile(cpuLevel = 1)), store.profiles.value)
    }

    @Test
    fun `un fichier corrompu est mis de cote et le resultat est vide`() = runTest {
        val corrupt = """{ "version": 1, "profiles": { "com.example.game": """
        file.writeText(corrupt)
        val store = store()
        store.load()

        assertEquals(emptyMap<String, GameProfile>(), store.profiles.value)
        assertFalse(file.exists())
        val backups = folder.root.listFiles { f -> f.name.startsWith("profiles.json.corrupt-") }.orEmpty()
        assertEquals(1, backups.size)
        assertEquals(corrupt, backups.single().readText())

        // La copie mise de côté survit au prochain enregistrement.
        store.save("com.example.game", GameProfile(refreshRate = 90))
        assertTrue(backups.single().exists())
    }

    @Test
    fun `une racine qui n'est pas un objet est traitee comme corrompue`() = runTest {
        file.writeText("[1, 2, 3]")
        val store = store()
        store.load()
        assertEquals(emptyMap<String, GameProfile>(), store.profiles.value)
        assertFalse(file.exists())
    }

    @Test
    fun `une version future n'est pas reecrite tant qu'aucun save n'a eu lieu`() = runTest {
        val future = """{ "version": 2, "profiles": { "com.example.game": { "refreshRate": 90, "newField": 1 } }, "shared": true }"""
        file.writeText(future)
        val store = store()
        store.load()

        assertEquals(mapOf("com.example.game" to GameProfile(refreshRate = 90)), store.profiles.value)
        assertEquals(future, file.readText())

        store.save("com.example.other", GameProfile(cpuLevel = 2))
        val reader = store()
        reader.load()
        assertEquals(
            mapOf("com.example.game" to GameProfile(refreshRate = 90), "com.example.other" to GameProfile(cpuLevel = 2)),
            reader.profiles.value,
        )
    }

    @Test
    fun `aucun fichier temporaire ne reste apres un save`() = runTest {
        val tmp = File(folder.root, "profiles.json.tmp")
        tmp.writeText("reste d'une écriture interrompue")
        val store = store()
        store.load()
        store.save("com.example.game", fullProfile)

        assertTrue(file.exists())
        assertFalse(tmp.exists())
    }
}
