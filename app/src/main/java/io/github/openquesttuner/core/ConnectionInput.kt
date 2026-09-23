package io.github.openquesttuner.core

/**
 * Validation des saisies de connexion. Ces valeurs servent uniquement à ouvrir un socket ; elles
 * ne vont jamais au shell (FR-025).
 */
object ConnectionInput {

    private val PAIRING_CODE = Regex("^[0-9]{6}$")
    private val DIGITS = Regex("^[0-9]{1,5}$")

    fun isValidPairingCode(code: String): Boolean = PAIRING_CODE.matches(code)

    /** Port dans 1–65535, ou `null` si la saisie est invalide. */
    fun parsePort(text: String): Int? {
        if (!DIGITS.matches(text)) return null
        return text.toInt().takeIf { it in 1..65535 }
    }
}
