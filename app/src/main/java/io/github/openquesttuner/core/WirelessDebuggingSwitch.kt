package io.github.openquesttuner.core

/**
 * Réglages système du casque lus sans shell, et la seule écriture de réglage système de l'appli
 * (specs/002-standalone-reconnect/contracts/wireless-switch.md). Toutes les fonctions sont
 * synchrones et rapides : lectures locales et une écriture, sans réseau.
 */
interface WirelessDebuggingSwitch {

    /** L'appli détient `WRITE_SECURE_SETTINGS`, accordée par C9 ou à la main depuis un PC. */
    fun hasWriteRight(): Boolean

    /** Débogage USB actif (`adb_enabled` = 1). Valeur illisible : `true`, pour ne pas bloquer à tort. */
    fun isDebuggingEnabled(): Boolean

    /** Le réseau actif est un Wi-Fi. */
    fun isOnWifi(): Boolean

    /** Débogage sans fil actif (`adb_wifi_enabled` = 1). Valeur illisible : `false`. */
    fun isWirelessDebuggingEnabled(): Boolean

    /**
     * Délai d'expiration des autorisations de débogage (`adb_allowed_connection_time`), en ms.
     * `null` : clé absente, délai par défaut du système (7 jours) ; `0` : jamais. Une valeur non
     * numérique est rendue comme `-1`, hors plage : l'appli ne la modifie pas (FR-024). Lecture
     * seule : seules les commandes C11 à C13 changent ce délai.
     */
    fun authorizationLifetimeMs(): Long?

    /**
     * Seule écriture de réglage système de l'appli : `adb_wifi_enabled = 1` (FR-015). Horizon OS
     * la traite comme la commande C7 : fenêtre « autoriser sur ce réseau » si le réseau n'est pas
     * encore autorisé, puis retour à 0 s'il est refusé.
     * @throws SecurityException sans la permission.
     */
    fun enableWirelessDebugging()
}
