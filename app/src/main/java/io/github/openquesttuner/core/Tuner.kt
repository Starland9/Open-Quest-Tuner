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

/** Séquences de commandes de contracts/shell-commands.md, sur un [ShellBackend] quelconque. */
class Tuner(private val shell: ShellBackend, private val model: QuestModel) {

    /**
     * « Appliquer et lancer » (FR-019, FR-020) : arrêt du jeu, les 7 propriétés dans l'ordre de
     * l'enum (valeur du profil, ou remise à vide), puis lancement. S'arrête au premier échec.
     *
     * @throws IllegalArgumentException si [packageName] ou [activity] est invalide, avant tout envoi.
     */
    suspend fun applyAndLaunch(packageName: String, activity: String, profile: GameProfile): TuneResult {
        val violations = profile.validateFor(model)
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
}
