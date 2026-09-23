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

        private fun requirePackage(packageName: String) {
            require(isValidPackageName(packageName)) { "Nom de paquet invalide : $packageName" }
        }
    }
}
