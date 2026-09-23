package io.github.openquesttuner.core

import kotlinx.serialization.Serializable

/**
 * Réglages voulus pour un jeu. Un champ `null` signifie « Par défaut du jeu » : la propriété est
 * remise à vide au lancement, pour ne pas hériter du jeu précédent (FR-012, FR-019).
 */
@Serializable
data class GameProfile(
    val refreshRate: Int? = null,
    val eyeTexture: EyeTexture? = null,
    val cpuLevel: Int? = null,
    val gpuLevel: Int? = null,
    val foveationLevel: FoveationLevel? = null,
    val dynamicFoveation: Boolean? = null,
) {
    /** Un profil vide équivaut à une absence de profil (FR-015). */
    val isEmpty: Boolean get() = this == GameProfile()

    /** Les 7 propriétés, dans l'ordre d'application ; `null` déclenche une réinitialisation. */
    fun toPropertyValues(): Map<QuestProperty, Int?> = QuestProperty.entries.associateWith { property ->
        when (property) {
            QuestProperty.REFRESH_RATE -> refreshRate
            QuestProperty.TEXTURE_WIDTH -> eyeTexture?.width
            QuestProperty.TEXTURE_HEIGHT -> eyeTexture?.height
            QuestProperty.CPU_LEVEL -> cpuLevel
            QuestProperty.GPU_LEVEL -> gpuLevel
            QuestProperty.FOVEATION_LEVEL -> foveationLevel?.code
            QuestProperty.FOVEATION_DYNAMIC -> dynamicFoveation?.let { if (it) 1 else 0 }
        }
    }

    /** Valeurs hors des plages du modèle ; si la liste n'est pas vide, aucune commande n'est envoyée. */
    fun validateFor(model: QuestModel): List<ProfileViolation> = buildList {
        refreshRate?.let { if (it !in model.refreshRates) add(ProfileViolation(QuestProperty.REFRESH_RATE, it)) }
        eyeTexture?.let { texture ->
            val bounds = EyeTexture.MIN_DIM..EyeTexture.MAX_DIM
            if (texture.width !in bounds) add(ProfileViolation(QuestProperty.TEXTURE_WIDTH, texture.width))
            if (texture.height !in bounds) add(ProfileViolation(QuestProperty.TEXTURE_HEIGHT, texture.height))
        }
        cpuLevel?.let { if (it !in model.cpuLevels) add(ProfileViolation(QuestProperty.CPU_LEVEL, it)) }
        gpuLevel?.let { if (it !in model.gpuLevels) add(ProfileViolation(QuestProperty.GPU_LEVEL, it)) }
    }

    /** Avertissements de chauffe et de fluidité (FR-018), selon la règle de data-model.md. */
    fun warnings(model: QuestModel): Set<ProfileWarning> = buildSet {
        // Rapport en pourcentage de la largeur par défaut : vaut aussi pour une taille personnalisée.
        val ratio = eyeTexture?.let { it.width * 100 / model.defaultEyeTexture.width }
        val hotCpu = cpuLevel != null && cpuLevel >= model.cpuLevels.last - 1
        val hotGpu = gpuLevel != null && gpuLevel >= model.gpuLevels.last - 1
        if (hotCpu || hotGpu || (ratio != null && ratio > 100)) add(ProfileWarning.HEAT)
        if (ratio != null && ratio > 130) add(ProfileWarning.SMOOTHNESS)
    }
}

data class ProfileViolation(val property: QuestProperty, val value: Int)

enum class ProfileWarning { HEAT, SMOOTHNESS }
