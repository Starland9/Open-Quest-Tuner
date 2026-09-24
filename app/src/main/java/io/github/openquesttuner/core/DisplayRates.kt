package io.github.openquesttuner.core

import kotlin.math.roundToInt

/**
 * Fréquences que l'écran du casque déclare, lues sans shell ni connexion
 * (specs/003-high-refresh-rates/contracts/display-rates.md). Lecture seule.
 */
interface DisplayRates {

    /**
     * Fréquences déclarées par l'écran 0, en Hz entiers. Ensemble vide si la lecture échoue :
     * aucune fréquence élevée n'est alors proposée ni permise (FR-001).
     */
    fun declaredRefreshRates(): Set<Int>

    companion object {
        /** Arrondit les fréquences des modes (`207.00003` → 207), en ignorant les valeurs invalides. */
        fun ratesFromModes(rates: Collection<Float>): Set<Int> =
            rates.filter { it.isFinite() && it > 0f }.mapTo(HashSet()) { it.roundToInt() }
    }
}
