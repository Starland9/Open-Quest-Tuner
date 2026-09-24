package io.github.openquesttuner.core

// Valeurs de research.md R2 (docs Meta 2025–2026). Le Quest 3 sert aussi de repli pour un casque
// non reconnu (FR-016).
private val QUEST_3_EYE_TEXTURE = EyeTexture(1680, 1760)
private val QUEST_3_REFRESH_RATES = listOf(72, 80, 90, 96, 100, 120)

/**
 * Modèles de casque et valeurs proposées pour chacun.
 *
 * [alwaysAvailableCpuMax] / [alwaysAvailableGpuMax] : au-delà, le niveau dépend de conditions
 * (passthrough coupé, suivi désactivé…) et l'interface l'explique.
 * [verified] : propriétés dont l'effet a été constaté sur ce modèle et consigné dans
 * docs/compatibility.md. Tout le reste est « expérimental » (FR-017, principe III).
 * [autoReconnectVerified] : reconnexion autonome vérifiée sur ce modèle et consignée dans
 * docs/compatibility.md ; sinon, badge « expérimental » (spec 002, FR-018).
 */
enum class QuestModel(
    val displayName: String,
    val deviceCodenames: Set<String>,
    val modelNames: Set<String>,
    val defaultEyeTexture: EyeTexture,
    val refreshRates: List<Int>,
    val cpuLevels: IntRange,
    val gpuLevels: IntRange,
    val alwaysAvailableCpuMax: Int,
    val alwaysAvailableGpuMax: Int,
    val verified: Set<QuestProperty>,
    val autoReconnectVerified: Boolean = false,
) {
    QUEST_3(
        displayName = "Quest 3",
        deviceCodenames = setOf("eureka"),
        modelNames = setOf("Quest 3"),
        defaultEyeTexture = QUEST_3_EYE_TEXTURE,
        refreshRates = QUEST_3_REFRESH_RATES,
        cpuLevels = 0..4,
        gpuLevels = 0..5,
        alwaysAvailableCpuMax = 3,
        alwaysAvailableGpuMax = 2,
        // Essais sur Beat Saber, vros 207 (docs/compatibility.md, 2026-09-23). Le fovéal
        // dynamique n'apparaît pas dans les statistiques VrApi : il reste expérimental.
        verified = setOf(
            QuestProperty.REFRESH_RATE,
            QuestProperty.TEXTURE_WIDTH,
            QuestProperty.TEXTURE_HEIGHT,
            QuestProperty.CPU_LEVEL,
            QuestProperty.GPU_LEVEL,
            QuestProperty.FOVEATION_LEVEL,
        ),
        // Reconnexion autonome : 5 redémarrages sur 5 sans PC, vros 207 (docs/compatibility.md,
        // 2026-09-24).
        autoReconnectVerified = true,
    ),
    QUEST_3S(
        displayName = "Quest 3S",
        deviceCodenames = setOf("panther"),
        modelNames = setOf("Quest 3S"),
        defaultEyeTexture = EyeTexture(1680, 1760),
        refreshRates = listOf(72, 80, 90, 96, 100, 120),
        cpuLevels = 0..4,
        gpuLevels = 0..5,
        alwaysAvailableCpuMax = 3,
        alwaysAvailableGpuMax = 2,
        verified = emptySet(),
    ),
    QUEST_2(
        displayName = "Quest 2",
        deviceCodenames = setOf("hollywood"),
        modelNames = setOf("Quest 2"),
        defaultEyeTexture = EyeTexture(1440, 1584),
        refreshRates = listOf(72, 80, 90, 96, 100, 120),
        cpuLevels = 0..4,
        gpuLevels = 0..4,
        alwaysAvailableCpuMax = 4,
        alwaysAvailableGpuMax = 4,
        verified = emptySet(),
    ),
    QUEST_PRO(
        displayName = "Quest Pro",
        deviceCodenames = setOf("seacliff"),
        modelNames = setOf("Quest Pro"),
        defaultEyeTexture = EyeTexture(1440, 1584),
        refreshRates = listOf(72, 80, 90),
        cpuLevels = 0..4,
        gpuLevels = 0..4,
        alwaysAvailableCpuMax = 3,
        alwaysAvailableGpuMax = 3,
        verified = emptySet(),
    ),
    UNKNOWN(
        displayName = "Unknown",
        deviceCodenames = emptySet(),
        modelNames = emptySet(),
        defaultEyeTexture = QUEST_3_EYE_TEXTURE,
        refreshRates = QUEST_3_REFRESH_RATES,
        cpuLevels = 0..4,
        gpuLevels = 0..5,
        alwaysAvailableCpuMax = 3,
        alwaysAvailableGpuMax = 2,
        verified = emptySet(),
    );

    fun isVerified(property: QuestProperty): Boolean = property in verified

    companion object {
        /** Détection par `Build.DEVICE` (insensible à la casse), puis `Build.MODEL`, sinon [UNKNOWN]. */
        fun fromBuild(device: String, model: String): QuestModel {
            val codename = device.trim().lowercase()
            entries.firstOrNull { codename in it.deviceCodenames }?.let { return it }
            val name = model.trim()
            return entries.firstOrNull { entry -> entry.modelNames.any { it.equals(name, ignoreCase = true) } }
                ?: UNKNOWN
        }
    }
}
