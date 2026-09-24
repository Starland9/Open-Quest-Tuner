package io.github.openquesttuner.core

/**
 * Commande shell de la liste fermée de contracts/shell-commands.md.
 *
 * Le constructeur est privé : les fabriques du companion sont le seul moyen d'en obtenir une, et
 * [ShellBackend.exec] n'accepte que ce type. Aucune chaîne libre ne peut donc atteindre le shell
 * (principe I, FR-025).
 */
class ShellCommand private constructor(val text: String) {

    /** Texte réellement envoyé : le service `shell:` historique ne transmet pas le code de sortie. */
    val wireText: String get() = "$text; echo ${ShellOutput.EXIT_MARKER}\$?"

    override fun toString(): String = text

    companion object {
        /** Paquet de l'appli, seule cible de C9 et C10 (vérifié par `AppPackageTest`). */
        const val APP_PACKAGE = "io.github.openquesttuner"

        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"

        private const val KEY_ALLOWED_CONNECTION_TIME = "adb_allowed_connection_time"

        /** Borne de C12 : 3 650 jours (principe I : entiers bornés par des plages connues). */
        const val MAX_KEY_LIFETIME_MS = 315_360_000_000L

        val PACKAGE_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        val CLASS_REGEX = Regex("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$")

        fun isValidPackageName(name: String): Boolean = PACKAGE_REGEX.matches(name)

        fun isValidClassName(name: String): Boolean = CLASS_REGEX.matches(name)

        /** C1 */
        fun setProperty(property: QuestProperty, value: Int): ShellCommand {
            require(value in property.absoluteRange) {
                "Valeur $value hors de la plage absolue ${property.absoluteRange} de ${property.key}"
            }
            return ShellCommand("setprop ${property.key} $value")
        }

        /** C2 : la chaîne vide rend la main au jeu (méthode officielle de Meta, research.md R7). */
        fun resetProperty(property: QuestProperty): ShellCommand =
            ShellCommand("setprop ${property.key} ''")

        /** C3 */
        fun forceStop(packageName: String): ShellCommand {
            requirePackage(packageName)
            return ShellCommand("am force-stop --user current '$packageName'")
        }

        /** C4 : les quotes neutralisent notamment le `$` des classes internes. */
        fun launch(packageName: String, activity: String): ShellCommand {
            requirePackage(packageName)
            require(isValidClassName(activity)) { "Nom d'activité invalide : $activity" }
            return ShellCommand("am start --user current -n '$packageName/$activity'")
        }

        /** C5 */
        fun readProperties(): ShellCommand = ShellCommand("getprop")

        /** C6 : vérifie une connexion tout juste établie (libadb-android #34, research.md R3). */
        fun probe(): ShellCommand = ShellCommand("true")

        /**
         * C7 : active le débogage sans fil. Horizon OS demande d'abord d'autoriser le réseau ; le
         * réglage revient à 0 à chaque redémarrage, il n'est donc pas persistant (FR-026).
         */
        fun enableWirelessDebugging(): ShellCommand = ShellCommand("settings put global adb_wifi_enabled 1")

        /** C8 : lit l'état du débogage sans fil (voir [ShellOutput.isSettingEnabled]). */
        fun readWirelessDebugging(): ShellCommand = ShellCommand("settings get global adb_wifi_enabled")

        /**
         * C9 : l'appli s'accorde `WRITE_SECURE_SETTINGS`, pour réactiver le débogage sans fil après
         * un redémarrage (spec 002). Sans paramètre : elle ne peut viser qu'elle-même, et que cette
         * permission (principe I).
         */
        fun grantWriteSecureSettings(): ShellCommand =
            ShellCommand("pm grant '$APP_PACKAGE' $WRITE_SECURE_SETTINGS")

        /** C10 : retire la permission de C9, seulement si c'est l'appli qui l'a accordée (principe I). */
        fun revokeWriteSecureSettings(): ShellCommand =
            ShellCommand("pm revoke '$APP_PACKAGE' $WRITE_SECURE_SETTINGS")

        /**
         * C11 : les autorisations de débogage n'expirent plus, sur choix explicite de l'utilisateur
         * (spec 002, FR-021). Vaut pour toutes les clés du casque, et survit au redémarrage.
         */
        fun disableAuthorizationExpiry(): ShellCommand =
            ShellCommand("settings put global $KEY_ALLOWED_CONNECTION_TIME 0")

        /** C12 : rétablit le délai d'origine retenu avant C11 (FR-023). */
        fun restoreAuthorizationExpiry(ms: Long): ShellCommand {
            require(ms in 1..MAX_KEY_LIFETIME_MS) { "Délai hors plage : $ms" }
            return ShellCommand("settings put global $KEY_ALLOWED_CONNECTION_TIME $ms")
        }

        /** C13 : rétablit le délai par défaut du système (7 jours), quand la clé était absente avant C11. */
        fun resetAuthorizationExpiry(): ShellCommand =
            ShellCommand("settings delete global $KEY_ALLOWED_CONNECTION_TIME")

        private fun requirePackage(packageName: String) {
            require(isValidPackageName(packageName)) { "Nom de paquet invalide : $packageName" }
        }
    }
}
