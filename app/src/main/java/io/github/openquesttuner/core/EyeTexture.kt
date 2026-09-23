package io.github.openquesttuner.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Paliers de résolution proposés, en pourcentage entier de la résolution par défaut (FR-011). */
val RESOLUTION_STEPS: List<Int> = (70..150 step 10).toList()

/** Taille du buffer de rendu par œil, en pixels. */
@Serializable
data class EyeTexture(val width: Int, val height: Int) {

    companion object {
        /**
         * Bornes acceptées pour chaque dimension. La borne haute protège le casque : l'environnement
         * Home a planté vers 3070 px (research.md R2).
         */
        const val MIN_DIM = 512
        const val MAX_DIM = 3072

        /** Taille pour un palier donné, chaque dimension arrondie au multiple de 8 le plus proche. */
        fun forStep(default: EyeTexture, percent: Int): EyeTexture = EyeTexture(
            width = roundToMultipleOf8(default.width * percent / 100.0),
            height = roundToMultipleOf8(default.height * percent / 100.0),
        )

        /** Palier correspondant à [texture], ou `null` si c'est une taille personnalisée. */
        fun stepOf(default: EyeTexture, texture: EyeTexture): Int? =
            RESOLUTION_STEPS.firstOrNull { forStep(default, it) == texture }

        private fun roundToMultipleOf8(value: Double): Int = (value / 8).roundToInt() * 8
    }
}
