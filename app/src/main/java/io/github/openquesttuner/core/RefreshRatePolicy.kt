package io.github.openquesttuner.core

/**
 * Profil après le choix d'une fréquence ; [loweredToStep] : palier auquel la résolution a été
 * abaissée, à signaler à l'utilisateur (FR-006, SC-004), ou `null`.
 */
data class RateSelection(val profile: GameProfile, val loweredToStep: Int?)

/**
 * Fréquences d'affichage au-delà de 120 Hz (specs/003-high-refresh-rates/data-model.md). Règles
 * pures, testées en JVM (principe IV) ; l'écran n'arrive ici que par un ensemble d'entiers.
 */
object RefreshRatePolicy {

    /** Au-delà, une fréquence est « élevée » : c'était la plus haute fréquence du MVP. */
    const val STANDARD_MAX_RATE = 120

    /** Liste fermée des fréquences élevées ; rien au-delà de 200 Hz, même déclaré (FR-001, FR-002). */
    val HIGH_REFRESH_RATES: List<Int> = listOf(144, 160, 180, 200)

    fun isHigh(rate: Int): Boolean = rate > STANDARD_MAX_RATE

    /** Pour tous les modèles, en plus du statut par propriété de `QuestModel` (FR-003, research.md R7). */
    fun isExperimental(rate: Int): Boolean = isHigh(rate)

    /**
     * Fréquences proposées : celles du modèle, sans filtre (SC-005), puis les fréquences élevées
     * que l'écran déclare ([declared], vide s'il est illisible).
     */
    fun available(model: QuestModel, declared: Set<Int>): List<Int> =
        model.refreshRates + HIGH_REFRESH_RATES.filter { it in declared }

    /**
     * Palier de résolution maximal (en %) par fréquence élevée. Table de FR-005 : √(160 ÷ fréquence),
     * arrondi au palier inférieur. Liste fermée, à revoir seulement avec un amendement de la spec.
     */
    private val RESOLUTION_CAPS = mapOf(144 to 100, 160 to 100, 180 to 90, 200 to 80)

    /** `null` : pas de limite (120 Hz ou moins, ou « Par défaut du jeu »), FR-007. */
    fun maxResolutionStep(rate: Int?): Int? = rate?.let(RESOLUTION_CAPS::get)

    /**
     * Vrai si [texture] est permise à [rate] : « Par défaut du jeu », pas de limite, ou les deux
     * dimensions au plus égales à celles du palier maximal. Vaut aussi pour une taille personnalisée.
     */
    fun allowed(rate: Int?, texture: EyeTexture?, default: EyeTexture): Boolean {
        val max = maxResolutionStep(rate) ?: return true
        if (texture == null) return true
        val limit = EyeTexture.forStep(default, max)
        return texture.width <= limit.width && texture.height <= limit.height
    }

    /** Paliers visibles mais indisponibles à [rate] (FR-005). */
    fun unavailableSteps(rate: Int?, default: EyeTexture): List<Int> =
        RESOLUTION_STEPS.filterNot { allowed(rate, EyeTexture.forStep(default, it), default) }

    /**
     * Choix d'une fréquence dans un profil (data-model.md, « Choisir une fréquence »). Une résolution
     * au-dessus du maximum est abaissée au palier maximal (FR-006) ; revenir à 120 Hz ou moins ne la
     * remonte pas (FR-007).
     */
    fun selectRate(profile: GameProfile, rate: Int?, model: QuestModel): RateSelection {
        val chosen = profile.copy(refreshRate = rate)
        val default = model.defaultEyeTexture
        if (allowed(rate, chosen.eyeTexture, default)) return RateSelection(chosen, null)
        val max = requireNotNull(maxResolutionStep(rate))
        return RateSelection(chosen.copy(eyeTexture = EyeTexture.forStep(default, max)), max)
    }
}
