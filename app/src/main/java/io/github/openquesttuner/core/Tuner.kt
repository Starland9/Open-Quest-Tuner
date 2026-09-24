package io.github.openquesttuner.core

sealed interface TuneResult {
    data object Success : TuneResult
    data object NotConnected : TuneResult
    data class InvalidProfile(val violations: List<ProfileViolation>) : TuneResult
    data class StepFailed(val step: TuneStep, val output: String) : TuneResult
}

sealed interface TuneStep {
    data object ForceStop : TuneStep
    data class SetProperty(val property: QuestProperty) : TuneStep
    data object Launch : TuneStep
}

/** Résultat de « Tout réinitialiser » ; une liste vide signifie un succès complet. */
data class ResetResult(val failed: List<QuestProperty>)

/** Propriétés `debug.oculus.*` non vides lues sur le casque, triées par clé (FR-024). */
data class Diagnostic(val active: Map<String, String>)

/**
 * Séquences de commandes de contracts/shell-commands.md, sur un [ShellBackend] quelconque.
 * [display] donne les fréquences que l'écran déclare (spec 003).
 */
class Tuner(
    private val shell: ShellBackend,
    private val model: QuestModel,
    private val display: DisplayRates,
) {

    /**
     * « Appliquer et lancer » (FR-019, FR-020) : arrêt du jeu, les 7 propriétés dans l'ordre de
     * l'enum (valeur du profil, ou remise à vide), puis lancement. S'arrête au premier échec.
     *
     * @throws IllegalArgumentException si [packageName] ou [activity] est invalide, avant tout envoi.
     */
    suspend fun applyAndLaunch(packageName: String, activity: String, profile: GameProfile): TuneResult {
        // Écran lu au moment du lancement : couvre un profil venu d'un autre casque, ou une mise à
        // jour d'Horizon OS qui retire une fréquence (spec 003, cas limites).
        val violations = profile.validateFor(model, display.declaredRefreshRates())
        if (violations.isNotEmpty()) return TuneResult.InvalidProfile(violations)

        // Toutes les commandes sont construites d'abord : une valeur ou un nom refusé par les
        // fabriques lève une exception sans qu'aucune commande n'ait été envoyée.
        val steps = buildList {
            add(TuneStep.ForceStop to ShellCommand.forceStop(packageName))
            for ((property, value) in profile.toPropertyValues()) {
                val command = value?.let { ShellCommand.setProperty(property, it) } ?: ShellCommand.resetProperty(property)
                add(TuneStep.SetProperty(property) to command)
            }
        }
        val launch = ShellCommand.launch(packageName, activity)

        return try {
            for ((step, command) in steps) {
                val result = shell.exec(command)
                if (!result.isSuccess) return TuneResult.StepFailed(step, result.output)
            }
            val result = shell.exec(launch)
            if (result.isSuccess && !ShellOutput.isLaunchError(result.output)) {
                TuneResult.Success
            } else {
                TuneResult.StepFailed(TuneStep.Launch, result.output)
            }
        } catch (e: ShellUnavailableException) {
            TuneResult.NotConnected
        }
    }

    /**
     * « Tout réinitialiser » (FR-023) : remet les 7 propriétés à vide. Toutes sont tentées même
     * si l'une échoue, pour ne jamais laisser plus de réglages actifs que nécessaire (principe I).
     *
     * @return `null` si l'appli n'est pas connectée, ou si la connexion est perdue en cours de route.
     */
    suspend fun resetAll(): ResetResult? = try {
        val failed = QuestProperty.entries.filterNot { property ->
            shell.exec(ShellCommand.resetProperty(property)).isSuccess
        }
        ResetResult(failed)
    } catch (e: ShellUnavailableException) {
        null
    }

    /**
     * Diagnostic (FR-024) : une seule lecture `getprop`, filtrée côté appli. Sert aussi à
     * l'indicateur « actif sur le casque » de l'écran profil (FR-033).
     *
     * @return `null` si l'appli n'est pas connectée ou si la lecture échoue.
     */
    suspend fun readDiagnostic(): Diagnostic? = try {
        val result = shell.exec(ShellCommand.readProperties())
        if (result.isSuccess) Diagnostic(ShellOutput.parseGetprop(result.output)) else null
    } catch (e: ShellUnavailableException) {
        null
    }
}
