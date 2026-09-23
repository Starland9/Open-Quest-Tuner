package io.github.openquesttuner.core

/**
 * État thermique du casque, tel que le système le rapporte (FR-031, research.md R10). L'ordre des
 * sept premières constantes suit les codes `PowerManager.THERMAL_STATUS_*` d'Android, de 0 à 6.
 */
enum class ThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN,
    ;

    /** À partir de « modéré », l'appli conseille de baisser les niveaux ou de faire une pause (FR-032). */
    val warning: Boolean get() = this != UNKNOWN && this >= MODERATE

    companion object {
        fun fromAndroidStatus(code: Int): ThermalLevel =
            entries.getOrNull(code)?.takeIf { it != UNKNOWN } ?: UNKNOWN
    }
}
