package io.github.openquesttuner.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProfileTest {

    private val q3 = QuestModel.QUEST_3
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // --- Paliers de résolution

    @Test
    fun `palier 120 sur Quest 3`() {
        assertEquals(EyeTexture(2016, 2112), EyeTexture.forStep(EyeTexture(1680, 1760), 120))
    }

    @Test
    fun `chaque dimension est arrondie au multiple de 8 le plus proche`() {
        // 1440 × 0,7 = 1008 (déjà multiple de 8) ; 1584 × 0,7 = 1108,8 → 1112.
        val texture = EyeTexture.forStep(EyeTexture(1440, 1584), 70)
        assertEquals(EyeTexture(1008, 1112), texture)
        assertEquals(0, texture.width % 8)
        assertEquals(0, texture.height % 8)
    }

    @Test
    fun `paliers de 70 a 150 par pas de 10`() {
        assertEquals(listOf(70, 80, 90, 100, 110, 120, 130, 140, 150), RESOLUTION_STEPS)
    }

    @Test
    fun `stepOf retrouve le palier ou renvoie null pour une taille personnalisee`() {
        val default = q3.defaultEyeTexture
        assertEquals(120, EyeTexture.stepOf(default, EyeTexture(2016, 2112)))
        assertEquals(100, EyeTexture.stepOf(default, default))
        assertNull(EyeTexture.stepOf(default, EyeTexture(2000, 2000)))
    }

    // --- Profil

    @Test
    fun `un profil sans reglage est vide`() {
        assertTrue(GameProfile().isEmpty)
        assertFalse(GameProfile(refreshRate = 90).isEmpty)
    }

    @Test
    fun `toPropertyValues couvre les 7 proprietes dans l'ordre de l'enum`() {
        val values = GameProfile().toPropertyValues()
        assertEquals(QuestProperty.entries.toList(), values.keys.toList())
        assertTrue(values.values.all { it == null })
    }

    @Test
    fun `toPropertyValues encode chaque reglage en entier`() {
        val profile = GameProfile(
            refreshRate = 120,
            eyeTexture = EyeTexture(2016, 2112),
            cpuLevel = 3,
            gpuLevel = 2,
            foveationLevel = FoveationLevel.HIGH_TOP,
            dynamicFoveation = true,
        )
        val values = profile.toPropertyValues()
        assertEquals(120, values[QuestProperty.REFRESH_RATE])
        assertEquals(2016, values[QuestProperty.TEXTURE_WIDTH])
        assertEquals(2112, values[QuestProperty.TEXTURE_HEIGHT])
        assertEquals(3, values[QuestProperty.CPU_LEVEL])
        assertEquals(2, values[QuestProperty.GPU_LEVEL])
        assertEquals(4, values[QuestProperty.FOVEATION_LEVEL])
        assertEquals(1, values[QuestProperty.FOVEATION_DYNAMIC])
        assertEquals(0, GameProfile(dynamicFoveation = false).toPropertyValues()[QuestProperty.FOVEATION_DYNAMIC])
    }

    @Test
    fun `codes du rendu foveal`() {
        assertEquals(listOf(0, 1, 2, 3, 4), FoveationLevel.entries.map { it.code })
        assertEquals(FoveationLevel.OFF, FoveationLevel.entries.first())
    }

    // --- Validation contre le modèle de casque

    @Test
    fun `un profil vide ou dans les plages est valide`() {
        assertTrue(GameProfile().validateFor(q3).isEmpty())
        assertTrue(
            GameProfile(refreshRate = 120, eyeTexture = EyeTexture(2520, 2640), cpuLevel = 4, gpuLevel = 5)
                .validateFor(q3).isEmpty(),
        )
    }

    @Test
    fun `le Quest Pro refuse 120 Hz`() {
        val violations = GameProfile(refreshRate = 120).validateFor(QuestModel.QUEST_PRO)
        assertEquals(listOf(ProfileViolation(QuestProperty.REFRESH_RATE, 120)), violations)
    }

    @Test
    fun `le Quest 3 refuse les niveaux hors plage et une texture hors bornes`() {
        assertEquals(
            listOf(ProfileViolation(QuestProperty.CPU_LEVEL, 5)),
            GameProfile(cpuLevel = 5).validateFor(q3),
        )
        assertEquals(
            listOf(ProfileViolation(QuestProperty.GPU_LEVEL, 6)),
            GameProfile(gpuLevel = 6).validateFor(q3),
        )
        assertEquals(
            listOf(
                ProfileViolation(QuestProperty.TEXTURE_WIDTH, 4000),
                ProfileViolation(QuestProperty.TEXTURE_HEIGHT, 4000),
            ),
            GameProfile(eyeTexture = EyeTexture(4000, 4000)).validateFor(q3),
        )
        assertEquals(
            listOf(ProfileViolation(QuestProperty.TEXTURE_WIDTH, 511)),
            GameProfile(eyeTexture = EyeTexture(511, 3072)).validateFor(q3),
        )
    }

    // --- Fréquences au-delà de 120 Hz (spec 003)

    private val quest3Screen = (72..207).toSet()

    @Test
    fun `une frequence elevee declaree par l'ecran est valide`() {
        assertTrue(GameProfile(refreshRate = 144).validateFor(q3, quest3Screen).isEmpty())
    }

    @Test
    fun `une frequence elevee non declaree est refusee`() {
        assertEquals(
            listOf(ProfileViolation(QuestProperty.REFRESH_RATE, 144, ViolationReason.RATE_NOT_DECLARED)),
            GameProfile(refreshRate = 144).validateFor(q3),
        )
        assertEquals(
            listOf(ProfileViolation(QuestProperty.REFRESH_RATE, 200, ViolationReason.RATE_NOT_DECLARED)),
            GameProfile(refreshRate = 200).validateFor(q3, (72..150).toSet()),
        )
    }

    @Test
    fun `une frequence hors de la liste fermee reste hors plage, meme declaree`() {
        listOf(150, 207).forEach { rate ->
            assertEquals(
                listOf(ProfileViolation(QuestProperty.REFRESH_RATE, rate, ViolationReason.OUT_OF_RANGE)),
                GameProfile(refreshRate = rate).validateFor(q3, quest3Screen),
            )
        }
    }

    @Test
    fun `resolution au-dessus du maximum de la frequence refusee`() {
        val above = EyeTexture.forStep(q3.defaultEyeTexture, 150)
        assertEquals(
            listOf(ProfileViolation(QuestProperty.TEXTURE_WIDTH, above.width, ViolationReason.ABOVE_RATE_LIMIT)),
            GameProfile(refreshRate = 200, eyeTexture = above).validateFor(q3, quest3Screen),
        )
        assertTrue(GameProfile(refreshRate = 200).validateFor(q3, quest3Screen).isEmpty())
        assertTrue(
            GameProfile(refreshRate = 200, eyeTexture = EyeTexture.forStep(q3.defaultEyeTexture, 80))
                .validateFor(q3, quest3Screen).isEmpty(),
        )
        // SC-005 : rien ne change à 120 Hz.
        assertTrue(GameProfile(refreshRate = 120, eyeTexture = above).validateFor(q3, quest3Screen).isEmpty())
    }

    @Test
    fun `violation a expliquer en premier`() {
        val cpu = ProfileViolation(QuestProperty.CPU_LEVEL, 9)
        val rate = ProfileViolation(QuestProperty.REFRESH_RATE, 200, ViolationReason.RATE_NOT_DECLARED)
        val limit = ProfileViolation(QuestProperty.TEXTURE_WIDTH, 2520, ViolationReason.ABOVE_RATE_LIMIT)
        assertEquals(rate, listOf(cpu, rate).primary())
        assertEquals(rate, listOf(limit, rate).primary())
        assertEquals(limit, listOf(cpu, limit).primary())
        assertNull(listOf(cpu).primary())
        assertNull(emptyList<ProfileViolation>().primary())
    }

    @Test
    fun `avertissement de frequence elevee`() {
        assertEquals(
            setOf(ProfileWarning.HIGH_REFRESH_RATE),
            GameProfile(refreshRate = 144, eyeTexture = EyeTexture.forStep(q3.defaultEyeTexture, 80)).warnings(q3),
        )
        assertTrue(ProfileWarning.HIGH_REFRESH_RATE !in GameProfile(refreshRate = 120).warnings(q3))
        assertTrue(GameProfile().warnings(q3).isEmpty())
    }

    @Test
    fun `resolution laissee au jeu a frequence elevee`() {
        assertEquals(
            setOf(ProfileWarning.HIGH_REFRESH_RATE, ProfileWarning.HIGH_RATE_GAME_RESOLUTION),
            GameProfile(refreshRate = 144).warnings(q3),
        )
        assertTrue(ProfileWarning.HIGH_RATE_GAME_RESOLUTION !in GameProfile(refreshRate = 120).warnings(q3))
    }

    // --- Avertissements (FR-018)

    @Test
    fun `pas d'avertissement aux reglages moderes`() {
        assertTrue(GameProfile().warnings(q3).isEmpty())
        assertTrue(GameProfile(cpuLevel = 2, gpuLevel = 3, eyeTexture = q3.defaultEyeTexture).warnings(q3).isEmpty())
    }

    @Test
    fun `chauffe si un niveau est au maximum ou juste en dessous`() {
        // Quest 3 : CPU 0..4 → alerte dès 3 ; GPU 0..5 → alerte dès 4.
        assertEquals(setOf(ProfileWarning.HEAT), GameProfile(cpuLevel = 3).warnings(q3))
        assertEquals(setOf(ProfileWarning.HEAT), GameProfile(gpuLevel = 4).warnings(q3))
        assertTrue(GameProfile(gpuLevel = 3).warnings(q3).isEmpty())
    }

    @Test
    fun `chauffe au-dela de 100 pour cent et perte de fluidite au-dela de 130`() {
        val default = q3.defaultEyeTexture
        assertEquals(setOf(ProfileWarning.HEAT), GameProfile(eyeTexture = EyeTexture.forStep(default, 110)).warnings(q3))
        assertEquals(setOf(ProfileWarning.HEAT), GameProfile(eyeTexture = EyeTexture.forStep(default, 130)).warnings(q3))
        assertEquals(
            setOf(ProfileWarning.HEAT, ProfileWarning.SMOOTHNESS),
            GameProfile(eyeTexture = EyeTexture.forStep(default, 140)).warnings(q3),
        )
    }

    @Test
    fun `les avertissements valent aussi pour une taille personnalisee`() {
        assertEquals(
            setOf(ProfileWarning.HEAT, ProfileWarning.SMOOTHNESS),
            GameProfile(eyeTexture = EyeTexture(2300, 2000)).warnings(q3),
        )
    }

    // --- Sérialisation (contracts/profiles-json.md)

    @Test
    fun `aller-retour JSON sans ecrire les champs par defaut`() {
        val profile = GameProfile(refreshRate = 90, foveationLevel = FoveationLevel.MEDIUM)
        val text = json.encodeToString(GameProfile.serializer(), profile)
        assertEquals("""{"refreshRate":90,"foveationLevel":"MEDIUM"}""", text)
        assertEquals(profile, json.decodeFromString(GameProfile.serializer(), text))
    }

    @Test
    fun `aller-retour JSON complet`() {
        val profile = GameProfile(120, EyeTexture(2016, 2112), 4, 4, FoveationLevel.HIGH, false)
        val text = json.encodeToString(GameProfile.serializer(), profile)
        assertEquals(profile, json.decodeFromString(GameProfile.serializer(), text))
    }

    @Test
    fun `les champs inconnus sont ignores`() {
        val profile = json.decodeFromString(GameProfile.serializer(), """{"refreshRate":72,"futur":true}""")
        assertEquals(GameProfile(refreshRate = 72), profile)
    }
}
