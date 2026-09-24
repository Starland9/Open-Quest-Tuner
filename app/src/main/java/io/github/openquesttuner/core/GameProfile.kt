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

    /**
     * Vrai si les 7 propriétés actives sur le casque ([active], issu du diagnostic) correspondent
     * à ce profil ; un réglage « Par défaut du jeu » exige une propriété vide (FR-033).
     */
    fun isActiveOn(active: Map<String, String>): Boolean =
        toPropertyValues().all { (property, value) -> active[property.key] == value?.toString() }

    /**
     * Valeurs hors des plages du modèle ; si la liste n'est pas vide, aucune commande n'est envoyée.
     * Une fréquence au-delà de 120 Hz doit en plus être déclarée par l'écran : [declaredRates] est
     * vide par défaut, donc sans lui toute fréquence élevée est refusée (spec 003, research.md R5).
     */
    fun validateFor(model: QuestModel, declaredRates: Set<Int> = emptySet()): List<ProfileViolation> = buildList {
        refreshRate?.let { rate ->
            when {
                rate in model.refreshRates -> Unit
                rate !in RefreshRatePolicy.HIGH_REFRESH_RATES -> add(ProfileViolation(QuestProperty.REFRESH_RATE, rate))
                rate !in declaredRates ->
                    add(ProfileViolation(QuestProperty.REFRESH_RATE, rate, ViolationReason.RATE_NOT_DECLARED))
            }
        }
        eyeTexture?.let { texture ->
            val bounds = EyeTexture.MIN_DIM..EyeTexture.MAX_DIM
            if (texture.width !in bounds) add(ProfileViolation(QuestProperty.TEXTURE_WIDTH, texture.width))
            if (texture.height !in bounds) add(ProfileViolation(QuestProperty.TEXTURE_HEIGHT, texture.height))
            // Spec 003, FR-005 : à fréquence élevée permise, la résolution a un maximum.
            val rateAccepted = none { it.property == QuestProperty.REFRESH_RATE }
            if (rateAccepted && !RefreshRatePolicy.allowed(refreshRate, texture, model.defaultEyeTexture)) {
                add(ProfileViolation(QuestProperty.TEXTURE_WIDTH, texture.width, ViolationReason.ABOVE_RATE_LIMIT))
            }
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
        // Spec 003, FR-004 : le jeu doit suivre la cadence ; sa propre résolution peut être trop élevée.
        if (refreshRate != null && RefreshRatePolicy.isHigh(refreshRate)) {
            add(ProfileWarning.HIGH_REFRESH_RATE)
            if (eyeTexture == null) add(ProfileWarning.HIGH_RATE_GAME_RESOLUTION)
        }
    }
}

data class ProfileViolation(
    val property: QuestProperty,
    val value: Int,
    val reason: ViolationReason = ViolationReason.OUT_OF_RANGE,
)

enum class ViolationReason {
    /** Hors des plages du modèle de casque (MVP). */
    OUT_OF_RANGE,

    /** Fréquence au-delà de 120 Hz que l'écran ne déclare pas (spec 003, FR-008). */
    RATE_NOT_DECLARED,

    /** Résolution au-dessus du maximum de la fréquence du profil (spec 003, FR-005, FR-008). */
    ABOVE_RATE_LIMIT,
}

/**
 * Violation à expliquer en premier : fréquence non déclarée, puis résolution trop élevée pour la
 * fréquence ; `null` pour le message du MVP. La règle de priorité des messages de refus est ici,
 * pas dans l'interface (principe IV, data-model.md de la spec 003).
 */
fun List<ProfileViolation>.primary(): ProfileViolation? =
    firstOrNull { it.reason == ViolationReason.RATE_NOT_DECLARED }
        ?: firstOrNull { it.reason == ViolationReason.ABOVE_RATE_LIMIT }

enum class ProfileWarning { HEAT, SMOOTHNESS, HIGH_REFRESH_RATE, HIGH_RATE_GAME_RESOLUTION }
