package io.github.openquesttuner.core

/** Écran simulé ; par défaut, celui du Quest 3 à sa résolution native (research.md R1). */
class FakeDisplayRates(var declared: Set<Int> = (72..207).toSet()) : DisplayRates {

    var reads = 0
        private set

    override fun declaredRefreshRates(): Set<Int> {
        reads++
        return declared
    }
}
