package io.github.openquesttuner.core

import kotlinx.serialization.Serializable

/**
 * Liste blanche des propriétés système que l'appli peut modifier (FR-025, principe I).
 *
 * L'ordre des constantes est l'ordre d'application dans « Appliquer et lancer ».
 * [absoluteRange] est une borne de sécurité indépendante du casque, vérifiée en dernier rempart par
 * [ShellCommand.setProperty] ; la plage propre au modèle est vérifiée avant, par [GameProfile.validateFor].
 */
enum class QuestProperty(val key: String, val absoluteRange: IntRange) {
    REFRESH_RATE("debug.oculus.refreshRate", 60..240),
    TEXTURE_WIDTH("debug.oculus.textureWidth", EyeTexture.MIN_DIM..EyeTexture.MAX_DIM),
    TEXTURE_HEIGHT("debug.oculus.textureHeight", EyeTexture.MIN_DIM..EyeTexture.MAX_DIM),
    CPU_LEVEL("debug.oculus.cpuLevel", 0..7),
    GPU_LEVEL("debug.oculus.gpuLevel", 0..7),
    FOVEATION_LEVEL("debug.oculus.foveation.level", 0..4),
    FOVEATION_DYNAMIC("debug.oculus.foveation.dynamic", 0..1),
}

/** Niveaux du rendu fovéal fixe ; [code] est la valeur écrite dans `debug.oculus.foveation.level`. */
@Serializable
enum class FoveationLevel(val code: Int) {
    OFF(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    HIGH_TOP(4),
}
